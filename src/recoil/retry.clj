;; Copyright (c) 2018 Anvetsu Technologies. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; MIT License (https://mit-license.org/)
;; which can be found in the file `LICENSE` at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

;; Implements the Retry pattern which enable an application to handle transient failures when
;; it tries to connect to a service or network resource, by transparently retrying a failed operation.
;; This can improve the stability of the application.

(ns ^{:doc "The Retry pattern"
      :author "Vijay Mathew"}
    recoil.retry)

(declare retry-for?)

(defn executor
  "Returns a function that can execute retries for a user-defined request
  based on `policies`."
  [policies]
  (let [handle (:handle policies)
        retry (or (:retry policies) 1)
        no-retries {:error :no-more-retries}]
    (fn [request-fn]
      (loop [r retry]
        (let [result (:result
                      (try
                        {:result (request-fn)}
                        (catch Exception ex
                          (if (retry-for? ex handle)
                            {:result {:error :handled-exception}}
                            (throw ex)))))]
          (if (:ok result)
            result
            (if (zero? r)
              no-retries
              (recur (dec r)))))))))              

(defn- retry-for? [ex handle]
  (loop [h handle]
    (if (seq h)
      (if (instance? ex (first h))
        true
        (recur (rest h)))
      false)))
