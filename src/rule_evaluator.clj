(ns rule-evaluator
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def ^:private missing (Object.))
(def ^:private operators #{:and :or :not :exists :eq :ne :gt :gte :lt :lte :in})

(defn- path? [x]
  (or (keyword? x) (and (vector? x) (seq x) (every? keyword? x))))

(defn validate-rule
  "Validate every branch before execution. Rules cannot invoke host-language functions."
  [rule]
  (let [nodes (atom 0)]
    (letfn [(walk [node depth]
              (when (or (> depth 32) (> (swap! nodes inc) 1000))
                (throw (ex-info "Rule exceeds depth or node limit" {})))
              (when-not (and (vector? node) (operators (first node)))
                (throw (ex-info "Unknown operator or malformed rule" {:node node})))
              (let [[op & args] node]
                (cond
                  (#{:and :or} op)
                  (do (when (empty? args) (throw (ex-info "Boolean operator needs children" {})))
                      (doseq [child args] (walk child (inc depth))))
                  (= :not op)
                  (do (when-not (= 1 (count args)) (throw (ex-info "not needs one child" {})))
                      (walk (first args) (inc depth)))
                  :else
                  (do
                    (when-not (= (if (= op :exists) 1 2) (count args))
                      (throw (ex-info "Wrong predicate arity" {:operator op})))
                    (when-not (path? (first args)) (throw (ex-info "Invalid keyword path" {})))
                    (when (and (= op :in) (not (or (set? (second args)) (vector? (second args)))))
                      (throw (ex-info "in needs a set or vector" {})))
                    (when (and (#{:gt :gte :lt :lte} op) (not (number? (second args))))
                      (throw (ex-info "Ordered comparison needs a number" {})))))))]
      (walk rule 0)
      rule)))

(defn- lookup [event path]
  (reduce (fn [value key]
            (if (and (map? value) (contains? value key)) (get value key) missing))
          event (if (keyword? path) [path] path)))

(defn- execute [event [op & args]]
  (case op
    :and (every? #(execute event %) args)
    :or (boolean (some #(execute event %) args))
    :not (not (execute event (first args)))
    (let [value (lookup event (first args)) expected (second args)]
      (if (= op :exists)
        (not (identical? value missing))
        (if (identical? value missing)
          false
          (case op
            :eq (= value expected)
            :ne (not= value expected)
            :in (boolean (some #(= value %) expected))
            :gt (and (number? value) (> value expected))
            :gte (and (number? value) (>= value expected))
            :lt (and (number? value) (< value expected))
            :lte (and (number? value) (<= value expected))))))))

(defn compile-rule [rule]
  (validate-rule rule)
  (fn [event]
    (when-not (map? event) (throw (ex-info "Event must be a map" {})))
    (boolean (execute event rule))))

(defn evaluate [rule event] ((compile-rule rule) event))

(defn evaluate-events [rules events]
  (when-not (and (map? rules) (every? keyword? (keys rules)))
    (throw (ex-info "Rules must be a map with keyword names" {})))
  (when-not (vector? events) (throw (ex-info "Events must be a vector" {})))
  (let [compiled (mapv (fn [[id rule]] [id (compile-rule rule)]) (sort-by (comp str key) rules))]
    (mapv (fn [index event]
            (when-not (map? event) (throw (ex-info "Event must be a map" {:index index})))
            {:event-index index :matched-rules (mapv first (filter (fn [[_ test]] (test event)) compiled))})
          (range) events)))

(defn- read-data [filename]
  (with-open [reader (java.io.PushbackReader. (io/reader filename))]
    (let [options {:readers {} :default (fn [tag _] (throw (ex-info "Unsupported EDN tag" {:tag tag})))}
          value (edn/read (assoc options :eof missing) reader)]
      (when (identical? value missing) (throw (ex-info "Empty input" {})))
      (when-not (identical? missing (edn/read (assoc options :eof missing) reader))
        (throw (ex-info "Expected exactly one EDN value" {})))
      value)))

(defn -main [& args]
  (if (not= 2 (count args))
    (do (binding [*out* *err*] (println "Usage: clojure -M -m rule-evaluator rules.edn events.edn")) (System/exit 2))
    (try (prn (evaluate-events (read-data (first args)) (read-data (second args))))
         (catch Exception error
           (binding [*out* *err*] (println "Error:" (.getMessage error)))
           (System/exit 2)))))
