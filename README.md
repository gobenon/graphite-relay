# graphite-relay

A Riemann plug-in called **_graphite-relay_**

An extension to built-in Riemann Graphite driver

Implements a Graphite relay using **_consistent-hash_** mechanism

## Description


Returns a function which accepts an event and sends it
to selected Graphite server using **_consistent-hash_** mechanism.
For each Graphite server its silently drops events when graphite is down.
Attempts to reconnect automatically every five seconds.
It removes the failure Graphite servers from the *consistent-hash* ring
until Graphite service becomes available.

### Usage
```clojure 
    (graphite-relay.plugin/graphite-relay 
      {:hosts [{:host "graphite1" :port 2003} 
               {:host "graphite2" :port 2003}]})
```
###Options

```
:path       A function which, given an event, returns the string describing
            the path of that event in graphite.
            Default graphite-path-percentiles.

:hash-fn    Hash function that will use for query and populate consistent-hash ring.
            Default sha1.

:replicas   Number of replicas per-node in the ring
            Default 8.


:dynamic-ring-population   true or false, dynamically updates the consistent-hash ring upon
                           Graphite server failure and recover.
                           It removes the failure Graphite servers from the consistent-hash
                           ring until Graphite service becomes available.
                           Default true.

:block-start  Wait for the pool's initial connections to open
              to all Graphite hosts before returning.
              Default false.

:hosts      list of Graphite hosts. 
            Each host in the list is a map containing host properties:
            :host               Graphite hostname or IP.
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
                                Default :tcp.
```

## License

Distributed under the Eclipse Public License, the same as Clojure.
