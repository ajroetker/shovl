(ns shovl.lcg-test
  (:require [clojure.test :refer :all]
            [shovl.lcg :as lcg]))

(deftest rand-seq
  (testing "the correct random sequence is generated for seed 17"
    (is (= [0,24107,16552,12125,9427,13152,21440,3383,6873,16117]
           (take 10 (lcg/rand-seq 17))))))
