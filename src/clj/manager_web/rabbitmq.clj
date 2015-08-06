(ns manager-web.rabbitmq
  (:require [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.exchange :as lx]
            [langohr.consumers :as lc]
            [langohr.basic :as lb]))

(defn start-consumer
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
  (rmq/close (:conn consumer)))
