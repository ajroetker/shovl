(ns shovl.core
  (:import goog.History)
  (:require [reagent.core :as reagent :refer [atom]]
            [cljsjs.showdown]
            [ajax.core :refer [GET]]
            [bidi.bidi :as bidi]
            [reagent.session :as session]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [clojure.walk :as walk]
            [shovl.tetris.consumer :as tetris]))

(def posts-state (atom []))

(defn load-shovl-posts []
  (GET "http://shovl.herokuapp.com/posts"
      {:handler (fn [response]
                  (swap! posts-state concat (-> response
                                                js->clj
                                                walk/keywordize-keys)))
       :headers {:Access-Control-Request-Methods "GET, POST, OPTIONS"}}))

(defn audio-component [src-file]
  [:audio {:controls "controls"}
   [:source {:src (str "audio/" src-file)
             :type "audio/mpeg"}]])

(defn post-component [title content audio]
  [:div.row.container {:style {:text-align "left"} :key (random-uuid)}

   [:div.col-md-12
    [:div.panel.panel-default
     [:div.panel-heading [:h3.panel-title title]]
     [:div.panel-body
      (when audio
        [:div {:style {:margin-bottom "10px"
                       :text-align "center"}}
         (audio-component audio)])
      [:div {:dangerouslySetInnerHTML
             {:__html (.makeHtml (js/Showdown.converter.) content)}}]]]]])

(defn home-page []
  [:div
   [:div#navbar.row
    [:div.col-md-12
     [:ul.nav.nav-tabs.nav-justified
      [:li.active {:role "presentation"} [:a {:href "#/"} "Home"]]
      [:li {:role "presentation"} [:a {:href "#/tetris"} "Arcade"]]]]]
   (for [post @posts-state]
     (post-component (:title post) (:content post) (:audio post)))])

(defn current-page []
  [:div [(session/get :current-page)]])

(def routes ["" {"" home-page
                 "/" home-page
                 "/tetris" tetris/tetris-page}])

(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (let [{:keys [handler]} (bidi/match-route routes (.-token event))]
         (session/put! :current-page handler))))
    (.setEnabled true)))

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (hook-browser-navigation!)
  (load-shovl-posts)
  (tetris/load-tetris-game)
  (mount-root))
