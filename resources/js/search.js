/**
 * search.js — client-side full-text search
 *
 * Loads /search-index.json lazily on first keypress, then scores each post
 * against the query across title (weight 10), categories (weight 3),
 * summary (weight 5), and body text (weight 1).
 *
 * No external dependencies.
 */
(function () {
  "use strict";

  var input   = document.getElementById("search-input");
  var results = document.getElementById("search-results");

  if (!input || !results) return;

  var posts       = null;   // populated after first fetch
  var loadPromise = null;   // cached Promise so we only fetch once
  var debounceId  = null;

  // ------------------------------------------------------------------
  // Index loading

  function loadIndex() {
    if (loadPromise) return loadPromise;
    loadPromise = fetch("/search-index.json")
      .then(function (r) {
        if (!r.ok) throw new Error("HTTP " + r.status);
        return r.json();
      })
      .then(function (data) {
        posts = data;
        return data;
      });
    return loadPromise;
  }

  // ------------------------------------------------------------------
  // Scoring

  function includes(haystack, needle) {
    return haystack.toLowerCase().indexOf(needle) !== -1;
  }

  function scorePost(post, q) {
    var score = 0;
    if (includes(post.title,   q)) score += 10;
    if (includes(post.summary, q)) score +=  5;
    if (includes(post.body,    q)) score +=  1;
    post.categories.forEach(function (cat) {
      if (includes(cat, q)) score += 3;
    });
    return score;
  }

  // ------------------------------------------------------------------
  // HTML helpers

  function escapeHtml(s) {
    return s
      .replace(/&/g,  "&amp;")
      .replace(/</g,  "&lt;")
      .replace(/>/g,  "&gt;")
      .replace(/"/g,  "&quot;")
      .replace(/'/g,  "&#x27;");
  }

  function renderResults(matched) {
    if (matched.length === 0) {
      results.innerHTML = "<p class=\"search-empty\">No results found.</p>";
      return;
    }

    results.innerHTML = matched
      .map(function (p) {
        return (
          "<div class=\"search-result-item\">" +
            "<a href=\"" + escapeHtml(p.url) + "\">" + escapeHtml(p.title) + "</a>" +
            "<span class=\"result-date\">" + escapeHtml(p.date) + "</span>" +
          "</div>"
        );
      })
      .join("");
  }

  // ------------------------------------------------------------------
  // Search execution

  function runSearch(query) {
    results.innerHTML = "";
    if (!query || query.length < 2) return;

    var q = query.toLowerCase();

    var matched = posts
      .map(function (p) { return { post: p, score: scorePost(p, q) }; })
      .filter(function (x) { return x.score > 0; })
      .sort(function (a, b) { return b.score - a.score; })
      .slice(0, 8)
      .map(function (x) { return x.post; });

    renderResults(matched);
  }

  // ------------------------------------------------------------------
  // Event wiring

  input.addEventListener("input", function () {
    var query = input.value.trim();
    clearTimeout(debounceId);

    debounceId = setTimeout(function () {
      if (!posts) {
        loadIndex()
          .then(function () { runSearch(query); })
          .catch(function () {
            results.innerHTML = "<p class=\"search-empty\">Search unavailable.</p>";
          });
      } else {
        runSearch(query);
      }
    }, 200);
  });

}());
