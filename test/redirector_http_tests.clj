(ns redirector-http-tests
  (:require [clojure.test :refer :all]
            [ring.adapter.jetty :as jetty]
            [org.httpkit.client :as http]
            [domain-redirector.core :as redirector]))

(def test-port 1234)
(def test-base-url (str "http://localhost:" test-port))

(use-fixtures
  :once
  (fn [f]
    (let [server (redirector/test-server test-port)]
      (try
        (f)
        (finally
          (.stop server))))))

(deftest test-get
   (let [options {:follow-redirects false}
         response (http/get test-base-url options)]
     (is (= "http://www.google.com/" (get-in @response [:headers :location])))
     (is (= 301 (:status @response)))))

(deftest test-path-get
   (let [options {:follow-redirects false}
         response (http/get (str test-base-url "/google") options)]
     (is (= "http://www.google.com/" (get-in @response [:headers :location])))
     (is (= 301 (:status @response))))
   (let [options {:follow-redirects false}
         response (http/get (str test-base-url "/bing") options)]
     (is (= "http://www.bing.com/" (get-in @response [:headers :location])))
     (is (= 301 (:status @response)))))

(deftest test-query-get
  (let [options {:follow-redirects false}
        response (http/get (str test-base-url "/google?q=1") options)]
    (is (= "http://www.google.com/?q=1" (get-in @response [:headers :location])))
    (is (= 301 (:status @response)))))

(deftest test-headers-get
  (let [options {:follow-redirects false}
        response (http/get (str test-base-url "/secure") options)]
    (is (= "https://www.google.com/" (get-in @response [:headers :location])))
    (is (= 301 (:status @response)))))
