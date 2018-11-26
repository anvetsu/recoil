;; Copyright (c) 2018 Anvetsu Technologies. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; MIT License (https://mit-license.org/)
;; which can be found in the file `LICENSE` at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

;; Implements a Fallback policy to ensure a substitute value is returned
;; in case of handled exception.

(ns ^{:doc "A Fallback policy implementation"
      :author "Vijay Mathew <vijay@anvetsu.com>"}
    recoil.fallback
  (:require [recoil.util :as u]))

(declare handle-fallback)

(defn execute
  ([action on-fallback handle]
   (let [result (u/try-call action handle nil)]
     (cond
       (:ok result)
       result

       (= (:error result) :handled-exception)
       (if on-fallback
         (handle-fallback on-fallback result)
         result)

       :else result)))
  ([action on-fallback]
   (execute action on-fallback [Exception])))

(defn- handle-fallback [on-fallback result]
  (let [result (u/try-call #(on-fallback result) [] :fallback)]
    (if (:ok result)
      (assoc result :fallback true)
      result)))
