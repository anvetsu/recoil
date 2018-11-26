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

(deftest test-open
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
