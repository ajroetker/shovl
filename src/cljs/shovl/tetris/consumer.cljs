(ns shovl.tetris.consumer
  (:require [cljsjs.d3]
            [cljs.pprint]
            [goog.string :as gstring]
            [ajax.core :refer [GET]]
            [shovl.hex :as hex]
            [clojure.walk :as walk]
            [reagent.core :as reagent :refer [atom]]
            [shovl.tetris :as tetris]))

(def purple [204 102 255])
(def bright-red [255 0 0])
(def reddish-purple [200 75 75])
(def yellow [255 255 100])
(def light-grey [234 234 234])
(def dark-grey [100 100 100])
(def melon [254 196 75])
(defn render-color [[r g b]]
  (str "rgba(" r "," g "," b ","  0.4 ")"))

(def draw-hexagon
  (.. js/d3.svg
      line
      (x #(.-x %))
      (y #(.-y %))
      (interpolate "linear-closed")))

(defn reagent-path-element [hexagon-data color]
  [:path {:key (random-uuid)
          :d (draw-hexagon hexagon-data)
          :stroke "black"
          :stroke-width 3
          :fill (render-color color)}])

(defn reagent-circle-element [[cx cy]]
  [:circle {:cy cy
            :cx cx
            :r "4px"
            :fill "black"}])

(def sqrt-3 (.sqrt js/Math 3.0))
(def orientation-pointy {:f0 sqrt-3 :f1 (/ sqrt-3 2.0) :f2 0.0 :f3 (/ 3.0 2.0)
                        :b0 (/ sqrt-3 3.0) :b1 (/ (- 1.0) 3.0) :b2 0.0 :b3 (/ 2.0 3.0)
                        :start-angle 0.5})

(def layout-pointy {:orientation orientation-pointy
                    :size [20.0 20.0]
                    :origin [27.0 28.0]})

(defn cube->pixel [layout [x _ z]]
  (let [{{:keys [f0 f1 f2 f3]} :orientation
         [size-x size-y] :size
         [origin-x origin-y] :origin} layout]
    [(+ origin-x (* size-x (+ (* f0 x) (* f1 z))))
     (+ origin-y (* size-y (+ (* f2 x) (* f3 z))))]))

(defn pixel->cube [layout [x y]]
  (let [{{:keys [b0 b1 b2 b3]} :orientation
         [size-x size-y] :size
         [origin-x origin-y] :origin} layout
        pt-x (/ (- x origin-x) size-x)
        pt-y (/ (- y origin-y) size-y)
        cube-x (+ (* b0 pt-x) (* b1 pt-y))
        cube-z (+ (* b2 pt-x) (* b3 pt-y))]
    [cube-x (- (+ cube-x cube-z)) cube-z]))

(defn cube-corner-offset [layout corner]
  (let [{{:keys [start-angle]} :orientation
         [size-x size-y] :size} layout
        angle (* 2.0 (.-PI js/Math) (/ (+ corner start-angle) 6.0))]
    [(* size-x (.cos js/Math angle))
     (* size-y (.sin js/Math angle))]))

(defn polygon-corners [layout cube]
  (let [[center-x center-y] (cube->pixel layout cube)]
    (apply array
           (for [corner (range 6)
                 :let [[offset-x offset-y] (cube-corner-offset layout corner)]]
             #js {:x (+ offset-x center-x)
                  :y (+ offset-y center-y)}))))

(def board-state (atom {}))

(defn make-game-move [command]
  (fn []
    (when-not (:game-over @board-state)
      (swap! board-state
             (fn [board]
               (tetris/board-transition board command))))))

(defn load-tetris-game []
  (GET "http://shovl.herokuapp.com/tetris"
      {:handler (comp #(reset! board-state %)
                      walk/keywordize-keys
                      first
                      js->clj)
       :headers {:Access-Control-Request-Methods "GET, POST, OPTIONS"}}))

(defn tetris-page []
  [:div
   [:div#navbar.row
    [:div.col-md-12
     [:ul.nav.nav-tabs.nav-justified
      [:li {:role "presentation"} [:a {:href "#/"} "Home"]]
      [:li.active {:role "presentation"} [:a {:href "#/tetris"} "Arcade"]]]]]
   [:div#tetris.row
    [:div.col-md-12
     [:div.panel.panel-default
      [:div.panel-heading {:style {:text-align "left"}}
       [:h3.panel-title "Hexagonal Tetris"]]
      [:div.panel-body
       [:div {:style {:text-align "left"}}
        [:h1 "Hexogonal Tetris"]
        [:p "This is a fun little game I made based off of the 2015 ICFP problem."]
        [:p
         "Click on the hex-board to focus your browser on the svg element and you can also use "
         [:ul
          [:li [:code "a"] " for left, "]
          [:li [:code "s"] " for left-down, "]
          [:li [:code "d"] " for right-down, "]
          [:li [:code "f"] " for right, "]
          [:li [:code "j"] " for clockwise rotation, and "]
          [:li [:code "k"] " for counter-clockwise rotation."]]]]
       [:div.panel.panel-default {:style {:text-align "center"}}
        [:div.panel-body
         (if (:game-over @board-state)
           [:h2 "Game over: " (tetris/score-game @board-state)]
           [:h2 "Score: " (tetris/score-game @board-state)])
         (let [{:keys [grid unit]} @board-state
               cols (count (first grid))
               rows (count grid)
               width (+ 35 (* 20 sqrt-3 cols))
               height (+ 5 25 (* 40 0.75 rows))
               {:keys [pivot members]} (-> unit
                                           (update :pivot hex/odd-r->cube)
                                           (update :members (partial map hex/odd-r->cube)))
               members-set (set members)]
           [:div {:style {:width "100%"}}
            [:svg {:style {:width width
                           :height height}
                   :tabIndex 0
                   :on-key-down #(case (.-which %)
                                   83 ((make-game-move :sw))
                                   65 ((make-game-move :w))
                                   70 ((make-game-move :e))
                                   68 ((make-game-move :se))
                                   74 ((make-game-move :cw))
                                   75 ((make-game-move :ccw))
                                   nil)}
             (for [[row-num row] (map-indexed vector grid)
                   [col-num filled] (map-indexed vector row)
                   :let [cube (hex/odd-r->cube [col-num row-num])
                         is-pivot? (= pivot cube)
                         is-member? (contains? members-set cube)
                         color (cond is-member? bright-red
                                     filled dark-grey
                                     :else melon)]]
               (reagent-path-element (polygon-corners layout-pointy cube) color))
             (reagent-circle-element (cube->pixel layout-pointy pivot))]])
         (if (:game-over @board-state)
           [:div.btn-group {:style {:margin-top "15px" :margin-bottom "15px"}}
            [:button#new-game.btn.btn-primary {:on-click load-tetris-game} "New game"]]
           [:div.btn-group {:style {:margin-top "15px" :margin-bottom "15px"}}
            [:button#cw.btn.btn-default {:on-click (make-game-move :cw)} (gstring/unescapeEntities "&#x21BB;")]
            [:button#w.btn.btn-default {:on-click (make-game-move :w)} (gstring/unescapeEntities "&#x2190;")]
            [:button#sw.btn.btn-default {:on-click (make-game-move :sw)} (gstring/unescapeEntities "&#x2199;")]
            [:button#se.btn.btn-default {:on-click (make-game-move :se)} (gstring/unescapeEntities "&#x2198;")]
            [:button#e.btn.btn-default {:on-click (make-game-move :e)} (gstring/unescapeEntities "&#x2192;")]
            [:button#ccw.btn.btn-default {:on-click (make-game-move :ccw)} (gstring/unescapeEntities "&#x21BA;")]])]]]]]]])

