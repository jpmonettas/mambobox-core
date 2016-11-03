(defproject mambobox-core "0.1.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clj" "src/cljc"]
  :plugins [[lein-environ "1.1.0"]]

  :main ^:skip-aot mambobox-core.main
  
  :dependencies [[org.clojure/clojure "1.9.0-alpha13"]
                 [com.datomic/datomic-pro "0.9.5407"]

                 
                 ;; managing configuration for dif envs
                 [environ "1.1.0"]
                 
                 ;; http layer
                 [ring "1.5.0"]
                 [compojure "1.5.1"]
                 [http-kit "2.2.0"]
                 [metosin/compojure-api "1.1.8"]
                 [ring-cors "0.1.7"]

                 [org.clojure/tools.nrepl "0.2.12"]
                 
                 ;; reloaded workflow
                 [org.danielsz/system "0.3.2-SNAPSHOT"]

                 ;; utilities
                 [io.rkn/conformity "0.4.0"]
                 [camel-snake-kebab "0.4.0"]
                 [com.taoensso/timbre "4.8.0-alpha1"]
                 [claudio "0.1.3"]
                 [clj-time "0.12.0"]
                 [org.postgresql/postgresql "9.3-1102-jdbc41"]]

  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["dev-src"]
                   :dependencies [[walmartlabs/datascope "0.1.1"]]
                   :env {:datomic-uri "datomic:free://localhost:4334/mambodb"
                         :http-port 8090
                         :repl-port 7744
                         :public-files-folder "/home/jmonetta/music"}}
             :prod {:env {:datomic-uri "datomic:sql://mambodb?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"
                          :http-port 8090
                          :repl-port 7744
                          :public-files-folder "/home/mambobox/music"}}})
