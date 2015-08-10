(ns manager-web.server
  (:require [clojure.core.async :as async :refer [<! >! put! chan alts! go go-loop]]
            [manager-web.db :as db]
            [manager-web.mq :as mq]
            [manager-web.auth :as auth]
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
            [taoensso.sente.server-adapters.http-kit :refer [sente-web-server-adapter]]
            [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [cemerick.friend.credentials :as creds])
  (:gen-class))

(defn publish-message [req topic msg]
  ((:mq-publish-fn req) topic msg))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]}
      (sente/make-channel-socket! sente-web-server-adapter {})]
  (def ring-ajax-post (fn [req]
                        (println "got SENTE POST:")
                        (println req)
                        (ajax-post-fn req)))
  (def ring-ajax-get-or-ws-handshake (fn [req]
                                       (println "got SENTE GET:")
                                       (println req)
                                       (ajax-get-or-ws-handshake-fn req)))
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def connected-uids connected-uids))
(defonce router (atom nil))

(defn event-msg-handler
  [{:keys [event id ?data ?reply-fn] :as req}]
  (let [name (name id)
        namespace (namespace id)]
    (when (= namespace "sync")
      (println "got sente router request =====")
      (println req)
      (println (str "event: " event ", id: " id ", data: " ?data))
      (when (= name "add-service")
        (publish-message (:ring-req req) "logs" "mq message received via sente!"))
      (when ?reply-fn
        (?reply-fn {:reply-data "reply-data"})))))

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
                                 ;:coll (vec (db/get-services))
                                 :coll [{:id 1 :name "111"} {:id 2 :name "222"}]
                                 }}))

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
  (GET "/protected" req (friend/authorize #{:manager-web.auth/admin} "PROTECTED PAGE"))
;  (POST "/login" req (println "login!"))
;  (GET "/mq" req (publish-message req "logs" "a message"))
  (resources "/")
  (resources "/react" {:root "react"})
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))
  (GET "/*" req (page)))

(defn get-dev-handler []
  (-> routes
      (friend/authenticate {:credential-fn (fn [req]
                                             (println "got login: ")
                                             (println req)
;                                             (partial creds/bcrypt-credential-fn auth/users)
                                             (creds/bcrypt-credential-fn auth/users req))
                            :workflows [(workflows/interactive-form)]})
      (wrap-defaults (assoc site-defaults :security false))
      wrap-edn-params
      (mq/wrap-mq {:hostname "localhost"} [["logs"
                                            (fn [ch meta payload] (println "1:message"))
                                            (fn [ch meta payload] (println "2:message"))]])
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
