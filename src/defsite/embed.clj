(ns defsite.embed)

;; ---------------------------------------------------------------------------
;; YouTube embed helpers shared by markdown and hiccup post parsers.

(def ^:private youtube-url-re
  #"(?:youtube\.com/watch\?(?:[^&]*&)*v=|youtu\.be/)([A-Za-z0-9_-]{11})")

(defn youtube-id
  "Extract the 11-character video ID from a YouTube watch/short URL,
   or return the input unchanged if it already looks like a bare ID."
  [url-or-id]
  (or (second (re-find youtube-url-re url-or-id))
      url-or-id))

(def ^:private allow-attrs
  "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share")

(defn youtube-html
  "Return an HTML string for a responsive YouTube iframe embed."
  [url-or-id]
  (let [id (youtube-id url-or-id)]
    (str "<div class=\"video-embed\">"
         "<iframe"
         " src=\"https://www.youtube-nocookie.com/embed/" id "\""
         " title=\"YouTube video player\""
         " frameborder=\"0\""
         " allow=\"" allow-attrs "\""
         " allowfullscreen>"
         "</iframe>"
         "</div>")))

(defn youtube-hiccup
  "Return a Hiccup vector for a responsive YouTube iframe embed."
  [url-or-id]
  (let [id (youtube-id url-or-id)]
    [:div.video-embed
     [:iframe {:src          (str "https://www.youtube-nocookie.com/embed/" id)
               :title        "YouTube video player"
               :frameborder  "0"
               :allow        allow-attrs
               :allowfullscreen true}]]))
