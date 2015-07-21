;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc "Locale resources."
      :author "kenl" }

  czlabclj.xlib.i18n.resources

  (:require [czlabclj.xlib.util.meta :refer [GetCldr]]
            [czlabclj.xlib.util.str :refer [nsb]])

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io])

  (:import  [java.util PropertyResourceBundle ResourceBundle Locale]
            [org.apache.commons.lang3 StringUtils]
            [java.io File FileInputStream]
            [java.net URL]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti LoadResource "Load properties file with localized strings." class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoadResource File

  ^ResourceBundle
  [^File aFile]

  (LoadResource (io/as-url aFile)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoadResource URL

  ^ResourceBundle
  [^URL url]

  (with-open [inp (.openStream url) ]
    (PropertyResourceBundle. inp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoadResource String

  ^ResourceBundle
  [^String path]

  (with-open [inp (some-> (GetCldr)
                          (.getResource path)
                          (.openStream)) ]
    (PropertyResourceBundle. inp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetResource "Return a resource bundle."

  (^ResourceBundle [^String baseName]
                   (GetResource baseName (Locale/getDefault) nil))

  (^ResourceBundle [^String baseName
                    ^Locale locale]
                   (GetResource baseName locale nil))

  (^ResourceBundle [^String baseName
                    ^Locale locale
                    ^ClassLoader cl]
                   (if (or (nil? baseName)
                           (nil? locale))
                     nil
                     (ResourceBundle/getBundle baseName
                                               locale (GetCldr cl))) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RStr "Return the string value for this key,
           pms may contain values for positional substitutions."

  ^String
  [^ResourceBundle bundle ^String pkey & pms]

  (if (empty? pkey)
    ""
    (let [kv (nsb (.getString bundle pkey))
          pc (count pms) ]
      ;;(log/debug "RStr key = " pkey ", value = "kv)
      (loop [src kv pos 0 ]
        (if (>= pos pc)
         src
         (recur (StringUtils/replace src
                                     "{}"
                                     (nsb (nth pms pos)) 1)
                (inc pos)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RStr* ""

  [^ResourceBundle bundle & pms]

  (map #(apply RStr bundle (first %) (drop 1 %)) pms))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

