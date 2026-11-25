# Clojure Rule Evaluator

Evaluates declarative EDN rules against event maps. Rules are validated as data and interpreted through a fixed operator set; they never pass through `eval`, code loading, or shell execution.

## Run

Requires Java and the [Clojure CLI](https://clojure.org/guides/install_clojure). `deps.edn` pins Clojure 1.11.1.

```sh
clojure -M -m rule-evaluator fixtures/rules.edn fixtures/events.edn
clojure -M -m rule-evaluator-test
```

On Debian/Ubuntu's `clojure` package, use `clojure -cp src:test -m ...` instead of `clojure -M -m ...`. CI uses that package wrapper.

## Rule format

```clojure
[:and [:gte :amount 10000]
      [:eq [:customer :tier] "gold"]]
```

Boolean operators are `:and`, `:or`, and `:not`. Predicates are `:exists`, `:eq`, `:ne`, `:gt`, `:gte`, `:lt`, `:lte`, and `:in`. Paths are keywords or nonempty vectors of keywords. Ordered comparisons require numbers; `:in` accepts a set or vector.

A present `nil` differs from a missing key. `:exists` is true for present `nil`; all other predicates return false for a missing path. Negation can therefore match a missing key. Boolean evaluation short-circuits, but validation checks **all** branches first. Each rule is limited to 1,000 nodes and 32 levels of nesting. These rule limits do not replace file-size limits at an external service boundary.

The CLI accepts a keyword-to-rule map and a vector of event maps, each stored as one EDN value. Results list zero-based event indices and matching rule names in sorted order. Errors exit 2. The library exposes `compile-rule` to validate once and reuse a predicate across events.

## Verification

The test suite covers nested paths, numeric type guards, missing versus null semantics, compiled-predicate reuse, invalid operators and arities, depth limits, invalid event shapes, and fixture results. The included events are demonstration data.

MIT licensed.
