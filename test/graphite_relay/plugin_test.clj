(ns graphite-relay.plugin_test
  (:require [graphite-relay.plugin :refer :all]
            [clojure.test :refer :all])
  (:use riemann.logging
        clojure.tools.logging)
  )


(def node-list [{:host "diag1" :port 2003}
                {:host "diag2" :port 2003}
                {:host "diag3" :port 2003}])
(init {:console? true})
(deftest spawn-test
  (testing "Initail Graphite pools"
     (let [gr (graphite-relay {:hosts node-list :dynamic-ring-population true :block-start false})]
        (time (Thread/sleep 10000))
       (info "Done...")
       )))
