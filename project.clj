(defproject citron "0.1.2"
  :description "citron general server"
  :url ""
  :license {:name "EMT"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.reader "1.0.0-alpha1"] ;; 0.9.2
                 [compojure "1.5.1" :exclusions [org.clojure/tools.reader clj-time]]
                 [hiccup "1.0.5"]
                 [http-kit "2.3.0"]
                 [buddy/buddy-sign "3.0.0" :exclusions [cheshire]]
                 [buddy/buddy-auth "2.1.0" :exclusions [buddy/buddy-sign cheshire]]
                 [clj-time "0.12.0"]
                 [org.clojure/core.memoize "0.5.9"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojars.august/sparrows "0.2.5" :exclusions [[org.clojure/tools.reader] [clj-http]]]
                 [org.clojars.august/session-store "0.1.0" :exclusions [[com.taoensso/carmine]]]
                 [clj-http "3.1.0"]
                 [com.taoensso/timbre "4.7.3"]
                 [ring-server "0.4.0"]
                 [ring "1.5.0"]
                 [ring/ring-json "0.4.0"] ;; handling json-body request
                 [ring/ring-defaults "0.2.1"] ;; supports auto set utf8 encoding in content-type
                 [org.slf4j/slf4j-jdk14 "1.7.21"]
                 [dk.ative/docjure "1.10.0"]
                 ;; shell
                 [me.raynes/conch "0.8.0"]
                 [com.climate/claypoole "1.1.4"]
                 ;; [net.sf.uadetector/uadetector-resources "2014.10"]
                 [environ "1.2.0"]
                 [org.clojure/core.cache "1.0.207"]
                 [markdown-clj "0.9.89"]
                 [org.jsoup/jsoup "1.7.1"]
                 [com.aliyun/aliyun-java-sdk-core "3.7.0"]
                 [com.rpl/specter "1.1.2"]
                 [net.glxn.qrgen/javase "2.0"] ;; qrcode
                 [org.clojure/core.async "1.1.587"]]
  :plugins [[lein-environ "1.2.0"]]
  :java-source-paths ["src-java"]
  :profiles {:uberjar {:aot [citron.core]}
             :dev {:env {:dev true}
                   :source-paths ["src-dev"]
                   :dependencies [[criterium "0.4.4"]]}}
  :main citron.core
  :omit-source true
  :repl-options {:init-ns citron.core}
  )
