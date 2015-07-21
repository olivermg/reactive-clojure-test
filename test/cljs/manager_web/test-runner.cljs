(ns manager_web.test-runner
  (:require
   [cljs.test :refer-macros [run-tests]]
   [manager_web.core-test]))

(enable-console-print!)

(defn runner []
  (if (cljs.test/successful?
       (run-tests
        'manager_web.core-test))
    0
    1))
