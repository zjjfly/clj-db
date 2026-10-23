;a database inspired by archaeology:https://aosabook.org/en/500L/an-archaeology-inspired-database.html
(ns db.archaeology.core)

;layers是entities的所有层次，top-id是下一个可用的唯一id，curr-time是这个Database最后更新的时间
(defrecord Database [layers top-id curr-time])

;storage是entities的存储，余下的是几种加速查询的索引
(defrecord Layer [storage VAET AVET VEAT EAVT])

;一个数据库由多个Database组成，每一个都代表了数据库在curr-time的一个快照
;一个Layer可能和另一个Layer有完全相同的entities，如果这些entities在这两个Layer相距的这段时间内没有变化

;entity由唯一的id和若干个属性
(defrecord Entity [id attrs])

(defn make-entity
  ([] (make-entity :db/no-id-yet))
  ([id] (Entity. id {})))

;属性有名称，值，最后更新时间以及之前的一次更新时间
(defrecord Attr [name value ts prev-ts])

;在Attr的元数据中还有这个属性的类型以及是单值的还是多值的
(defn make-attr
  ([name value type
    & {:keys [cardinality] :or {cardinality :db/single}}]
   {:pre [(contains? #{:db/single :db/multiple} cardinality)]}
   (with-meta (Attr. name value -1 -1) {:type type :cardinality cardinality})))

(defn add-attr [ent attr]
  (let [attr-id (keyword (:name attr))]
    (assoc-in ent [:attrs attr-id] attr)))

;存储的protocol，目前只实现最简单的基于内存的存储
(defprotocol Storage
  (get-entity [storage e-id])
  (write-entity [storage entity])
  (drop-entity [storage entity]))

(defrecord InMemory [] Storage
  (get-entity [storage e-id] (e-id storage))
  (write-entity [storage entity] (assoc storage (:id entity) entity))
  (drop-entity [storage entity] (dissoc storage (:id entity))))

;EVAT索引有三层：
;1.entity id
;2.属性名
;3.属性值
;每一个layer都有自己的索引，所以索引本身是和一个特定的时间相关的

;AVET也有三层：
;1.属性名
;2.属性值
;3.entity id

;其他的索引也是类似，是E，A，V这三个的不同的排列顺序

;索引的元数据有一个函数:from-eav来从EAV转为该种索引，还有一个函数:to-eav来把该种索引转为EAV
;还有一个函数:usage-pred来确定某个属性是否要存入索引
(defn make-index [from-eav to-eav usage-pred]
  (with-meta {} {:from-eav from-eav :to-eav to-eav :usage-pred usage-pred}))

(defn from-eav [index] (:from-eav (meta index)))
(defn to-eav [index] (:to-eav (meta index)))
(defn usage-pred [index] (:usage-pred (meta index)))

(defn indexes [] [:VAET :AVET :VEAT :EAVT])

;Database初始化
(defn ref? [attr] (= :db/ref (:type (meta attr))))

(defn always [& more] true)

(defn make-db []
  (atom
    (Database. [(Layer.
                  (InMemory.)                               ; storage
                  (make-index #(vector %3 %2 %1) #(vector %3 %2 %1) #(ref? %)) ;VAET
                  (make-index #(vector %2 %3 %1) #(vector %3 %1 %2) always) ;AVET
                  (make-index #(vector %3 %1 %2) #(vector %2 %3 %1) always) ;VEAT
                  (make-index #(vector %1 %2 %3) #(vector %1 %2 %3) always) ;EAVT
                  )] 0 0)))
