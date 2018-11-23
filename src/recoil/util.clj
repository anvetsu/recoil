;; Copyright (c) 2018 Anvetsu Technologies. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; MIT License (https://mit-license.org/)
;; which can be found in the file `LICENSE` at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Shared functions and macros"
      :author "Vijay Mathew <vijay@anvetsu.com>"}
    recoil.util)

(defn retry-for? [ex handle]
  (loop [h handle]
    (when (seq h)
      (if (instance? (first h) ex)
        true
        (recur (rest h))))))

(defn try-call [request-fn handle source]
  (try
    (request-fn)
    (catch Exception ex
      (if (retry-for? ex handle)
        {:error :handled-exception
         :source source
         :exception ex}
        {:error :unhandled-exception
         :source source
         :exception ex}))))
