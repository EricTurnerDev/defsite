(ns defsite.hiccup-post
  (:require [hiccup.core :as h]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def ^:private required-fields [:title :date :categories :summary :content])

(defn- slug-from-filename
  "Derive a URL slug from the file name.
   '2024-01-15-my-post.edn' → 'my-post'"
  [filename]
  (-> filename
      (str/replace #"^\d{4}-\d{2}-\d{2}-" "")
      (str/replace #"\.edn$" "")))

(defn- parse-date [v]
  (when v (java.time.LocalDate/parse (str v))))

(defn- validate-post!
  "Throw when a required field is absent or :content is not a vector."
  [post filename]
  (doseq [k required-fields]
    (when-not (contains? post k)
      (throw (ex-info (str filename " is missing required field: " (name k))
                      {:file filename :missing k}))))
  (when-not (vector? (:content post))
    (throw (ex-info (str filename " :content must be a Hiccup vector")
                    {:file filename :content (:content post)})))
  post)

(defn parse-post
  "Parse an EDN Hiccup post file into the unified post map.
   The file is read with clojure.edn/read-string — no code is evaluated."
  [^java.io.File file]
  (let [filename   (.getName file)
        post       (-> file slurp edn/read-string)
        _          (validate-post! post filename)
        slug       (or (some-> (:slug post) str not-empty)
                       (slug-from-filename filename))
        date       (parse-date (:date post))
        categories (mapv str (:categories post))
        body-html  (h/html (:content post))]
    {:title      (str (:title post))
     :date       date
     :categories categories
     :summary    (str (:summary post))
     :slug       slug
     :url        (str "/posts/" slug "/")
     :body-html  body-html
     :source     :hiccup}))
