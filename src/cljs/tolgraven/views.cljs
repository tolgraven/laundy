(ns tolgraven.views
  (:require
   [reagent.core :as r]
   [re-frame.core :as rf]
   [re-graph.core :as rg]
   [reitit.frontend.easy :as rfe]
   [clojure.string :as string]
   [markdown.core :refer [md->html]]
   [tolgraven.ui :as ui]
   [tolgraven.db :as db :refer [<-db ->db]]
   [tolgraven.util :as util :refer [at]]))


(defn ln->br "Ugh. UGH! Why"
  [text]
  (for [line (string/split-lines text)]
        [:p line]))

(defn bg-logo "Try to remember why had to put img in css/style..."
  [path]
  (let [div-ref (r/atom nil) ;wait should actually go in intro, pass us in-view
        in-view (r/atom 0.0)
        observer (util/frac-in-view #(reset! in-view %))]
    (fn [path]
      (observer div-ref)
      [:div#logo-top.logo-bg.stick-up ;.parallax-sm
       {:class "logo-tolgraven"
        :style {:background-image (str "url('" path "')")
                :opacity (str "0.29 + " (/ (- 1 @in-view) 5))}
        :ref #(reset! div-ref %)}
       [:p @in-view]]))) ; cant remember why I did the weird path-in-css bs but anyways...


(defn link-img-title "Link eith an image and a title, for posts for example"
  [{:as content :keys [title text url side]
    :or [side :left]}]
  (let []
    [:div.float-wrapper
     [:div.caption-inset {:class (str side)}
      [:p text]]
     [:h2 title]]))

(defn input-toggle "Don't forget to put ze label - only was sep in first place due to css bs?"
  [id checked-path & {:keys [class label]}]
  [:input ;.toggle
   {:id id :class class ;must be outside header or breaks...
    :type "checkbox"    :default-checked @(rf/subscribe checked-path)
    :on-click (fn []
                ; (rf/dispatch (into (or on-click-path checked-path)
                (rf/dispatch (into checked-path
                                   [(not @(rf/subscribe checked-path))])))}])

(defn loading-spinner [model]
  [:div.loading-spinner
     (when (at model) ;should it be outside so not put anything when not loading? or better know element goes here
       [:i {:class "fa fa-spinner fa-spin"}])])

(defn user-menu [model]
  (let [user @(rf/subscribe [:state [:user]])]
  [:div.user-menu
   [:i {:class "fa fa-user"
        :style {:position :absolute
                :left "88%"
                :top "40%"}
        :on-click #(rf/dispatch [:user/request-page])}
    (when user [:span user])]]))


(defn ui-button "Pass text and id, plus either link anchor or action..."
  [text id & {:keys [type bg-div-class link action]
              :or   {type "button" bg-div-class "blur-bg" link (str "#" id)}}]
  [:button {:id (str "btn-" id) :type type}
   (when bg-div-class [:div {:class bg-div-class}])
   [:label {:for (str "btn-" id)}
    (if action
      true
      [:a {:href link} text]) ]])

(defn ui-fading "Hitherto just css but prog gen prob easier in some cases..."
  [& {:keys [fade-to dir content]
      :or {fade-to "fade-to-black" dir "light-from-below"}}]
  [:div.fader [:div {:class (str fade-to " " dir)}]])


(defn ui-intro [{:keys [title text buttons logo-bg bg]}]
  [:section#intro
   [bg-logo logo-bg]
   [:img#top-banner.media.media-as-bg (first bg)]

   [:<>
    [:h1.h-responsive title]
    (into [:<>] (ln->br text)) ; or just fix :pre css lol
    ; [:br]
    [:div.buttons
     (for [[id text] buttons] ^{:key (str "intro-button-" id)}
       [ui-button id text])]]])


(defn ui-moneyshot "needs better name lol. what is hero img halfway down page?"
  [{:keys [title caption bg]}]
  (let [div-ref (r/atom nil) ;] ; gotta ratom so can give empty first? cause wont be mounted when building etc? ;div-ref (atom nil) ; yes is true
        frac (r/atom 0.0)
        observer (util/frac-in-view #(reset! frac %))]
    (fn [{:keys [title caption bg]}]
        (observer div-ref)
        [:div {:class "section-with-media-bg-wrapper covering stick-up"
               :ref #(reset! div-ref %)}
         [:img.media-as-bg
          (merge bg {:class "fade-5 parallax-sm" ; origin-toptop
                     :style (merge (when-not (pos? @frac)
                                     {:visibility "hidden"})
                                   {:transition "transform 2.5s ease"
                                    :transform (str "translateZ(calc(5px * " @frac "))")})})]
                                    ; but proper way would be, do nothing, attach class,
                                    ; it then has some anim whatever, right? seems it's
                                    ; throttled while scrolling or maybe just events?
         [:section#intro-end.center-content
          [:h1.h0-responsive.parallax-bg
           {:style {:transition "transform 2.5s ease"
                    :transform (str "translateZ(" (* 33 @frac) "px)")}}
           title]] ;ideally want this also growing (and moving quicker upwards)]
         [ui-inset caption 3]
         [ui-inset (str "Fraction visible:" frac) 2]
         [ui-fading]])))

;; TODO basically figure out neatest way of getting gotten rid of in-the-way bg layers...
;; parallax makes things tricky apparently - cue-db viz it works both from above and below...
;; but here only get 0 above, stuck on 1 below.
;; guess full gamut of did-mount for better observer, plus loadsa self-modding killing parallax?
;; just look at how people normally do it in react i guess.
;;
;; but clearly "out there fkn content" + mostly clipping div then tracking that leaky container div
;; must be right course of action.
(defn fading-bg-heading [{:keys [title bg tint] :as content}]
  [:<>
   [:div {:class "section-with-media-bg-wrapper covering stick-up fullwidth"}
    [:div.fader
     ; [:img.media.media-as-bg.parallax-sm bg]
     [:img.media.media-as-bg bg]
     [:section.covering-faded.noborder
      {:style (when tint {:background (str "var(--" tint ")")
                          :filter "saturate(1.9) brightness(0.9)"})}
      [:h1.h-responsive
       {:style {:transform "translateY(-15%)"}}
       title]]]]
   [:div.fader>div.fade-to-black.bottom]])


(defn ui-story "Big img header + story" [{:keys [heading] :as content}]
  [:<>
   [:div#about-intro {:class "section-with-media-bg-wrapper covering stick-up fullwidth"}
    [fading-bg-heading heading]]
   [:div.fader>div.fade-to-black.between]

   [:section#about-story.anim-gradient-bg.noborder
    [:h1#about.link-anchor (:title content)]
    [ui/auto-layout-text-imgs content]]])

(defn ui-gallery "Stupid css thing slides sidewayus x) Make it go out left side would be cool"
  [img-attrs]
  [:section#gallery.covering.fullwide
   [:div.sideways
    (for [img img-attrs] ^{:key (str "gallery-" (:src img))}
         [:img.media img])]])

; tho should do hiccup pre-render server side then just inject news feed and whatnots
; TODO for good separation of frontpage / personal/bloggy, and leveraging "line all the way to right"
; make entire view scroll sideways and basically flip geometry
; so literally parallel pages
; logo text opposite side and changes to "tolgraven actual physical" or w/e,
; colors bit different,
(defn ui []
  (let [interlude @(rf/subscribe [:content [:interlude]])
        interlude-counter (atom 0)
        get-lewd #(merge (nth interlude @interlude-counter)
                         {:nr (swap! interlude-counter inc)})]
    [:<>
     [ui-intro @(rf/subscribe [:content [:intro]])]

     [ui-interlude (get-lewd)]
     [ui-services @(rf/subscribe [:content [:services]])]

     [ui-interlude (get-lewd)]
     [ui-moneyshot @(rf/subscribe [:content [:moneyshot]])]
     ; need to watch div and show/hide para laxy bee gees as appropriate - both bc now fucking compositor and ugly clipping etc
     ; also sidesteps "omg each new div higher z" induced problem
     [ui-story @(rf/subscribe [:content [:story]])]
     [ui-interlude (get-lewd)]
     [ui-gallery @(rf/subscribe [:content [:gallery]])]
     [ui-interlude (get-lewd)]]))

