(ns domain-redirector.core
  (:import (java.net URL))
  (:require [ring.util.request :as request]
            [ring.util.response :as response]
            [ring.adapter.jetty :as jetty]
            [cheshire.core :refer :all]
            [clojure.java.io :as io]
            [clojure.core.memoize :as memo]
            [domain-redirector.redis-helper :as redis]
            [domain-redirector.mongo-helper :as mongo]
            [environ.core :refer [env]]))

(def defaults {:redirect-status-code 301
               :ttl                  60000
               :port                 5000
               :protocol-header-name "x-forwarded-proto"
               :json-file            "domains.json"
               :unspecified-port     -1
               :location-header-name "Location"})

(def storage-config
  (if (env :prefer-network-backing-store)
    {:backing-store :network}
    {:backing-store :memory}))

; Network infrastructure often terminates SSL before hitting the server so we need to
; know what the real protocol is (ie what is the protocol requested by the user)
(def forwarded-protocol-header (or (env :protocol-header-name) (:protocol-header-name defaults)))

; using defrecord because: free constructor and documentation
(defrecord url-record [scheme domain port path query])

; functions to marshall URLs as data
(defn request-to-url-record [request]
  (let [url-string (request/request-url request)
        url-object (URL. url-string)
        domain (.getHost url-object)
        path (.getPath url-object)
        query (.getQuery url-object)
        headers (:headers request)
        x-protocol-header (and headers (headers forwarded-protocol-header))
        scheme (or x-protocol-header (.getProtocol url-object))
        port (.getPort url-object)]
    (->url-record scheme domain port path query)))

(defn url-string-from-record [url-record]
  (let [port (if (= (:unspecified-port defaults) (:port url-record)) "" (str ":" (:port url-record)))
        url-str (str (:scheme url-record)
                     "://"
                     (:domain url-record)
                     port
                     (:path url-record)
                     (and (:query url-record) (str "?" (:query url-record))))]
    url-str))

(defn get-domain-map-from-mongo
  [url-record]
  (if-let [path (or (= "" (:path url-record)) (= "/" (:path url-record)))]
    (mongo/get-domain-map (:domain url-record))
    (mongo/get-domain-map (:domain url-record) (:path url-record))))

(defn get-domain-from-network [url-record]
  "Obtains the domain from Redis or Mongo. Caches results from Mongo in Redis"
  (let [redis-key (str (:domain url-record) (:path url-record))]
    (if-let [redis-val (redis/get-domain-map-from-redis redis-key)]
      redis-val
      (if-let [domain-map (get-domain-map-from-mongo url-record)]
        (redis/set-domain-map-in-redis! redis-key domain-map)))))

(defn load-json-file [filename]
  (and (.exists (io/file filename))
       (first (parsed-seq (clojure.java.io/reader filename) true))))

(def domains (atom {}))

(defn set-domains-in-memory!
  ([] (let [filename (or (env :domain-json-file) (:json-file defaults))]
        (set-domains-in-memory! filename)))
  ([filename] (let [new-domains (load-json-file filename)]
                (and new-domains (reset! domains new-domains)))))

(defn- matching-domain [domain-name domain-name-list]
  (some #(= % domain-name) domain-name-list))

(defn- matching-path [matching-path path]
  (and matching-path path (.startsWith matching-path path)))

(defn get-domain-from-memory [url-record]
  (let [result (filter #(matching-domain
                         (:domain url-record) (get-in % [:source :domain])) @domains)
        path-results (and (:path url-record)
                          (filter #(matching-path (:path url-record) (get-in % [:source :path])) result))]
    (or (first path-results) (first result))))

(defn get-domain-from-backing-store [url-record]
  (if (= (:backing-store storage-config) :network)
    (get-domain-from-network url-record)
    (get-domain-from-memory url-record)))

(def get-domain
  "Memoize fetching the domain from a network store"
  (memo/ttl get-domain-from-backing-store :ttl/threshold (or (env :memoize-ttl) (:ttl defaults))))

(defn make-response-url [from-url-record mappings]
  "Produces a URL based on inputs and the target domain map"
  (let [record (->url-record (or (:scheme mappings) (:scheme from-url-record))
                             (:domain mappings)
                             (or (:port mappings) (:unspecified-port defaults))
                             (or (:path mappings) "/")
                             (or (:query from-url-record) nil))]
    (url-string-from-record record)))

(defn make-301-response [url-record target-map]
  "Create a ring response object with the 301 status code and Location header"
  (let [url (make-response-url url-record target-map)
        response-301 (response/status (response/response "") (:redirect-status-code defaults))]
    (response/header response-301 (:location-header-name defaults) url)))

(defn generate-response [request]
  (let [url-record (request-to-url-record request)]
    (if-let [mappings (get-domain url-record)]
      (make-301-response url-record (:target mappings))
      (response/not-found "Could not find forwarding domain"))))

(defn handler [request]
  (println (str "Processing request for " (request/request-url request)))
  (time (generate-response request)))

; -------*** START WEB SERVER
;
(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) (:port defaults)))]
    (if (= (:backing-store storage-config) :memory)
      (set-domains-in-memory!))

    (jetty/run-jetty handler {:port port :join? false})))

(defn test-server [port] (-main port))
