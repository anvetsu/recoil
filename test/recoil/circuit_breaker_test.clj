(ns recoil.circuit-breaker-test
  (:require [recoil.circuit-breaker :as cb])
  (:use clojure.test)
  (:import [java.util.concurrent TimeoutException]))

(deftest test-make
  (is (fn? (cb/executor {} :abc))))

(defn- make-db-connector
  [window-size]
  (let [c (atom 0)]
    (fn []
      (if (<= @c (inc window-size))
        (do (reset! c (inc @c))
            (throw (TimeoutException.)))
        {:ok :connected}))))

(deftest test-open-close
  (let [wsz 3
        wsecs 3
        exec (cb/executor {:handle [TimeoutException]
                           :wait-secs wsecs
                           :window-size wsz})
        action (make-db-connector wsz)]
    (doseq [_ (range (inc wsz))]
      (let [r (exec action)]
        (is (= (:error r) :handled-exception))
        (is (= (:source r) :circuit-breaker))))
    (let [r (exec action)]
      (is (= (:error r) :circuit-breaker-open))
      (is (= (:source r) :circuit-breaker)))
    (let [r (exec action)]
      (is (= (:error r) :circuit-breaker-open))
      (is (= (:source r) :circuit-breaker)))
    (Thread/sleep (* 1000 wsecs))
    (is (= (:ok (exec action)) :connected))))

(defn- make-logger
  [counter err-counts]
  (fn [log]
    (is (= :a-circuit-breaker (:name log)))
    (let [c @counter]
      (cond
        (<= 0 c err-counts)
        (is (= :closed (:state (:state log))))
        (= c (inc err-counts))
        (is (= :open (:state (:state log))))
        (= c (+ 2 err-counts))
        (is (= :half-open (:state (:state log))))
        :else
        (is (= :closed (:state (:state log))))))
    (reset! counter (inc @counter))))

(deftest test-state-changes
  (let [wsz 2
        wsecs 3
        log-counter (atom 0)
        exec (cb/executor {:handle [TimeoutException]
                           :wait-secs wsecs
                           :window-size wsz
                           :logger (make-logger log-counter wsz)}
                          :a-circuit-breaker)
        action (make-db-connector wsz)]
    (doseq [_ (range (inc wsz))]
      (let [r (exec action)]
        (is (= (:error r) :handled-exception))
        (is (= (:source r) :circuit-breaker))))
    (exec action)
    (Thread/sleep (* 1000 wsecs))
    (exec action)
    (exec action)
    (is (= @log-counter (+ wsz 5)))))
