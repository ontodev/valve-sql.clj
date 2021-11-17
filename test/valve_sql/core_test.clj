(ns valve-sql.core-test
  (:require [clojure.test :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as string]
            [valve-sql.core :refer [sqlify-condition]]))

(defn- remove-ws
  [string]
  (-> string
      (string/replace #"([\s\(])\s+" "$1")
      (string/replace #"\s+\)" ")")))

(deftest test-in
  (let [condition "in(lookup_t.lookup_c)"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(str "SELECT *, ? AS failed_condition "
                   "FROM target_t "
                   "WHERE NOT target_c IN (SELECT lookup_c FROM lookup_t)")
              condition])))))

(deftest test-not-in
  (let [condition "not(in(lookup_t.lookup_c))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(str "SELECT *, ? AS failed_condition "
                   "FROM target_t "
                   "WHERE (target_c IN (SELECT lookup_c FROM lookup_t))")
              condition])))))

(deftest test-in-2
  (let [condition "in(lookup_t1.lookup_c, lookup_t2.lookup_c)"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "SELECT *, ? AS failed_condition "
                       "FROM target_t "
                       "WHERE NOT target_c IN (SELECT lookup_c FROM lookup_t1) "
                       "   OR NOT target_c IN (SELECT lookup_c FROM lookup_t2)")
                  (remove-ws))
              condition])))))

(deftest test-not-in-2
  (let [condition "not(in(lookup_t1.lookup_c, lookup_t2.lookup_c))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "SELECT *, ? AS failed_condition "
                       "FROM target_t "
                       "WHERE (target_c IN (SELECT lookup_c FROM lookup_t1)) "
                       "  AND (target_c IN (SELECT lookup_c FROM lookup_t2))")
                  (remove-ws))
              condition])))))

(deftest test-any
  (let [condition "any(in(lookup_t_1.lookup_c), in(lookup_t_2.lookup_c))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "SELECT * FROM ("
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE NOT target_c IN (SELECT lookup_c FROM lookup_t_1) "
                       "  INTERSECT "
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE NOT target_c IN (SELECT lookup_c FROM lookup_t_2)"
                       ")")
                  (remove-ws))
              condition
              condition])))))

(deftest test-not-any
  (let [condition "not(any(in(lookup_t_1.lookup_c), in(lookup_t_2.lookup_c)))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "SELECT * FROM ("
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE (target_c IN (SELECT lookup_c FROM lookup_t_1)) "
                       "  UNION "
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE (target_c IN (SELECT lookup_c FROM lookup_t_2))"
                       ")")
                  (remove-ws))
              condition
              condition])))))

(deftest test-any-2
  (let [condition "any(in(lookup_t_1.lookup_c, lookup_t_2.lookup_c), in(lookup_t_3.lookup_c, lookup_t_4.lookup_c))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition}))
          [(-> (str "SELECT * FROM ("
                    "  SELECT *, ? AS failed_condition "
                    "  FROM target_t "
                    "  WHERE NOT target_c IN (SELECT lookup_c FROM lookup_t_1) "
                    "     OR NOT target_c IN (SELECT lookup_c FROM lookup_t_2) "
                    "  INTERSECT "
                    "  SELECT *, ? AS failed_condition "
                    "  FROM target_t "
                    "  WHERE NOT target_c IN (SELECT lookup_c FROM lookup_t_3) "
                    "     OR NOT target_c IN (SELECT lookup_c FROM lookup_t_4))")
               (remove-ws))
           condition
           condition]))))

(deftest test-not-any-2
  (let [condition "not(any(in(lookup_t_1.lookup_c, lookup_t_2.lookup_c), in(lookup_t_3.lookup_c, lookup_t_4.lookup_c)))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "SELECT * FROM ("
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE (target_c IN (SELECT lookup_c FROM lookup_t_1)) "
                       "    AND (target_c IN (SELECT lookup_c FROM lookup_t_2)) "
                       "  UNION "
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE (target_c IN (SELECT lookup_c FROM lookup_t_3)) "
                       "    AND (target_c IN (SELECT lookup_c FROM lookup_t_4)))")
                  (remove-ws))
              condition
              condition])))))

(deftest test-all
  (let [condition "all(in(lookup_t_1.lookup_c), in(lookup_t_2.lookup_c))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t",
                                            :column "target_c",
                                            :condition condition})
             [(-> (str "SELECT * FROM ("
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE NOT target_c IN (SELECT lookup_c FROM lookup_t_1) "
                       "  UNION "
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE NOT target_c IN (SELECT lookup_c FROM lookup_t_2)"
                       ")")
                  (remove-ws))
              condition
              condition])))))

(deftest test-not-all
  (let [condition "not(all(in(lookup_t_1.lookup_c), in(lookup_t_2.lookup_c)))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "SELECT * FROM ("
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE (target_c IN (SELECT lookup_c FROM lookup_t_1)) "
                       "  INTERSECT "
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE (target_c IN (SELECT lookup_c FROM lookup_t_2))"
                       ")")
                  (remove-ws))
              condition
              condition])))))

(deftest test-all-2
  (let [condition "all(in(lookup_t_1.lookup_c, lookup_t_2.lookup_c), in(lookup_t_3.lookup_c, lookup_t_4.lookup_c))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition}))
          [(-> (str "SELECT * FROM ("
                    "  SELECT *, ? AS failed_condition "
                    "  FROM target_t "
                    "  WHERE NOT target_c IN (SELECT lookup_c FROM lookup_t_1) "
                    "     OR NOT target_c IN (SELECT lookup_c FROM lookup_t_2) "
                    "  UNION "
                    "  SELECT *, ? AS failed_condition "
                    "  FROM target_t "
                    "  WHERE NOT target_c IN (SELECT lookup_c FROM lookup_t_3) "
                    "     OR NOT target_c IN (SELECT lookup_c FROM lookup_t_4))")
               (remove-ws))
           condition
           condition]))))

(deftest test-not-all-2
  (let [condition "not(all(in(lookup_t_1.lookup_c, lookup_t_2.lookup_c), in(lookup_t_3.lookup_c, lookup_t_4.lookup_c)))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "SELECT * FROM ("
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE (target_c IN (SELECT lookup_c FROM lookup_t_1)) "
                       "    AND (target_c IN (SELECT lookup_c FROM lookup_t_2)) "
                       "  INTERSECT "
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE (target_c IN (SELECT lookup_c FROM lookup_t_3)) "
                       "    AND (target_c IN (SELECT lookup_c FROM lookup_t_4)))")
                  (remove-ws))
              condition
              condition])))))

(deftest test-all-3
  (let [condition "all(in(lookup_t_1.c), in(lookup_t_2.c), in(lookup_t_3.c))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "SELECT * FROM ("
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE NOT target_c IN (SELECT c FROM lookup_t_1) "
                       "  UNION "
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE NOT target_c IN (SELECT c FROM lookup_t_2) "
                       "  UNION "
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE NOT target_c IN (SELECT c FROM lookup_t_3)"
                       ")")
                  (remove-ws))
              condition
              condition
              condition])))))

(deftest test-any-3
  (let [condition "any(in(lookup_t_1.c), in(lookup_t_2.c), in(lookup_t_3.c))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "SELECT * FROM ("
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE NOT target_c IN (SELECT c FROM lookup_t_1) "
                       "  INTERSECT "
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE NOT target_c IN (SELECT c FROM lookup_t_2) "
                       "  INTERSECT "
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE NOT target_c IN (SELECT c FROM lookup_t_3)"
                       ")")
                  (remove-ws))
              condition
              condition
              condition])))))

(deftest test-all-mixed
  (let [condition "all(in(lookup_t_1.c), not(in(lookup_t_2.c)))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "SELECT * FROM ("
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE NOT target_c IN (SELECT c FROM lookup_t_1) "
                       "  UNION "
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE (target_c IN (SELECT c FROM lookup_t_2))"
                       ")")
                  (remove-ws))
              condition
              condition])))))

(deftest test-any-mixed
  (let [condition "any(in(lookup_t_1.c), not(in(lookup_t_2.c)))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "SELECT * FROM ("
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE NOT target_c IN (SELECT c FROM lookup_t_1) "
                       "  INTERSECT "
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE (target_c IN (SELECT c FROM lookup_t_2))"
                       ")")
                  (remove-ws))
              condition
              condition])))))

(deftest test-any-all
  (let [condition "any(all(in(lookup_t1.c), in(lookup_t2.c)), all(in(lookup_t3.c), in(lookup_t4.c)))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "SELECT * FROM ("
                       "  SELECT * FROM ("
                       "    SELECT *, ? AS failed_condition "
                       "    FROM target_t "
                       "    WHERE NOT target_c IN (SELECT c FROM lookup_t1) "
                       "    UNION "
                       "    SELECT *, ? AS failed_condition "
                       "    FROM target_t "
                       "    WHERE NOT target_c IN (SELECT c FROM lookup_t2)"
                       "  )"
                       "  INTERSECT "
                       "  SELECT * FROM ("
                       "    SELECT *, ? AS failed_condition "
                       "    FROM target_t "
                       "    WHERE NOT target_c IN (SELECT c FROM lookup_t3) "
                       "    UNION "
                       "    SELECT *, ? AS failed_condition "
                       "    FROM target_t "
                       "    WHERE NOT target_c IN (SELECT c FROM lookup_t4)"
                       "  )"
                       ")")
                  (remove-ws))
              condition
              condition
              condition
              condition])))))

(deftest test-all-any
  (let [condition "all(any(in(lookup_t1.c), in(lookup_t2.c)), any(in(lookup_t3.c), in(lookup_t4.c)))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "SELECT * FROM ("
                       "  SELECT * FROM ("
                       "    SELECT *, ? AS failed_condition "
                       "    FROM target_t "
                       "    WHERE NOT target_c IN (SELECT c FROM lookup_t1) "
                       "    INTERSECT "
                       "    SELECT *, ? AS failed_condition "
                       "    FROM target_t "
                       "    WHERE NOT target_c IN (SELECT c FROM lookup_t2)"
                       "  )"
                       "  UNION "
                       "  SELECT * FROM ("
                       "    SELECT *, ? AS failed_condition "
                       "    FROM target_t "
                       "    WHERE NOT target_c IN (SELECT c FROM lookup_t3) "
                       "    INTERSECT "
                       "    SELECT *, ? AS failed_condition "
                       "    FROM target_t "
                       "    WHERE NOT target_c IN (SELECT c FROM lookup_t4)"
                       "  )"
                       ")")
                  (remove-ws))
              condition
              condition
              condition
              condition])))))

(deftest test-mixed-nested
  (let [condition "all(in(lookup_t1.c, lookup_t2.c), not(all(in(lookup_t3.c), in(lookup_t4.c))), any(in(lookup_t5.c), not(in(lookup_t6.c))))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "SELECT * FROM ("
                       "  SELECT *, ? AS failed_condition "
                       "  FROM target_t "
                       "  WHERE NOT target_c IN (SELECT c FROM lookup_t1) "
                       "     OR NOT target_c IN (SELECT c FROM lookup_t2) "
                       "  UNION "
                       "  SELECT * FROM ("
                       "    SELECT *, ? AS failed_condition "
                       "    FROM target_t "
                       "    WHERE (target_c IN (SELECT c FROM lookup_t3)) "
                       "    INTERSECT "
                       "    SELECT *, ? AS failed_condition "
                       "    FROM target_t "
                       "    WHERE (target_c IN (SELECT c FROM lookup_t4))"
                       "  )"
                       "  UNION "
                       "  SELECT * FROM ("
                       "    SELECT *, ? AS failed_condition "
                       "    FROM target_t "
                       "    WHERE NOT target_c IN (SELECT c FROM lookup_t5) "
                       "    INTERSECT "
                       "    SELECT *, ? AS failed_condition "
                       "    FROM target_t "
                       "    WHERE (target_c IN (SELECT c FROM lookup_t6))"
                       "  )"
                       ")")
                  (remove-ws))
              condition
              condition
              condition
              condition
              condition])))))

(deftest test-list
  (let [condition "list(\"@\", in(lookup_t.lookup_c))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "WITH target_t_split (target_c) AS ("
                       "  WITH RECURSIVE target_t_split (target_c, str) AS ("
                       "    SELECT ?, target_c||'@' "
                       "    FROM target_t "
                       "    UNION ALL "
                       "    SELECT SUBSTR(str, ?, (INSTR(str, ?))), SUBSTR(str, (INSTR(str, ?)) + ?) "
                       "    FROM target_t_split "
                       "    WHERE str <> ?"
                       "  ) "
                       "  SELECT DISTINCT target_c "
                       "  FROM target_t_split "
                       "  WHERE target_c <> ?"
                       ") "
                       "SELECT *, ? AS failed_condition "
                       "FROM target_t_split "
                       "WHERE NOT target_c IN (SELECT lookup_c FROM lookup_t)")
                  (remove-ws))
              ""
              0
              "@"
              "@"
              1
              ""
              ""
              condition])))))

(deftest test-not-list
  (let [condition "not(list(\"@\", in(lookup_t.lookup_c)))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "WITH target_t_split (reference, id, target_c) AS ("
                       "  WITH RECURSIVE target_t_split (reference, id, target_c, str) AS ("
                       "    SELECT target_c, ?, ?, target_c||'@' "
                       "    FROM target_t "
                       "    UNION ALL "
                       "    SELECT reference, id + ?, SUBSTR(str, ?, (INSTR(str, ?))), SUBSTR(str, (INSTR(str, ?)) + ?) "
                       "    FROM target_t_split "
                       "    WHERE str <> ?"
                       "  ) "
                       "  SELECT reference, id, target_c "
                       "  FROM target_t_split "
                       "  WHERE target_c <> ?"
                       ") "
                       "SELECT reference, ? AS failed_condition "
                       "FROM target_t_split "
                       "GROUP BY reference "
                       "HAVING COUNT(?) = SUM(CASE WHEN (target_c IN (SELECT lookup_c FROM lookup_t)) THEN ? ELSE ? END)")
                  (remove-ws))
              0
              ""
              1
              0
              "@"
              "@"
              1
              ""
              ""
              condition
              1
              1
              0])))))

(deftest test-not-list-not
  (let [condition "not(list(\"@\", not(in(lookup_t.lookup_c))))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "WITH target_t_split (reference, id, target_c) AS ("
                       "  WITH RECURSIVE target_t_split (reference, id, target_c, str) AS ("
                       "    SELECT target_c, ?, ?, target_c||'@' "
                       "    FROM target_t "
                       "    UNION ALL "
                       "    SELECT reference, id + ?, SUBSTR(str, ?, (INSTR(str, ?))), SUBSTR(str, (INSTR(str, ?)) + ?) "
                       "    FROM target_t_split WHERE str <> ?) SELECT reference, id, target_c FROM target_t_split "
                       "    WHERE target_c <> ?"
                       "  ) "
                       "  SELECT reference, ? AS failed_condition "
                       "  FROM target_t_split "
                       "  GROUP BY reference "
                       "  HAVING COUNT(?) = SUM(CASE WHEN NOT target_c IN (SELECT lookup_c FROM lookup_t) THEN ? ELSE ? END)")
                  (remove-ws))
              0
              ""
              1
              0
              "@"
              "@"
              1
              ""
              ""
              condition
              1
              1
              0])))))

(deftest test-list-not
  (let [condition "list(\"@\", not(in(lookup_t.lookup_c)))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "WITH target_t_split (target_c) AS ("
                       "  WITH RECURSIVE target_t_split (target_c, str) AS ("
                       "    SELECT ?, target_c||'@' "
                       "    FROM target_t "
                       "    UNION ALL "
                       "    SELECT SUBSTR(str, ?, (INSTR(str, ?))), SUBSTR(str, (INSTR(str, ?)) + ?) "
                       "    FROM target_t_split "
                       "    WHERE str <> ?"
                       "  ) "
                       "  SELECT DISTINCT target_c "
                       "  FROM target_t_split "
                       "  WHERE target_c <> ?"
                       ") "
                       "SELECT *, ? AS failed_condition "
                       "FROM target_t_split "
                       "WHERE (target_c IN (SELECT lookup_c FROM lookup_t))")
                  (remove-ws))
              ""
              0
              "@"
              "@"
              1
              ""
              ""
              condition])))))

(deftest test-not-list-not
  (let [condition "not(list(\"@\", not(in(lookup_t.lookup_c))))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "WITH target_t_split (reference, id, target_c) AS ("
                       "  WITH RECURSIVE target_t_split (reference, id, target_c, str) AS ("
                       "    SELECT target_c, ?, ?, target_c||'@' "
                       "    FROM target_t "
                       "    UNION ALL "
                       "    SELECT reference, id + ?, SUBSTR(str, ?, (INSTR(str, ?))), SUBSTR(str, (INSTR(str, ?)) + ?) "
                       "    FROM target_t_split "
                       "    WHERE str <> ?"
                       "  ) "
                       "  SELECT reference, id, target_c "
                       "  FROM target_t_split "
                       "  WHERE target_c <> ?"
                       ") "
                       "SELECT reference, ? AS failed_condition "
                       "FROM target_t_split "
                       "GROUP BY reference "
                       "HAVING COUNT(?) = SUM(CASE WHEN NOT target_c IN (SELECT lookup_c FROM lookup_t) THEN ? ELSE ? END)")
                  (remove-ws))
              0
              ""
              1
              0
              "@"
              "@"
              1
              ""
              ""
              condition
              1
              1
              0])))))

(deftest test-split
  (let [condition "split(\"@\", 3, in(lookup_t_1.c), in(lookup_t_2.c), in(lookup_t_3.c))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "WITH target_t_split (reference, id, target_c) AS ("
                       "  WITH RECURSIVE target_t_split (reference, id, target_c, str) AS ("
                       "    SELECT target_c, ?, ?, target_c||'@' "
                       "    FROM target_t "
                       "    UNION ALL "
                       "    SELECT reference, id + ?, SUBSTR(str, ?, (INSTR(str, ?))), SUBSTR(str, (INSTR(str, ?)) + ?) "
                       "    FROM target_t_split WHERE str <> ?"
                       "  ) "
                       "  SELECT reference, id, target_c "
                       "  FROM target_t_split "
                       "  WHERE target_c <> ?"
                       "), count_invalid (reference, invalid) AS ("
                       "  SELECT reference, COUNT(?) <> ? AS invalid "
                       "  FROM target_t_split "
                       "  GROUP BY reference"
                       "), col1_invalid (reference, invalid) AS ("
                       "  SELECT reference, NOT target_c IN (SELECT c FROM lookup_t_1) AS invalid "
                       "  FROM target_t_split "
                       "  WHERE id = ?"
                       "), col2_invalid (reference, invalid) AS ("
                       "  SELECT reference, NOT target_c IN (SELECT c FROM lookup_t_2) AS invalid "
                       "  FROM target_t_split "
                       "  WHERE id = ?"
                       "), col3_invalid (reference, invalid) AS ("
                       "  SELECT reference, NOT target_c IN (SELECT c FROM lookup_t_3) AS invalid "
                       "  FROM target_t_split "
                       "  WHERE id = ?"
                       "), results (reference, count_invalid, col1_invalid, col2_invalid, col3_invalid) AS ("
                       "  SELECT "
                       "    count_invalid.reference AS reference, "
                       "    count_invalid.invalid AS count_invalid, "
                       "    col1_invalid.invalid AS col1_invalid, "
                       "    col2_invalid.invalid AS col2_invalid, "
                       "    col3_invalid.invalid AS col3_invalid "
                       "  FROM count_invalid "
                       "  LEFT JOIN col1_invalid ON col1_invalid.reference = count_invalid.reference "
                       "  LEFT JOIN col2_invalid ON col2_invalid.reference = count_invalid.reference "
                       "  LEFT JOIN col3_invalid ON col3_invalid.reference = count_invalid.reference"
                       ") "
                       "SELECT *, ? AS failed_condition "
                       "FROM results "
                       "WHERE (? = count_invalid) "
                       "  OR (? = col1_invalid) "
                       "  OR (? = col2_invalid) "
                       "  OR (? = col3_invalid)")
                  (remove-ws))
              0
              ""
              1
              0
              "@"
              "@"
              1
              ""
              ""
              1
              3
              1
              2
              3
              condition
              1
              1
              1
              1])))))

(deftest test-split-not
  (let [condition "split(\"@\", 3, in(lookup_t_1.c), not(in(lookup_t_2.c)), in(lookup_t_3.c))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "WITH target_t_split (reference, id, target_c) AS ("
                       "  WITH RECURSIVE target_t_split (reference, id, target_c, str) AS ("
                       "    SELECT target_c, ?, ?, target_c||'@' "
                       "    FROM target_t "
                       "    UNION ALL "
                       "    SELECT reference, id + ?, SUBSTR(str, ?, (INSTR(str, ?))), SUBSTR(str, (INSTR(str, ?)) + ?) "
                       "    FROM target_t_split WHERE str <> ?"
                       "  ) "
                       "  SELECT reference, id, target_c "
                       "  FROM target_t_split "
                       "  WHERE target_c <> ?"
                       "), count_invalid (reference, invalid) AS ("
                       "  SELECT reference, COUNT(?) <> ? AS invalid "
                       "  FROM target_t_split "
                       "  GROUP BY reference"
                       "), col1_invalid (reference, invalid) AS ("
                       "  SELECT reference, NOT target_c IN (SELECT c FROM lookup_t_1) AS invalid "
                       "  FROM target_t_split "
                       "  WHERE id = ?"
                       "), col2_invalid (reference, invalid) AS ("
                       "  SELECT reference, (target_c IN (SELECT c FROM lookup_t_2)) AS invalid "
                       "  FROM target_t_split "
                       "  WHERE id = ?"
                       "), col3_invalid (reference, invalid) AS ("
                       "  SELECT reference, NOT target_c IN (SELECT c FROM lookup_t_3) AS invalid "
                       "  FROM target_t_split "
                       "  WHERE id = ?"
                       "), results (reference, count_invalid, col1_invalid, col2_invalid, col3_invalid) AS ("
                       "  SELECT "
                       "    count_invalid.reference AS reference, "
                       "    count_invalid.invalid AS count_invalid, "
                       "    col1_invalid.invalid AS col1_invalid, "
                       "    col2_invalid.invalid AS col2_invalid, "
                       "    col3_invalid.invalid AS col3_invalid "
                       "  FROM count_invalid "
                       "  LEFT JOIN col1_invalid ON col1_invalid.reference = count_invalid.reference "
                       "  LEFT JOIN col2_invalid ON col2_invalid.reference = count_invalid.reference "
                       "  LEFT JOIN col3_invalid ON col3_invalid.reference = count_invalid.reference"
                       ") "
                       "SELECT *, ? AS failed_condition "
                       "FROM results "
                       "WHERE (? = count_invalid) "
                       "  OR (? = col1_invalid) "
                       "  OR (? = col2_invalid) "
                       "  OR (? = col3_invalid)")
                  (remove-ws))
              0
              ""
              1
              0
              "@"
              "@"
              1
              ""
              ""
              1
              3
              1
              2
              3
              condition
              1
              1
              1
              1])))))

(deftest test-not-split
  (let [condition "not(split(\"@\", 3, in(lookup_t_1.c), in(lookup_t_2.c), in(lookup_t_3.c)))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "WITH target_t_split (reference, id, target_c) AS ("
                       "  WITH RECURSIVE target_t_split (reference, id, target_c, str) AS ("
                       "    SELECT target_c, ?, ?, target_c||'@' "
                       "    FROM target_t "
                       "    UNION ALL "
                       "    SELECT reference, id + ?, SUBSTR(str, ?, (INSTR(str, ?))), SUBSTR(str, (INSTR(str, ?)) + ?) "
                       "    FROM target_t_split WHERE str <> ?"
                       "  ) "
                       "  SELECT reference, id, target_c "
                       "  FROM target_t_split "
                       "  WHERE target_c <> ?"
                       "), count_invalid (reference, invalid) AS ("
                       "  SELECT reference, COUNT(?) <> ? AS invalid "
                       "  FROM target_t_split "
                       "  GROUP BY reference"
                       "), col1_invalid (reference, invalid) AS ("
                       "  SELECT reference, NOT target_c IN (SELECT c FROM lookup_t_1) AS invalid "
                       "  FROM target_t_split "
                       "  WHERE id = ?"
                       "), col2_invalid (reference, invalid) AS ("
                       "  SELECT reference, NOT target_c IN (SELECT c FROM lookup_t_2) AS invalid "
                       "  FROM target_t_split "
                       "  WHERE id = ?"
                       "), col3_invalid (reference, invalid) AS ("
                       "  SELECT reference, NOT target_c IN (SELECT c FROM lookup_t_3) AS invalid "
                       "  FROM target_t_split "
                       "  WHERE id = ?"
                       "), results (reference, count_invalid, col1_invalid, col2_invalid, col3_invalid) AS ("
                       "  SELECT "
                       "    count_invalid.reference AS reference, "
                       "    count_invalid.invalid AS count_invalid, "
                       "    col1_invalid.invalid AS col1_invalid, "
                       "    col2_invalid.invalid AS col2_invalid, "
                       "    col3_invalid.invalid AS col3_invalid "
                       "  FROM count_invalid "
                       "  LEFT JOIN col1_invalid ON col1_invalid.reference = count_invalid.reference "
                       "  LEFT JOIN col2_invalid ON col2_invalid.reference = count_invalid.reference "
                       "  LEFT JOIN col3_invalid ON col3_invalid.reference = count_invalid.reference"
                       ") "
                       "SELECT *, ? AS failed_condition "
                       "FROM results "
                       "WHERE (? = count_invalid) "
                       "  AND (? = col1_invalid) "
                       "  AND (? = col2_invalid) "
                       "  AND (? = col3_invalid)")
                  (remove-ws))
              0
              ""
              1
              0
              "@"
              "@"
              1
              ""
              ""
              1
              3
              1
              2
              3
              condition
              0
              0
              0
              0])))))

(deftest test-not-split-not
  (let [condition "not(split(\"@\", 3, in(lookup_t_1.c), not(in(lookup_t_2.c)), in(lookup_t_3.c)))"]
    (testing (str "target_t.target_c " condition)
      (is (= (sqlify-condition #:conditions{:table "target_t"
                                            :column "target_c"
                                            :condition condition})
             [(-> (str "WITH target_t_split (reference, id, target_c) AS ("
                       "  WITH RECURSIVE target_t_split (reference, id, target_c, str) AS ("
                       "    SELECT target_c, ?, ?, target_c||'@' "
                       "    FROM target_t "
                       "    UNION ALL "
                       "    SELECT reference, id + ?, SUBSTR(str, ?, (INSTR(str, ?))), SUBSTR(str, (INSTR(str, ?)) + ?) "
                       "    FROM target_t_split WHERE str <> ?"
                       "  ) "
                       "  SELECT reference, id, target_c "
                       "  FROM target_t_split "
                       "  WHERE target_c <> ?"
                       "), count_invalid (reference, invalid) AS ("
                       "  SELECT reference, COUNT(?) <> ? AS invalid "
                       "  FROM target_t_split "
                       "  GROUP BY reference"
                       "), col1_invalid (reference, invalid) AS ("
                       "  SELECT reference, NOT target_c IN (SELECT c FROM lookup_t_1) AS invalid "
                       "  FROM target_t_split "
                       "  WHERE id = ?"
                       "), col2_invalid (reference, invalid) AS ("
                       "  SELECT reference, (target_c IN (SELECT c FROM lookup_t_2)) AS invalid "
                       "  FROM target_t_split "
                       "  WHERE id = ?"
                       "), col3_invalid (reference, invalid) AS ("
                       "  SELECT reference, NOT target_c IN (SELECT c FROM lookup_t_3) AS invalid "
                       "  FROM target_t_split "
                       "  WHERE id = ?"
                       "), results (reference, count_invalid, col1_invalid, col2_invalid, col3_invalid) AS ("
                       "  SELECT "
                       "    count_invalid.reference AS reference, "
                       "    count_invalid.invalid AS count_invalid, "
                       "    col1_invalid.invalid AS col1_invalid, "
                       "    col2_invalid.invalid AS col2_invalid, "
                       "    col3_invalid.invalid AS col3_invalid "
                       "  FROM count_invalid "
                       "  LEFT JOIN col1_invalid ON col1_invalid.reference = count_invalid.reference "
                       "  LEFT JOIN col2_invalid ON col2_invalid.reference = count_invalid.reference "
                       "  LEFT JOIN col3_invalid ON col3_invalid.reference = count_invalid.reference"
                       ") "
                       "SELECT *, ? AS failed_condition "
                       "FROM results "
                       "WHERE (? = count_invalid) "
                       "  AND (? = col1_invalid) "
                       "  AND (? = col2_invalid) "
                       "  AND (? = col3_invalid)")
                  (remove-ws))
              0
              ""
              1
              0
              "@"
              "@"
              1
              ""
              ""
              1
              3
              1
              2
              3
              condition
              0
              0
              0
              0])))))
