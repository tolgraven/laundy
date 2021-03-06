(ns tolgraven.routes.services
  (:require
    [reitit.swagger :as swagger]
    [reitit.swagger-ui :as swagger-ui]
    [reitit.ring.coercion :as coercion]
    [reitit.coercion.spec :as spec-coercion]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.multipart :as multipart]
    [reitit.ring.middleware.parameters :as parameters]
    [tolgraven.routes.services.graphql :as graphql]
    [tolgraven.middleware.formats :as formats]
    [tolgraven.middleware.exception :as exception]
    [tolgraven.db.core :as db]
    [taoensso.timbre :as timbre]
    ; [ring.util.http-response :refer :all]
    [ring.util.http-response :as response]
    [clojure.java.io :as io]))

(defn plain-text-header [resp]
  (response/header resp "Content-Type" "text/plain; charset=utf-8"))

; well this shit is being cached for some shit whatever reason
; so stay in home-routes for now...
(defn service-routes []
  ["/api"
   {:coercion spec-coercion/coercion
    :muuntaja formats/instance
    :swagger {:id ::api}
    :middleware [parameters/parameters-middleware       ;; query-params & form-params
                 muuntaja/format-negotiate-middleware   ;; content-negotiation
                 muuntaja/format-response-middleware    ;; encoding response body
                 exception/exception-middleware         ;; exception handling
                 muuntaja/format-request-middleware     ;; decoding request body
                 coercion/coerce-response-middleware    ;; coercing response bodys
                 coercion/coerce-request-middleware     ;; coercing request parameters
                 multipart/multipart-middleware]}       ;; multipart

   ;; swagger documentation
   ["" {:no-doc true
        :swagger {:info {:title "my-api"
                         :description "https://cljdoc.org/d/metosin/reitit"}}}

    ["/swagger.json"
     {:get (swagger/create-swagger-handler)}]

    ["/api-docs/*"
     {:get (swagger-ui/create-swagger-ui-handler
             {:url "/api/swagger.json"
              :config {:validator-url nil}})}]]

   ["/fart" {:get (fn [_]
                    (-> "buttrez"
                        response/ok
                        plain-text-header))}]
   ["/blog" {:summary "Get specific blog-post"
             :parameters {:query {:id int?}}
             :get (fn [{{{:keys [id]} :query} :parameters}]
                    (-> (db/get-blog id)
                        ; (str "faaart")

                        response/ok
                        plain-text-header))}]
   ["/blog/:id" {:get (fn [{{:keys [id]} :path-params}]
                    (-> (db/get-blog id)
                        str
                        response/ok
                        plain-text-header))}]
   ; ["/blog-new" {:post (fn [{{:keys [id]} :path-params}]
   ;                       (-> (db/add-blog id)
   ;                           str
   ;                           response/ok
   ;                           plain-text-header))}]
   ["/user/:id" {:get (fn [{{:keys [id]} :path-params}]
                        (let [user (db/get-user id)]
                          (timbre/debug user)
                          (-> (or user {})
                              str
                              response/ok
                              plain-text-header)))}]

   ["/ping"
    {:get (constantly (response/ok {:message "pong"}))}]
   
   ["/graphql" {:no-doc true
                :post (fn [req] (response/ok
                                 (graphql/execute-request
                                  (-> req :body slurp))))}]

   ["/math"
    {:swagger {:tags ["math"]}}

    ["/plus"
     {:get {:summary "plus with spec query parameters"
            :parameters {:query {:x int?, :y int?}}
            :responses {200 {:body {:total pos-int?}}}
            :handler (fn [{{{:keys [x y]} :query} :parameters}]
                       {:status 200
                        :body {:total (+ x y)}})}
      :post {:summary "plus with spec body parameters"
             :parameters {:body {:x int?, :y int?}}
             :responses {200 {:body {:total pos-int?}}}
             :handler (fn [{{{:keys [x y]} :body} :parameters}]
                        {:status 200
                         :body {:total (+ x y)}})}}]]

   ["/files"
    {:swagger {:tags ["files"]}}

    ["/upload"
     {:post {:summary "upload a file"
             :parameters {:multipart {:file multipart/temp-file-part}}
             :responses {200 {:body {:name string?, :size int?}}}
             :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                        {:status 200
                         :body {:name (:filename file)
                                :size (:size file)}})}}]

    ["/download"
     {:get {:summary "downloads a file"
            :swagger {:produces ["image/png"]}
            :handler (fn [_]
                       {:status 200
                        :headers {"Content-Type" "image/png"}
                        :body (-> "public/img/warning_clojure.png"
                                  (io/resource)
                                  (io/input-stream))})}}]]])
