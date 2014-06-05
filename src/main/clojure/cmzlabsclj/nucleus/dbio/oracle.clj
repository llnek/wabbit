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
  cmzlabsclj.nucleus.dbio.oracle

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:import (java.util Map HashMap))
  (:use [cmzlabsclj.nucleus.dbio.drivers])
  (:use [cmzlabsclj.nucleus.dbio.core :as dbcore]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- create_sequence_trigger

  [db table col]

  (str "CREATE OR REPLACE TRIGGER TRIG_" table "\n"
       "BEFORE INSERT ON " table "\n"
       "REFERENCING NEW AS NEW\n"
       "FOR EACH ROW\n"
       "BEGIN\n"
       "SELECT SEQ_" table ".NEXTVAL INTO :NEW."
       col " FROM DUAL;\n"
       "END" (GenExec db) "\n\n"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- create_sequence

  [db table]

  (str "CREATE SEQUENCE SEQ_" table
       " START WITH 1 INCREMENT BY 1"
       (GenExec db) "\n\n"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Oracle
(defmethod GetStringKeyword Oracle [db] "VARCHAR2")
(defmethod GetTSDefault Oracle [db] "DEFAULT SYSTIMESTAMP")
(defmethod GetLongKeyword Oracle [db] "NUMBER(38)")
(defmethod GetDoubleKeyword Oracle [db] "BINARY_DOUBLE")
(defmethod GetFloatKeyword Oracle [db] "BINARY_FLOAT")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenAutoInteger Oracle

  [db table fld]

  (.put ^Map *DDL_BVS* table (:column fld))
  (GenInteger db fld))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenAutoLong Oracle

  [db table fld]

  (.put ^Map *DDL_BVS* table (:column fld))
  (GenLong db fld))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenEndSQL Oracle

  [db]

  (let [ m (into {} *DDL_BVS*)
         bf (StringBuilder.) ]
    (doseq [ en (seq m) ]
      (doto bf
        (.append (create_sequence db (first en)))
        (.append (create_sequence_trigger db (first en) (last en)))))
    (.toString bf)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenDrop Oracle

  [db table]

  (str "DROP TABLE " table " CASCADE CONSTRAINTS PURGE" (GenExec db) "\n\n"))

;;(println (GetDDL (MakeMetaCache testschema) (Oracle.) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private oracle-eof nil)
