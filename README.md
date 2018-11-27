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
(let [exec (recoil.retry/executor {:handle [TimeoutException]
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

### Timeouts

### Fallbacks
