(ns lambda-shelf.templates
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [hasch.core :refer [sha-1 hash->str uuid]]
            [kioo.om :refer [content set-attr do-> substitute listen]]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [kioo.om :refer [deftemplate defsnippet]]))


(defn- url->hash [u]
  (-> u sha-1 hash->str (subs 0 8)))


(defn bookmark-view
  "Bookmark entry in the data table"
  [add-bookmark-comment vote {:keys [title url date votes comments author] :as bookmark} owner]
  (let [comment-count (count comments)]
    (reify
      om/IRenderState
      (render-state [this {:keys [incoming input-text ws-in ws-out] :as state}]
        (html
         [:tr
          ;; title and collapsed comments
          [:td
           [:a {:href url :target "_blank"} title]

           [:div.panel-collapse.collapse
            {:id (str "comments-panel-" (url->hash url))}

            [:br]

            [:ul.list-group
             (map #(vec [:li.list-group-item
                         [:em.small#comment-user (str (:author %) " - " (.toLocaleTimeString (:date %)))]
                         [:p (:text %)]
                         ]) comments)]

            [:br]

            [:div.form-group {:ref (str "new-comment-" (url->hash url) "-group")}
             [:textarea.form-control
              {:type "text"
               :ref (str "new-comment-" (url->hash url))
               :rows 3
               :value (:modal-comment input-text)
               :style {:resize "vertical"}
               :on-change #(om/set-state! owner [:input-text :modal-comment] (.. % -target -value))
               :placeholder "What do you think?"}]]

            [:button.btn.btn-primary.btn-xs
             {:type "button"
              :on-click (fn [_] (add-bookmark-comment @bookmark owner))}
             "add comment"]]]

          [:td.bookmark-author [:em.small author]]

          [:td.bookmark-date [:em.small (.toLocaleDateString date)]]

          ;; comment counter and toggle
          [:td
           [:a {:href (str "#comments-panel-" (url->hash url))
                :data-parent "#bookmark-table"
                :data-toggle "collapse"}
            [:span.badge
             {:data-toggle "tooltip"
              :data-placement "left"
              :title "Comments"}
             comment-count]]]

          ;; votes
          [:td
           [:button.btn.btn-default.btn-sm
            {:type "button"
             :data-toggle "tooltip"
             :data-placement "left"
             :title "Votes"
             :on-click #(vote url)}
            [:span (count votes)]
            " \u03BB"]]])))))


(defn pagination-view
  "Simple paging with selectable pages"
  [app owner {:keys [page page-size] :as state}]
  (let [page-count (/ (count (:bookmarks app)) page-size)]
    [:div.text-center
     [:ul.pagination
      (if (= page 0)
        [:li.disabled [:a {:href "#"} "\u00AB"]]
        [:li [:a {:href "#" :on-click (fn [_] (om/set-state! owner :page (dec page)))} "\u00AB"]])

      (map
       #(if (= % page)
          (vec [:li.active
                [:a {:href "#"} (inc %)
                 [:span.sr-only "(current)"]]])
          (vec [:li
                [:a {:href "#" :on-click (fn [_] (om/set-state! owner :page %))}
                 (inc %)]]))
       (range 0 page-count))

      (if (= page (Math/floor page-count))
        [:li.disabled [:a {:href "#"} "\u00BB"]]
        [:li [:a {:href "#" :on-click #(om/set-state! owner :page (inc page))} "\u00BB"]])]]))


(defsnippet back-page "bookmark.html" [:back-page]
  [owner page]
  {[:a] (listen :onClick #(om/set-state! owner :page (dec page)))})

(defsnippet page "bookmark.html" [:page]
  []
  {[:a] (content "page")})

(defsnippet pagination "bookmark.html" [:pagination]
  [owner {:keys [page]}]
  {[:ul] (content (back-page owner page)
                  (page))})


#_(.log js/console (pagination nil {:page 0}))

(comment
  (deftemplate my-page "bookmark.html"
    [data]
    {[:.content] (content (:content data))})

  (defn init [data owner] (om/component (my-page data)))

  (def app-state (atom {:content "blub"}))

  (om/root init app-state {:target (.-body js/document)}))
