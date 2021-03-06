(ns tolgraven.ui
  (:require
   [reagent.core :as r]
   [re-frame.core :as rf]
   [re-graph.core :as rg]
   [tolgraven.db :as db]
   [tolgraven.util :as util :refer [at]]
   [clojure.string :as string]
   [clojure.pprint :as pprint]
   [markdown.core :refer [md->html]]
   [cljsjs.react-highlight :as highlight]
   [highlight.js :as highlight.js] ; can auto-run on hook instead of tagging manually...
   [cljsjs.highlight.langs.clojure] ; needs to be included somewhere
   [react-transition-group :as rtg]
   [cljs-time.core :as ct]
   [cljs-time.format :refer [formatters formatter unparse]]))

(defn md->div [md]
  [:div {:dangerouslySetInnerHTML {:__html (md->html md)}}])

(defn button "Pass text and id, plus either link anchor or action..."
  [text id & {:keys [type bg-div-class link action disabled?]
              :or   {type "button" bg-div-class "blur-bg"
                     #_link #_(str "#" id)}}]
  [:button {:id (str "btn-" id) :type type :on-click action :disabled disabled?}
   (when bg-div-class [:div {:class bg-div-class}])
   [:label {:for (str "btn-" id)}
    (if link
      [:a {:href link} text]
      text)]])

(defn toggle-for-path "Like below but supports attrs + path. I mean could just check if first arg is map anyways but yeah"
  ([model-path])
  ([attrs model-path]))

(defn toggle "A nice little (but not native checkbox little) toggle"
 ([model-path]
  (let [on-change #(rf/dispatch [:set model-path %])
        model (rf/subscribe (into [:get] model-path))]
  [toggle {} model on-change]))
 ([model on-change]
  (toggle {} model on-change))
 ([attrs model on-change]
  (let []
   [:label.toggle-switch
      attrs
    #_(util/deep-merge {:style {}} attrs)
    [:input {:type :checkbox :default-checked @model
             :on-click (fn [e] ; (.preventDefault e) ;broke it! :O what
                 (on-change (not @model)))}]
    ; [:span.toggle-slider.round]
    ])))

(defn burger "Toggle main menu. Entire point vs css is shove everything below down by extra height..."
  ([id model on-change]
   [:label.burger
    {:for id}
    [:input.burger-check
     {:id id :type :checkbox
      :default-checked @model
      :on-click (fn [e] ; (.preventDefault e) ;broke it! :O what
                  (on-change (not @model)))}]]))

(defn float-img "Needs to go within a float-wrapper..."
  [id img-attr & [caption pos]]
  [:figure.float-with-caption {:id id :class (or pos "left")}
      [:img.media.image-inset img-attr]
      (when caption [:figcaption caption])])

(defn auto-layout-text-imgs "Take text and images and space out floats appropriately. Pretty dumb but eh"
  [content]
  (let [text-part (string/split-lines (:text content))
         img-count (count (:images content))
         line-count (count text-part)
         chunk-size (/ line-count img-count)
         content (->> (interleave (map #(into [float-img] %)
                                        (:images content))
                                  (map #(into [:p] %)
                                        (partition chunk-size text-part)))
                      (map-indexed (fn [i v]
                                     (with-meta
                                      v {:key (str "auto-layout-part-" i)}))))] ;would need a parent id thingy as well tho
     [:div.float-wrapper
      [:h3 (:title content)]
      content]))


(defn material-toggle
 [model-path [on-state off-state & [prefix]]]
 (let [model (rf/subscribe (into [:get] model-path))]
  [:i.zmdi
   {:class (str "zmdi-"
                (when prefix (str prefix "-"))
                (if @model on-state off-state))
    :style {:margin "0.1em 0.2em"}
    :on-click #(db/toggle model-path)}]))

(defn minimize [model-path]
 [material-toggle
  (into model-path [:minimized])
  ["maximize" "minimize" "window"]])

(defn close [on-click]
 [:i.zmdi.zmdi-close.close-btn
  {:on-click on-click}])

(defn formatted-data [title path-or-data]
 (let [data (if (vector? path-or-data)
             @(rf/subscribe path-or-data)
             path-or-data)]
  [:div {:style {:text-align :left}}
  [:h5 title]
  [:pre (pprint/write data :stream nil)]]))


(defn input-text "Returns markup for a basic text input label"
 [& {:as args :keys [value path on-enter]}]
 (let [external-model (r/atom (or (rf/subscribe path) (at value))) ;ok so why does (sub in ratom...) work, straight subscribe not...
       internal-model (r/atom (if (nil? @external-model) "" @external-model))] ;; Create a new atom from the model to be used internally (avoid nil)
  (fn [& {:as args :keys [value path on-enter on-change placeholder width height change-on-blur? disabled? class style attr]}] ;to pass through from outer
   (let [latest-ext-model (or @(rf/subscribe path) (at value)) ;how repl this if not passing model but sub?
         disabled?        (at disabled?)
         change-on-blur?  (at change-on-blur?)]
    (when (not= @external-model latest-ext-model) ;; Has model changed externally?
     (reset! external-model latest-ext-model)
     (reset! internal-model latest-ext-model))
    [:input.form-control
     (merge {:class class, :type "search" ;for clear button ;"text"
             :style (merge {:display "inline-flex" :flex "1 1 auto"
                            :width (or width 100) ; how do like "min-width 'chars in str model + 10' up til 200 pixels yada?"
                            :height height}       ; user best wrap in div or pass class for more fine grained control either way
                           style)
             :placeholder placeholder
             :value       @internal-model
             :disabled    disabled?
             :on-change (fn [e]
                         (let [new-val (-> e .-target .-value)]
                          (when (and on-change (not disabled?))
                           (reset! internal-model new-val)
                           (when-not change-on-blur?
                            (reset! external-model @internal-model) ;uhh cant do that obviously
                            (on-change @internal-model)))))
             :on-blur   (fn [e]
                         (when (and on-change change-on-blur?
                                    (not= @internal-model @external-model))
                          (reset! external-model @internal-model) ;nor here...
                          (on-change @internal-model)))
             :on-key-up (fn [e]
                         (if disabled? (.preventDefault e)
                          (case (.-key e)
                           "Enter" (if change-on-blur?
                                    (do (reset! external-model @internal-model)
                                        (when on-change (on-change @internal-model)))
                                    (when (and on-enter (not= "" @internal-model))
                                     (on-enter @internal-model)))
                           "Escape" (if change-on-blur?
                                     (reset! internal-model @external-model)) ;already loses focus automatically when press esc
                           true)))}
            attr)])))) ;after not before, want to be able to override stuff duh


(defn log "Show an expandable log thingy. Prob dumb here but good base for any sorta feed thingy I guess!"
  [options content]
 (let [time-format (formatters :hour-minute-second)
       table-ref (atom nil) ; scroll (r/atom nil)
       log-line (fn [{:keys [time level title message] :as msg}]
                  [:tr.log-messages
                   [:td.log-time (unparse time-format time)]
                   [:td.log-level {:class (name level)} (name level)]
                   [:td.log-title title]
                   [:td.log-message #_{:style {:position :relative :left "1em"}} (str message)]])]
  (r/create-class
   {:display-name "Log"
    :component-did-update (fn [this]
                           (set! (.-scrollTop @table-ref)
                                 (.-scrollHeight @table-ref))) ;resort to this since :scroll-top @ratom in the actual element doesnt work...
    :reagent-render
    (fn []
      (let [messages (:messages @content)]
        [:section.log-container.solid-bg
         [minimize [:state [:display :log]]] ;this also needs to send an event to scroll-top the fucker...
         [:table.lined-container-sm
            {:ref (fn [el] (reset! table-ref el))
             :style {:max-height (if (:minimized @options) "1.2em" "20em")}}
          [:tbody.log
           (for [msg (map messages (sort (keys messages))
                          #_(if (:minimized @options) ;upside-down?
                              [(count messages)]
                              (sort (keys messages))))]
             ^{:key (str (:id msg))}
             [log-line msg])]]]))})))

(defn modal "Container for anything modal, taking care of common stuff. USE FOR COOKIE NOTICE LOL YES"
 [component & [on-outside-click]]
 (let []
  (rf/dispatch [:modal true])
  [:div#modal-container
   [:div#modal-bg {:on-click on-outside-click
                   :style {:position :fixed
                           :width "100%" :height "100%" :top 0 :left 0
                           :background "rgb(30, 30, 30, 0.5)"}}]
   [:div#modal {:class (when @(rf/subscribe [:modal]) "modal-is-open")}
    component]]))

(defn hud-modal "Show more info about a specific HUD message"
 [] ;doesnt really have to be modal but wanted to implement that, so...
 (if-let [msg @(rf/subscribe   [:hud :modal])]
  (let [to-close #(rf/dispatch [:hud :modal :remove])]
   [modal [:div.hud-modal-main
           {:class (str "hud-message " (name (:level msg)))}
           [:h3  (:title   msg)]
           [:p   (str (:message msg))]
           [:p   (str (:time    msg))]
           [close to-close]]
    to-close])
  (rf/dispatch [:modal false]))) ;eww gross

(defn hud "Render a HUD sorta like figwheel's but at reagent/re-frame level"
  [to-show]
 (let [;to-show @(rf/subscribe [:hud])
       msg-fn (fn [{:keys [level title message time actions id]}]
                (let [class (str "hud-message " (name level))]
                 [:div.hud-message
                  {:class class
                   :style {:position :relative}
                   :on-click #(rf/dispatch (or (:on-click actions) ; or actions navigate to log...
                                               [:diag/unhandled :remove id])) }
                  [:span [:em title]]
                  [:p message]
                  [close (fn [e]
                             (.stopPropagation e) ;it's causing a click on hud-message as well...
                             (rf/dispatch [:diag/unhandled :remove id]))]]))]
  [:div.hud.hidden
   {:class (when (seq @to-show) "visible")}
   (for [msg @to-show]
     [msg-fn msg])]))



(def highlight (r/adapt-react-class js/Highlight))
(def css-trans (r/adapt-react-class rtg/CSSTransition))
