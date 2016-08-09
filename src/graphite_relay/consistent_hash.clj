(ns graphite-relay.consistent_hash
 (:import
   (java.security MessageDigest)
   ))
 

(defn sha1 [obj]
  "SHA1 hashing converted to digest vector."
  (let [bytes (.getBytes (with-out-str (pr obj)))] 
    (apply vector (.digest (MessageDigest/getInstance "SHA1") bytes))))

;; --API-- ;;
(defprotocol CHR-API
  "Consistent Hash Ring APIs"
  (initialRing
    [this] [this nodes])
  (addNode
    [this node])
  (hashNode
    [this node])
  (removeNode
    [this node])
  (nodeExist?
    [this node])
  (tailRing
    [this hash])
  (headRing
    [this hash])
  (lookUp
    [this obj])
  (getNodes
    [this])
  (countNodes
    [this])
  (partitionSize
    [this])
  )

(defrecord CHR
  ;; "CHR - Consistent Hashing Ring
  ;; :replicas      Number of replicas per-node in the ring
  ;; :hash-fn    Hash function that will use for query and populate consistent hash rin
  ;; :ring          An Atom map that will be used to populate nodes

  [replicas hash-fn ring]
  CHR-API

  (initialRing [this node-list]
    (reset! ring (reduce (fn[ring node] (addNode this node)) 
                         (sorted-map) node-list))
    )
  (addNode
      [this node]
    (reset! ring (apply merge @ring (hashNode this node)))
    )
  (hashNode
      [this node]
    (map #(hash-map (hash-fn (str node %)) node) (range replicas))
    )
  (removeNode
      [this node]
    (reset! ring (apply dissoc @ring (map first (map keys (hashNode this node)))))
    )
  (nodeExist?
      [this node]
    (some #(= node %) (vals @ring))
    )
  (tailRing
      [this hash]
    (filter #(<= 0 (compare (key %) hash)) @ring)
    )
  (headRing
      [this hash]
    (filter #(> 0 (compare (key %) hash)) @ring)
      )
  (lookUp
      [this obj]
    (if-not (empty? @ring)
      (let [hash (hash-fn obj)
            tail-ring (tailRing this hash)] 
        (if (empty? tail-ring)
          (val (first @ring))
          (val (first tail-ring)))))
    )
  (getNodes
      [this]
    (distinct (vals @ring))
    )
  (countNodes
      [this]
    (count (getNodes this))
    )
  (partitionSize
      [this]
    (* replicas (countNodes this))
    )
  )

(defn new-consistent-hash-ring
  "Constractor for CHR
   :node-list      list of nodes. each node in the list is a map contianing node properties
                   like: IP, PORT, connection timeout,...
   :replicas       Number of replicas per-node in the ring
   :hash-fn    Hash function that will use for query and populate consistent hash ring"

  [node-list replicas hash-fn]
  (let [ring (sorted-map)
        ch (map->CHR {:hash-fn hash-fn
                      :replicas replicas
                      :ring (atom ring)})]
    (initialRing ch node-list)
    ch
    )
  )
