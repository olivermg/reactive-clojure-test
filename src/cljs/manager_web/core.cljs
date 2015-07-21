(ns manager-web.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [om-bootstrap.button :as btn]
            [om-bootstrap.random :as rnd]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello Chestnut!"}))

(defn main []
  (om/root
    (fn [app owner]
      (reify
        om/IRender
        (render [_]
          (dom/h1 nil (:text app)))))
    app-state
    {:target (. js/document (getElementById "app"))})

  (om/root
   (fn [app owner]
     (reify
       om/IRender
       (render [_]
         (dom/div nil
                  (btn/toolbar
                   {}
                   (btn/button {} "Button 1")
                   (btn/button {:bs-style "primary"} "Primary")
                   (btn/button {:bs-style "success"} "Success"))))))
   app-state
   {:target (. js/document (getElementById "row1"))}))
