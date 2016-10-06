(ns mambobox-core.core.music-test
  (:require [clojure.test :refer [deftest is]]
            [mambobox-core.core.music :refer :all]))


(deftest normalize-entity-name-string-test
  (is (= "el-gran-combo-de-puerto-rico"
         (normalize-entity-name-string "ElGranComboDePuertoRico")
         (normalize-entity-name-string "El GranCombo DePuertoRico")
         (normalize-entity-name-string "El-GranCombo_DePuertoRico"))))
