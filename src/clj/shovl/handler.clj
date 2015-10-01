(ns shovl.handler
  (:require [bidi.ring :refer [make-handler resources-maybe]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [prone.middleware :refer [wrap-exceptions]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.util.response :refer [content-type response]]
            [environ.core :refer [env]]
            [cheshire.core :as json]
            [shovl.tetris-producer :as tetris]))

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
     [:div#app.container {:role "main"} [:h3 "ClojureScript has not been compiled!"]
      [:p "please run "
       [:b "lein figwheel"]
       " in order to start the compiler"]]
     (include-js "js/app.js")]]))

(defn static-html-handler [html]
  (fn [_]
    (-> home-page
        response
        (content-type "text/html"))))

(defn static-json-handler [json]
  (fn [_]
    (-> json
        json/generate-string
        response
        (content-type "application/json"))))

(def routes (make-handler
             ["/" {"" (static-html-handler home-page)
                   "css" (resources-maybe {:prefix "public/css/"})
                   "js" (resources-maybe {:prefix "public/js/"})
                   "tetris" (static-json-handler tetris/problem-0-boards)}]))

(def app
  (let [handler (wrap-defaults #'routes site-defaults)]
    (if (env :dev) (-> handler wrap-exceptions wrap-reload) handler)))
