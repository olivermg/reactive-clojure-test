(ns manager-web.server
  (:require [manager-web.db :as db]
            [clojure.java.io :as io]
            [manager-web.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel start-less]]
            [compojure.core :refer [GET PUT POST defroutes]]
            [compojure.route :refer [resources]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [net.cgrand.reload :refer [auto-reload]]
            [ring.middleware.reload :as reload]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.edn :refer [wrap-edn-params]]
            [environ.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))

(deftemplate page (io/resource "index.html") []
  [:body] (if is-dev? inject-devmode-html identity))

(defn generate-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn services []
  (generate-response (db/get-services)))

(defn service-update [data]
  (println (str "received update request: " data))
  {:status 501})

(defroutes routes
  (resources "/")
  (resources "/react" {:root "react"})
  (GET "/services" [] (services))
  (PUT "/services/:id/update"
       {params :params}
       (service-update params))
  (GET "/*" req (page)))

(defn get-dev-handler []
  (-> routes
      (wrap-defaults (assoc site-defaults :security false))
      wrap-edn-params
      reload/wrap-reload))

(defn get-production-handler []
  (-> routes
      (wrap-defaults site-defaults)
      wrap-edn-params))

(def http-handler
  (if is-dev?
    (get-dev-handler)
    (get-production-handler)))

(defn run-web-server [& [port]]
  (let [port (Integer. (or port (env :port) 10555))]
    (println (format "Starting web server on port %d." port))
    (run-jetty http-handler {:port port :join? false})))

(defn run-auto-reload [& [port]]
  (auto-reload *ns*)
  (start-figwheel)
  (start-less))

(defn run [& [port]]
  (when is-dev?
    (run-auto-reload))
  (run-web-server port))

(defn -main [& [port]]
  (run port))
