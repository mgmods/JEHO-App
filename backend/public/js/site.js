(function () {
  if (window.AOS) {
    AOS.init({ duration: 800, once: true, offset: 100 });
  }

  var y = document.getElementById("y");
  if (y) y.textContent = String(new Date().getFullYear());

  window.toggleLanguage = function () {
    var htmlTag = document.documentElement;
    var bootstrapRtl = document.getElementById("bootstrap-rtl");
    if (!bootstrapRtl) return;
    if (htmlTag.getAttribute("lang") === "ar") {
      htmlTag.setAttribute("lang", "en");
      htmlTag.removeAttribute("dir");
      bootstrapRtl.setAttribute("href", "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css");
      localStorage.setItem("hams_lang", "en");
    } else {
      htmlTag.setAttribute("lang", "ar");
      htmlTag.setAttribute("dir", "rtl");
      bootstrapRtl.setAttribute("href", "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.rtl.min.css");
      localStorage.setItem("hams_lang", "ar");
    }
    if (window.AOS) AOS.refresh();
  };

  window.addEventListener("DOMContentLoaded", function () {
    var saved = localStorage.getItem("hams_lang");
    var htmlTag = document.documentElement;
    var bootstrapRtl = document.getElementById("bootstrap-rtl");
    if (!bootstrapRtl) return;
    if (saved === "en") {
      htmlTag.setAttribute("lang", "en");
      htmlTag.removeAttribute("dir");
      bootstrapRtl.setAttribute("href", "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css");
    } else {
      htmlTag.setAttribute("lang", "ar");
      htmlTag.setAttribute("dir", "rtl");
      bootstrapRtl.setAttribute("href", "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.rtl.min.css");
    }
  });

  window.addEventListener("scroll", function () {
    var navbar = document.querySelector(".navbar");
    if (!navbar) return;
    if (window.scrollY > 40) {
      navbar.style.background = "rgba(255, 255, 255, 0.98)";
      navbar.style.boxShadow = "0 10px 30px rgba(0,0,0,0.05)";
    } else {
      navbar.style.background = "rgba(255, 255, 255, 0.85)";
      navbar.style.boxShadow = "none";
    }
  });

  var rail = document.getElementById("shotsRail");
  var prev = document.getElementById("shotPrev");
  var next = document.getElementById("shotNext");
  if (rail && prev && next) {
    var step = function () {
      var card = rail.querySelector(".shot-card");
      return card ? card.getBoundingClientRect().width + 18 : 220;
    };
    prev.addEventListener("click", function () {
      rail.scrollBy({ left: step(), behavior: "smooth" });
    });
    next.addEventListener("click", function () {
      rail.scrollBy({ left: -step(), behavior: "smooth" });
    });
  }
})();
