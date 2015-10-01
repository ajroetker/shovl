(ns shovl.core
    (:require [reagent.core :as reagent]
              [shovl.tetris-consumer :as tetris]))

(defn mount-components []
  (reagent/render [tetris/tetris-page] (.getElementById js/document "app")))

(defn init! []
  (tetris/get-new-game)
  (mount-components))
