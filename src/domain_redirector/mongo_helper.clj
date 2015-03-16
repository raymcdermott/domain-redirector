(ns domain-redirector.mongo-helper
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [environ.core :refer [env]]))


(defn get-redirect-map [query]
  (let [mongo-uri (or (env :mongo-url) "mongodb://localhost/test")
        {:keys [conn db]} (mg/connect-via-uri mongo-uri)
        mongo-collection (or (env :mongo-collection) "redirections")
        result (mc/find-one-as-map db mongo-collection query)]
    (mg/disconnect conn)
    result))


(defn get-domain-map
  ([domain]
   (get-redirect-map {"source.domain" {$in [domain]} "source.path" {$exists false}}))
  ([domain path]
   (get-redirect-map {"source.domain" {$in [domain]} "source.path" path})))

