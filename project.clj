(defproject graphite-relay "0.1.0"

  :description  "A Riemann plug-in called graphite-relay.
                 An extension to built-in Riemann Graphite driver,
                 implements a Graphite relay using consistent hash mechanism"
  :url "https://github.com/mocoloco/graphite-relay"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]]

  :profiles {:dev {:dependencies [[riemann "0.2.11"]]}}

  :resource-paths ["resources" "target/resources"])
