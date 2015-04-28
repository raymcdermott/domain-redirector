(ns redirector-http-tests
  (:require [clojure.test :refer :all]
            [ring.adapter.jetty :as jetty]
            [org.httpkit.client :as http]
            [domain-redirector.core :as redirector]))

(def test-port 1234)
(def test-base-url (str "http://localhost:" test-port))
(def test-options {:follow-redirects false})

(use-fixtures
  :once
  (fn [f]
    (let [server (redirector/test-server test-port)]
      (try
        (f)
        (finally
          (.stop server))))))

(deftest test-get
  (let [response (http/get test-base-url test-options)]
    (is (= "http://www.google.com/" (get-in @response [:headers :location])))
    (is (= 301 (:status @response)))))

(deftest test-path-get
  (let [response (http/get (str test-base-url "/google") test-options)]
    (is (= "http://www.google.com/" (get-in @response [:headers :location])))
    (is (= 301 (:status @response))))
  (let [response (http/get (str test-base-url "/bing") test-options)]
    (is (= "http://www.bing.com/" (get-in @response [:headers :location])))
    (is (= 301 (:status @response)))))

(deftest test-query-get
  (let [response (http/get (str test-base-url "/google?q=1") test-options)]
    (is (= "http://www.google.com/?q=1" (get-in @response [:headers :location])))
    (is (= 301 (:status @response)))))

(deftest test-headers-get
  (let [response (http/get (str test-base-url "/secure") test-options)]
    (is (= "https://www.google.com/" (get-in @response [:headers :location])))
    (is (= 301 (:status @response)))))
