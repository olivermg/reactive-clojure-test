(ns manager-web.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [om-bootstrap.button :as btn]
            [om-bootstrap.random :as rnd]
            [om-bootstrap.grid :as grid]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello Chestnut!"}))

(defn buttons []
  (dom/div nil
           (btn/toolbar
            {}
            (btn/button {} "Button 1")
            (btn/button {:bs-style "primary"} "Primary")
            (btn/button {:bs-style "success"} "Success"))))

(defn main []

  (om/root
    (fn [app owner]
      (reify
        om/IRender
        (render [_]
          (rnd/page-header {} "Manager console"))))
    app-state
    {:target (. js/document (getElementById "app"))})

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
   {:target (. js/document (getElementById "container"))}))
