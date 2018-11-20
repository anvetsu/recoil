(ns recoil.retry-test
  (:require [recoil.retry :as r])
  (:use clojure.test)
  (:import [java.util.concurrent TimeoutException]
           [java.sql SQLException]))

(deftest test-make
  (is (fn? (r/executor {}))))

(defn- make-db-connector
  []
  ;; Simulates a database connection with exceptions.
  (let [state (atom 0)]
    (fn []
      (cond
        (= @state 0) (do
                       (reset! state 1)
                       (throw (TimeoutException.)))
        (= @state 1) (do
                       (reset! state 2)
                       (throw (SQLException.)))
        :else {:ok :connected}))))

(deftest test-execptions
  (let [exec (r/executor {:handle [TimeoutException SQLException]
                          :retry 3})]
    (is (= (exec (make-db-connector)) {:ok :connected})))
  (let [exec (r/executor {:handle [Exception]
                          :retry 3})]
    (is (= (exec (make-db-connector)) {:ok :connected})))
  (let [exec (r/executor {:handle [TimeoutException]
                          :retry 3})]
    (try
      (do (exec (make-db-connector))
          (is false))
      (catch SQLException ex
        (is true)))))

(deftest test-no-retries
  (let [no-retries {:error :no-more-retries}]
    (let [exec (r/executor {:handle [TimeoutException SQLException]
                            :retry 1})]
      (is (= (exec (make-db-connector)) no-retries)))
    (let [exec (r/executor {:handle [TimeoutException SQLException]
                            :retry 0})]
      (is (= (exec (make-db-connector)) no-retries)))))
