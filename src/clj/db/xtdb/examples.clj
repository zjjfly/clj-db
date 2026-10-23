(ns db.xtdb.examples
  (:require [xtdb.api :as xt]))

;启动一个XTDB节点，使用H2作为transaction log和document store
(defonce node (xt/start-node
                {:xtdb.jdbc/connection-pool {:dialect   {:xtdb/module 'xtdb.jdbc.h2/->dialect}
                                             :pool-opts {:maximumPoolSize 10
                                                         :idleTimeout     (* 10 60 1000)
                                                         :autoCommit      true}
                                             :db-spec   {:dbtype "h2" :dbname "xtdb-store"}}
                 :xtdb/tx-log               {:xtdb/module     'xtdb.jdbc/->tx-log
                                             :connection-pool :xtdb.jdbc/connection-pool}
                 :xtdb/document-store       {:xtdb/module     'xtdb.jdbc/->document-store
                                             :connection-pool :xtdb.jdbc/connection-pool}
                 }))

;XTDB的记录是包含唯一的:xt/id的edn结构的文档
(def manifest
  {:xt/id       :manifest
   :pilot-name  "Johanna"
   :id/rocket   "SB002-sol"
   :id/employee "22910x2"
   :badges      "SETUP"
   :cargo       ["stereo" "gold fish" "slippers" "secret note"]})

;存入操作
(xt/submit-tx node [[::xt/put manifest]])
;:xtdb.api{:tx-id 0, :tx-time #inst"2023-05-14T15:21:22.566-00:00"}

;确保事务已经同步到这个节点
(xt/sync node)

;使用:xtdb/id进行整条记录的查询操作
(xt/entity (xt/db node) :manifest)
;{:pilot-name "Johanna",
; :id/rocket "SB002-sol",
; :id/employee "22910x2",
; :badges "SETUP",
; :cargo ["stereo" "gold fish" "slippers" "secret note"],
; :xt/id :manifest}

;put操作
(comment
  (xt/submit-tx
    node
    [[::xt/put
      {:xt/id       :commodity/Pu
       :common-name "Plutonium"
       :type        :element/metal
       :density     19.816
       :radioactive true}]
     [::xt/put
      {:xt/id       :commodity/N
       :common-name "Nitrogen"
       :type        :element/gas
       :density     1.2506
       :radioactive false}]
     [::xt/put
      {:xt/id       :commodity/CH4
       :common-name "Methane"
       :type        :molecule/gas
       :density     0.717
       :radioactive false}]])

  (xt/sync node)

  ;可以为同一个:xt/id的记录加上时间，这样可以track这个记录随时间的变化
  (xt/submit-tx
    node
    [[::xt/put
      {:xt/id      :stock/Pu
       :commod     :commodity/Pu
       :weight-ton 21}
      #inst "2115-02-13T18"]

     [::xt/put
      {:xt/id      :stock/Pu
       :commod     :commodity/Pu
       :weight-ton 23}
      #inst "2115-02-14T18"]

     [::xt/put
      {:xt/id      :stock/Pu
       :commod     :commodity/Pu
       :weight-ton 22.2}
      #inst "2115-02-15T18"]

     [::xt/put
      {:xt/id      :stock/Pu
       :commod     :commodity/Pu
       :weight-ton 24}
      #inst "2115-02-18T18"]

     [::xt/put
      {:xt/id      :stock/Pu
       :commod     :commodity/Pu
       :weight-ton 24.9}
      #inst "2115-02-19T18"]])

  (xt/sync node)

  ;查询也需要带上时间，结构是该条记录在这个时间点之前最近的一个版本
  (xt/entity (xt/db node #inst "2115-02-14") :stock/Pu)

  (xt/entity (xt/db node #inst "2115-02-18") :stock/Pu)
  )

;一个方便批量插入的函数
(defn easy-ingest
  "Uses XTDB put transaction to add a vector of documents to a specified
  node"
  [node docs]
  (xt/submit-tx node
                (vec (for [doc docs]
                       [::xt/put doc])))
  (xt/sync node))

(comment
  (xt/submit-tx
    node
    [[::xt/put
      {:xt/id       :manifest
       :pilot-name  "Johanna"
       :id/rocket   "SB002-sol"
       :id/employee "22910x2"
       :badges      ["SETUP" "PUT"]
       :cargo       ["stereo" "gold fish" "slippers" "secret note"]}]])

  (xt/sync node)
  )

;查询这个记录所有的历史版本
(xt/entity-history (xt/db node) :manifest :desc)

(def data
  [{:xt/id       :commodity/Pu
    :common-name "Plutonium"
    :type        :element/metal
    :density     19.816
    :radioactive true}

   {:xt/id       :commodity/N
    :common-name "Nitrogen"
    :type        :element/gas
    :density     1.2506
    :radioactive false}

   {:xt/id       :commodity/CH4
    :common-name "Methane"
    :type        :molecule/gas
    :density     0.717
    :radioactive false}

   {:xt/id       :commodity/Au
    :common-name "Gold"
    :type        :element/metal
    :density     19.300
    :radioactive false}

   {:xt/id       :commodity/C
    :common-name "Carbon"
    :type        :element/non-metal
    :density     2.267
    :radioactive false}

   {:xt/id       :commodity/borax
    :common-name "Borax"
    :IUPAC-name  "Sodium tetraborate decahydrate"
    :other-names ["Borax decahydrate" "sodium borate" "sodium tetraborate" "disodium tetraborate"]
    :type        :mineral/solid
    :appearance  "white solid"
    :density     1.73
    :radioactive false}])

;find操作
(comment
  (easy-ingest node data)

  ;查询的参数是一个map，由clause和variable组成，:find对应是clause，:where对应的是variable
  ;XTDB中的索引的存储形式是[entity attribute value]，在查询的:where中也是以这个形式定义查询条件的
  ;其中的不在上下文中的symbol可以理解为占位符，可以匹配任意的值
  (xt/q (xt/db node)
        '{:find  [element]
          :where [[element :type :element/metal]]})
  ;#{[:commodity/Pu] [:commodity/Au]}

  ;clause需要被quote，quote可以作用于整个查询参数map，也可以只是:find对应的vector
  (=
    (xt/q (xt/db node)
          '{:find  [element]
            :where [[element :type :element/metal]]})

    (xt/q (xt/db node)
          {:find  '[element]
           :where '[[element :type :element/metal]]})

    (xt/q (xt/db node)
          (quote
            {:find  [element]
             :where [[element :type :element/metal]]})))

  ;查询所有:type为:element/meta,且具有:common-name字段的记录,并返回其:common-name字段的值
  (xt/q (xt/db node)
        '{:find  [name]
          :where [[e :type :element/metal]
                  [e :common-name name]]})
  ;#{["Gold"] ["Plutonium"]}

  ;查询包含多个字段的记录，返回这些字段的值
  (xt/q (xt/db node)
        '{:find  [name rho]
          :where [[e :density rho]
                  [e :common-name name]]})
  ; #{["Nitrogen" 1.2506] ["Carbon" 2.267] ["Methane" 0.717] ["Borax" 1.73] ["Gold" 19.3] ["Plutonium" 19.816]}

  ;在查询是传入参数，使用:in，它的值是一个在:where中出现过的某些占位符的vector，参数作为xt/q的第三个参数传入
  (xt/q (xt/db node)
        '{:find  [name]
          :where [[e :type type]
                  [e :common-name name]]
          :in    [type]}
        :element/metal)
  ;#{["Gold"] ["Plutonium"]}
  )

(defn filter-type
  [type]
  (xt/q (xt/db node)
        '{:find  [name]
          :where [[e :common-name name]
                  [e :type type]]
          :in    [type]}
        type))

(defn filter-appearance
  [description]
  (xt/q (xt/db node)
        '{:find  [name IUPAC]
          :where [[e :common-name name]
                  [e :IUPAC-name IUPAC]
                  [e :appearance appearance]]
          :in    [appearance]}
        description))

(filter-type :element/metal)

(filter-appearance "white solid")

(comment
  (xt/submit-tx
    node
    [[::xt/put
      {:xt/id       :manifest
       :pilot-name  "Johanna"
       :id/rocket   "SB002-sol"
       :id/employee "22910x2"
       :badges      ["SETUP" "PUT" "DATALOG-QUERIES"]
       :cargo       ["stereo" "gold fish" "slippers" "secret note"]}]])

  (xt/sync node)
  )

;Bitemporality，即XTDB的记录存在两种时间，一个是transaction time，指的是存入XTDB的时间
;另一种是valid time，可以认为是这条记录实际产生的时间，需要在put的时候指定，valid time有start和end两个
;一个:xtdb/id可以有多个valid time，可以认为是这条记录的多个版本
(comment
  (xt/submit-tx
    node
    [[::xt/put
      {:xt/id       :consumer/RJ29sUU
       :consumer-id :RJ29sUU
       :first-name  "Jay"
       :last-name   "Rose"
       :cover?      true
       :cover-type  :Full}
      #inst "2114-12-03"]])

  (xt/sync node)

  (xt/submit-tx
    node
    [[::xt/put
      {:xt/id       :consumer/RJ29sUU
       :consumer-id :RJ29sUU
       :first-name  "Jay"
       :last-name   "Rose"
       :cover?      true
       :cover-type  :Full}
      #inst "2113-12-03"
      #inst "2114-12-03"]

     [::xt/put
      {:xt/id       :consumer/RJ29sUU
       :consumer-id :RJ29sUU
       :first-name  "Jay"
       :last-name   "Rose"
       :cover?      true
       :cover-type  :Full}
      #inst "2112-12-03"
      #inst "2113-12-03"]

     [::xt/put
      {:xt/id       :consumer/RJ29sUU
       :consumer-id :RJ29sUU
       :first-name  "Jay"
       :last-name   "Rose"
       :cover?      false}
      #inst "2112-06-03"
      #inst "2112-12-02"]

     [::xt/put
      {:xt/id       :consumer/RJ29sUU
       :consumer-id :RJ29sUU
       :first-name  "Jay"
       :last-name   "Rose"
       :cover?      true
       :cover-type  :Promotional}
      #inst "2111-06-03"
      #inst "2112-06-03"]])

  (xt/sync node)

  (xt/q (xt/db node #inst "2114-01-01")
        '{:find  [cover type]
          :where [[e :consumer-id :RJ29sUU]
                  [e :cover? cover]
                  [e :cover-type type]]})
  ;#{[true :Full]}

  (xt/q (xt/db node #inst "2111-07-03")
        '{:find  [cover type]
          :where [[e :consumer-id :RJ29sUU]
                  [e :cover? cover]
                  [e :cover-type type]]})
  ;#{[true :Promotional]}

  (xt/q (xt/db node #inst "2112-07-03")
        '{:find  [cover type]
          :where [[e :consumer-id :RJ29sUU]
                  [e :cover? cover]
                  [e :cover-type type]]})
  ;#{}
  )
(comment
  (xt/submit-tx
    node
    [[::xt/put
      {:xt/id       :manifest
       :pilot-name  "Johanna"
       :id/rocket   "SB002-sol"
       :id/employee "22910x2"
       :badges      ["SETUP" "PUT" "DATALOG-QUERIES" "BITEMP"]
       :cargo       ["stereo" "gold fish" "slippers" "secret note"]}]])

  (xt/sync node)
  )

(defn stock-check
  [company-id item]
  {:result (xt/q (xt/db node)
                 {:find  '[name funds stock]
                  :where ['[e :company-name name]
                          '[e :credits funds]
                          ['e item 'stock]]
                  :in    '[e]}
                 company-id)
   :item   item})

(defn format-stock-check
  [{:keys [result item] :as stock-check}]
  (for [[name funds commod] result]
    (str "Name: " name ", Funds: " funds ", " item " " commod)))

(def data
  [{:xt/id        :gold-harmony
    :company-name "Gold Harmony"
    :seller?      true
    :buyer?       false
    :units/Au     10211
    :credits      51}

   {:xt/id        :tombaugh-resources
    :company-name "Tombaugh Resources Ltd."
    :seller?      true
    :buyer?       false
    :units/Pu     50
    :units/N      3
    :units/CH4    92
    :credits      51}

   {:xt/id        :encompass-trade
    :company-name "Encompass Trade"
    :seller?      true
    :buyer?       true
    :units/Au     10
    :units/Pu     5
    :units/CH4    211
    :credits      1002}

   {:xt/id        :blue-energy
    :seller?      false
    :buyer?       true
    :company-name "Blue Energy"
    :credits      1000}])

;match操作，接收一个id和一个预期的记录
;如果当前数据库中的的记录不符合预期，则停止事务。如果预期是当前不存在这个记录，则传入nil
(comment

  (easy-ingest node data)

  (xt/submit-tx
    node
    [[::xt/match
      :blue-energy
      {:xt/id        :blue-energy
       :seller?      false
       :buyer?       true
       :company-name "Blue Energy"
       :credits      1000}]
     [::xt/put
      {:xt/id        :blue-energy
       :seller?      false
       :buyer?       true
       :company-name "Blue Energy"
       :credits      900
       :units/CH4    10}]

     [::xt/match
      :tombaugh-resources
      {:xt/id        :tombaugh-resources
       :company-name "Tombaugh Resources Ltd."
       :seller?      true
       :buyer?       false
       :units/Pu     50
       :units/N      3
       :units/CH4    92
       :credits      51}]
     [::xt/put
      {:xt/id        :tombaugh-resources
       :company-name "Tombaugh Resources Ltd."
       :seller?      true
       :buyer?       false
       :units/Pu     50
       :units/N      3
       :units/CH4    82
       :credits      151}]])

  (xt/sync node)

  (format-stock-check (stock-check :tombaugh-resources :units/CH4))
  ;("Name: Blue Energy, Funds: 900, :units/CH4 10")

  (format-stock-check (stock-check :blue-energy :units/CH4))
  ;{:result #{["Tombaugh Resources Ltd." 151 82]}, :item :units/CH4}

  (xt/submit-tx
    node
    [[::xt/match
      :gold-harmony
      {:xt/id        :gold-harmony
       :company-name "Gold Harmony"
       :seller?      true
       :buyer?       false
       :units/Au     10211
       :credits      51}]
     [::xt/put
      {:xt/id        :gold-harmony
       :company-name "Gold Harmony"
       :seller?      true
       :buyer?       false
       :units/Au     211
       :credits      51}]

     [::xt/match
      :encompass-trade
      {:xt/id        :encompass-trade
       :company-name "Encompass Trade"
       :seller?      true
       :buyer?       true
       :units/Au     10
       :units/Pu     5
       :units/CH4    211
       :credits      100002}]
     [::xt/put
      {:xt/id        :encompass-trade
       :company-name "Encompass Trade"
       :seller?      true
       :buyer?       true
       :units/Au     10010
       :units/Pu     5
       :units/CH4    211
       :credits      1002}]])

  (xt/sync node)

  (format-stock-check (stock-check :gold-harmony :units/Au))
  ;("Name: Gold Harmony, Funds: 51, :units/Au 10211")

  (format-stock-check (stock-check :encompass-trade :units/Au))
  ;("Name: Encompass Trade, Funds: 1002, :units/Au 10")
  )

(comment
  (xt/submit-tx
    node
    [[::xt/put
      {:xt/id       :manifest
       :pilot-name  "Johanna"
       :id/rocket   "SB002-sol"
       :id/employee "22910x2"
       :badges      ["SETUP" "PUT" "DATALOG-QUERIES" "BITEMP" "MATCH"]
       :cargo       ["stereo" "gold fish" "slippers" "secret note"]}]])

  (xt/sync node)
  )


(xt/q (xt/db node)
      '{:find  [belongings]
        :where [[e :cargo belongings]]
        :in    [belongings]}
      "secret note")
;#{["secret note"]}

;delete操作
(comment
  (xt/submit-tx node
                [[::xt/put {:xt/id   :kaarlang/clients
                            :clients [:encompass-trade]}
                  #inst "2110-01-01T09"
                  #inst "2111-01-01T09"]

                 [::xt/put {:xt/id   :kaarlang/clients
                            :clients [:encompass-trade :blue-energy]}
                  #inst "2111-01-01T09"
                  #inst "2113-01-01T09"]

                 [::xt/put {:xt/id   :kaarlang/clients
                            :clients [:blue-energy]}
                  #inst "2113-01-01T09"
                  #inst "2114-01-01T09"]

                 [::xt/put {:xt/id   :kaarlang/clients
                            :clients [:blue-energy :gold-harmony :tombaugh-resources]}
                  #inst "2114-01-01T09"
                  #inst "2115-01-01T09"]])

  (xt/sync node)

  (xt/entity-history
    (xt/db node #inst "2116-01-01T09")
    :kaarlang/clients
    ;按照valid time倒序排列
    :desc
    ;把当时的记录放在每一条历史信息中
    {:with-docs? true}
    )

  (xt/submit-tx
    node
    [[::xt/delete :kaarlang/clients #inst "2110-01-01" #inst "2116-01-01"]])

  (xt/sync node)

  (xt/entity-history
    (xt/db node #inst "2115-01-01T08")
    :kaarlang/clients
    :desc
    {:with-docs? true})

  ;delete操作是软删除，所以还是可以查到被删除的记录
  (xt/entity-history
    (xt/db node #inst "2115-01-01T08")
    :kaarlang/clients
    :desc
    {:with-docs?        true
     ;根据valid time和tx-id排序，由于put和delete不是发生在一个事务中，所以可以看到被删除的数据
     :with-corrections? true})
  )


;查询存储在XTDB中的所有东西
(defn full-query
  [node]
  (xt/q
    (xt/db node)
    '{:find  [(pull e [*])]
      :where [[e :xt/id id]]}))

(full-query node)

;evict操作，可以删除entity的所有的document store，但transaction log由于是不可变的，所以还是会保留
(comment
  (xt/submit-tx node
                [[::xt/put
                  {:xt/id         :person/kaarlang
                   :full-name     "Kaarlang"
                   :origin-planet "Mars"
                   :identity-tag  :KA01299242093
                   :DOB           #inst "2040-11-23"}]

                 [::xt/put
                  {:xt/id         :person/ilex
                   :full-name     "Ilex Jefferson"
                   :origin-planet "Venus"
                   :identity-tag  :IJ01222212454
                   :DOB           #inst "2061-02-17"}]

                 [::xt/put
                  {:xt/id        :person/thadd
                   :full-name    "Thad Christover"
                   :origin-moon  "Titan"
                   :identity-tag :IJ01222212454
                   :DOB          #inst "2101-01-01"}]

                 [::xt/put
                  {:xt/id         :person/johanna
                   :full-name     "Johanna"
                   :origin-planet "Earth"
                   :identity-tag  :JA012992129120
                   :DOB           #inst "2090-12-07"}]])

  (xt/sync node)

  (xt/q (xt/db node)
        '{:find  [e DOB]
          :where [[e :DOB DOB]]})
  ;#{[:person/ilex #inst"2061-02-17T00:00:00.000-00:00"]
  ;  [:person/thadd #inst"2101-01-01T00:00:00.000-00:00"]
  ;  [:person/johanna #inst"2090-12-07T00:00:00.000-00:00"]
  ;  [:person/kaarlang #inst"2040-11-23T00:00:00.000-00:00"]}

  (xt/submit-tx node [[::xt/evict :person/kaarlang]])
  (xt/sync node)

  (xt/q (xt/db node)
        '{:find  [e DOB]
          :where [[e :DOB DOB]]})
  ;#{[:person/ilex #inst"2061-02-17T00:00:00.000-00:00"]
  ;  [:person/thadd #inst"2101-01-01T00:00:00.000-00:00"]
  ;  [:person/johanna #inst"2090-12-07T00:00:00.000-00:00"]}

  (xt/entity-history (xt/db node)
                     :person/kaarlang
                     :desc
                     {:with-docs? true})

  )


;await-tx，阻塞当前线程直到该事务在节点已建立索引
(comment
  (def stats
    [{:body    "Sun"
      :type    "Star"
      :units   {:radius  "Earth Radius"
                :volume  "Earth Volume"
                :mass    "Earth Mass"
                :gravity "Standard gravity (g)"}
      :radius  109.3
      :volume  1305700
      :mass    33000
      :gravity 27.9
      :xt/id   :Sun}
     {:body    "Jupiter"
      :type    "Gas Giant"
      :units   {:radius  "Earth Radius"
                :volume  "Earth Volume"
                :mass    "Earth Mass"
                :gravity "Standard gravity (g)"}
      :radius  10.97
      :volume  1321
      :mass    317.83
      :gravity 2.52
      :xt/id   :Jupiter}
     {:body   "Saturn"
      :type   "Gas Giant"
      :units  {:radius  "Earth Radius"
               :volume  "Earth Volume"
               :mass    "Earth Mass"
               :gravity "Standard gravity (g)"}
      :radius :volume
      :mass   :gravity
      :xt/id  :Saturn}
     {:body    "Saturn"
      :units   {:radius  "Earth Radius"
                :volume  "Earth Volume"
                :mass    "Earth Mass"
                :gravity "Standard gravity (g)"}
      :radius  9.14
      :volume  764
      :mass    95.162
      :gravity 1.065
      :type    "planet"
      :xt/id   :Saturn}
     {:body    "Uranus"
      :units   {:radius  "Earth Radius"
                :volume  "Earth Volume"
                :mass    "Earth Mass"
                :gravity "Standard gravity (g)"}
      :radius  3.981
      :volume  63.1
      :mass    14.536
      :gravity 0.886
      :type    "planet"
      :xt/id   :Uranus}
     {:body    "Neptune"
      :units   {:radius  "Earth Radius"
                :volume  "Earth Volume"
                :mass    "Earth Mass"
                :gravity "Standard gravity (g)"}
      :radius  3.865
      :volume  57.7
      :mass    17.147
      :gravity 1.137
      :type    "planet"
      :xt/id   :Neptune}
     {:body    "Earth"
      :units   {:radius  "Earth Radius"
                :volume  "Earth Volume"
                :mass    "Earth Mass"
                :gravity "Standard gravity (g)"}
      :radius  1
      :volume  1
      :mass    1
      :gravity 1
      :type    "planet"
      :xt/id   :Earth}
     {:body    "Venus"
      :units   {:radius  "Earth Radius"
                :volume  "Earth Volume"
                :mass    "Earth Mass"
                :gravity "Standard gravity (g)"}
      :radius  0.9499
      :volume  0.857
      :mass    0.815
      :gravity 0.905
      :type    "planet"
      :xt/id   :Venus}
     {:body    "Mars"
      :units   {:radius  "Earth Radius"
                :volume  "Earth Volume"
                :mass    "Earth Mass"
                :gravity "Standard gravity (g)"}
      :radius  0.532
      :volume  0.151
      :mass    0.107
      :gravity 0.379
      :type    "planet"
      :xt/id   :Mars}
     {:body    "Ganymede"
      :units   {:radius  "Earth Radius"
                :volume  "Earth Volume"
                :mass    "Earth Mass"
                :gravity "Standard gravity (g)"}
      :radius  0.4135
      :volume  0.0704
      :mass    0.0248
      :gravity 0.146
      :type    "moon"
      :xt/id   :Ganymede}
     {:body    "Titan"
      :units   {:radius  "Earth Radius"
                :volume  "Earth Volume"
                :mass    "Earth Mass"
                :gravity "Standard gravity (g)"}
      :radius  0.4037
      :volume  0.0658
      :mass    0.0225
      :gravity 0.138
      :type    "moon"
      :xt/id   :Titan}
     {:body    "Mercury"
      :units   {:radius  "Earth Radius"
                :volume  "Earth Volume"
                :mass    "Earth Mass"
                :gravity "Standard gravity (g)"}
      :radius  0.3829
      :volume  0.0562
      :mass    0.0553
      :gravity 0.377
      :type    "planet"
      :xt/id   :Mercury}])

  (xt/submit-tx node (mapv (fn [stat] [::xt/put stat]) stats))
  ;#:xtdb.api{:tx-id 117, :tx-time #inst"2023-05-19T10:38:29.140-00:00"}

  (xt/sync node)
  ;#inst"2023-05-19T10:38:29.140-00:00"

  (xt/submit-tx
    node
    [[::xt/put
      {:body    "Kepra-5"
       :units   {:radius  "Earth Radius"
                 :volume  "Earth Volume"
                 :mass    "Earth Mass"
                 :gravity "Standard gravity (g)"}
       :radius  0.6729
       :volume  0.4562
       :mass    0.5653
       :gravity 1.4
       :type    "planet"
       :xt/id   :Kepra-5}]])

  (xt/sync node)

  (sort
    (xt/q
      (xt/db node)
      '{:find  [g planet]
        :where [[planet :gravity g]]}))
  ;([0.138 :Titan]
  ; [0.146 :Ganymede]
  ; [0.377 :Mercury]
  ; [0.379 :Mars]
  ; [0.886 :Uranus]
  ; [0.905 :Venus]
  ; [1 :Earth]
  ; [1.065 :Saturn]
  ; [1.137 :Neptune]
  ; [1.4 :Kepra-5]
  ; [2.52 :Jupiter]
  ; [27.9 :Sun])
  )

(defn ingest-and-query
  [traveller-doc]
  (xt/submit-tx node [[::xt/put traveller-doc]])
  (xt/q
    (xt/db node)
    '{:find  [n]
      :where [[id :passport-number n]]
      :in    [id]}
    (:xt/id traveller-doc)))

;第一次执行ingest-and-query无法查到插入的数据，必须执行两次，
;这是因为第一次执行的时候插入的数据还没有实际同步到节点，导致查询不到数据，本质上是因为XTDB默认是异步执行事务的
(comment
  (ingest-and-query
    {:xt/id           :origin-planet/test-traveller
     :chosen-name     "Test"
     :given-name      "Test Traveller"
     :passport-number (java.util.UUID/randomUUID)
     :stamps          []
     :penalties       []})
  ;#{}

  (ingest-and-query
    {:xt/id           :origin-planet/test-traveller
     :chosen-name     "Test"
     :given-name      "Test Traveller"
     :passport-number (java.util.UUID/randomUUID)
     :stamps          []
     :penalties       []})
  ;#{[#uuid"3bca2944-9522-4e05-aa20-dcdb9d9e8919"]}
  )

;使用await-tx重新实现ingest-and-query
(defn ingest-and-query
  [traveller-doc]
  (xt/await-tx node
               (xt/submit-tx node [[::xt/put traveller-doc]]))
  (xt/q
    (xt/db node)
    '{:find  [n]
      :where [[id :passport-number n]]
      :in    [id]}
    (:xt/id traveller-doc)))

(comment
  (ingest-and-query
    {:xt/id :origin-planet/new-test-traveller
     :chosen-name "Testy"
     :given-name "Test Traveller"
     :passport-number (java.util.UUID/randomUUID)
     :stamps []
     :penalties []})
  ;#{[#uuid"9c008ec1-6e6f-4a83-be50-d96ad623fdf1"]}
  )
