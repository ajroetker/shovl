(ns shovl.components)

(defn panel-component [title body]
  [:div.panel.panel-default
   (when title
     [:div.panel-heading {:style {:text-align "left"}}
      [:h3.panel-title title]])
   [:div.panel-body body]])

(defn btn-group [body]
  [:div.btn-group {:style {:margin-top "15px" :margin-bottom "15px"}}
   body])

(defn btn [on-click body]
  [:button.btn.btn-default {:on-click on-click} body])

(defn link [href body]
  [:a {:href href} body])

(defn li [is-active? body]
  [(if is-active? :li.active :li) {:role "presentation"} body])

(defn navbar [active-tab]
  [:div#navbar.row
   [:div.col-md-12
    [:ul.nav.nav-tabs.nav-justified
     (li (= active-tab :home) (link "#/" "Home"))
     (li (= active-tab :arcade) (link "#/tetris" "Arcade"))]]])
