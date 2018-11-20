(ns recoil.retry-test
  (:require [recoil.retry :as r])
  (:use clojure.test)
  (:import [java.util.concurrent TimeoutException ExecutionException]
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

(def no-retries {:error :no-more-retries})

(deftest test-no-retries
  (let [exec (r/executor {:handle [TimeoutException SQLException]
                          :retry 1})]
    (is (= (exec (make-db-connector)) no-retries)))
  (let [exec (r/executor {:handle [TimeoutException SQLException]
                          :retry 0})]
    (is (= (exec (make-db-connector)) no-retries))))

(defn- make-timed-connector
  [secs-to-succeed]
  ;; Simulates a network connection with timeouts.
  (let [state (atom 0)
        ems (* 1000 secs-to-succeed)]
    (fn []
      (if (= @state 0)
        (do
          (reset! state (System/currentTimeMillis))
          (throw (TimeoutException.)))
        (if (>= (- (System/currentTimeMillis) @state) ems)
          {:ok :connected}
          (throw (TimeoutException.)))))))

(deftest test-wait-secs
  (let [exec (r/executor {:handle [TimeoutException]
                          :retry 1
                          :wait-secs 3})]
    (is (= (exec (make-timed-connector 3)) {:ok :connected}))
    (is (= (exec (make-timed-connector 1)) {:ok :connected}))
    (is (= (exec (make-timed-connector 5)) no-retries)))
  (let [exec (r/executor {:retry 1
                          :wait-secs 3})]
    (try
      (do (exec (make-timed-connector 1))
          (is false))
      (catch TimeoutException ex
        (is true)))))

(deftest test-wait-fn
  (let [exec (r/executor {:handle [TimeoutException]
                          :retry 1
                          :wait-fn (fn [_ _ _] 3)})]
    (is (= (exec (make-timed-connector 3)) {:ok :connected}))
    (is (= (exec (make-timed-connector 1)) {:ok :connected}))
    (is (= (exec (make-timed-connector 5)) no-retries)))

  (let [exec (r/executor {:handle [TimeoutException]
                          :retry 3
                          :wait-fn (fn [_ wait-secs _]
                                     (if wait-secs
                                       (* wait-secs 2) ; exponential back-off
                                       1))})]
    (is (= (exec (make-timed-connector 3)) {:ok :connected}))
    (is (= (exec (make-timed-connector 1)) {:ok :connected}))
    (is (= (exec (make-timed-connector 5)) {:ok :connected}))
    (is (= (exec (make-timed-connector 8)) no-retries))))

(deftest test-with-future
  (let [exec (r/executor {:handle [TimeoutException]
                          :retry 1
                          :wait-secs 3})
        f1 (future (exec (make-timed-connector 3)))
        f2 (future (exec (make-timed-connector 1)))
        f3 (future (exec (make-timed-connector 5)))]
    (is (= @f1 {:ok :connected}))
    (is (= @f2 {:ok :connected}))
    (is (= @f3  no-retries)))
  (let [exec (r/executor {:retry 1
                          :wait-secs 3})
        f (future (exec (make-timed-connector 1)))]
    (try
      (do @f
          (is false))
      (catch ExecutionException ex
        (is true)))))
