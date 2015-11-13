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
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.reload :refer [wrap-reload]]
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

(defn static-html-handler [html]
  (fn [_]
    (-> html
        response
        default-headers
        (content-type "text/html"))))

(defn static-json-handler [json]
  (fn [_]
    (-> json
        json/generate-string
        response
        default-headers
        (content-type "application/json"))))

(def routes (make-handler
             ["/" {"" (static-html-handler home-page)
                   "css" (resources-maybe {:prefix "public/css/"})
                   "js" (resources-maybe {:prefix "public/js/"})
                   "img" (resources-maybe {:prefix "public/img/"})
                   "audio" (resources-maybe {:prefix "public/audio/"})
                   "posts" (static-json-handler shovl-posts)
                   "tetris" (static-json-handler tetris/problem-0-boards)}]))

(def app
  (let [handler (wrap-defaults #'routes site-defaults)]
    (if (env :dev) (-> handler wrap-exceptions wrap-reload) handler)))
