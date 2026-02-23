(ns com.example.model.seed
  (:require
    [com.fulcrologic.rad.attributes :as attr]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.type-support.decimal :as math]))

(defn new-account
  "Seed helper. Uses `name` as db/id (tempid)."
  [id name email password & {:as extras}]
  #?(:clj
     (let [salt (attr/gen-salt)]
       (merge
         {:db/id                 name
          :account/id            id
          :account/email         email
          :account/name          name
          :password/hashed-value (attr/encrypt password salt 100)
          :password/salt         salt
          :password/iterations   100
          :account/role          :account.role/user
          :account/active?       true}
         extras))
     :cljs
     (merge
       {:account/id      id
        :account/email   email
        :account/name    name
        :account/role    :account.role/user
        :account/active? true}
       extras)))

(defn new-address
  "Seed helper. Uses `street` as db/id for tempid purposes."
  [id street & {:as extras}]
  (merge
    {:db/id          street
     :address/id     id
     :address/street street
     :address/city   "Sacramento"
     :address/state  :address.state/CA
     :address/zip    "99999"}
    extras))

(defn new-category
  "Seed helper. Uses `label` for tempid purposes."
  [id label & {:as extras}]
  (merge
    {:db/id          label
     :category/id    id
     :category/label label}
    extras))

(defn new-item
  "Seed helper. Uses `name` as db/id for tempid purposes."
  [id name price & {:as extras}]
  (merge
    {:db/id      name
     :item/id    id
     :item/name  name
     :item/price (math/numeric price)}
    extras))

(defn new-line-item
  "Seed helper for line items."
  [item quantity price & {:as extras}]
  (let [id (get extras :line-item/id (new-uuid))]
    (merge
      {:db/id                  (str id)
       :line-item/id           id
       :line-item/item         item
       :line-item/quantity     quantity
       :line-item/quoted-price (math/numeric price)
       :line-item/subtotal     (math/* quantity price)}
      extras)))

(defn new-invoice
  "Seed helper for invoices. Computes total from `line-items`."
  [str-id date customer line-items & {:as extras}]
  (merge
    {:db/id              str-id
     :invoice/id         (new-uuid)
     :invoice/customer   customer
     :invoice/line-items line-items
     :invoice/total      (reduce
                           (fn [total {:line-item/keys [subtotal]}]
                             (math/+ total subtotal))
                           (math/zero)
                           line-items)
     :invoice/date       date}
    extras))
