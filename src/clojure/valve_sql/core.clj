(ns valve-sql.core
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.pprint :refer [pprint]]
            [honey.sql :as honey]
            [honey.sql.helpers :as h]
            [instaparse.core :as insta]
            [next.jdbc :as jdbc]
            [valve-sql.log :as log])
  (:import [valve_sql Sqlite])
  (:gen-class))

(def db
  {:dbtype "sqlite"
   :dbname "example.db"})

(def conn
  (-> db (jdbc/get-datasource) (jdbc/get-connection)))

;; Create user-defined regexp function:
(. Sqlite (createRegexMatchesFunc conn))

(defn- postprocess
  "Given the result of parsing a given condition using VALVE's grammar, generate a map containing
  the condition's type and its value, where the latter can in general be composed of further
  conditions which are postprocessed recursively."
  [result]
  (let [node-type (first result)
        node-values (drop 1 result)
        first-value (first node-values)]
    (cond
      (some #(= node-type %) '(:start :expression :label :function-name
                                      :argument :regex :regex-pattern))
      (postprocess first-value)

      (= node-type :string)
      {:type "string"
       :value (postprocess first-value)}

      (= node-type :ALPHANUM)
      (->> result (drop 1) (string/join ""))

      ;; Whitespace:
      (= node-type :_)
      (when (> (count result) 1)
        {:type "space"
         :value (->> result (drop 1) (string/join ""))})

      ;; Double-quoted string:
      (= node-type :dqstring)
      (->> result (drop 1) (string/join ""))

      (= node-type :function)
      {:type "function"
       :name (postprocess first-value)
       :args (postprocess (nth node-values 1))}

      (= node-type :arguments)
      (->> node-values (map postprocess) (vec))

      (= node-type :field)
      {:type "field"
       :table (postprocess first-value)
       :column (postprocess (nth node-values 1))}

      (= node-type :named-arg)
      {:type "named-arg"
       :key (postprocess first-value)
       :value (postprocess (nth node-values 1))}

      (= node-type :regex-sub)
      {:type "regex"
       :pattern (postprocess first-value)
       :replace (postprocess (nth node-values 1))
       :flags (postprocess (nth node-values 2))}

      (= node-type :regex-match)
      {:type "regex"
       :pattern (postprocess first-value)
       :flags (postprocess (nth node-values 1))}

      (= node-type :regex-unescaped)
      (->> result (drop 1) (string/join ""))

      (= node-type :regex-flag)
      (->> result (drop 1) (vec))

      :else
      first-value)))

(defn parse
  "TODO: Add a docstring here"
  [{:keys [conditions/table conditions/column conditions/condition]}]
  (log/debug "Parsing condition:" condition "for table.column:" (str table "." column))
  (let [condition-parser (-> "valve_grammar.ebnf"
                             (io/resource)
                             (insta/parser))]
    (let [result (-> condition (condition-parser))]
      (if (insta/failure? result)
        (log/error "Parsing failed due to reason:" (insta/get-failure result))
        ;; Drop the :start keyword and iterate over the remaining nodes in the tree:
        (->> (drop 1 result)
             (map postprocess)
             (remove nil?)
             (vec)
             (remove #(= (:type %) "space"))
             ;; After removing whitespace, the result list should contain only one entry,
             ;; corresponding to the `expression` to be parsed (see resources/valve_grammar.ebnf):
             (#(if (> (count %) 1)
                 (throw (Exception. (str "Unable to parse condition: '" condition
                                         "'. Got result vector with more than one entry: " result)))
                 {:table table
                  :column column
                  :pre-parsed condition
                  :condition (first %)})))))))

(defn gen-sql-in
  "TODO: Add a docstring here"
  [table column args negate?]
  (log/debug "Generating SQL to validate function: 'in' with args:" args "against table.column:"
             (str table "." column))
  (letfn [(field-condition [{child-table :table
                             child-column :column}]
            (let [base-condition [:in (keyword column) (-> (h/select (keyword child-column))
                                                           (h/from (keyword child-table)))]]
              (if negate?
                base-condition
                [:not base-condition])))]
    (->> args
         (map field-condition)
         (#(if negate?
             (apply conj [:and] %)
             (apply conj [:or] %))))))

;; TODO: Need to add `rowid` to the output.
(defn gen-sql
  "TODO: Add a docstring here"
  [{table :table
    column :column
    pre-parsed :pre-parsed
    {cond-type :type
     cond-name :name
     cond-args :args
     negate? :negate?} :condition}]
  (log/debug "Generating SQL for cond-name:" cond-name "of type:" cond-type "with args:" cond-args
             "against table.column:" (str table "." column))
  (letfn
   [(list [] ;; Implements list(separator, expr)
      (if-not (-> cond-args (first) :type (= "string"))
        (log/error "Invalid delimiter argument:" (first cond-args) "to list()")
        (let [delim-arg (-> cond-args (first) :value (string/replace #"^\"|\"$" ""))
              list-cond (second cond-args)
              table-k (keyword table)
              column-k (keyword column)
              split-table-k (-> table (str "_split") (keyword))
              split-column-k (keyword (str column "||'" delim-arg "'"))
              inner-sql (gen-sql {:table split-table-k :column column-k :pre-parsed pre-parsed
                                  :condition (assoc list-cond :negate? negate?)})]
          (if-not negate?
            (-> inner-sql
                (h/with [[split-table-k {:columns [:rowid column-k]}]
                         (-> (h/with-recursive [[split-table-k {:columns [:rowid column-k :str]}]
                                                (h/union-all
                                                 (-> (h/select :rowid "" split-column-k)
                                                     (h/from table-k))
                                                 (-> (h/select :rowid
                                                               [[:substr :str 0
                                                                 [[:instr :str delim-arg]]]]
                                                               [[:substr :str
                                                                 [:+ [[:instr :str delim-arg]] 1]]])
                                                     (h/from split-table-k)
                                                     (h/where [:<> :str ""])))])
                             (h/select :rowid column-k)
                             (h/from split-table-k)
                             (h/where [:<> column-k ""]))]))
            (-> (h/with [[split-table-k {:columns [:rowid :reference :id column-k]}]
                         (-> (h/with-recursive [[split-table-k {:columns [:rowid :reference :id
                                                                          column-k :str]}]
                                                (h/union-all
                                                 (-> (h/select :rowid column-k 0 "" split-column-k)
                                                     (h/from table-k))
                                                 (-> (h/select :rowid :reference [[:+ :id 1]]
                                                               [[:substr :str 0
                                                                 [[:instr :str delim-arg]]]]
                                                               [[:substr :str
                                                                 [:+ [[:instr :str delim-arg]] 1]]])
                                                     (h/from split-table-k)
                                                     (h/where [:<> :str ""])))])
                             (h/select :rowid :reference :id column-k)
                             (h/from split-table-k)
                             (h/where [:<> column-k ""]))])
                (h/select :rowid :reference [pre-parsed :failed-condition])
                (h/from split-table-k)
                (h/group-by :rowid :reference)
                (h/having := [:count 1] [:sum [:case (:where inner-sql) 1
                                               :else 0]]))))))

    (concatenate [] ;; Implements "concat()"
      (let [regexp (reduce
                    #(str %1 (if (= (:type %2) "string")
                               (string/replace (:value %2) #"^\"|\"$" "")
                               ;; TODO: (\w+) is probably not exactly what we want ...
                               "(\\w+)"))
                    ""
                    cond-args)
            table-k (keyword table)
            column-k (keyword column)
            delim-arg "@"
            conds (->> cond-args (filter #(not= (:type %) "string")))
            num-conds (count conds)
            regexp-func (keyword "regexp_matches")
            inner-sql (gen-sql {:table "captures" :column "capture" :pre-parsed pre-parsed
                                ;; Note that we do not associate negate? with the condition we send
                                ;; to split(), but handle it at this level (see below).
                                :condition {:type "function" :name "split"
                                            :args (concat
                                                   [{:type "string", :value delim-arg}
                                                    {:type "string",
                                                     :value (str num-conds)}]
                                                   conds)}})]
        (-> inner-sql
            (h/with [[:captures {:columns [:rowid column-k :capture]}]
                     (-> (h/select :rowid column-k [[regexp-func column-k regexp]])
                         (h/from table-k))])
            ;; Remove the main query that split uses and replace it with our own:
            (dissoc :select :from :where)
            (h/select :captures/rowid column-k [pre-parsed :failed-condition])
            (h/from :captures)
            (h/left-join :results [:and
                                   [:= :captures/rowid :results/rowid]
                                   [:= :captures/capture :results/reference]])
            (h/where
             (if negate?
               (into [:and [:<> :captures/capture ""]]
                     (for [i (range 1 (+ num-conds 1))]
                       [:= 0 (keyword (str "col" i "-invalid"))]))
               (into [:or [:= :captures/capture ""]]
                     (for [i (range 1 (+ num-conds 1))]
                       [:= 1 (keyword (str "col" i "-invalid"))])))))))

    (split [] ;; Implements "split(separator, count, expr, ...)"
      (let [delim-arg (-> cond-args (first) :value (string/replace #"^\"|\"$" ""))
            num-conds (-> cond-args (second) :value (Integer/parseInt))
            split-conds (drop 2 cond-args)]
        (if-not (= num-conds (count split-conds))
          (log/error "Number of conditions to split(sep, count, condition, ...) must match"
                     "count, but count was:" num-conds "and there were" (count split-conds)
                     "conditions")
          (let [table-k (keyword table)
                column-k (keyword column)
                split-table-k (-> table (str "_split") (keyword))
                split-column-k (keyword (str column "||'" delim-arg "'"))
                inner-sql (for [split-cond split-conds]
                            (gen-sql {:table split-table-k :column column-k
                                      :pre-parsed pre-parsed
                                         ;; Unlike list(), we do not pass along the negation but
                                         ;; take care of it at this level (see below).
                                      :condition split-cond}))
                main-sql
                (-> (apply h/with
                           (-> [[[split-table-k {:columns [:rowid :reference :id column-k]}]
                                 (-> (h/with-recursive
                                       [[split-table-k {:columns [:rowid :reference :id column-k :str]}]
                                        (h/union-all
                                         (-> (h/select :rowid column-k 0 "" split-column-k)
                                             (h/from table-k))
                                         (-> (h/select :rowid
                                                       :reference
                                                       [[:+ :id 1]]
                                                       [[:substr :str 0
                                                         [[:instr :str delim-arg]]]]
                                                       [[:substr :str
                                                         [:+ [[:instr :str delim-arg]] 1]]])
                                             (h/from split-table-k)
                                             (h/where [:<> :str ""])))])
                                     (h/select :rowid :reference :id column-k)
                                     (h/from split-table-k)
                                     (h/where [:<> column-k ""]))]
                                [[:count-invalid {:columns [:rowid :reference :invalid]}]
                                 (-> (h/select :rowid :reference [[:<> [:count 1] num-conds] :invalid])
                                     (h/from split-table-k)
                                     (h/group-by :rowid :reference))]]
                               (into (for [i (range 1 (+ num-conds 1))]
                                       [[(keyword (str "col" i "-invalid")) ;; The table name
                                         {:columns [:rowid :reference :invalid]}]
                                        (-> (h/select :rowid
                                                      :reference
                                                      [(-> inner-sql
                                                           (nth (- i 1)) ;; (i-1)th where clause
                                                           :where)
                                                       :invalid])
                                            (h/from split-table-k)
                                            (h/where [:= :id i]))]))
                               (into [[[:results {:columns
                                                  (into [:rowid :reference :count-invalid]
                                                           ;; The column names for the i columns:
                                                        (for [i (range 1 (+ num-conds 1))]
                                                          (keyword (str "col" i "-invalid"))))}]
                                       (-> (apply h/select
                                                  (into [[:count-invalid/rowid :rowid]
                                                         [:count-invalid/reference :reference]
                                                         [:count-invalid/invalid :count-invalid]]
                                                           ;; The i "invalid" columns and aliases:
                                                        (for [i (range 1 (+ num-conds 1))]
                                                          [(keyword
                                                            (str "col" i "-invalid/invalid"))
                                                           (keyword (str "col" i "-invalid"))])))
                                           (h/from :count-invalid)
                                           (#(loop [query %, i 1]
                                               ;; Left joins for each of the i invalid tables:
                                               (if (-> num-conds (+ 1) (= i))
                                                 query
                                                 (let [table (keyword (str "col" i "-invalid"))
                                                       column-ref (keyword
                                                                   (str "col" i
                                                                        "-invalid/reference"))
                                                       column-rid (keyword
                                                                   (str "col" i
                                                                        "-invalid/rowid"))]
                                                   (recur
                                                    (h/left-join
                                                     query table
                                                     [:and
                                                      [:= column-rid :count-invalid/rowid]
                                                      [:= column-ref :count-invalid/reference]])
                                                    (+ i 1)))))))]])))
                    (h/select :rowid :reference [pre-parsed :failed-condition])
                    (h/from :results))]
            (-> main-sql
                (#(if-not negate?
                       ;; Return rows that have something invalid in them:
                    (h/where % (into [:or [:= 1 :count-invalid]]
                                     (for [i (range 1 (+ num-conds 1))]
                                       [:= 1 (keyword (str "col" i "-invalid"))])))
                       ;; Return rows that have nothing invalid in them:
                    (h/where % (into [:and [:= 0 :count-invalid]]
                                     (for [i (range 1 (+ num-conds 1))]
                                       [:= 0 (keyword (str "col" i "-invalid"))]))))))))))]
    (cond
      (= cond-type "function")
      (cond
        (= cond-name "in")
        (-> (h/select :rowid, (keyword column) [pre-parsed :failed-condition])
            (h/from (keyword table))
            (h/where (gen-sql-in table column cond-args negate?)))

        (= cond-name "all")
        (let [inner-sql (->> cond-args
                             (map #(gen-sql {:table table :column column :pre-parsed pre-parsed
                                             :condition (assoc % :negate? negate?)}))
                             (remove nil?)
                             (#(if negate?
                                 (apply h/intersect %)
                                 (apply h/union %))))]
          {:select [:*]
           :from inner-sql})

        (= cond-name "any")
        (let [inner-sql (->> cond-args
                             (map #(gen-sql {:table table :column column :pre-parsed pre-parsed
                                             :condition (assoc % :negate? negate?)}))
                             (remove nil?)
                             (#(if negate?
                                 (apply h/union %)
                                 (apply h/intersect %))))]
          {:select [:*]
           :from inner-sql})

        ;; not is a unary operator so we can just take the first argument from the list of args
        ;; and associate a negation to it:
        (= cond-name "not")
        (let [operand (first cond-args)]
          (gen-sql {:table table :column column :pre-parsed pre-parsed
                    :condition (-> operand
                                   (assoc :negate?
                                          ;; if negate? is set, then do not propogate the negation
                                          ;; (case of double-negation):
                                          (when-not negate? true)))}))

        (= cond-name "list")
        (try
          (list)
          (catch Exception e
            (log/error (.getMessage e))))

        (= cond-name "concat")
        (try
          (concatenate)
          (catch Exception e
            (log/error (.getMessage e))))

        (= cond-name "split")
        (try
          (split)
          (catch Exception e
            (log/error (.getMessage e))))

        :else
        (log/error "Function:" cond-name "not yet supported by gen-sql."))

      :else
      (log/error "Condition type:" cond-type "not yet supported by gen-sql."))))

(defn sqlify-condition
  "TODO: Add a docstring here"
  [condition]
  (-> condition
      (parse)
      (gen-sql)
      (honey/format {:inline false})))

(defn validate-condition
  "TODO: Add a docstring here"
  [condition]
  (-> condition
      (sqlify-condition)
      (#(do
          (log/debug "Executing SQL:" %)
          (jdbc/execute! conn %)))))

(defn -main
  "TODO: Add a docstring here."
  [& args]
  (let [rows (jdbc/execute! conn ["select * from conditions"])]
    (->> rows
         (map validate-condition))))
