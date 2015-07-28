(ns manager-web.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :as async :refer [<! >! put! chan alts!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [om-sync.core :refer [om-sync]]
            [om-sync.util :refer [tx-tag edn-xhr]]
            [sablono.core :as html :refer-macros [html]]
            [om-bootstrap.button :as btn]
            [om-bootstrap.random :as rnd]
            [om-bootstrap.grid :as grid]
            [om-bootstrap.panel :as panel]
            [taoensso.sente :as sente :refer [cb-success?]]
;            [cljs.reader :as reader]
;            [goog.events :as events]
            )
  (:import
;   [goog.net XhrIo]
;   goog.net.EventType
;   [goog.events EventType]
   ))

(enable-console-print!)

(defonce app-state (atom {:text "Hello Chestnut!"
                          :view {:selected :table}
                          :data [{:value 24000 :timestamp "2015-07-01"}
                                 {:value 10000 :timestamp "2015-07-20"}]
                          :services {}
                          :server-state {:input1 {:value 123}}}))

;;
;; ==========================
;;

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :auto})]
  (def chsk chsk)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(comment (go-loop [d (<! ch-chsk)]
   (println "got message!")
   (println d)
   (recur (<! ch-chsk))))

;;
;; ==========================
;;

(defn editable-input
  [state owner {:keys [key type label placeholder]}]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
               (when label
                 (dom/label nil label))
               (dom/input #js {:type type
                               :value (key state)
                               :placeholder placeholder
                               :onChange (fn [e])
                               :onBlur (fn [e]
                                         (om/transact! state key
                                                       #(.. e -target -value)))})))))

(defn server-synced-view
  [state owner]
  (reify
    om/IRender
    (render [_]
      (dom/input #js {:type "text" :value (:value state)
                      :onChange (fn [e])
                      :onBlur (fn [e]
                                (om/transact! state :value
                                              (fn [_]
                                                (.. e -target -value))))}))))

;;
;; ==========================
;;

(defn display [show]
  (if show
    #js {}
    #js {:display "none"}))

(defn handle-change [e data edit-key owner]
  (om/transact! data edit-key
                (fn [_]
                  (.. e -target -value))))

(defn end-edit [data edit-key text owner cb]
  (om/set-state! owner :editing false)
  (om/transact! data edit-key
                (fn [_] text) :update)
  (when cb
    (cb text)))

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
                                    (end-edit data edit-key text owner on-edit))
                      :onBlur #(when (om/get-state owner :editing)
                                 (end-edit data edit-key text owner on-edit))})
                (dom/button
                 #js {:style (display (not editing))
                      :onClick #(om/set-state! owner :editing true)}
                 "Edit"))))))

(defn create-service [services owner]
  (let [service-id-el   (om/get-node owner "service-id")
        service-id      (.-value service-id-el)
        service-name-el (om/get-node owner "service-name")
        service-name    (.-value service-name-el)
        new-service     {:_id service-id :name service-name}]
    (om/transact! services [] #(conj % new-service)
                  [:create new-service])
    (set! (.-value service-id-el) "")
    (set! (.-value service-name-el) "")))

(defn services-view [services owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:id "services"}
               (dom/h2 nil "Services")
               (apply dom/ul nil
                      (map #(om/build
                             editable %
                             {:opts {:edit-key :name
                                     :on-edit (fn [text]
                                                (println (str "on edit got: " text)))}})
                           services))
               (dom/div nil
                        (dom/label nil "ID:")
                        (dom/input #js {:ref "service-id"})
                        (dom/label nil "Name:")
                        (dom/input #js {:ref "service-name"})
                        (dom/button
                         #js {:onClick (fn [e] (create-service services owner))}
                         "Add"))))))

(defn app-view [app owner]
  (reify
    om/IWillUpdate
    (will-update [_ next-props next-state]
      (when (:err-msg next-state)
        (js/setTimeout #(om/set-state! owner :err-msg nil) 5000)))
    om/IRenderState
    (render-state [_ {:keys [err-msg]}]
      (dom/div nil
               (om/build om-sync (:services app)
                         {:opts {:view services-view
                                 :filter (comp #{:create :update :delete} tx-tag)
                                 :id-key :_id
                                 :on-success (fn [res tx-data] (println res))
                                 :on-error
                                 (fn [err tx-data]
                                   (println (str "got error: " err))
                                   (println (str "old state: " (:old-state tx-data)))
                                   (reset! app-state (:old-state tx-data))
                                   (om/set-state! owner :err-msg
                                                  "Oops!"))}})
               (om/build editable-input
                         (-> app :server-state :input1)
                         {:opts {:key :value
                                 :type "text"
                                 :label "text1"
                                 :placeholder "placeholder1"}})
               (om/build editable-input
                         (-> app :server-state :input1)
                         {:opts {:key :value
                                 :type "text"
                                 :label "text2"
                                 :placeholder "placeholder2"}})
               (when err-msg
                 (dom/div nil err-msg))))))

(let [tx-chan (chan)
      tx-pub-chan (async/pub tx-chan (fn [_] :txs))]
  (edn-xhr
   {:method :get
    :url "/init"
    :on-complete
    (fn [res]
;      (reset! app-state res)
      (swap! app-state #(assoc % :services (:services res)))
      (om/root app-view app-state
               {:target (.getElementById js/document "services")
                :shared {:tx-chan tx-pub-chan}
                :tx-listen (fn [tx-data root-cursor]
                             (put! tx-chan [tx-data root-cursor]))}))}))

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
