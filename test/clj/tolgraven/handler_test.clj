(ns tolgraven.handler-test
  (:require
    [clojure.test :refer :all]
    [ring.mock.request :refer :all]
    [tolgraven.handler :refer :all]
    [tolgraven.middleware.formats :as formats]
    [muuntaja.core :as m]
    [mount.core :as mount]))

(defn parse-json [body]
  (m/decode formats/instance "application/json" body))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'tolgraven.config/env
                 #'tolgraven.handler/app-routes)
    (f)))

(deftest test-app
  (testing "main route"
    (let [response ((app) (request :get "/"))]
      (is (= 200 (:status response)))))

  (testing "not-found route"
    (let [response ((app) (request :get "/invalid"))]
      (is (= 404 (:status response)))))
  (testing "services"

    (testing "success"
      (let [response ((app) (-> (request :post "/api/math/plus")
                                (json-body {:x 10, :y 6})))]
        (is (= 200 (:status response)))
        (is (= {:total 16} (m/decode-response-body response)))))

    (testing "parameter coercion error"
      (let [response ((app) (-> (request :post "/api/math/plus")
                                (json-body {:x 10, :y "invalid"})))]
        (is (= 400 (:status response)))))

    (testing "response coercion error"
      (let [response ((app) (-> (request :post "/api/math/plus")
                                (json-body {:x -10, :y 6})))]
        (is (= 500 (:status response)))))

    (testing "content negotiation"
      (let [response ((app) (-> (request :post "/api/math/plus")
                                (body (pr-str {:x 10, :y 6}))
                                (content-type "application/edn")
                                (header "accept" "application/transit+json")))]
        (is (= 200 (:status response)))
        (is (= {:total 16} (m/decode-response-body response)))))

    (testing "get user"
      (let [response ((app) (-> (request :get "/user/tolgraven")
                                ; (body (pr-str {:x 10, :y 6}))
                                ; (content-type "application/edn")
                                #_(header "accept" "application/transit+json")))]
        (is (= 200 (:status response)))
        (is (map? (m/decode-response-body response)))))))
