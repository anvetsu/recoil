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
      (if (< @c window-size)
        (do (reset! c (inc @c))
            (throw (TimeoutException.)))
        {:ok :connected}))))

(deftest test-open
  (let [wsz 3
        exec (cb/executor {:handle [TimeoutException]
                           :wait-secs 2
                           :window-size wsz})
        action (make-db-connector wsz)]
    (doseq [_ (range wsz)]
      (let [r (exec action)]
        (is (= (:error r) :handled-exception))
        (is (= (:source r) :circuit-breaker))))))
