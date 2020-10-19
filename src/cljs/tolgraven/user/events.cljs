(ns tolgraven.user.events
  (:require
   [re-frame.core :as rf]
   [re-frame.std-interceptors :refer [path]]
   [tolgraven.util :as util]))


(defn- get-user
  [user users]
  (first (filter #(= (:name %) user) users)))

(rf/reg-event-fx
 :user/request-login
 (fn [{:keys [db]} [_ info]]
   (let [login (-> db :state :login-field)
         user (get-user (:user login) (-> db :users))]
     (if (and (pos? (count (:user login)))
              (= (:password login) (:password user)))
       {:dispatch [:user/login (:user login)]}
       {:dispatch [:diag/new :error "Sign in" "Can't find user, or wrong password"]}))))

(rf/reg-event-db :user/login 
(fn [db [_ user]]
  (-> db
      (assoc-in [:state :user] user)
      (assoc-in [:state :user-section] :admin))))

(rf/reg-event-db :user/logout 
(fn [db [_ user]]
  (-> db
      (update-in [:state] dissoc :user)
      (assoc-in [:state :user-section] :login)
      )))


(rf/reg-event-fx
 :user/request-register [(path [:state])]
 (fn [{:keys [db]} [_ info]]
   (let [{:keys [user password]} (:login-field db)
         {:keys [email]} (:register-field db)]
   {:dispatch-n [[:user/register user password email]
                 [:user/login user password]
                 [:state [:user-section] :admin]]})))

(rf/reg-event-db :user/register
 (fn [db [_ user password email]]
   (update-in db [:users] conj {:name user
                                :password password
                                :email email})))

(rf/reg-event-fx
 :user/request-page
 (fn [{:keys [db]} [_ info]]
   (let [user (get-in db [:state :user])]
     {:dispatch (if user
                  [:state [:user-section] :admin]
                  [:state [:user-section] :login])})))


(rf/reg-event-db
 :user/active-page
 (fn [db [_ v]]
   (assoc-in db [:state :user-section] v)))

(rf/reg-event-fx ;needs to defer changing :user-section to false
 :user/close-ui
 (fn [{:keys [db]} [_ v]]
   {:dispatch-later {:ms 400,
                     :dispatch [:state [:user-section] false]}}
    :db (assoc-in db [:state :user-section] :closing)))
