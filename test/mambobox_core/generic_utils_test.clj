(ns mambobox-core.generic-utils-test
  (:require [clojure.test :refer [deftest is]]
            [mambobox-core.generic-utils :refer :all]))

(deftest normalize-entity-name-string-test
  (is (= "el-gran-combo-de-puerto-rico"
         (normalize-entity-name-string "ElGranComboDePuertoRico")
         (normalize-entity-name-string "El GranCombo DePuertoRico")
         (normalize-entity-name-string "El-GranCombo_DePuertoRico"))))
