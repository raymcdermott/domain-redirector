(ns redirector-unit-tests
  (:require [clojure.test :refer :all]
            [environ.core :refer [env]]
            [domain-redirector.core :as redirector]))

; Test that it loads the test defaults
(deftest test-load-json
  (let [json (redirector/load-json-file (env :domain-json-file))
        first-json (first json)
        second-json (second json)]
    (is (false? (empty? json)))
    (is (<= 4 (count json)))
    (is (= (get-in first-json [:target :domain]) "www.google.com"))
    (is (= (get-in second-json [:target :domain]) "www.bing.com"))))

; check that we create the maps / records correctly

(deftest check-url-record-creation
  (let [simple-record (redirector/request-to-url-record {:scheme "http" :uri "localhost"})]
    (is (= (:scheme simple-record) "http"))
    (is (= (:domain simple-record) "localhost"))
    (is (= (redirector/url-string-from-record simple-record) "http://localhost")))

  (let [path-record (redirector/request-to-url-record {:scheme "http" :uri "localhost/path"})]
    (is (= (:scheme path-record) "http"))
    (is (= (:domain path-record) "localhost"))
    (is (= (:path path-record) "/path"))
    (is (= (redirector/url-string-from-record path-record) "http://localhost/path")))

  (let [query-record (redirector/request-to-url-record {:scheme "http" :uri "localhost/path?q=1&r=2"})]
    (is (= (:scheme query-record) "http"))
    (is (= (:domain query-record) "localhost"))
    (is (= (:path query-record) "/path"))
    (is (= (:query query-record) "q=1&r=2"))
    (is (= (redirector/url-string-from-record query-record) "http://localhost/path?q=1&r=2")))

  (let [headers {"x-forwarded-proto" "https"}
        x-proto-record (redirector/request-to-url-record {:scheme "http" :uri "localhost/path?q=1&r=2" :headers headers})]
    (is (= (:scheme x-proto-record) "https"))
    (is (= (:domain x-proto-record) "localhost"))
    (is (= (:path x-proto-record) "/path"))
    (is (= (redirector/url-string-from-record x-proto-record) "https://localhost/path?q=1&r=2"))))

; work on the filters

(deftest test-match-domains-in-memory
  (redirector/set-domains-in-memory!)
  (let [matching-record (redirector/get-domain-from-memory
                          (redirector/request-to-url-record {:scheme "http" :uri "localhost"}))]
    (is (= (get-in matching-record [:target :domain]) "www.google.com")))
  (let [matching-record (redirector/get-domain-from-memory
                          (redirector/request-to-url-record {:scheme "http" :uri "other.host.com"}))]
    (is (= (get-in matching-record [:target :domain]) "www.google.com")))
  (let [matching-record (redirector/get-domain-from-memory
                          (redirector/request-to-url-record {:scheme "http" :uri "this.host.com"}))]
    (is (= (get-in matching-record [:target :domain]) "www.bing.com"))))

(deftest test-match-paths-in-memory
  (redirector/set-domains-in-memory!)
  (let [matching-record (redirector/get-domain-from-memory
                          (redirector/request-to-url-record {:scheme "http" :uri "my.host.com/google"}))]
    (is (= (get-in matching-record [:target :domain]) "www.google.com")))
  (let [matching-record (redirector/get-domain-from-memory
                          (redirector/request-to-url-record {:scheme "http" :uri "my.host.com/bing"}))]
    (is (= (get-in matching-record [:target :domain]) "www.bing.com"))))

; by default this should call the in-memory domains
(deftest test-get-domain
  (redirector/set-domains-in-memory!)
  (let [matching-record (redirector/get-domain
                          (redirector/request-to-url-record {:scheme "http" :uri "my.host.com/google"}))]
    (is (= (get-in matching-record [:target :domain]) "www.google.com")))
  (let [matching-record (redirector/get-domain
                          (redirector/request-to-url-record {:scheme "http" :uri "my.host.com/bing"}))]
    (is (= (get-in matching-record [:target :domain]) "www.bing.com"))))

; not easy to test the REDIS / mongo ... what to do?


