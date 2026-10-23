(ns db.jdbc.next.examples
  (:require [hikari-cp.core]
            [next.jdbc :as jdbc]
            [next.jdbc.plan :as plan]
            [next.jdbc.result-set :as rs]
            )
  (:gen-class))

(def datasource-options {:auto-commit        true
                         :read-only          false
                         :connection-timeout 30000
                         :validation-timeout 5000
                         :idle-timeout       600000
                         :max-lifetime       1800000
                         :minimum-idle       10
                         :maximum-pool-size  10
                         :pool-name          "db-pool"
                         :adapter            "h2"
                         :url                "jdbc:h2:~/example"})

(defonce ds
         (hikari-cp.core/make-datasource datasource-options))

(comment
  (jdbc/execute! ds ["create table address (
  id int auto_increment primary key,
  name varchar(32),
  email varchar(255))"])

  (jdbc/execute! ds ["insert into address(name,email)
  values('Sean Corfield','sean@corfield.org')"])

  (jdbc/execute! ds ["select * from address"])
  )

(comment
  (jdbc/execute-one! ds ["
  insert into address(name,email)
  values('Someone Else','some@elsewhere.com')"]
                     {:return-keys true})
  (jdbc/execute-one! ds ["select * from address where id = ?" 2])
  )

(comment
 (jdbc/execute-one! ds ["insert into address(name,email)
 values('Someone Else','some@elsewhere.com')"]
                    {:return-keys true :builder-fn rs/as-unqualified-lower-maps})
 ;{:id 4}
 (jdbc/execute-one! ds ["select * from address where id = ?" 3]
                    {:builder-fn rs/as-unqualified-lower-maps})
 ;(jdbc/execute-one! ds ["select * from address where id = ?" 3]
 ;                          {:builder-fn rs/as-unqualified-lower-maps})
 )

(def ds-opts (jdbc/with-options ds {:builder-fn rs/as-unqualified-lower-maps}))
(comment
  (jdbc/execute-one! ds-opts ["insert into address(name,email)
  values('Someone Else','some@elsewhere.com')"]
                     {:return-keys true})
  ;{:id 7}
  (jdbc/execute-one! ds-opts ["select * from address where id = ?" 7])
  ;{:id 7, :name "Someone Else", :email "some@elsewhere.com"}
  )

(comment
  (jdbc/execute-one! ds ["create table invoice (
  id int auto_increment primary key,
  product varchar(32),
  unit_price decimal(10,2),
  unit_count int,
  customer_id int)"])

  (jdbc/execute-one! ds ["insert into invoice (product, unit_price, unit_count, customer_id)
values ('apple', 0.99, 6, 100),
       ('banana', 1.25, 3, 100),
       ('cucumber', 2.49, 2, 100)"])
  ;reduce和plan可以避免把ResultSet放入Clojure数据结构
  (reduce
    (fn [cost row]
      (+ cost (* (:unit_price row)
                 (:unit_count row))))
    0
    (jdbc/plan ds ["select * from invoice where customer_id = ?" 100]))

  (transduce
    (map #(* (:unit_price %) (:unit_count %)))
    +
    0
    (jdbc/plan ds ["select * from invoice where customer_id = ?" 100]))

  (transduce
    (map :unit_count)
    +
    0
    (jdbc/plan ds ["select * from invoice where customer_id = ?" 100]))

  (into #{}
        (map :product)
        (jdbc/plan ds ["select * from invoice where customer_id = ?" 100]))

  (run! #(println (:product %))
        (jdbc/plan ds ["select * from invoice where customer_id = ?" 100]))

  (into []
        (map #(select-keys % [:id :product :unit_price :unit_count :customer_id]))
        (jdbc/plan ds ["select * from invoice where customer_id = ?" 100]))

  (into []
        (map #(select-keys % [:invoice/id :invoice/product
                              :invoice/unit_price :invoice/unit_count
                              :invoice/customer_id]))
        (jdbc/plan ds ["select * from invoice where customer_id = ?" 100]))

  (into []
        (map #(select-keys % [:foo/id :bar/product
                              :quux/unit_price :wibble/unit_count
                              :blah/customer_id]))
        (jdbc/plan ds ["select * from invoice where customer_id = ?" 100]))

  (into []
        (map #(rs/datafiable-row % ds {}))
        (jdbc/plan ds ["select * from invoice where customer_id = ?" 100]))

  (plan/select-one!
    ds [:n] ["select count(*) as n from invoice where customer_id = ?" 100])

  (plan/select! ds
                :product
                ["select * from invoice where customer_id = ?" 100]
                {:into #{}})

  (plan/select! ds
                :product
                ["select * from invoice where customer_id = ?" 100]
                {:into #{}})

  (jdbc/with-transaction [tx ds]
                         (jdbc/execute-one! ds ""))
  )
