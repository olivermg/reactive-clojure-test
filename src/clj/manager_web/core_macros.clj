(ns manager-web.core-macros)

;;
;; use it like this:
;;
;; (make-col [12 6 4]
;;           (println 11)
;;           (println 22))
;;
(defmacro make-col
  [[xs md lg] & body]
  `(grid/col {:xs ~xs :md ~md :lg ~lg}
             ~@body))

;;
;; use it like this:
;;
;; (make-row ([12 6 4]
;;            (println 11)
;;            (println 12))
;;           ([8 7 6]
;;            (println 21)
;;            (println 22)))
;;
(defmacro make-row
  [& cols]
  `(grid/row {}
             ~@(map (fn [col]
                      (let [[xs md lg] (first col)
                            col-body (rest col)]
                        `(make-col [~xs ~md ~lg]
                                   ~@col-body)))
                    cols)))

(defmacro make-grid
  [& rows]
  (map (fn [& cols]
         `(grid/grid {}
                     ~@(map
                        (fn [])
                        ~cols)))
       rows))

