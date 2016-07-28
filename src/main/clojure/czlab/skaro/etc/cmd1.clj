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

  czlab.skaro.etc.cmd1

  ;;(:refer-clojure :rename {first fst second snd last lst})

  (:require
    [czlab.xlib.str :refer [addDelim! strbf<> ucase hgl? strim]]
    [czlab.crypto.codec :refer [strongPwd passwd<>]]
    [czlab.xlib.cmdline :refer [consoleIO]]
    [czlab.xlib.resources :refer [rstr]]
    [czlab.xlib.dates :refer [+months gcal<>]]
    [czlab.xlib.meta :refer [getCldr]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [clojure.string :as cs]
    [czlab.xlib.format :refer [readEdn]]
    [czlab.xlib.files
     :refer [readFile
             cleanDir
             mkdirs
             writeFile
             listFiles]]
    [czlab.xlib.core
     :refer [isWindows?
             fpath
             spos?
             getCwd
             trap!
             exp!
             try!
             stringify
             flatnil
             convLong
             resStr]]
    [czlab.crypto.core
     :refer [AES256_CBC
             assertJce
             PEM_CERT
             exportPublicKey
             exportPrivateKey
             dbgProvider
             asymKeyPair<>
             ssv1PKCS12<>
             csreq<>]])

  (:use [czlab.skaro.etc.boot]
        [czlab.skaro.etc.cmd2]
        [czlab.xlib.guids]
        [czlab.xlib.meta]
        [czlab.skaro.core.consts])

  (:import
    [java.util
     ResourceBundle
     Properties
     Calendar
     Map
     Date]
    [czlab.skaro.loaders AppClassLoader]
    [czlab.skaro.server CLJShim ]
    [czlab.skaro.etc CmdHelpError]
    [czlab.crypto PasswordAPI]
    [czlab.wflow Job]
    [java.io File]
    [java.security KeyPair PublicKey PrivateKey]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe create a new app?
(defn onCreate

  "Create a new app"
  [args]

  (let [args (vec (drop 1 args))]
    (if (> (count args) 1)
      (createApp (args 0) (args 1))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe build an app?
(defn onBuild

  "Build the app"
  [args]

  (let [args (vec (drop 1 args))]
    (->> (if (empty? args) ["dev"] args)
         (apply execBootScript (getHomeDir) (getCwd)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe compress and package an app?
(defn onPodify

  "Package the app"
  [args]

  (let [args (vec (drop 1 args))]
    (if-not (empty? args)
      (bundleApp (getHomeDir)
                 (getCwd) (args 0))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe run tests on an app?
(defn onTest

  "Test the app"
  [args]

  (let [args (vec (drop 1 args))]
    (->> (if (empty? args) ["tst"] args)
         (apply execBootScript (getHomeDir) (getCwd) ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe start the server?
(defn onStart

  "Start and run the app"
  [args]

  (let [func "czlab.skaro.impl.climain/startViaCLI"
        home (getHomeDir)
        cwd (getCwd)
        rt (-> (doto
                 (AppClassLoader. (getCldr))
                 (.configure cwd))
               (CLJShim/newrt (.getName cwd)))
        args (drop 1 args)
        s2 (first args)]
    ;; background job is handled differently on windows
    (if (and (= s2 "bg")
             (isWindows?))
      (runAppBg home)
      (try!
        (.callEx rt func (object-array [home]))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe run in debug mode?
(defn onDebug "Debug the app" [args] (onStart args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Maybe generate some demo apps?
(defn onDemos

  "Generate demo apps"
  [args]

  (let [args (vec (drop 1 args))]
    (if-not (empty? args)
      (publishSamples (args 0))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro generatePassword

  "Generate a random password"
  {:private true}
  [len]

  `(println (str (strongPwd ~len))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genKeyPair

  "Generate a keypair"
  [^String lenStr & [^chars pwd]]

  ;;(DbgProvider java.lang.System/out)
  (let [kp (asymKeyPair<> "RSA" (convLong lenStr 1024))
        pvk (.getPrivate kp)
        puk (.getPublic kp)]
    (println "privatekey=\n"
             (stringify (exportPrivateKey pvk pwd)))
    (println "publickey=\n"
             (stringify (exportPublicKey puk )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genWwid

  ""
  []

  (println (wwid<>)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genGuid ""

  []

  (println (uuid<>)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeCsrQs

  "Set of questions to capture the DN information"
  [^ResourceBundle rcb]

  {:fname {:question (rstr rcb "cmd.save.file")
           :default "csr"
           :required true
           :next :end
           :result :fn}

   :size {:question (rstr rcb "cmd.key.size")
          :default "1024"
          :required true
          :next :fname
          :result :size}

   :c {:question (rstr rcb "cmd.dn.c")
       :default "US"
       :required true
       :next :size
       :result :c}

   :st {:question (rstr rcb "cmd.dn.st")
        :required true
        :next :c
        :result :st}

   :loc {:question (rstr rcb "cmd.dn.loc")
         :required true
         :next :st
         :result :l }

   :o {:question (rstr rcb "cmd.dn.org")
       :required true
       :next :loc
       :result :o }

   :ou {:question (rstr rcb "cmd.dn.ou")
        :required true
        :next :o
        :result :ou }

   :cn {:question (rstr rcb "cmd.dn.cn")
        :required true
        :next :ou
        :result :cn } })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeKeyQs

  "Set of questions to save info to file"

  [^ResourceBundle rcb]

  {:fname {:question (rstr rcb "cmd.save.file")
           :default "test.p12"
           :required true
           :next :end
           :result :fn }

   :pwd {:question (rstr rcb "cmd.key.pwd")
         :required true
         :next :fname
         :result :pwd }

   :duration {:question (rstr rcb "cmd.key.duration")
              :default "12"
              :required true
              :next :pwd
              :result :months }

   :size {:question (rstr rcb "cmd.key.size")
          :default "1024"
          :required true
          :next :duration
          :result :size } })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- promptQs ""

  [questions start]

  (when-some [rc (cliConverse questions start)]
    (let [ssn (map #(let [v (get rc %) ]
                      (if (hgl? v)
                        (str (ucase (name %)) "=" v)))
                   [ :c :st :l :o :ou :cn ]) ]
      [(cs/join "," (flattenNil ssn)) rc])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- keyfile

  "Maybe generate a server key file?"
  []

  (if-some
    [res (promptQs (merge (makeCsrQs (resBdl))
                          (makeKeyQs (resBdl))) :cn) ]
    (let [dn (fst res)
          rc (lst res)
          now (Date.)
          ff (io/file (:fn rc))]
      (println "DN entered: " dn)
      (ssv1PKCS12
        dn
        (pwdify (:pwd rc))
        ff
        {:keylen (convLong (:size rc) 1024)
         :start now
         :end (-> (gcal now)
                  (addMonths (convLong (:months rc) 12))
                  (.getTime)) })
      (println "Wrote file: " ff))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- csrfile

  "Maybe generate a CSR?"
  [args]

  (when-not (== (count args) 14)
    (trap! CmdHelpError))
  (let [[v1 v2] (split-at 12 args)
        sz (convLong (first v2) 0)
        bf (strbf<>)
        v1 (into {} (map #(vec %)
                         (partition 2 v1)))]
    (when-not (spos? sz) (trap! CmdHelpError))
    (doseq [k [:c :st :l :o :ou :cn]
            :let [v (strim (v1 (str "-" (name k))))]]
      (when-not (hgl? v)
        (trap! CmdHelpError))
      (->> (format "%s=%s" (ucase (name k)) v)
           (addDelim! bf ",")))
    (csreq<> (str dn)
      (let [f1 (io/file (:fn rc) ".key")
            f2 (io/file (:fn rc) ".csr") ]
        (writeOneFile f1 pkey)
        (println "Wrote file: " f1)
        (writeOneFile f2 req)
        (println "Wrote file: " f2)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onGenerate

  "Generate a bunch of stuff"
  [args]

  (let
    [args (drop 1 args)
     rc
     (condp = (first args)
       "keypair"
       (if (> (count args) 1)
         (genKeyPair (second args))
         false)
       "password"
       (generatePassword 12)
       "serverkey"
       (keyfile)
       "guid"
       (genGuid)
       "wwid"
       (genWwid)
       "csr"
       (csrfile (drop 1 args))
       false)]
    (when (false? rc)
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genHash

  ""
  [text]

  (->> (pwdify text)
       (.hashed )
       (:hash )
       (println )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHash

  "Generate a hash"
  [args]

  (let [args (drop 1 args)]
    (if-not (empty? args)
      (genHash (first args))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- encrypt

  ""
  [pkey text]

  (->> (pwdify text pkey)
       (.encoded )
       (println )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onEncrypt

  "Encrypt the data"
  [args]

  (let [args (drop 1 args)]
    (if (> (count args) 1)
      (encrypt (first args) (second args))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- decrypt

  ""
  [pkey secret]

  (->> (pwdify secret pkey)
       (.text )
       (println )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onDecrypt

  "Decrypt the cypher"
  [args]

  (let [args (drop 1 args)]
    (if (> (count args) 1)
      (decrypt (first args) (second args))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onTestJCE

  "Test if JCE (crypto) is ok"
  []

  (assertJce)
  (println "JCE is OK."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onVersion

  "Show the version of system"
  []

  (println "skaro version : "  (System/getProperty "skaro.version"))
  (println "java version  : "  (System/getProperty "java.version")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp

  "Show help"
  []

  (trap! CmdHelpError))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scanJars

  ""
  [^StringBuilder out ^File dir]

  (let [sep (System/getProperty "line.separator")
        fs (listFiles dir "jar") ]
    (doseq [f fs]
      (doto out
        (.append (str "<classpathentry  kind=\"lib\" path=\""
                      (fpath f)
                      "\"/>" ))
        (.append sep)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genEclipseProj

  ""
  [^File appdir]

  (let [ec (mkdirs (io/file appdir "eclipse.projfiles"))
        app (.getName appdir)
        sb (strbf)]
    (cleanDir ec)
    (writeFile
      (io/file ec ".project")
      (-> (resStr (str "czlab/skaro/eclipse/"
                       "java"
                       "/project.txt"))
          (cs/replace "${APP.NAME}" app)
          (cs/replace "${JAVA.TEST}"
                      (fpath (io/file appdir
                                      "src/test/java")))
          (cs/replace "${JAVA.SRC}"
                      (fpath (io/file appdir
                                      "src/main/java")))
          (cs/replace "${CLJ.TEST}"
                      (fpath (io/file appdir
                                      "src/test/clojure")))
          (cs/replace "${CLJ.SRC}"
                      (fpath (io/file appdir
                                      "src/main/clojure")))))
    (.mkdirs (io/file appdir DN_BUILD "classes"))
    (doall
      (map (partial scanJars sb)
           [(io/file (getHomeDir) DN_DIST)
            (io/file (getHomeDir) DN_LIB)
            (io/file appdir DN_TARGET)]))
    (writeFile
      (io/file ec ".classpath")
      (-> (resStr (str "czlab/skaro/eclipse/"
                       "java"
                       "/classpath.txt"))
          (cs/replace "${CLASS.PATH.ENTRIES}" (str sb))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onIDE

  "Generate IDE project files"
  [args]

  (let [args (drop 1 args)]
    (if (and (> (count args) 0)
             (= "eclipse" (first args)))
      (genEclipseProj (getCwd))
      (trap! CmdHelpError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


