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
  (:require [recoil.util :as u]
            [clojure.core.async :as a]))

(defn execute
  ([action timeout-ms on-eventual-complete]
   (let [c (a/chan)]
     (a/go (a/>! c (u/try-call action nil :timeout)))
     (let [[val ch] (a/alts!! [c (a/timeout timeout-ms)])]
       (if val
         val
         (do
           (when on-eventual-complete
             (a/go (on-eventual-complete (a/<! c))))
           {:error :timeout
            :source :timeout})))))
  ([action timeout-ms]
   (execute action timeout-ms nil)))
