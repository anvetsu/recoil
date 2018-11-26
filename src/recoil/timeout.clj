;; Copyright (c) 2018 Anvetsu Technologies. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; MIT License (https://mit-license.org/)
;; which can be found in the file `LICENSE` at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

;; Implements a Timeout policy to ensure the caller never has to wait
;; beyond the configured timeout. Useful for enforcing a timeout on
;; actions having no in-built timeout.

(ns ^{:doc "A Timeout policy implementation"
      :author "Vijay Mathew <vijay@anvetsu.com>"}
    recoil.timeout
  (:require [clojure.core.async :as async :refer :all]))

(defn execute
  ([action timeout-ms on-eventual-complete]
   (let [c (chan)
         f (try
             (action)
             (catch Exception ex
               {:error :handled-exception
                :exception ex}))]
     (go (>! c (f)))
     (let [[val ch] (alts!! [c (timeout timeout-ms)])]
       (if val
         val
         (do
           (when on-eventual-complete
             (go (fn []
                   (on-eventual-complete (<! c)))))
           {:error :timeout
            :source :recoil.timeout})))))
  ([action timeout-ms]
   (execute action timeout-ms nil)))
