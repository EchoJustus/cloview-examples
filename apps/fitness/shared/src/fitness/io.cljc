(ns fitness.io
  "Host file-system helpers for the fitness tools and tests (JVM, ClojureCLR, Dart).
  Paths use forward slashes, which every host accepts (Windows included)."
  (:require [clojure.string :as str]
            #?@(:cljd [["dart:io" :as io]])))

(def baseline-marker "benchmarks/fitness-baseline/fixtures/business-inputs.edn")

(defn file-exists? [path]
  #?(:cljd (.existsSync (io/File path))
     :clj (.isFile (java.io.File. ^String path))
     :cljr (System.IO.File/Exists path)))

(defn read-text [path]
  #?(:cljd (.readAsStringSync (io/File path))
     :clj (slurp path :encoding "UTF-8")
     :cljr (System.IO.File/ReadAllText path)))

(defn write-text! [path text]
  #?(:cljd (let [f (io/File path)]
             (.createSync (.-parent f) .recursive true)
             (.writeAsStringSync f text))
     :clj (let [f (java.io.File. ^String path)]
            (when-some [p (.getParentFile f)] (.mkdirs p))
            (spit f text :encoding "UTF-8"))
     :cljr (let [full (System.IO.Path/GetFullPath path)]
             (System.IO.Directory/CreateDirectory (System.IO.Path/GetDirectoryName full))
             (System.IO.File/WriteAllText full text)))
  path)

(defn parent-dir [path]
  #?(:cljd (.-path (.-parent (io/File path)))
     :clj (.getParent (java.io.File. ^String path))
     :cljr (System.IO.Path/GetDirectoryName (System.IO.Path/GetFullPath path))))

(defn absolute [path]
  #?(:cljd (.-path (.-absolute (io/File path)))
     :clj (.getAbsolutePath (java.io.File. ^String path))
     :cljr (System.IO.Path/GetFullPath path)))

(defn join [dir name] (str (str/replace dir #"[\\/]+$" "") "/" name))

(defn getenv [k]
  #?(:cljd (get io/Platform.environment k)
     :clj (System/getenv k)
     :cljr (System.Environment/GetEnvironmentVariable k)))

(defn- start-dirs
  "Where to look for the repository: the working directory, plus (on the CLR, where
  CloViewHost runs from anywhere) every CLOJURE_LOAD_PATH entry, e.g. <repo>/target/clr-src."
  []
  (let [cwd #?(:cljd (.-path io/Directory.current)
               :clj (System/getProperty "user.dir")
               :cljr System.Environment/CurrentDirectory)
        load-path (or (getenv "CLOJURE_LOAD_PATH") "")]
    (into [cwd] (remove str/blank?) (str/split load-path #"[;:](?![\\/])"))))

(defn find-repo-root
  "Nearest ancestor of a start dir that contains the fitness baseline, or nil."
  []
  (some (fn [start]
          (loop [dir (absolute start)]
            (when (and dir (not (str/blank? dir)))
              (if (file-exists? (join dir baseline-marker))
                dir
                (let [p (parent-dir dir)]
                  (when (and p (not= p dir)) (recur p)))))))
        (start-dirs)))

