(defproject manager-web "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/clj"]

  :test-paths ["test/clj"]

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "0.0-3308" :scope "provided"]
;                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [ring "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [compojure "1.3.4"]
                 [enlive "1.1.6"]
                 [org.omcljs/om "0.9.0"]
                 [om-sync "0.1.1"]
                 [racehub/om-bootstrap "0.5.0"]
                 [environ "1.0.0"]
                 [sablono "0.3.4"]
                 [fogus/ring-edn "0.3.0"]
                 [com.novemberain/monger "3.0.0"]
                 [com.taoensso/sente "1.5.0"]
                 [http-kit "2.1.19"]]

  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-environ "1.0.0"]
            [lein-less "1.7.5"]]

  :min-lein-version "2.5.0"

  :uberjar-name "manager-web.jar"

  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to     "resources/public/js/app.js"
                                        :output-dir    "resources/public/js/out"
                                        :source-map    "resources/public/js/out.js.map"
                                        :preamble      ["react/react.min.js"]
                                        :optimizations :none
                                        :pretty-print  true}}}}


  :less {:source-paths ["src/less"]
         :target-path "resources/public/css"}

  :profiles {:dev {:source-paths ["env/dev/clj"]
                   :test-paths ["test/clj"]

                   :dependencies [[figwheel "0.3.7"]
                                  [figwheel-sidecar "0.3.7" :exclusions [org.codehaus.plexus/plexus-utils]]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [weasel "0.7.0"]]

                   :repl-options {:init-ns manager-web.server
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

                   :plugins [[lein-figwheel "0.3.7" :exclusions [org.clojure/clojure
                                                                 org.codehaus.plexus/plexus-utils]]]

                   :figwheel {:http-server-root "public"
                              :server-port 3449
                              :css-dirs ["resources/public/css"]
                              :ring-handler manager-web.server/http-handler}

                   :env {:is-dev true}

                   :cljsbuild {:test-commands { "test" ["phantomjs" "env/test/js/unit-test.js" "env/test/unit-test.html"] }
                               :builds {:app {:source-paths ["env/dev/cljs"]}
                                        :test {:source-paths ["src/cljs" "test/cljs"]
                                               :compiler {:output-to     "resources/public/js/app_test.js"
                                                          :output-dir    "resources/public/js/test"
                                                          :source-map    "resources/public/js/test.js.map"
                                                          :preamble      ["react/react.min.js"]
                                                          :optimizations :whitespace
                                                          :pretty-print  false}}}}}

             :uberjar {:source-paths ["env/prod/clj"]
                       :hooks [leiningen.cljsbuild leiningen.less]
                       :env {:production true}
                       :omit-source true
                       :aot :all
                       :main manager-web.server
                       :cljsbuild {:builds {:app
                                            {:source-paths ["env/prod/cljs"]
                                             :compiler
                                             {:optimizations :advanced
                                              :pretty-print false}}}}}})
