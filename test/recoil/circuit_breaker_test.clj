(ns recoil.circuit-breaker-test
  (:require [recoil.circuit-breaker :as cb])
  (:use clojure.test))

(deftest test-make
  (is (fn? (cb/executor {} :abc))))
