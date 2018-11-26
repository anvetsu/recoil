(ns recoil.fallback-test
  (:require [recoil.fallback :as f])
  (:use clojure.test))

(defn- connect [x]
  (if (not x)
    (throw (Exception.))
    {:ok :connected}))

(deftest test-basic
  (let [r (f/execute (fn [] (connect false))
                     (fn [last-r]
                       (is (= :handled-exception (:error last-r)))
                       (connect true)))]
    (is (= r {:ok :connected :fallback true}))))
