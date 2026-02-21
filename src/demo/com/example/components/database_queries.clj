(ns com.example.components.database-queries
  "Database query functions used by model resolvers."
  (:require
   [com.fulcrologic.rad.database-adapters.datomic-options :as do]
   [datomic.client.api :as d]
   [taoensso.timbre :as log]))

(defn- env->db
  "Extract the Datomic db value for the :production schema from the pathom env."
  [env]
  (some-> env (get-in [do/databases :production]) deref))

(defn get-all-accounts
  "Return all account IDs, optionally including inactive accounts when
   `:show-inactive?` is set in `query-params`."
  [env query-params]
  (if-let [db (env->db env)]
    (let [ids (if (:show-inactive? query-params)
                (d/q '[:find ?uuid
                       :where
                       [?dbid :account/id ?uuid]] db)
                (d/q '[:find ?uuid
                       :where
                       [?dbid :account/active? true]
                       [?dbid :account/id ?uuid]] db))]
      (mapv (fn [[id]] {:account/id id}) ids))
    (log/error "No database atom for production schema!")))

(defn get-customer-invoices
  "Return invoice IDs for a given customer account."
  [env {:account/keys [id]}]
  (if-let [db (env->db env)]
    (let [ids (d/q '[:find ?uuid
                     :in $ ?cid
                     :where
                     [?dbid :invoice/id ?uuid]
                     [?dbid :invoice/customer ?c]
                     [?c :account/id ?cid]] db id)]
      (mapv (fn [[id]] {:invoice/id id}) ids))
    (log/error "No database atom for production schema!")))

(defn get-all-invoices
  "Return all invoice IDs."
  [env _query-params]
  (if-let [db (env->db env)]
    (let [ids (d/q '[:find ?uuid
                     :where
                     [?dbid :invoice/id ?uuid]] db)]
      (mapv (fn [[id]] {:invoice/id id}) ids))
    (log/error "No database atom for production schema!")))

(defn get-invoice-customer-id
  "Return the account UUID for the customer on a given invoice."
  [env invoice-id]
  (if-let [db (env->db env)]
    (ffirst
     (d/q '[:find ?account-uuid
            :in $ ?invoice-uuid
            :where
            [?i :invoice/id ?invoice-uuid]
            [?i :invoice/customer ?c]
            [?c :account/id ?account-uuid]] db invoice-id))
    (log/error "No database atom for production schema!")))

(defn get-all-items
  "Return all item IDs, optionally filtered by category."
  [env {:category/keys [id]}]
  (if-let [db (env->db env)]
    (let [ids (if id
                (d/q '[:find ?uuid
                       :in $ ?catid
                       :where
                       [?c :category/id ?catid]
                       [?i :item/category ?c]
                       [?i :item/id ?uuid]] db id)
                (d/q '[:find ?uuid
                       :where
                       [_ :item/id ?uuid]] db))]
      (mapv (fn [[id]] {:item/id id}) ids))
    (log/error "No database atom for production schema!")))

(defn get-all-categories
  "Return all category IDs."
  [env _query-params]
  (if-let [db (env->db env)]
    (let [ids (d/q '[:find ?id
                     :where
                     [?e :category/label]
                     [?e :category/id ?id]] db)]
      (mapv (fn [[id]] {:category/id id}) ids))
    (log/error "No database atom for production schema!")))

(defn get-line-item-category
  "Return the category UUID for a given line item's item."
  [env line-item-id]
  (if-let [db (env->db env)]
    (ffirst
     (d/q '[:find ?cid
            :in $ ?line-item-id
            :where
            [?e :line-item/id ?line-item-id]
            [?e :line-item/item ?item]
            [?item :item/category ?c]
            [?c :category/id ?cid]] db line-item-id))
    (log/error "No database atom for production schema!")))
