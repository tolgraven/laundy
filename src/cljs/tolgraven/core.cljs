(ns tolgraven.core
  (:require
    [reagent.dom :as rdom]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [goog.events :as gevents]
    [goog.object :as gobj]
    [goog.history.EventType :as HistoryEventType]
    [markdown.core :refer [md->html]]
    [cljs-time.core :as ct]
    [cljs-time.format :refer [formatters formatter unparse]]
    [cljsjs.smoothscroll-polyfill :as smooth]
    [react-highlight.js :as highlight]
    [cljsjs.highlight :as hljs]
    [react-transition-group :as react-transition-group]

    [tolgraven.ajax :as ajax]
    [tolgraven.events]
    [tolgraven.db :as db]
    [tolgraven.subs]
    [tolgraven.ui :as ui]
    [tolgraven.util :as util]
    [tolgraven.views :as view]
    [tolgraven.views-common :as common]
    [tolgraven.blog.views :as blog]
    [tolgraven.user.views :as user]
    [reitit.core :as reitit]
    [reitit.frontend.history :as rfh]
    [reitit.frontend.easy :as rfe]
    [clojure.string :as string]
    [clojure.pprint :refer [pprint]])
  (:import goog.History))

(defn safe "Error boundary for components. Also prints/logs error" [component]
 (let [exception (r/atom nil)] ;or reg not ratom or would cause etra re-render?
  (r/create-class
  {:display-name "Error boundary"
   :component-did-catch (fn [this error info] ;this should log instantly tho
                          (reset! exception {:error error :info info})) ; error and info are empty.
   :reagent-render
   (fn [args]
    (if-not @exception   ;state change downstream? then it gets easier to debug "in-page",
     args
     (let [[component state] args]
       (util/log :error "Component" (pprint ((js->clj component) state))) ;replace with better logging eh...
        [:section.component-failed
          [:p "Component exception"]
          [:pre (str "Error " (:error exception))]
          [:pre (str "Info " (:error exception))]
          [:div
           [:button {:on-click #(reset! exception nil)}
            "Attempt reload"]]])))})))


(defn page "Render active page inbetween header, footer and general stuff." []
  [:<>
   [common/header @(rf/subscribe [:content [:header]])] ;TODO smooth transition to personal
   [:a {:name "linktotop" :id "linktotop"}]

   (let [user-section @(rf/subscribe [:state [:user-section]])]
     [safe [:div.user-section-wrapper.stick-up ;.hi-z
      {:class (when (not= user-section :closing) "active")}
      (when user-section
        [:div.user-section.stick-up.hi-z ;.noborder
         [user/user-box
          (case user-section ;guess should hook up to proper router though.
            :login  user/sign-in-or
            :register user/register
            :admin user/admin
            :comments user/comments
            :closing user/admin)]
         ])
      ]] ;except get which from path
      )
   
   (if-let [page @(rf/subscribe [:common/page])]
     [:main.main-content.perspective-top
      {:class (if @(rf/subscribe [:state [:transition]])
                "hidden" "visible")}; "slide-in ")} ; visible
      [safe [page]]] ; this doesnt reload when switch page. need to do manually...
     [view/loading-spinner true])

   [common/footer @(rf/subscribe [:content [:footer]])]
   [ui/hud (rf/subscribe [:hud])]
   [common/to-top]
   [:a {:name "bottom"}]])


(defn intro [{:keys [title text buttons logo-bg bg]}]
  [:section#intro
   
   [:img#top-banner.media.media-as-bg (first bg)]

   [:<>
    [:h1.h0-responsive {:style {:color "var(--light-1)"}} title]
    (into [:<>] (view/ln->br text)) ; or just fix :pre css lol
    [:section.buttons
     (for [[id text] buttons] ^{:key (str "intro-button-" id)}
       [:<>
        [view/ui-button id text]
        [:br]])]
    ; [:br] [:br] [:br]
    ; [:br] [:br] [:br]
   ; [view/bg-logo logo-bg]
    [:h1 "Welcome"]
    ]])


(defn home-page []
  [intro @(rf/subscribe [:content [:intro]])])

(defn settings-page []
  [:div "SETTINGS"])

; (println (rf/subscribe [:state [:bookings :mon] ]))
(defn booking-page []
  (let [state @(rf/subscribe [:state [:bookings]])
        user @(rf/subscribe [:user/active-user-name])]
    [:section "BOOKING"
     (doall (for [day @(rf/subscribe [:content [:booking :days]]) ]
              ^{:key (str "booking-day-" (name day))}
       [:section
        {:style {:display :flex}}
        (string/capitalize (name day))
        (doall (for [shift @(rf/subscribe [:content [:booking :shifts]])
                      :let [booked-by @(rf/subscribe [:state [:bookings day shift] ])
                            queued-by @(rf/subscribe [:state [:queueing day shift] ])]]
                 ^{:key (str "booking-day-" (name day) "-shift-" (name shift))}
          [:div
           {:style {:padding "0.5rem"}}
           [:button 
            {:style {:background-color
                     (cond
                       (and (pos? (count user)) (= booked-by user)) "var(--green)"
                       (and (pos? (count user)) (= queued-by user)) "var(--purple)"
                       (pos? (count booked-by)) "var(--red)") }
             :on-click #(rf/dispatch [:booking/request day shift])}
            (name shift)]]))])) ]))

(defn log-page []
  (let [bg @(rf/subscribe [:content [:common :banner-heading]])]
    [:<>
     [view/fading-bg-heading (merge bg {:title "Log" :tint "blue"})]
     [ui/log (rf/subscribe [:option [:log]])
      (rf/subscribe [:get :diagnostics])]]))



(def router ; XXX weird thing doesnt automatically scroll to top when change page...
  (reitit/router
    [["/"
      {:name        :home
       :view        #'home-page
       :controllers [{:start (fn [_]
                               (rf/dispatch [:page/init-home])) }]}]
     ["/booking" {:name :booking
               :view #'booking-page
               :controllers [{:start (fn [_] (rf/dispatch [:page/init-settings]))}]}]
     ["/settings" {:name :settings
               :view #'settings-page
               :controllers [{:start (fn [_] (rf/dispatch [:page/init-settings]))}]}] ]))

(defn start-router! []
  (rfe/start!
    router
    (fn [match _] (rf/dispatch [:common/start-navigation match]))
    {}))
;; -------------------------
;; Initialize app
(defn mount-components "Called each update when developing" []
  (rf/clear-subscription-cache!)
  (rdom/render [#'page] (.getElementById js/document "app")))

(defn init! "Called only on page load" []
  (start-router!)
  (rf/dispatch-sync [:init-db])
  (ajax/load-interceptors!)
  (util/log "Init complete, mounting root component")
  (mount-components))
