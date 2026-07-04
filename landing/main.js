// Progressive enhancement only — the page is fully readable without this file.
(function () {
  // Footer year.
  var y = document.getElementById("year");
  if (y) y.textContent = new Date().getFullYear();

  // Reveal-on-scroll. We ADD the .reveal class from JS so that, without JS,
  // nothing is hidden. Respect reduced-motion by skipping entirely.
  var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (reduce || !("IntersectionObserver" in window)) return;

  var targets = document.querySelectorAll("main > section, .chapter, .stage");
  targets.forEach(function (el) { el.classList.add("reveal"); });

  var io = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (entry.isIntersecting) {
        entry.target.classList.add("is-in");
        io.unobserve(entry.target);
      }
    });
  }, { threshold: 0.12, rootMargin: "0px 0px -8% 0px" });

  targets.forEach(function (el) { io.observe(el); });
})();
