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

(defn- validate-unhandled-exception [result exec-class]
  (is (= :unhandled-exception (:error result)))
  (is (instance? exec-class (:exception result))))

(deftest test-execptions
  (let [exec (r/executor {:handle [TimeoutException SQLException]
                          :retry 3})]
    (is (= (exec (make-db-connector)) {:ok :connected})))
  (let [exec (r/executor {:handle [Exception]
                          :retry 3})]
    (is (= (exec (make-db-connector)) {:ok :connected})))
  (let [exec (r/executor {:handle [TimeoutException]
                          :retry 3})]
    (validate-unhandled-exception
     (exec (make-db-connector))
     SQLException)))

(def no-retries :no-retries-left)

(defn- validate-status
  ([status contains-error?]
   (is (= no-retries (:status status)))
   (when contains-error?
     (is (= :handled-exception (:error (:result status))))
     (is (:exception (:result status)))))
  ([status]
   (validate-status status false)))

(deftest test-no-retries
  (let [exec (r/executor {:handle [TimeoutException SQLException]
                          :retry 1})]
    (validate-status (exec (make-db-connector)) true))
  (let [exec (r/executor {:handle [TimeoutException SQLException]
                          :retry 0})]
    (validate-status (exec (make-db-connector)) true)))

(defn- make-timed-connector [secs-to-succeed]
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
    (validate-status (exec (make-timed-connector 5))))
  (let [exec (r/executor {:retry 1
                          :wait-secs 3})]
    (validate-unhandled-exception
     (exec (make-timed-connector 1))
     TimeoutException)))

(deftest test-wait-fn
  (let [exec (r/executor {:handle [TimeoutException]
                          :retry 1
                          :wait-secs 1 ; should be ignored
                          :wait-fn (fn [_ _ _] 3)})]
    (is (= (exec (make-timed-connector 3)) {:ok :connected}))
    (is (= (exec (make-timed-connector 1)) {:ok :connected}))
    (validate-status (exec (make-timed-connector 5)) true))

  (let [exec (r/executor {:handle [TimeoutException]
                          :retry 3
                          :wait-fn (fn [_ wait-secs _]
                                     (if wait-secs
                                       (* wait-secs 2) ; exponential back-off
                                       1))})]
    (is (= (exec (make-timed-connector 3)) {:ok :connected}))
    (is (= (exec (make-timed-connector 1)) {:ok :connected}))
    (is (= (exec (make-timed-connector 5)) {:ok :connected}))
    (validate-status (exec (make-timed-connector 8)))))

(deftest test-with-future
  (let [exec (r/executor {:handle [TimeoutException]
                          :retry 1
                          :wait-secs 3})
        f1 (future (exec (make-timed-connector 3)))
        f2 (future (exec (make-timed-connector 1)))
        f3 (future (exec (make-timed-connector 5)))]
    (is (= @f1 {:ok :connected}))
    (is (= @f2 {:ok :connected}))
    (validate-status @f3))
  (let [exec (r/executor {:retry 1
                          :wait-secs 3})
        f (future (exec (make-timed-connector 1)))]
    (validate-unhandled-exception
      @f
      TimeoutException)))

(defn- make-values-tester [tries]
  (let [state (atom 0)]
    (fn []
      (if (< @state tries)
        (let [resp {:error @state}]
          (reset! state (inc @state))
          resp)
        {:ok :done}))))

(deftest test-values
  (let [tries 3]
    (defn callback [last-result wait-secs n-tries]
      (when-let [n (:error last-result)]
        (is (= tries (+ n n-tries))))
      1)
    (let [exec (r/executor {:wait-fn callback
                            :retry tries})]
      (exec (make-values-tester tries)))))
