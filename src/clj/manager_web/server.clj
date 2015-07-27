(ns manager-web.server
  (:require [clojure.core.async :as async :refer [<! >! put! chan alts! go go-loop]]
            [manager-web.db :as db]
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
;            [ring.adapter.jetty :refer [run-jetty]]
            [org.httpkit.server :as hk]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [sente-web-server-adapter]])
  (:gen-class))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]}
      (sente/make-channel-socket! sente-web-server-adapter {})]
  (def ring-ajax-post (fn [req]
                        (println "got POST:")
                        (println req)
                        (ajax-post-fn req)))
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def connected-uids connected-uids))
(defonce router (atom nil))

(defn event-msg-handler
  [{:keys [event id ?data ?reply-fn]}]
  (println "got sente router request =====")
  (when ?reply-fn
    (?reply-fn {:reply-data "reply-data"})))

(defn stop-router!
  []
  (when-let [stop-f @router]
    (stop-f)))

(defn start-router!
  []
  (stop-router!)
  (reset! router
          (sente/start-chsk-router! ch-chsk event-msg-handler)))

(comment (go-loop [d (<! ch-chsk)]
   (println "server go message!")
   (println d)
   (recur (<! ch-chsk))))

(deftemplate page (io/resource "index.html") []
  [:body] (if is-dev? inject-devmode-html identity))

(defn generate-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn init []
  (generate-response {:services {:url "/services"
                                 :coll (vec (db/get-services))}}))

(defn services []
  (generate-response (db/get-services)))

(defn service-create [params]
  (println (str "received create request: " params))
  {:status 501})

(defn service-update [data]
  (println (str "received update request: " data))
  {:status 501})

(defroutes routes
  (GET "/init" [] (init))
  (GET "/services" [] (services))
  (POST "/services" {params :params} (service-create params))
  (PUT "/services" {params :params} (service-update params))
  (resources "/")
  (resources "/react" {:root "react"})
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))
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
    (println "starting sente router")
    (start-router!)
    (println (format "Starting web server on port %d." port))
;    (run-jetty http-handler {:port port :join? false})
    (hk/run-server http-handler {:port port})))

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
