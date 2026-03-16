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

(defn load-config
  "Read config.edn from path, validate required keys, and return the map."
  [path]
  (let [f (io/file path)]
    (when-not (.exists f)
      (throw (ex-info (str "Config file not found: " path) {:path path})))
    (-> f slurp edn/read-string (validate! path))))
