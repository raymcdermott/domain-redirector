(ns domain-redirector.core
  (:import (java.net URL))
  (:require [ring.util.request :as request]
            [ring.util.response :as response]
            [ring.adapter.jetty :as jetty]
            [domain-redirector.redis-helper :as redis]
            [domain-redirector.mongo-helper :as mongo]
            [environ.core :refer [env]]))

(def default-unspecified-port -1)

(def forwarded-protocol-header (or (env :protocol-header-name) "x-forwarded-proto")) ; default is the header provided by Heroku

; using defrecord because: free constructor and documentation
(defrecord url-record [scheme domain port path query url-key])

; functions to marshall URLs as data
(defn request-to-url-record [request]
  (let [url-string (request/request-url request)
        url-object (URL. url-string)
        domain (.getHost url-object)
        path (.getPath url-object)
        query (.getQuery url-object)
        headers (:headers request)
        x-protocol-header (headers forwarded-protocol-header)
        scheme (or x-protocol-header (.getProtocol url-object))
        port (.getPort url-object)]
    (->url-record scheme domain port path query (str domain path))))

(defn url-string-from-record [url-record]
  (let [port (if (= default-unspecified-port (:port url-record)) "" (str ":" (:port url-record)))
        url-str (str (:scheme url-record) "://"
                     (:domain url-record)
                     port
                     (:path url-record)
                     (and (:query url-record) (str "?" (:query url-record))))]
    url-str))

; TODO -- think ... do we need to evaluate for longest matching path?

(defn get-domain-map-from-mongo
  [url-record]
  (if-let [path (or (= "" (:path url-record)) (= "/" (:path url-record)))]
    (mongo/get-domain-map (:domain url-record))
    (mongo/get-domain-map (:domain url-record) (:path url-record))))

(defn get-domain [url-record]
  "Obtains the domain from Redis or Mongo. Caches results from Mongo in Redis"
  (if-let [redis-val (redis/get-domain-map-from-redis (:url-key url-record))]
    redis-val
    (if-let [domain-map (get-domain-map-from-mongo url-record)]
      (redis/set-domain-map-in-redis! (:url-key url-record) domain-map))))

(defn make-response-url [from-url-record mappings]
  "Produces a URL based on inputs and the target domain map"
  (let [record (->url-record (or (:scheme mappings) (:scheme from-url-record))
                             (:domain mappings)
                             (or (:port mappings) default-unspecified-port)
                             (or (:path mappings) "/")
                             (or (:query from-url-record) nil)
                             (str (:domain mappings) (:path mappings)))]
    (url-string-from-record record)))

(defn make-301-response [url-record target-map]
  "Create a ring response object with the 301 status code and Location header"
  (let [url (make-response-url url-record target-map)
        response-301 (response/status (response/response "") 301)]
    (response/header response-301 "Location" url)))

(defn generate-response [request]
  (let [url-record (request-to-url-record url)]
    (if-let [mappings (get-domain url-record)]
      (make-301-response url-record (:target mappings))
      (response/not-found "Could not find forwarding domain"))))

(defn print-headers [headers]
  (clojure.pprint/pprint headers))

(defn handler [request]
  (println (str "Processing request for " (request/request-url request)))
  (print-headers (:headers request))
  (time (generate-response request)))

; -------*** START WEB SERVER
;
(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty handler {:port port :join? false})))

;(.stop server)
;(def server (-main))