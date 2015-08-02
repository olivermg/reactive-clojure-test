(ns manager-web.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [manager-web.core-macros :refer [make-grid]])
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
            [om-bootstrap.input :as inp]
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

(defonce man-app-state (atom {:services [{:id 111 :enabled false}
                                         {:id 222 :enabled true}
                                         {:id 333 :enabled true}
                                         {:id 444 :enabled false}
                                         {:id 555 :enabled true}
                                         {:id 666 :enabled true}
                                         {:id 777 :enabled false}]}))

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

(def sync-ch (chan))

(defn editable-text-input
  [state owner {:keys [key label placeholder help]}]
  (reify
    om/IRender
    (render [_]
      (inp/input {:type "text"
                  :value (key state)
                  :label label
                  :placeholder placeholder
                  :help help
                  :onChange (fn [e])
                  :onBlur (fn [e]
                            (let [newval (.. e -target -value)]
                              (put! sync-ch (str "a message: " newval))
                              (om/transact! state key (fn [] newval))))}))))

(defn synced-view
  [state owner {:keys [view channel] :as opts}]
  (reify
    om/IWillMount
    (will-mount [_]
      (go-loop [msg (<! channel)]
        (chsk-send! [:sync/view {:data "aaa"}]
                    10000
                    (fn [d]
                      (println "sente reply")
                      (println d)))
        (recur (<! channel))))
    om/IRender
    (render [_]
      (om/build view state {:opts opts}))))

(defn grid-view
  [state owner]
  (reify
    om/IRender
    (render [_]
      (grid/grid {}
                 (grid/row {}
                           (grid/col {:xs 12 :md 8}
                                     "11111"
                                     (om/build editable-text-input
                                               (-> state :server-state :input1)
                                               {:opts {:key :value
                                                       :label "text1"
                                                       :placeholder "placeholder1"
                                                       :help "help1"}})
                                     (om/build editable-text-input
                                               (-> state :server-state :input1)
                                               {:opts {:key :value
                                                       :label "text2"
                                                       :placeholder "placeholder2"
                                                       :help "help2"}})
                                     )
                           (grid/col {:xs 12 :md 4}
                                     "22222"
                                     (om/build synced-view
                                               (-> state :server-state :input1)
                                               {:opts {:view editable-text-input
                                                       :channel sync-ch
                                                       :key :value
                                                       :label "synced1"
                                                       :placeholder "placeholder3"
                                                       :help "help3"}})
                                     ))))))

(defn add-service
  [services id enabled]
  (om/transact! services :services #(conj % {:id id :enabled enabled})))

(defn toggle-enabled
  [service]
  (om/transact! service :enabled not))

(defn man-actions-view
  [state owner]
  (reify
    om/IInitState
    (init-state [_]
      {:aaa 111})
    om/IRender
    (render [_]
      (btn/dropdown {:bs-style "primary"
                     :title "Add service"}
                    (btn/menu-item {:key :nginx
                                    :on-select #(add-service state 888 false)}
                                   "nginx")
                    (btn/menu-item {:key :apache
                                    :on-select #(add-service state 999 false)}
                                   "apache")
                    (btn/menu-item {:key :mysql
                                    :on-select #(add-service state 1111 false)}
                                   "mysql")
                    ))))

(defn man-service-view
  [service owner]
  (reify
    om/IRender
    (render [_]
      (panel/panel {:header (:id service)
                    :bs-style (if (:enabled service)
                                "success"
                                "warning")
                    :on-click (fn [e]
                                (toggle-enabled service))}
                   "service"))))

(defn man-services-view
  [services owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
               (map #(grid/col {:xs 12 :md 6 :lg 3}
                               (om/build man-service-view %))
                    (:services services))))))

(defn man-app-view
  [state owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
               (rnd/page-header {}
                                "Header"
                                (dom/small nil "Subheader"))
               (grid/grid {}
                          (grid/row {}
                                    (grid/col {:xs 12 :md 4}
                                              (om/build man-actions-view
                                                        state))
                                    (grid/col {:xs 12 :md 8}
                                              (om/build man-services-view
                                                        state))))))))

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
               (om/build grid-view app)
               (when err-msg
                 (dom/div nil err-msg))))))

(comment (let [tx-chan (chan)
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
                                      (put! tx-chan [tx-data root-cursor]))}))})))

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

  (comment (om/root
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

  (om/root man-app-view
           man-app-state
           {:target (.getElementById js/document "man-app")}))
