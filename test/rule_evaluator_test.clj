(ns rule-evaluator-test
  (:require [clojure.test :refer :all]
            [clojure.edn :as edn]
            [rule-evaluator :as rules]))

(deftest nested-and-numeric
  (is (rules/evaluate [:and [:gte :amount 100] [:eq [:customer :tier] "gold"]]
                      {:amount 150 :customer {:tier "gold"}}))
  (is (false? (rules/evaluate [:gt :amount 10] {:amount "12"})))
  (is (rules/evaluate [:in :region #{"AB" "BC"}] {:region "AB"}))
  (is (rules/evaluate [:not [:eq :status "closed"]] {:status "open"})))

(deftest missing-and-null
  (is (rules/evaluate [:exists :value] {:value nil}))
  (is (false? (rules/evaluate [:exists :value] {})))
  (is (rules/evaluate [:eq :value nil] {:value nil}))
  (is (false? (rules/evaluate [:eq :value nil] {})))
  (is (false? (rules/evaluate [:ne :value 3] {})))
  (is (false? (rules/evaluate [:exists [:a :b]] {:a 7}))))

(deftest rejects-invalid-trees
  (doseq [rule [[:shell "rm"] [:and] [:not] [:eq :a] [:eq "a" 1]
                [:in :a 7] [:gt :a "x"] [:or [:eq :x 1] [:eval "x"]]]]
    (is (thrown? clojure.lang.ExceptionInfo (rules/compile-rule rule))))
  (is (thrown? clojure.lang.ExceptionInfo
               (rules/compile-rule (nth (iterate #(vector :not %) [:eq :x 1]) 40))))
  (is (thrown? clojure.lang.ExceptionInfo (rules/evaluate [:eq :x 1] []))))

(deftest fixture-and-reuse
  (let [spec (edn/read-string (slurp "fixtures/rules.edn"))
        events (edn/read-string (slurp "fixtures/events.edn"))
        actual (rules/evaluate-events spec events)]
    (is (= [:high-value :needs-review] (:matched-rules (first actual))))
    (is (= [] (:matched-rules (second actual))))
    (is (= [:needs-review] (:matched-rules (nth actual 2)))))
  (let [test (rules/compile-rule [:gte :value 5])]
    (is (= [false true true] (mapv test [{:value 4} {:value 5} {:value 6}])))))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'rule-evaluator-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
