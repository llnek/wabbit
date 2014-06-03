;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{ :doc ""
       :author "kenl" }

  cmzlabsclj.nucleus.dbio.composite

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabsclj.nucleus.util.core :only [test-nonil notnil? Try!] ])
  (:use [cmzlabsclj.nucleus.util.str :only [hgl?] ])
  (:require  [cmzlabsclj.nucleus.dbio.core :as dbcore :only [ese] ])
  (:require [cmzlabsclj.nucleus.dbio.sql :as dbsql ])

  (:import (com.zotohlab.frwk.dbio Transactable SQLr MetaCache DBAPI))
  (:import (java.sql Connection)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mk-tx ""

  ^SQLr
  [^MetaCache metaCache
   ^DBAPI db
   ^cmzlabsclj.nucleus.dbio.sql.SQLProcAPI proc
   ^Connection conn]

  (let [ metas (.getMetas metaCache) ]
    (reify SQLr

      (getMetaCache [_] metaCache)

      (findAll [this model extra] (.findSome this model {} extra))
      (findAll [this model] (.findAll this model ""))

      (findOne [this model filters]
        (let [ rset (.findSome this model filters "") ]
          (if (empty? rset) nil (first rset))))

      (findSome [this model filters] (.findSome this model filters ""))
      (findSome [_ model filters extraSQL]
        (let [ zm (metas model)
               [wc pms] (dbsql/SqlFilterClause zm filters)
               tbl (dbsql/Tablename zm)
               s (str "SELECT * FROM " (dbcore/ese tbl))
               extra (if (hgl? extraSQL) extraSQL "") ]
          (if (hgl? wc)
            (.doQuery proc conn (str s " WHERE " wc " " extra) pms model)
            (.doQuery proc conn (str s " " extra) [] model))) )

      (select [_ model sql params] (.doQuery proc conn sql params model) )
      (select [_ sql params] (.doQuery proc conn sql params) )

      (update [_ obj] (.doUpdate proc conn obj) )
      (delete [_ obj] (.doDelete proc conn obj) )
      (insert [_ obj] (.doInsert proc conn obj) )

      (executeWithOutput [_ sql pms]
        (.doExecuteWithOutput proc conn sql pms { :pkey "DBIO_ROWID" } ) )

      (execute [_ sql pms] (.doExecute proc conn sql pms) )

      (countAll [_ model] (.doCount proc conn model) )
      (purge [_ model] (.doPurge proc conn model) )
  )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CompositeSQLr ""

  ^Transactable
  [ ^MetaCache metaCache ^DBAPI db ]

  (let [ proc (dbsql/MakeProc metaCache db) ]
    (test-nonil "meta-cache" metaCache)
    (test-nonil "sql-proc!" proc)
    (test-nonil "dbapi" db)
    (reify Transactable

      (execWith [this func]
        (with-local-vars [ rc nil ]
          (with-open [ conn (.begin this) ]
            (test-nonil "sql-connection" conn)
            (try
              (let [ tx (mk-tx metaCache db proc conn)
                     r (func tx) ]
                (var-set rc r)
                (.commit this conn)
                @rc)
              (catch Throwable e#
                (do (.rollback this conn) (log/warn e# "") (throw e#))) ))))

      (rollback [_ conn] (Try! (.rollback ^Connection conn)))
      (commit [_ conn] (.commit ^Connection conn))

      (begin [_]
        (let [ ^Connection conn (.open db) ]
          (.setAutoCommit conn false)
          ;;(.setTransactionIsolation conn Connection/TRANSACTION_READ_COMMITTED)
          (.setTransactionIsolation conn Connection/TRANSACTION_SERIALIZABLE)
          conn))
  )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private composite-eof nil)

