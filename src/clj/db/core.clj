(ns db.core
  (:require [clojure.math :as m]))

(defn binary-search
  ([n xs] (binary-search n 0 xs))
  ([n offset xs]
   (let [c (count xs)
         f (first xs)
         l (last xs)]
     (cond
       (= 0 c) nil
       (= 1 c) (if (= n f) offset nil)
       (= n f) offset
       (= n l) (+ offset (- c 1))
       (< n f) nil
       (> n l) nil
       :else
       (let [half-idx (m/floor-div c 2)
             [x y] (split-at half-idx xs)]
         (if-let [xi (binary-search n offset x)]
           xi
           (if-let [yi (binary-search n (+ half-idx offset) y)]
             yi
             nil)))))))

(binary-search 3 [1 2 3 4])

:dbg


