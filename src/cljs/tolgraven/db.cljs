(ns tolgraven.db
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [cljs.reader]))


; issue anyways is often start with this then need to change to doing it properly...
(defn <-db "Quick subscription getter. Bad habit I guess..."
 [& path] ;default is better handled ext since this goes through :get -> we already know there is a valid sub
 (let [path (if (seqable? (first path)) (first path) path) ;handle both individual args and vector
       sub (rf/subscribe (into [:get] path))]
  @sub))

(defn ->db "Quick db setter. Returns passed value."
 [path value]
 (rf/dispatch [:set path value])
 value)


(defn setter "Quick db setter fn getter"
 [path]
 (fn [value & _] (->db path value)))

(defn toggle "Toggle bool at path in db"
 [path]
 (rf/dispatch [:toggle path]))


; localstorage i took from somewhere, maybe use for partly written comments
(def user-key "tolglow-web-dev-storage")  ;; localstore key

(defn set-user-ls [user]
  (.setItem js/localStorage user-key (str user)))  ;; sorted-map written as an EDN map

(defn remove-user-ls  []
  (.removeItem js/localStorage user-key))

(rf/reg-cofx :local-store-user
 (fn [cofx _]
   (assoc cofx :local-store-user  ;; put the local-store user into the coeffect under :local-store-user
          (into (sorted-map)      ;; read in user from localstore, and process into a sorted map
                (some->> (.getItem js/localStorage user-key)
                         (cljs.reader/read-string))))))  ;; EDN map -> map


(def data ; default db
  {:state {:menu false
           :theme-force-dark true
           :user-section :login
           :bookings {}}
   :users [{:id 0 :name "user1" :password "user1"}
           {:id 1 :name "user2" :password "user2" :roles [:blogger]}]
   :content {:header {:text ["LAUNDRY" ["booking" "system"]]
                      :menu {:work  [["Booking"  "#" :services]
                                     ["Settings"     "#settings"        :about] ]
                             :personal [ ["Log"       "#/log"      :log]]} }
             :intro {:title "Laundry"
                     :text ""
                     :buttons  [["Book"            "/booking"]
                                ["Settings"        "/settings"]]
                     :bg [{:src "img/skandinavisk-tvaettstuga-4.jpg" :alt "Tvättstuga"} ]
                     :logo-bg "img/tolgrav.png"} ; no :src cause goes in :style background-image...

             :booking {:title "Booking"
                       ; :days ["Mon" "Tue" "Wed" "Thu" "Fri" "Sat" "Sun"]
                       :days [:mon :tue :wed :thu :fri :sat :sun]
                       :shifts [:08-12 :12-16 :16-20 :20-24]}

             :common  {:banner-heading {:bg {:src "img/wide-spot-ctrl-small.jpg"}}}

             :footer [{:id "left"
                       :title "laundry@bookers.co"
                       :text ["© 2020"]
                       :img {:src "img/cljs.png" :alt "cljs logo"}}
                      {:id "middle"
                       :title ""
                       :text [""]}
                      {:id "right"
                     ; :title "More ways to get in touch"
                       :links [{:name "Github" :href "https://github.com/tolgraven" :icon "github"}
                               {:name "Facebook" :href "https://facebook.com/tolgraven" :icon "facebook"} ]}]}

   :options {:auto-save-vars true
             :transition {:time 200 :style :slide} ; etc
             :hud {:timeout 15 :level :info}}
   })
   
; DONT FORGET STUPID #_ fucks you for some reason??

(rf/reg-event-db :init-db [rf/debug]
  (fn [db _]
    data))
