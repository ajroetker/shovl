(ns shovl.handler
  (:require [bidi.ring :refer [make-handler resources-maybe]]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [environ.core :refer [env]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [clj-yaml.core :as yaml]
            [prone.middleware :refer [wrap-exceptions]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [content-type response header]]
            [shovl.tetris :as tetris]))

(def home-page
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1"}]
     (include-css (if (env :dev) "css/site.css" "css/site.min.css"))
     (include-css "css/bootstrap.min.css")]
    [:body
     [:div#app.container {:role "main"}
      [:h3 "ClojureScript has not been compiled!"]
      [:p "please run " [:b "lein figwheel"] " in order to start the compiler"]]
     (include-js "js/app.js")]]))

(defn path->response-map [f]
  (let [file-string (slurp (io/resource f))
        [_ metadata-string markdown-string] (str/split file-string #"---" 3)
        metadata (yaml/parse-string metadata-string)]
    (merge {:title "Default Post Title"
            :content markdown-string}
           metadata)))

(def shovl-posts
  (->> ["_posts/2015-10-11-Welcome-To-The-Party.md"]
       (mapv path->response-map)))

(defn default-headers [response]
  (-> response
      (header "Access-Control-Allow-Origin" "http://shovl.herokuapp.com")
      (header "Access-Control-Allow-Methods" "GET, POST, OPTIONS")))

(defn html-response [html]
  (-> html
      response
      default-headers
      (content-type "text/html")))

(defn json-response [json]
  (-> json
      json/generate-string
      response
      default-headers
      (content-type "application/json")))


(defn parse-int [s]
  (Integer. (re-find  #"\d+" s )))

(def high-scores (atom {}))
(defn maybe-add-score [high-scores user score]
  (cond
    (contains? high-scores user)
    (if (> score (get high-scores user))
      (assoc high-scores user score)
      high-scores)
    (< (count high-scores) 10)
    (assoc high-scores user score)
    :else
    (let [[[_ lower-highest-score] :as high-scores-ascending] (sort-by val < high-scores)]
      (if (> score lower-highest-score)
        (into {} (-> high-scores-ascending rest (conj [user score])))
        high-scores))))

(def routes (make-handler
             ["/" {"" (fn [_] (html-response home-page))
                   "css" (resources-maybe {:prefix "public/css/"})
                   "js" (resources-maybe {:prefix "public/js/"})
                   "img" (resources-maybe {:prefix "public/img/"})
                   "audio" (resources-maybe {:prefix "public/audio/"})
                   "scores" {:get {"" (fn [_] (json-response @high-scores))}
                             :post {"" (wrap-params (fn [{:keys [params] :as req}]
                                                      (let [{:strs [user score]} params]
                                                        (->> (parse-int score)
                                                             (swap! high-scores maybe-add-score user)
                                                             json-response))))}}
                   "posts" (fn [_] (json-response shovl-posts))
                   "tetris" (fn [_] (json-response tetris/problem-0-boards))}]))

(def app
  (let [handler (wrap-resource #'routes "public")]
    (cond-> handler
      (env :dev) (-> wrap-exceptions wrap-reload))))
