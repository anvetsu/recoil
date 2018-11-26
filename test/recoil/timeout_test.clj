(ns recoil.timeout-test
  (:require [recoil.timeout :as t])
  (:use clojure.test))

(defn- make-db-connector
  [wait-ms ex1? ex2? cancel-token]
  (fn []
    (when ex1?
      (throw (Exception. "Failed before")))
    (Thread/sleep wait-ms)
    (when ex2?
      (throw (Exception. "Failed after")))
    (if @cancel-token
      {:error :canceled}
      {:ok :connected})))

(deftest test-basic
  (let [db-conn (make-db-connector 3000 false false (atom nil))
        eventual-connect (fn [r]
                           (is (= r {:ok :connected})))
        result (t/execute db-conn 1500 eventual-connect)]
    (is (= :timeout (:error result)))
    (is (= :timeout (:source result)))
    (Thread/sleep 2000))); wait for eventual-connect to execute.

(deftest test-exceptions
  (let [db-conn (make-db-connector 3000 true false (atom nil))
        result (t/execute db-conn 1500)]
    (is (= :unhandled-exception (:error result)))
    (is (= :timeout (:source result))))
  (let [db-conn (make-db-connector 3000 false true (atom nil))
        eventual-connect (fn [r]
                           (is (= :unhandled-exception (:error r)))
                           (is (= :timeout (:source r))))
        result (t/execute db-conn 1500 eventual-connect)]
    (is (= :timeout (:error result)))
    (is (= :timeout (:source result)))))

    
