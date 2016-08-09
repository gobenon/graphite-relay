(ns graphite-relay.consistent_hash_test
  (:require [clojure.test :refer :all]
            [graphite-relay.consistent_hash :refer :all]))

(def node-list [{:host "1.1.1.1" :port 1111}
                {:host "2.2.2.2" :port 2222}
                {:host "3.3.3.3" :port 3333}])

(def lookup-list ["AA.BB.CC.moco577.disk.sda.util" ; return third node
                  "AA.BB.CC.itzik577.disk.sda.util" ; return first node
                  "AA.BB.CC.avner155.disk.sda.util"]) ; return second node

(deftest initial-test
  (testing "Initail CHR with 3 nodes and replicas of 8"
    (let [replicas 8
          ring (new-consistent-hash-ring node-list replicas sha1)]
      (is (= (countNodes ring) (count node-list)))
      (is (= (partitionSize ring) (* replicas (countNodes ring))))
      (doseq [node node-list]
        (is (nodeExist? ring node)))
      )
    )
  )

(deftest add-node-test
  (testing "Adding node to the CHR ring"
    (let [replicas 2
          node {:host "4.4.4.4" :port 4444}
          ring (new-consistent-hash-ring node-list replicas sha1)]
      ;; add-node tests for a non-existing & existing node
      (doseq [_ (range 2)]
        (addNode ring node)
        (is (= (countNodes ring) (+ 1 (count node-list))))
        (is (nodeExist? ring node))
        (is (= (partitionSize ring) (* replicas (+ 1 (count node-list)))))
        (is (some #(if (= node %) true) (getNodes ring)))
      )
      )
    )
  )

(deftest remove-node-test
  (testing "Removing node from the CHR ring"
    (let [replicas 2
          node {:host "2.2.2.2" :port 2222}
          fack-node {:host "4.4.4.4" :port 4444}
          ring (new-consistent-hash-ring node-list replicas sha1)]
      ;; remove-node tests for a non-existing & existing node
      (doseq [_ (range 2)]
        (removeNode ring node)
        (is (= (countNodes ring) (- (count node-list) 1)))
        (is (nil? (nodeExist? ring node)))
        (is (= (partitionSize ring) (* replicas (- (count node-list) 1))))
        (is (nil? (some #(if (= node %) true) (getNodes ring))))
        )
      )
    )
  )

(defn lookup-iteration 
  [ring]
  (loop [lookup-map {}
         [obj & objects] lookup-list]
    (cond (nil? obj)
          lookup-map
          :else
          (recur 
           (assoc lookup-map (lookUp ring obj) (inc (get lookup-map (lookUp ring obj) 0))) 
           objects)))
  )

(deftest lookup-node-test
  (testing "Lookup CHR ring"
    (let [replicas 8
          ring (new-consistent-hash-ring node-list replicas sha1)
          lookup-map (lookup-iteration ring)]
      (is (not (some #(if-not (= 1 %) true) (vals lookup-map))))
      )
    )
  )
