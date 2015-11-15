(ns shovl.tetris
  (:require [cljsjs.d3]
            [goog.string :as gstring]
            [ajax.core :refer [GET POST]]
            [clojure.walk :as walk]
            [reagent.core :as reagent :refer [atom]]
            [hexlib.renderer :as renderer]
            [hexlib.core :as hex]
            [hexlib.tetris :as tetris]))

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
          :d (draw-hexagon (clj->js hexagon-data))
          :stroke "black"
          :stroke-width 3
          :fill (render-color color)}])

(defn reagent-circle-element [[cx cy]]
  [:circle {:cy cy
            :cx cx
            :r "4px"
            :fill "black"}])

(def layout-pointy {:orientation renderer/orientation-pointy
                    :size [20.0 20.0]
                    :origin [27.0 28.0]})

(def board-state (atom {}))
(def waiting-for-user (atom true))
(def high-scores (atom {}))
(defn high-scores-table [high-scores]
  [:ol {:style {:width "50%"
                :margin "auto"
                :text-align "left"
                :font-weight "bold"
                :overflow "scroll"}}
   (for [[user score] (sort-by second > high-scores)]
     [:li {:key (random-uuid)}
      (str user ": " score)])])
(def user (atom ""))

(defn make-game-move [command]
  (when-not (:game-over @board-state)
    (swap! board-state
           (fn [board]
             (tetris/board-transition board command)))))

(defn load-tetris-scores [user]
  (POST "http://shovl.herokuapp.com/scores"
      {:params {:user user
                :score (tetris/score-game @board-state)}
       :format :raw
       :response-format :json
       :handler #(reset! high-scores %)
       :headers {:Access-Control-Request-Methods "GET, POST, OPTIONS"}})
  (reset! waiting-for-user false))


(defn load-tetris-game []
  (GET "http://shovl.herokuapp.com/tetris"
      {:handler #(reset! board-state (first %))
       :keywords? true
       :response-format :json
       :headers {:Access-Control-Request-Methods "GET, POST, OPTIONS"}})
  (reset! waiting-for-user true))

(def arcade-navbar
  [:div#navbar.row
   [:div.col-md-12
    [:ul.nav.nav-tabs.nav-justified
     [:li {:role "presentation"} [:a {:href "#/"} "Home"]]
     [:li.active {:role "presentation"} [:a {:href "#/tetris"} "Arcade"]]]]])

(def tetris-description
  [:div {:style {:text-align "left"}}
   [:h1 "Hexogonal Tetris"]
   [:p "This is a fun little game I made based off of the "
    [:a {:href "http://2015.icfpcontest.org/"} "2015 ICFP problem."]]
   [:p "Be careful not to let a unit end up in the same position twice, otherwise " [:strong "GAME OVER!"]]
   [:p "Click on the hex-board to focus your browser on the svg element and you can also use "
    [:ul
     [:li [:code "a"] " for left, "]
     [:li [:code "s"] " for left-down, "]
     [:li [:code "d"] " for right-down, "]
     [:li [:code "f"] " for right, "]
     [:li [:code "j"] " for clockwise rotation, and "]
     [:li [:code "k"] " for counter-clockwise rotation."]]]])

(defn key-code->command [key-code]
  (case key-code
    83 :sw
    65 :w
    70 :e
    68 :se
    74 :cw
    75 :ccw
    nil))

(defn raw-unit-odd-r->cube
  "Don't center the about the pivot.
  Used for rendering the cells."
  [unit]

  (-> unit
      (update :pivot hex/odd-r->cube)
      (update :members (partial map hex/odd-r->cube))))

(defn board-svg [board-state]
  (let [{:keys [grid unit]} board-state
        cols (count (first grid))
        rows (count grid)
        {:keys [pivot members]} (raw-unit-odd-r->cube unit)
        members-set (set members)]
    [:div {:style {:width "100%"}}
     [:svg {:style {:max-width "100%"
                    :overflow "scroll"
                    :width (* 35 (inc cols))
                    :height (* 30 (inc rows))}
            :tabIndex 0
            :on-key-down #(some-> (.-which %)
                                  key-code->command
                                  make-game-move)}
      (for [[row-num row] (map-indexed vector grid)
            [col-num filled] (map-indexed vector row)
            :let [cube (hex/odd-r->cube [col-num row-num])
                  color (cond filled dark-grey
                              (contains? members-set cube) bright-red
                              :else melon)]]
        (reagent-path-element (renderer/polygon-corners layout-pointy cube) color))
      (reagent-circle-element (renderer/cube->pixel layout-pointy pivot))]]))

(def board-btn-group
  [:div.btn-group {:style {:margin-top "15px" :margin-bottom "15px"}}
   [:button.btn.btn-default {:on-click #(make-game-move :cw)} (gstring/unescapeEntities "&#x21BB;")]
   [:button.btn.btn-default {:on-click #(make-game-move :w)} (gstring/unescapeEntities "&#x2190;")]
   [:button.btn.btn-default {:on-click #(make-game-move :sw)} (gstring/unescapeEntities "&#x2199;")]
   [:button.btn.btn-default {:on-click #(make-game-move :se)} (gstring/unescapeEntities "&#x2198;")]
   [:button.btn.btn-default {:on-click #(make-game-move :e)} (gstring/unescapeEntities "&#x2192;")]
   [:button.btn.btn-default {:on-click #(make-game-move :ccw)} (gstring/unescapeEntities "&#x21BA;")]])

(defn todo-input [{:keys [title on-save on-stop]}]
  (let [val (atom title)
        stop #(do (reset! val "")
                  (when on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
                (when-not (empty? v) (on-save v))
                (stop))]
    (fn [props]
      [:input.form-control
       (merge props
              {:type "text" :value @val :on-blur save
               :on-change #(reset! val (-> % .-target .-value))
               :on-key-down #(case (.-which %)
                               13 (save)
                               27 (stop)
                               nil)})])))

(defn tetris-page []
  [:div
   arcade-navbar
   [:div#tetris.row
    [:div.col-md-12
     [:div.panel.panel-default
      [:div.panel-heading {:style {:text-align "left"}}
       [:h3.panel-title "Hexagonal Tetris"]]
      [:div.panel-body
       tetris-description
       [:div {:style {:text-align "center"}}
        [:h2 (if (:game-over @board-state) "Final Score: " "Score: ")
         (tetris/score-game @board-state)]
        [board-svg @board-state]
        (when-not (:game-over @board-state)
          board-btn-group)
        ;; We render the "New Game" button when the board-state is empty for
        ;; development purposes. Reagent won't re-initialize the `init!` hooks
        ;; on restart so we need to be able to get the board ourselves.
        (when (or (empty? @board-state)
                  (:game-over @board-state))
          [:div {:style {:width "100%"}}
           [:div.btn-group {:style {:margin-top "15px" :margin-bottom "15px"}}
            [:button.btn.btn-default {:on-click load-tetris-game} "New game"]]])
        (when (and (:game-over @board-state)
                   (not @waiting-for-user))
          [:h3 {:style {:margin-top "5px"}} "High Scores"])
        (when (:game-over @board-state)
          [:div {:style {:width "100%"}}
           (if @waiting-for-user
             [:div
              [:div.col-md-4]
              [:div.input-group.col-md-4
               [:span.input-group-addon "Submit your score:"]
               [todo-input {:placeholder "Enter your name."
                            :on-save load-tetris-scores}]]]
             [high-scores-table @high-scores])])]]]]]])

