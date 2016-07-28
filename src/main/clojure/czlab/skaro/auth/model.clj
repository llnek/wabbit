;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.

(ns ^{:doc ""
      :author "Kenneth Leung" }

  czlab.skaro.auth.model

  (:require
    [czlab.xlib.files :refer [spitUTF8]]
    [czlab.xlib.resources :refer [rstr]]
    [czlab.xlib.str :refer [toKW]]
    [czlab.xlib.logging :as log])

  (:use
    [czlab.dbddl.drivers]
    [czlab.dbio.core]
    [czlab.dbddl.postgresql]
    [czlab.dbddl.h2]
    [czlab.dbddl.mysql]
    [czlab.dbddl.sqlserver]
    [czlab.dbddl.oracle])

  (:import
    [czlab.dbio JDBCInfo JDBCPool Schema]
    [java.sql Connection]
    [java.io File]
    [czlab.xlib I18N]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:dynamic *auth-mcache*
  (dbschema<>
    (dbmodel<> StdAddress
      (dbfields
        {:addr1 {:size 255 :null false }
         :addr2 {}
         :city {:null false}
         :state {:null false}
         :zip {:null false}
         :country {:null false} })
      (dbindexes
        {:i1 #{:city :state :country}
         :i2 #{:zip :country}
         :state #{:state}
         :zip #{:zip} }))
    (dbmodel<> AuthRole
      (dbfields
        {:name {:column "role_name" :null false }
         :desc {:column "description" :null false } })
      (dbuniques
        {:u1 #{:name} }))
    (dbmodel<> LoginAccount
      (dbfields
        {:acctid {:null false }
         :email {:size 128 }
          ;;:salt { :size 128 }
         :passwd {:null false :domain :Password } })
      (dbassocs
        {:addr {:kind :O2O
                :cascade true
                :other ::StdAddress } })
      (dbuniques
        {:u2 #{:acctid} }))
    (dbjoined<> AccountRoles LoginAccount AuthRole)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn genAuthPluginDDL

  "Generate db ddl for the auth-plugin"
  ^String
  [spec]

  (if (contains? *DBTYPES* spec)
    (getDDL *auth-mcache* spec)
    (dberr! (rstr (I18N/getBase)
                  "db.unknown"
                  (name spec)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol PluginDDL

  "Upload the auth-plugin ddl to db"

  (applyDDL [_]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(extend-protocol PluginDDL

  JDBCInfo
  (applyDDL [this]
    (when-some [t (matchUrl (.url this))]
      (with-open [c (dbconnect<> this)]
        (uploadDdl c (genAuthPluginDDL t)))))

  JDBCPool
  (applyDDL [this]
    (when-some [t (matchUrl (.dbUrl this))]
      (uploadDdl this (genAuthPluginDDL t)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn exportAuthPluginDDL

  "Output the auth-plugin ddl to file"
  [spec file]

  (spitUTF8 file (genAuthPluginDDL spec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

