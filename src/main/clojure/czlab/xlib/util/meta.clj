;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc "Utility functions for class related or
           reflection related operations"
      :author "kenl" }

  czlab.xlib.util.meta

  (:require
    [czlab.xlib.util.str :refer [EqAny? hgl?]]
    [czlab.xlib.util.logging :as log]
    [czlab.xlib.util.core :refer [test-nonil]])

  (:import
    [java.lang.reflect Member Field Method Modifier]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IsChild?

  "true if clazz is subclass of this base class"

  (fn [_ b]
    (if
      (instance? Class b)
      :class
      :object)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IsChild? :class

  [^Class basz ^Class cz]

  (if (or (nil? basz)
          (nil? cz))
    false
    (.isAssignableFrom basz cz)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IsChild? :object

  [^Class basz ^Object obj]

  (if (or (nil? basz)
          (nil? obj))
    false
    (IsChild? basz (.getClass obj))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BytesClass "java class for byte[]" ^Class [] (Class/forName "[B"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CharsClass "java class for char[]" ^Class [] (Class/forName "[C"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- gcn "" [^Class c] (.getName c))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro ^:private isXXX? ""

  [classObj classes]

  `(EqAny? (gcn ~classObj) ~classes))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsBoolean?

  "if class is Boolean"

  [classObj]

  (isXXX? classObj ["boolean" "Boolean" "java.lang.Boolean"] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsVoid?

  "if class is Void"

  [classObj]

  (isXXX? classObj ["void" "Void" "java.lang.Void"] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsChar?

  "if class is Char"

  [classObj]

  (isXXX? classObj [ "char" "Char" "java.lang.Character" ] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsInt?

  "if class is Int"

  [classObj]

  (isXXX? classObj [ "int" "Int" "java.lang.Integer" ] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsLong?

  "if class is Long"

  [classObj]

  (isXXX? classObj [ "long" "Long" "java.lang.Long" ] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsFloat?

  "if class is Float"

  [classObj]

  (isXXX? classObj [ "float" "Float" "java.lang.Float" ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsDouble?

  "if class is Double"

  [classObj]

  (isXXX? classObj [ "double" "Double" "java.lang.Double" ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsByte?

  "if class is Byte"

  [classObj]

  (isXXX? classObj [ "byte" "Byte" "java.lang.Byte" ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsShort?

  "if class is Short"

  [classObj]

  (isXXX? classObj [ "short" "Short" "java.lang.Short" ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsString?

  "if class is String"

  [classObj]

  (isXXX? classObj [ "String" "java.lang.String" ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsObject?

  "if class is Object"

  [classObj]

  (isXXX? classObj [ "Object" "java.lang.Object" ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsBytes?

  "if class is byte[]"

  [classObj]

  (= classObj (BytesClass)) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ForName

  "Load a java class by name"

  ^Class
  [^String z & [cl]]

  (if (nil? cl)
    (java.lang.Class/forName z)
    (->> ^ClassLoader cl
         (java.lang.Class/forName z true))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetCldr

  "Get the current classloader"

  ^ClassLoader
  [ & [cl] ]

  (or cl (.getContextClassLoader (Thread/currentThread))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SetCldr

  "Set current classloader"

  [^ClassLoader cl]

  {:pre [(some? cl)]}

  (.setContextClassLoader (Thread/currentThread) cl))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn LoadClass

  "Load this class by name"

  ^Class
  [^String clazzName & [cl]]

  (if (empty? clazzName)
    nil
    (.loadClass (GetCldr cl) clazzName)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^Object NewObjArgN

  "Instantiate object with arity-n constructor"

  (fn [a & args] (class a)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod NewObjArgN Class

  [^Class cz & args ]

  {:pre [(some? cz)]}

  (let [len (count args)
        cargs (object-array len)
        ca (make-array Class len) ]
    (doseq [n (range len)]
      (aset #^"[Ljava.lang.Object;" cargs n (nth args n))
      (aset #^"[Ljava.lang.Class;" ca n Object))
    (-> (.getDeclaredConstructor cz ca)
        (.newInstance cargs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod NewObjArgN String

  [^String cz & args ]

  (apply NewObjArgN (LoadClass cz) args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CtorObj*

  "Call the default contructor"

  ^Object
  [^Class cz]

  {:pre [(some? cz)]}

  (-> (.getDeclaredConstructor cz (make-array Class 0))
      (.newInstance (object-array 0)  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NewObj*

  "Make an object of this class by calling the default constructor"

  ^Object
  [^String clazzName & [cl]]

  (if (empty? clazzName)
    nil
    (CtorObj* (LoadClass clazzName cl))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ListParents

  "List all parent classes"

  [^Class javaClass]

  {:pre [(some? javaClass)]}

  (let
    [rc (loop [sum (transient [])
               par javaClass ]
          (if (nil? par)
            (persistent! sum)
            (recur (conj! sum par)
                   (.getSuperclass par)))) ]
    ;; since we always add the original class,
    ;; we need to ignore it on return
    (drop 1 rc)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- iterXXX ""

  [cz level getDeclXXX bin]

  (reduce (fn [sum ^Member m]
            (let [x (.getModifiers m) ]
              (if (and (> level 0)
                       (or (Modifier/isStatic x)
                           (Modifier/isPrivate x)) )
                sum
                (assoc! sum (.getName m) m))))
          bin
          (getDeclXXX cz)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- listMtds ""

  [^Class cz level]

  (let [par (.getSuperclass cz) ]
    (iterXXX cz
             level
             #(.getDeclaredMethods ^Class %)
             (if (nil? par)
               (transient {})
               (listMtds par (inc level))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- listFlds ""

  [^Class cz level]

  (let [par (.getSuperclass cz) ]
    (iterXXX cz
             level
             #(.getDeclaredFields ^Class %)
             (if (nil? par)
               (transient {})
               (listFlds par (inc level))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ListMethods

  "List all methods belonging to this class, including inherited ones"

  [^Class javaClass]

  {:pre [(some? javaClass)]}

  (vals (if (nil? javaClass)
          {}
          (persistent! (listMtds javaClass 0 )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ListFields

  "List all fields belonging to this class, including inherited ones"

  [^Class javaClass]

  {:pre [(some? javaClass)]}

  (vals (if (nil? javaClass)
          {}
          (persistent! (listFlds javaClass 0 )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

