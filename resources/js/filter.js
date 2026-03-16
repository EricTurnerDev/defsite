/**
 * filter.js — client-side category filtering for the post index
 *
 * Reads data-filter on .filter-btn elements and data-categories
 * (comma-separated slugs) on .post-card elements.
 *
 * No external dependencies.
 */
(function () {
  "use strict";

  var buttons = Array.prototype.slice.call(document.querySelectorAll(".filter-btn"));
  var cards   = Array.prototype.slice.call(document.querySelectorAll(".post-card"));

  // Nothing to do on non-index pages.
  if (!buttons.length || !cards.length) return;

  function applyFilter(filter) {
    // Update active state on buttons.
    buttons.forEach(function (btn) {
      btn.classList.toggle("active", btn.dataset.filter === filter);
    });

    // Show or hide each post card.
    cards.forEach(function (card) {
      if (filter === "all") {
        card.classList.remove("hidden");
      } else {
        var cats    = (card.dataset.categories || "").split(",");
        var matches = cats.some(function (c) { return c.trim() === filter; });
        card.classList.toggle("hidden", !matches);
      }
    });
  }

  buttons.forEach(function (btn) {
    btn.addEventListener("click", function () {
      applyFilter(btn.dataset.filter);
    });
  });

  // Keyboard: allow Space/Enter to activate a focused button.
  buttons.forEach(function (btn) {
    btn.addEventListener("keydown", function (e) {
      if (e.key === " " || e.key === "Enter") {
        e.preventDefault();
        applyFilter(btn.dataset.filter);
      }
    });
  });

}());
