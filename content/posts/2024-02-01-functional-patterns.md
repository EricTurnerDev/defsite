---
title: "Functional Patterns in Clojure"
published: true
date: 2024-02-01
categories: [clojure, patterns]
summary: "A tour of the functional patterns that make Clojure code readable, composable, and easy to test."
---

## Immutability by default

Clojure's data structures are persistent and immutable. This eliminates an
entire class of bugs related to shared mutable state.

```clojure
(def original {:a 1 :b 2})
(def modified (assoc original :c 3))

original ;; => {:a 1, :b 2}      — unchanged
modified ;; => {:a 1, :b 2, :c 3}
```

Because mutation never happens in-place, you can pass maps freely between
functions without defensive copying.

## Threading macros

The `->` and `->>` macros make data pipelines readable by eliminating nesting.

```clojure
;; Without threading — reads inside-out
(reduce + (map #(* % %) (filter odd? (range 1 11))))

;; With ->> — reads top-to-bottom
(->> (range 1 11)
     (filter odd?)
     (map #(* % %))
     (reduce +))
;; => 165
```

Use `->` when threading into the *first* argument position (e.g., maps, strings)
and `->>` for the *last* argument position (e.g., sequences).

## Separating data from behaviour

A key Clojure idiom is to keep data in plain maps and behaviour in functions,
rather than bundling them together in objects.

```clojure
;; Data
(def user {:name "Alice" :role :admin :active? true})

;; Behaviour — pure functions on plain maps
(defn admin? [user]
  (= :admin (:role user)))

(defn deactivate [user]
  (assoc user :active? false))
```

This makes both trivial to test and trivial to serialise.

## Transducers

For efficiency when chaining transformations over large or unknown collections:

```clojure
(def xf
  (comp
    (filter odd?)
    (map #(* % %))
    (take 5)))

;; Works with any collection, channel, or custom reducer
(transduce xf conj (range 1 1000))
;; => [1 9 25 49 81]
```

Transducers describe the *what* of a transformation without committing to *how*
the data is consumed. The same `xf` works on vectors, lazy seqs, and
`core.async` channels.

## Pure functions and easy testing

When functions are pure — no side effects, same output for same input — testing
collapses to property checking:

```clojure
(defn celsius->fahrenheit [c]
  (+ (* c 9/5) 32))

(assert (= 32  (celsius->fahrenheit 0)))
(assert (= 212 (celsius->fahrenheit 100)))
```

No mocks, no fixtures, no setup. This is the payoff for keeping I/O at the
edges of the system.
