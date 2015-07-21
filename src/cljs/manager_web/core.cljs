(ns manager-web.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [om-bootstrap.button :as btn]
            [om-bootstrap.random :as rnd]
            [om-bootstrap.grid :as grid]
            [om-bootstrap.panel :as panel]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello Chestnut!"
                          :view {:selected :table}
                          :data [{:value 24000 :timestamp "2015-07-01"}
                                 {:value 10000 :timestamp "2015-07-20"}]}))

(defn buttons []
  (dom/div nil
           (btn/toolbar
            {}
            (btn/button {}
                        "Button 1")
            (btn/button {:bs-style "primary"}
                        "Primary")
            (btn/button {:bs-style "success"
                         :on-click (fn [_] (js/alert "clicked!"))}
                        "Success"))))

(defn toggle [cursor owner opts]
  (om/component
   (btn/toolbar {}
                (btn/button {:active? (when (= :table (-> cursor :selected)) true)
                             :on-click (fn [_] (om/update! cursor :selected :table))}
                            "Table")
                (btn/button {:active? (when (= :panels (-> cursor :selected)) true)
                             :on-click (fn [_] (om/update! cursor :selected :panels))}
                            "Panels"))))

(defmulti view (fn [cursor owner opts]
                 (-> cursor :view :selected)))

(defmethod view :table  [cursor owner opts]
  (om/component
   (dom/table {:striped? true :bordered? true :hover? true}
              (dom/thead nil
                         (dom/tr nil
                                 (dom/th nil "Timestamp")
                                 (dom/th nil "Value"))
                         (dom/tbody nil
                                    (for [v (:data cursor)]
                                      (dom/tr nil
                                              (dom/td nil (:timestamp v))
                                              (dom/td nil (:value v)))))))))

(defmethod view :panels [cursor owner opts]
  (om/component
   (dom/div nil
            (for [v (:data cursor)]
              (panel/panel nil
                         (dom/p nil
                                (dom/p nil (str "Timestamp: " (:timestamp v)))
                                (dom/p nil (str "Value: " (:value v)))))))))

(defn main []

  (om/root
   (fn [app owner]
     (reify
       om/IRender
       (render [_]
         (rnd/page-header {} "Manager console"))))
   app-state
   {:target (. js/document (getElementById "app1"))})

  (om/root
   (fn [app owner]
     (reify
       om/IRender
       (render [_]
         (grid/grid {}
                    (grid/row {}
                              (grid/col {:xs 12 :md 8} (buttons))
                              (grid/col {:xs 6 :md 4} "col 2"))))))
   app-state
   {:target (. js/document (getElementById "app2"))})

  (om/root
   (fn [app owner]
     (reify
       om/IRender
       (render [_]
         (grid/grid {}
                    (grid/row {}
                              (grid/col {:xs 12}
                                        (om/build toggle (:view app))
                                        (om/build view app)))))))
   app-state
   {:target (. js/document (getElementById "app3"))}))
