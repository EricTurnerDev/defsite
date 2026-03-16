(ns defsite.search
  (:require [clojure.data.json :as json]
            [clojure.string :as str]))

(defn- strip-html
  "Remove HTML tags and collapse whitespace to produce plain text."
  [html]
  (-> html
      (str/replace #"<[^>]+>" " ")
      (str/replace #"&[a-zA-Z]+;" " ")
      (str/replace #"&#\d+;"    " ")
      (str/replace #"\s+"       " ")
      str/trim))

(defn- post->entry
  "Project a post map to a search index entry.
   The :body field is plain text so the client can search it without a DOM parser."
  [post]
  {:title      (:title post)
   :slug       (:slug post)
   :url        (:url post)
   :date       (str (:date post))
   :categories (:categories post)
   :summary    (:summary post)
   :body       (strip-html (:body-html post))})

(defn build-index
  "Return a vector of search index entries from a seq of post maps."
  [posts]
  (mapv post->entry posts))

(defn index->json
  "Serialize the search index to a JSON string for public/search-index.json."
  [index]
  (json/write-str index {:escape-unicode false}))
