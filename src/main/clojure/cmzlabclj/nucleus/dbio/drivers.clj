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

  cmzlabclj.nucleus.dbio.drivers

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr]
            [cmzlabclj.nucleus.dbio.core :as dbcore])
  (:use [cmzlabclj.nucleus.util.str :only [hgl? AddDelim! nsb] ])
  (:import  [com.zotohlab.frwk.dbio MetaCache DBAPI DBIOError]
            [java.util Map HashMap]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getcolname ""

  ^String
  [flds fid]

  (let [^String c (:column (get flds fid)) ]
    (if (hgl? c) (cstr/upper-case c) c)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getNotNull  ""

  ^String
  [db]

  "NOT NULL")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getNull  ""

  ^String
  [db]

  "NULL")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetPad  ""

  ^String
  [db]

  "    ")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- nullClause ""

  [db opt?]

  (if opt? (getNull db) (getNotNull db)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genSep ""

  ^String
  [db]

  (if dbcore/*USE_DDL_SEP* dbcore/DDL_SEP ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GenCol ""

  ^String
  [fld]

  (cstr/upper-case ^String (:column fld)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti GenBegin (fn [a & more] a))
(defmulti GenExec (fn [a & more] a))
(defmulti GenDrop (fn [a & more] a))

(defmulti GenEndSQL (fn [a & more] a))
(defmulti GenGrant (fn [a & more] a))
(defmulti GenEnd (fn [a & more] a))

(defmulti GenAutoInteger (fn [a & more] a))
(defmulti GenDouble (fn [a & more] a))
(defmulti GenLong (fn [a & more] a))
(defmulti GenFloat (fn [a & more] a))
(defmulti GenAutoLong (fn [a & more] a))
(defmulti GetTSDefault (fn [a & more] a))
(defmulti GenTimestamp (fn [a & more] a))
(defmulti GenDate (fn [a & more] a))
(defmulti GenCal (fn [a & more] a))
(defmulti GenBool (fn [a & more] a))
(defmulti GenInteger (fn [a & more] a))

(defmulti GetFloatKeyword (fn [a & more] a))
(defmulti GetIntKeyword (fn [a & more] a))
(defmulti GetTSKeyword (fn [a & more] a))
(defmulti GetDateKeyword (fn [a & more] a))
(defmulti GetBoolKeyword (fn [a & more] a))
(defmulti GetLongKeyword (fn [a & more] a))
(defmulti GetDoubleKeyword (fn [a & more] a))
(defmulti GetStringKeyword (fn [a & more] a))
(defmulti GetBlobKeyword (fn [a & more] a))
(defmulti GenBytes (fn [a & more] a))
(defmulti GenString (fn [a & more] a))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenExec :default

  ^String
  [db]
  (str ";\n" (genSep db)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenDrop :default

  ^String
  [db table]

  (str "DROP TABLE " table (GenExec db) "\n\n"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenBegin :default

  ^String
  [db table]

  (str "CREATE TABLE " table "\n(\n"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenEnd :default

  ^String
  [db table]

  (str "\n)" (GenExec db) "\n\n"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenGrant :default

  ^String
  [db table]

  "")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenEndSQL :default

  ^String
  [db]

  "")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GenColDef

  ^String
  [db ^String col ty opt? dft]

  (str (GetPad db)
       (cstr/upper-case col)
       " " ty " "
       (nullClause db opt?)
       (if (nil? dft) "" (str " DEFAULT " dft))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GetFloatKeyword :default [db] "FLOAT")
(defmethod GetIntKeyword :default [db] "INTEGER")
(defmethod GetTSKeyword :default [db] "TIMESTAMP")
(defmethod GetDateKeyword :default [db] "DATE")
(defmethod GetBoolKeyword :default [db] "INTEGER")
(defmethod GetLongKeyword :default [db] "BIGINT")
(defmethod GetDoubleKeyword :default [db] "DOUBLE PRECISION")
(defmethod GetStringKeyword :default [db] "VARCHAR")
(defmethod GetBlobKeyword :default [db] "BLOB")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenBytes :default

  [db fld]

  (GenColDef db (:column fld) (GetBlobKeyword db) (:null fld) nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenString :default

  [db fld]

  (GenColDef db
             (:column fld)
             (str (GetStringKeyword db)
                  "("
                  (:size fld) ")")
             (:null fld)
             (if (:dft fld) (first (:dft fld)) nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenInteger :default

  [db fld]

  (GenColDef db
             (:column fld)
             (GetIntKeyword db)
             (:null fld)
             (if (:dft fld) (first (:dft fld)) nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenAutoInteger :default

  [db table fld]

  "")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenDouble :default

  [db fld]

  (GenColDef db
             (:column fld)
             (GetDoubleKeyword db)
             (:null fld)
             (if (:dft fld) (first (:dft fld)) nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenFloat :default

  [db fld]

  (GenColDef db
             (:column fld)
             (GetFloatKeyword db)
             (:null fld)
             (if (:dft fld) (first (:dft fld)) nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenLong :default

  [db fld]

  (GenColDef db
             (:column fld)
             (GetLongKeyword db)
             (:null fld)
             (if (:dft fld) (first (:dft fld)) nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenAutoLong :default

  [db table fld]

  "")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GetTSDefault :default

  [db]

  "CURRENT_TIMESTAMP")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenTimestamp :default

  [db fld]

  (GenColDef db
             (:column fld)
             (GetTSKeyword db)
             (:null fld)
             (if (:dft fld) (GetTSDefault db) nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenDate :default

  [db fld]

  (GenColDef db
             (:column fld)
             (GetDateKeyword db)
             (:null fld)
             (if (:dft fld) (GetTSDefault db) nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenCal :default

  [db fld]

  (GenTimestamp db fld))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenBool :default

  [db fld]

  (GenColDef db
             (:column fld)
             (GetBoolKeyword db)
             (:null fld)
             (if (:dft fld) (first (:dft fld)) nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genExIndexes ""

  ^String
  [db cache table flds zm]

  (let [m (dbcore/CollectDbIndexes cache zm)
        bf (StringBuilder.) ]
    (doseq [[nm nv] (seq m) ]
      (let [cols (map #(getcolname flds %) nv) ]
        (when (empty? cols)
          (dbcore/DbioError (str "Cannot have empty index: " nm)))
        (.append bf (str "CREATE INDEX "
                         (cstr/lower-case (str table "_" (name nm)))
                         " ON " table
                         " ( "
                         (cstr/join "," cols)
                         " )"
                         (GenExec db) "\n\n" ))))
    (.toString bf)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genUniques ""

  [db cache flds zm]

  (let [m (dbcore/CollectDbUniques cache zm)
        bf (StringBuilder.) ]
    (doseq [[nm nv] (seq m) ]
      (let [cols (map #(getcolname flds %) nv) ]
        (when (empty? cols)
          (dbcore/DbioError (str "Illegal empty unique: " (name nm))))
        (AddDelim! bf ",\n"
            (str (GetPad db) "UNIQUE(" (cstr/join "," cols) ")"))))
    (.toString bf)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genPrimaryKey ""

  [db zm pks]

  (str (GetPad db)
       "PRIMARY KEY("
       (cstr/upper-case (nsb (cstr/join "," pks)) )
       ")"
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genBody ""

  [db cache table zm]

  (let [flds (dbcore/CollectDbFields cache zm)
        inx (StringBuilder.)
        bf (StringBuilder.) ]
    (with-local-vars [pkeys (transient #{}) ]
      ;; 1st do the columns
      (doseq [[fid fld] (seq flds) ]
        (let [cn (cstr/upper-case ^String (:column fld))
              dt (:domain fld)
              col (case dt
                    :Boolean (GenBool db fld)
                    :Timestamp (GenTimestamp db fld)
                    :Date (GenDate db fld)
                    :Calendar (GenCal db fld)
                    :Int (if (:auto fld)
                           (GenAutoInteger db table fld)
                           (GenInteger db fld))
                    :Long (if (:auto fld)
                            (GenAutoLong db table fld)
                            (GenLong db fld))
                    :Double (GenDouble db fld)
                    :Float (GenFloat db fld)
                    (:Password :String) (GenString db fld)
                    :Bytes (GenBytes db fld)
                    (dbcore/DbioError (str "Unsupported domain type " dt))) ]
          (when (:pkey fld) (var-set pkeys (conj! @pkeys cn)))
          (AddDelim! bf ",\n" col)))
      ;; now do the assocs
      ;; now explicit indexes
      (-> inx (.append (genExIndexes db cache table flds zm)))
      ;; now uniques, primary keys and done.
      (when (> (.length bf) 0)
        (when (> (count @pkeys) 0) 
          (.append bf (str ",\n" (genPrimaryKey db zm (persistent! @pkeys)))))
        (let [s (genUniques db cache flds zm) ]
          (when (hgl? s)
            (.append bf (str ",\n" s)))))
    [ (.toString bf) (.toString inx) ] )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genOneTable ""

  [db ms zm]

  (let [table (cstr/upper-case ^String (:table zm))
        b (GenBegin db table)
        d (genBody db ms table zm)
        e (GenEnd db table)
        s1 (str b (first d) e)
        inx (last d) ]
    (str s1 (if (hgl? inx) inx "") (GenGrant db table))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetDDL  ""

  ^String
  [^MetaCache metaCache db ]

  (binding [dbcore/*DDL_BVS* (HashMap.) ]
    (let [ms (.getMetas metaCache)
          drops (StringBuilder.)
          body (StringBuilder.) ]
      (doseq [[id tdef] (seq ms) ]
        (let [^String tbl (:table tdef) ]
          (when (and (not (:abstract tdef)) (hgl? tbl))
            (log/debug "model id: " (name id) " table: " tbl)
            (-> drops (.append (GenDrop db (cstr/upper-case tbl) )))
            (-> body (.append (genOneTable db ms tdef))))))
      (str "" drops body (GenEndSQL db)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private drivers-eof nil)
