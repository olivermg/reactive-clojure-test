(ns manager-web.mq
  (:require [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.exchange :as lx]
            [langohr.consumers :as lc]
            [langohr.basic :as lb]))

;;
;; ring middleware
;;

(defn wrap-mq
  [handler conndef topics-consumers]
  (let [conn (rmq/connect conndef)
        ch (lch/open conn)]
    (doseq [[topic & consumers] topics-consumers]
      (lx/declare ch topic "fanout")
      (doseq [consumer consumers]
        (let [q (lq/declare ch "" {:exclusive true :auto-delete true})]
          (lq/bind ch (:queue q) topic)
          (lc/subscribe ch (:queue q)
                        consumer))))
    (fn [request]
      (handler (assoc request
                      :mq-publish-fn (fn [topic msg]
                                       (lb/publish ch topic "" msg)))))))


;;
;; consumer
;;

(comment(defn start-consumer
          [topic]
          (let [conn (rmq/connect {:hostname "localhost"})
                ch (lch/open conn)
                x (lx/declare ch topic "fanout")
                q (lq/declare ch "" {:exclusive true :auto-delete true})]
            (lq/bind ch (:queue q) topic)
            (lc/subscribe ch (:queue q) (fn [ch meta ^bytes payload]
                                          (println (str "received msg: " (String. payload "UTF-8")))))
            {:conn conn :ch ch}))

        (defn stop-consumer
          [consumer]
          (lch/close (:ch consumer))
          (rmq/close (:conn consumer))))


;;
;; producer
;;
