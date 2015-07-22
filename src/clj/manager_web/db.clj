(ns manager-web.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]))

(defonce conn (mg/connect {:host "172.17.0.1"}))
(defonce db (mg/get-db conn "managerweb"))

(defn get-services []
;  (map #(dissoc % :_id))
  (map #(update-in % [:_id]
                   (fn [objid] (.toString objid)))
       (mc/find-maps db "services")))
