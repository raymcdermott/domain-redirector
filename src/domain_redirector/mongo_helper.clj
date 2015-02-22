(ns domain-redirector.mongo-helper
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [environ.core :refer [env]]))

(defn get-domain-map-from-mongo [domain]
  (let [mongo-uri (or (env :mongo-url) "mongodb://localhost/test")
        {:keys [conn db]} (mg/connect-via-uri mongo-uri)
        mongo-collection (or (env :mongo-collection) "redirections")
        result (mc/find-one-as-map db mongo-collection {"source.domain" {$in [domain]}})]
    (mg/disconnect conn)
    result))