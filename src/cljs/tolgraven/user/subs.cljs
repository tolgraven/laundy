(ns tolgraven.user.subs
  (:require
   [re-frame.core :as rf]))

(rf/reg-sub :user/users
 :<- [:get :users]
 (fn [users [_ path]]
   (get-in users path)))

(rf/reg-sub :user/user ;find user
 :<- [:get :users]
 (fn [users [_ user]]
   (first (filter #(= (:name %) user) users))))

(rf/reg-sub :user/active-user-name
 :<- [:state [:user]]
 (fn [user [_ _]]
   user))
(rf/reg-sub :user/active-user-details
 :<- [:state [:user]]
 (fn [user [_ _]]
   @(rf/subscribe [:user/user user])))

(rf/reg-sub :user/session
 :<- [:state [:user]]
 (fn [user [_ _]]
   (-> user :session)))

; (rf/reg-sub :user/status
;  :<- [:user/session]
;  (fn [session [_ _]]
;    (-> session :status)))

(rf/reg-sub :register/field
 :<- [:state [:register]]
 (fn [register [_ field]]
  (field register)))

(rf/reg-sub
 :user/login-valid-input?
 :<- [:state [:login-field]]
 (fn [login-field [_ field]]
   true)) ; do validation.
