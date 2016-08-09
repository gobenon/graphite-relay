(ns graphite-relay.plugin
 "A Riemann plug-in called graphite-relay.
  An extension to the built-in Riemann Graphite driver,
  implements a Graphite relay using consistent-hash mechanism"
  (:import [riemann.graphite GraphiteTCPClient GraphiteUDPClient])
  (:require [riemann.graphite :refer :all]
            [graphite-relay.consistent_hash :as consistent-hash])
  (:use clojure.tools.logging
        [clojure.string :only [join]]
        [riemann.pool :only [fixed-pool with-pool]]
        [riemann.transport :only [resolve-host]])
  )

(defn graphite-relay
  "Returns a function which accepts an event and sends it
  to selected Graphite server using \"consistent-hash\" mechanism.
  For each Graphite server its silently drops events when graphite is down.
  Attempts to reconnect automatically every five seconds.
  It removes the failure Graphite server(s) from the consistent-hash ring
  until Graphite service becomes available.
  Use: (graphite-relay.plugin/graphite-relay 
         {:hosts [{:host \"graphite1\" :port 2003}
                  {:host \"graphite2\" :port 2003}]})
  Options:
  :path       A function which, given an event, returns the string describing
              the path of that event in graphite.
              Default graphite-path-percentiles.
  :hash-fn    Hash function that will use for query and populate consistent-hash ring.
              Default sha1.
  :replicas   Number of replicas per-node in the ring
              Default 8.
  :dynamic-ring-population   true/false, dynamically updates the consistent-hash ring upon
                             Graphite server failure and recover.
                             It removes the failure Graphite server(s) from the consistent-hash
                             ring until Graphite service becomes available.
                             Default true.
  :block-start  Wait for the pool's initial connections to open
                to all Graphite hosts before returning.
                Default false.
  :hosts      list of Graphite hosts.
              Each host in the list is a map containing host properties:
              :host               Graphite hostname/IP.
                                  Require keyword.
              :port               Graphite service port.
                                  Default 2003.
              :pool-size          The number of connections to keep open.
                                  Default 4.
              :reconnect-interval How many seconds to wait between attempts to connect.
                                  Default 5.
              :claim-timeout      How many seconds to wait for a graphite connection from the pool.
                                  Default 0.1.
              :protocol           Protocol to use. Either :tcp or :udp.
                                  Default :tcp."
  [opts]
  (let [opts (merge {:block-start false
                     :replicas 8
                     :dynamic-ring-population true
                     :path graphite-path-percentiles
                     :metric graphite-metric}
                    opts)
        dest-list (map #(merge {:port 2003
                                 :protocol :tcp
                                 :claim-timeout 0.1
                                 :reconnect-interval 5
                                 :pool-size 4}
                                %)
                        (:hosts opts))
        ring (consistent-hash/new-consistent-hash-ring
              dest-list
              (get opts :replicas 8)
              (get opts :hash-fn consistent-hash/sha1))
        pools (loop [pools {}
                     [dest & dests] dest-list]
                (let [host (:host dest)
                      port (:port dest)]
                  (cond
                    (nil? dest) pools
                    :else
                    (recur (assoc pools
                                dest
                                (fixed-pool
                                 (fn []
                                   (info "Connecting to" host port)
                                   (try
                                     (let [ip (resolve-host host)
                                           client (open (condp = (:protocol dest)
                                                          :tcp (GraphiteTCPClient.
                                                                host port)
                                                          :udp (GraphiteUDPClient.
                                                                host port)))]
                                       (info "Connected to" host)
                                       (if (and (:dynamic-ring-population opts)
                                                (not (consistent-hash/nodeExist? ring dest)))
                                         (do
                                           (info "Adding host" 
                                                 host 
                                                 "to consistent hash ring")
                                           (consistent-hash/addNode ring dest)))
                                       client)
                                   (catch Exception e
                                     (if (and (:dynamic-ring-population opts)
                                              (consistent-hash/nodeExist? ring dest))
                                       (do
                                         (error "Failed to connect to"
                                               host
                                               "removing host from consistent hash ring")
                                         (consistent-hash/removeNode ring dest)))
                                     (throw e))))
                                 (fn [client]
                                   (info "Closing connection to" host port)
                                   (if (and (:dynamic-ring-population opts)
                                            (consistent-hash/nodeExist? ring dest))
                                     (do
                                       (info "Removing"
                                              host
                                              "from consistent hash ring")
                                       (consistent-hash/removeNode ring dest)))
                                   (close client))
                                 (-> opts
                                     (select-keys [:block-start])
                                     (assoc :size (:pool-size dest))
                                     (assoc :regenerate-interval
                                            (:reconnect-interval dest)))))
                         dests))))
        path (:path opts)]

    (fn [event]
      (when (:metric event)
        (let [dest (.lookUp ring (:service event))
              pool (get pools dest)]
          (debug "ring size" (consistent-hash/countNodes ring)
                "selected host"
                (:host dest) "for service"
                (:service event) "value:"
                (:metric event))
          (cond
            (nil? pool) nil
            (nil? dest) nil
            :else
            (with-pool [client pool (:claim-timeout dest)]
              (let [string (str (join " " [(path event)
                                           (graphite-metric event)
                                           (int (:time event))]) "\n")]
                (try
                  (send-line client string)
                  (catch Exception e
                    (if (and (:dynamic-ring-population opts)
                             (consistent-hash/nodeExist? ring dest))
                      (do
                        (error "Failed to send-line to"
                               (:host dest)
                               "removing host from consistent hash ring")
                        (consistent-hash/removeNode ring dest)))
                    (throw e)))
                ))
            ))))))
