# recoil

Recoil is a resilience and fault-handling library for Clojure.

### Retries

The Retry Pattern enable an application to handle transient failures by transparently retrying a failed operation.
Examples of such transient faults include the momentary loss of network connectivity to components and services,
the temporary unavailability of a service, or timeouts that occur when a service is busy. If the
operation is retried, there is a chance for it to succeed because most of these faults are self-correcting.

The Recoil implementation of the Retry Pattern allows the application to execute an operation under the control of
certain retry policies. For example, consider a `connect-to-db` function that can fail with either a `TimeoutException` or
a `SQLException`. The `TimeoutException` is usually caused by a temporary network error and may go away if the operation
is retried after a few seconds. So `connect-to-db` can be called under a retry policy as:

```clojure
(use '[recoil.retry :as r])

(let [exec (r/executor {:handle [TimeoutException]
                        :retry 3
                        :wait-secs 5})]
  (exec (connect-to-db)))
```

`recoil.retry/executor` returns a function that can retry an operation under the specified policies. In this example,
the policy is to retry the operation every 5 seconds if it raises the `TimeoutException`. A maximum of 3 retries will be made,
in addition to the original call to the operation. This means a total of 4 tries will be made.

Note that the operation must return `{:ok some-value}` on success. Any other return value will also trigger a retry.
If all retries fail, the final result or exception will be returned in a map with the `:error` key set to `:handled-exception`
or `:unhandled-exception`. The map will contain additional information about the error. `:handled-exception` is returned if
the last exception raised by the operation is listed in the `:handle` policy setting. Because the executor will only
return values, it can be easily composed with other patterns in recoil.

In some cases, the ideal duration between the retries can be computed dynamically, probably based on the last response received from
the operation. To take care of these scenarios, you can configure the policy with a wait function, instead of a static wait seconds.
The following program shows how to do this:

```clojure
(r/executor {:handle [TimeoutException]
             :retry 3
             :wait-fn (fn [last-result current-wait-secs n-try]
	                (if (:expected-downtime-secs last-result)
			  (+ (:expected-downtime-secs last-result) 5)
			   3))})
```

Here the function passed to `:wait-fn` will be called to compute the duration between retries.
It is passed the last result from the operation, the current value of `:wait-secs` and the number of retries
remaining as arguments. In this particular case, it is assumed that the operation is able to report the number
of seconds of downtime for the remote service and this value is use to dynamically calculate a wait duration.

A simpler strategy of exponential back-off can be implemented as follows:

```clojure
(r/executor {:handle [TimeoutException]
             :retry 3
	     :wait-fn (fn [_ wait-secs _]
	                (if wait-secs
			  (* wait-secs 2) ; exponential back-off
			   1))})
```

### Circuit breaker

A network service may begin to fail because it's overloaded with requests. It may stabilize if new connections are limited.
A Circuit breaker is an abstraction which can keep track of such failures, control new requests from going out to the remote service
and give it an opportunity to heal.

Going back to the earlier example of connecting to a database, we can wrap the calls to the `connect-to-db` function in a circuit
breaker as shown below:

```clojure
(use '[recoil.circuit-breaker :as cb])

(let [exec (cb/executor {:handle [TimeoutException]
                         :wait-secs 5
                         :window-size 3})
      result (exec connect-to-db)]
  (cond
    (:ok result) :connected
    (= (:error result) :circuit-breaker-open) :retry-later
    :else :some-other-error))
```

The policy for the circuit breaker means to open the circuit breaker if it encounters 5 consecutive `TimeoutException`s.
Once the circuit breaker is open, no more operations are allowed through it until it is closed again. During that time,
the executor will return `{:error :circuit-breaker-open}`. Once open, the circuit breaker will remain in that state for
the duration of `:wait-secs`. After that, it changes to a half-open state, where a limited number of operations will be allowed.
If those succeed by returns an `{ok ...}` result, the circuit breaker will go to the normal closed state. Otherwise, it will
go back to the `open` state and repeat the cycle.

As a single circuit breaker will be shared between multiple operations on a single resource, it may become a performance bottleneck.
To avoid this, the recoil implementation has avoided the use of long-held global locks. This means, the circuit breaker only guarantees
that eventually `open`. In other words, a few calls may go through the circuit breaker even after the state machine has decided to
move to the `open` state.

A circuit breaker can be configured with a "logger" function. This is useful for capturing the state changes that a circuit breaker will
go through for later debugging purposes.

```clojure
(defn cb-logger [cb-info]
  (log/debug (str "circuit breaker " (:name cb-info) " changed to state: " (:state cb-info))))

(cb/executor {:handle [TimeoutException]
              :wait-secs 5
              :window-size 3
	      :logger cb-logger})
```

### Timeouts

A responsive application should avoid blocking threads by waiting forever on a resource.
The recoil Timeout pattern makes it simple to add timeouts to blocking operations.
For instance, this is how we will add a timeout of 5 seconds to the `connect-to-db` function call:

```clojure
(use '[recoil.timeout :as t])

(let [result (t/execute connect-to-db 5000)]
  (cond
   (:ok result) :connected
   (= :timeout (:error result)) :failed-with-timeout
   :else :other-error))
```
Note that the timeout is specified in milliseconds.

Sometimes it is possible to issue a cancellation on the low-level resource handle passed to
functions like `connect-to-db`. But may services like databases do not support a reliable
cancellation mechanism. In such cases, the operation may still continue to execute in a background thread
and eventually return a resource. If not disposed off properly resources returned by timed-out operations
may lead to resource leaks in the system. The recoil implementation of timeout allows you to get access to
such eventually acquired resources and deal with them appropriately. This is achieved by passing an single arity
"resource handler" function as a third argument to `t/execute`.

```clojure
(defn eventual-connect [result]
  (when (:ok result)
    (db/close (:connection result))))

(t/execute connect-to-db 5000 eventual-connect)
```

### Fallbacks
