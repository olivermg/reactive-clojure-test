(ns manager-web.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [om-bootstrap.button :as btn]
            [om-bootstrap.random :as rnd]
            [om-bootstrap.grid :as grid]
            [om-bootstrap.panel :as panel]
            [cljs.reader :as reader]
            [goog.events :as events])
  (:import [goog.net XhrIo]
           goog.net.EventType
           [goog.events EventType]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello Chestnut!"
                          :view {:selected :table}
                          :data [{:value 24000 :timestamp "2015-07-01"}
                                 {:value 10000 :timestamp "2015-07-20"}]
                          :services []}))

;;
;; ==========================
;;

(defn display [show]
  (if show
    #js {}
    #js {:display "none"}))

(defn edn-xhr [{:keys [method url data on-complete]}]
  (let [xhr (XhrIo.)]
    (events/listen xhr goog.net.EventType.COMPLETE
                   (fn [e]
                     (on-complete (reader/read-string (.getResponseText xhr)))))
    (. xhr
       (send url method
             (when data
               (pr-str data))
             #js {"Content-Type" "application/edn"}))))

(defn handle-change [e data edit-key owner]
  (om/transact! data edit-key (fn [_]
                                .. e -target -value)))

(defn end-edit [text owner cb]
  (om/set-state! owner :editing false)
  (cb text))

(defn editable [data owner {:keys [edit-key on-edit] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:editing false})
    om/IRenderState
    (render-state [_ {:keys [editing]}]
      (let [text (get data edit-key)]
        (dom/li nil
                (dom/span #js {:style (display (not editing))} text)
                (dom/input
                 #js {:style (display editing)
                      :value text
                      :onChange #(handle-change % data edit-key owner)
                      :onKeyDown #(when (= (.-key %) "Enter")
                                    (end-edit text owner on-edit))
                      :onBlur (fn [e]
                                (when (om/get-state owner :editing)
                                  (end-edit text owner on-edit)))})
                (dom/button
                 #js {:style (display (not editing))
                      :onClick #(om/set-state! owner :editing true)}
                 "Edit"))))))

(defn on-edit [id name]
  (edn-xhr
   {:method "PUT"
    :url (str "services/" id "/update")
    :data {:name name}
    :on-complete
    (fn [res]
      (println "server response: " res))}))

(defn services-view [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (edn-xhr
       {:method "GET"
        :url "services"
        :on-complete #(om/transact! app :services (fn [_] %))}))
    om/IRender
    (render [_]
      (dom/div #js {:id "services"}
               (dom/h2 nil "Services")
               (apply dom/ul nil
                      (map (fn [service]
                             (let [id (:_id service)]
                               (om/build editable service
                                         {:opts {:edit-key :name
                                                 :on-edit #(on-edit id %)}})))
                           (:services app)))))))

(om/root
 services-view
 app-state
 {:target (.getElementById js/document "services")})

;;
;; =================
;;

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
