(ns defsite.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def ^:private required-keys
  [:site/title
   :site/base-url
   :site/description
   :author/name
   :author/bio
   :author/photo])

(defn- validate!
  "Throw a descriptive error if any required key is absent."
  [config path]
  (doseq [k required-keys]
    (when-not (contains? config k)
      (throw (ex-info (str "config.edn is missing required key: " k)
                      {:path path :missing-key k}))))
  config)

(defn- read-edn [path]
  (-> path io/file slurp edn/read-string))

(defn load-config
  "Read config from base-path, optionally merge in override-path (site-specific
   config — only keys present in the override are replaced), validate that all
   required keys are present in the merged result, and return the map."
  ([base-path]
   (load-config base-path nil))
  ([base-path override-path]
   (let [f (io/file base-path)]
     (when-not (.exists f)
       (throw (ex-info (str "Config file not found: " base-path) {:path base-path})))
     (let [base     (read-edn base-path)
           override (when override-path
                      (let [of (io/file override-path)]
                        (when (.exists of)
                          (read-edn override-path))))
           merged   (merge base (or override {}))]
       (validate! merged base-path)
       merged))))
