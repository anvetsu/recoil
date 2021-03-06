;; Copyright (c) 2018 Anvetsu Technologies. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; MIT License (https://mit-license.org/)
;; which can be found in the file `LICENSE` at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

;; Implements the Circuit Breaker pattern to handle faults that might take a variable amount
;; of time to recover from, when connecting to a remote service or resource.
;; This can improve the stability and resiliency of an application.

(ns ^{:doc "The Circuit Breaker pattern"
      :author "Vijay Mathew <vijay@anvetsu.com>"}
    recoil.circuit-breaker
  (:require [recoil.util :as ru])
  (:import [java.util.concurrent TimeUnit]))

(declare invoke invoke-open)

(def ^:private cb-open-error {:error :circuit-breaker-open :source :circuit-breaker})

(defn executor
  "Returns a function that execute a user-defined request through a circuit-breaker.
  The circuit-breaker is configured by the `policies` parameter.
  `policies` is a map with following keys:
    :handle              - list of exceptions that can cause the circuit-breaker to open
    :window-size         - the number of exceptions allowed before moving to the open state
    :wait-secs           - number of seconds for the circuit-breaker to remain in the open state
    :logger              - a single-arity function that will receive the current state change of the circuit-breaker
  The `cb-name` parameter will be useful for identifying the circuit breaker, especially in the logs."
  ([policies cb-name]
   (let [state (atom {:state :closed
                      :exceptions 0
                      :closed-ts 0})
         info (assoc policies :name cb-name)]
     (fn [request-fn]
       (case (:state @state)
         :closed (invoke request-fn info state false)
         :open (invoke-open request-fn info state)
         :half-open cb-open-error)))) ; a limited number of threads are already in the half-open mode.
  ([policies]
   (executor policies (.toString (java.util.UUID/randomUUID)))))

(defn- log [cb-info current-state]
  (when-let [logger (:logger cb-info)]
    (logger {:name (:name cb-info)
             :state current-state})))

(defn- invoke [request-fn cb-info state close?]
  ;; Another thread might have already decided to open the circuit breaker by now,
  ;; we choose to ignore it and proceed instead of serializing and slowing down the request.
  ;; Even if this request succeeds, the implementation makes sure that the circuit breaker
  ;; stays open. This design choice makes sure requests complete fast and the network
  ;; resource is given enough time to stabilize.
  (let [current-state @state
        result (ru/try-call request-fn (:handle cb-info) :circuit-breaker)]
    (if (and (= (:error result) :handled-exception)
             (= (:source result) :circuit-breaker))
      (if (> (:exceptions current-state) (:window-size cb-info))
        (do (swap! state assoc :state :open :closed-ts (System/nanoTime))
            (log cb-info @state)
            cb-open-error)
        (do (swap! state assoc :exceptions (inc (:exceptions current-state)))
            (log cb-info @state)
            result))
      (do (if close?
            (do (swap! state assoc :state :closed :exceptions 0 :closed-ts 0)
                (log cb-info @state))
            (do (swap! state assoc :exceptions 0)
                (log cb-info @state)))
          result))))

(defn- invoke-open [request-fn cb-info state]
  (let [cts (System/nanoTime)]
    (if (>= (.toSeconds TimeUnit/NANOSECONDS (- cts (:closed-ts @state))) (:wait-secs cb-info))
      (do (swap! state assoc :state :half-open)
          (log cb-info @state)
          (invoke request-fn cb-info state true))
      cb-open-error)))
