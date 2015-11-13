(ns shovl.tetris
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [hexlib.loader :as loader]))

(defn load-json-file [file-name]
  (-> file-name
      io/resource
      slurp
      (json/parse-string true)))

(def problem-0-boards (-> "problem_0.json"
                          load-json-file
                          loader/load-boards))
