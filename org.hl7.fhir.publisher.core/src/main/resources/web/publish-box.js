// FHIR IG publish box support (website.dynamic-publish-box).
// Deployed by the IG publisher to the IG root next to package-list.json, and loaded
// (deferred, together with package-list.js) by every published page via a relative
// script reference, so it works at the canonical host, on mirrors/previews served
// under any base path, and from file:// (script tags are not subject to the CORS
// rules that block fetch() on file://).
//
// The publish box statement baked into each page is accurate as of the time that
// page was published. This script's only job is to correct the statement on pages
// of past versions when a newer version has been published since. If nothing has
// changed - the baked statement still matches package-list.json - it makes no DOM
// changes at all, so there is no flicker and no difference from the static output.
//
// Markup contract (data-pb-fmt="1", written by PublishBoxStatementGenerator and
// IGReleaseVersionUpdater; this script must remain able to process every fmt it
// has ever shipped with, because pages of past versions are never rewritten):
//   span.fhir-pb           the "current version" statement
//     data-pb-version      the version of the IG this page belongs to
//     data-pb-current      the version that was current when the statement was baked ("" if none)
//     data-pb-canonical    the IG's canonical URL
//   span.fhir-pb-versions  the "Page versions:" cross-version link list
//     data-pb-version      as above
//     data-pb-known        space-separated versions of all milestones known when the list was baked
//     data-pb-rel          this page's path relative to its version root
//     data-pb-canonical    as above
(function () {
  "use strict";

  // version ordering equivalent to org.hl7.fhir.utilities.VersionUtilities.compareVersions
  // for publication versions: numeric dot-parts compared numerically, a pre-release
  // suffix ("1.0.0-ballot") sorts before its release, suffixes compare as strings
  function cmpVer(a, b) {
    if (a === b) {
      return 0;
    }
    function parse(v) {
      var s = String(v).split("-");
      return {
        num: s[0].split(".").map(function (x) { var n = parseInt(x, 10); return isNaN(n) ? x : n; }),
        pre: s.length > 1 ? s.slice(1).join("-") : null
      };
    }
    var pa = parse(a), pb = parse(b);
    var len = Math.max(pa.num.length, pb.num.length);
    for (var i = 0; i < len; i++) {
      var xa = pa.num[i] === undefined ? 0 : pa.num[i];
      var xb = pb.num[i] === undefined ? 0 : pb.num[i];
      if (xa !== xb) {
        if (typeof xa === "number" && typeof xb === "number") {
          return xa > xb ? 1 : -1;
        }
        return String(xa) > String(xb) ? 1 : -1;
      }
    }
    if (pa.pre && !pb.pre) { return -1; }
    if (!pa.pre && pb.pre) { return 1; }
    if (pa.pre && pb.pre) { return pa.pre > pb.pre ? 1 : (pa.pre < pb.pre ? -1 : 0); }
    return 0;
  }

  // mirrors PackageList.current(): the entry flagged current whose status is not ci-build
  function currentEntry(list) {
    for (var i = 0; i < list.length; i++) {
      var e = list[i];
      if (e && e.current === true && e.status !== "ci-build") {
        return e;
      }
    }
    return null;
  }

  // mirrors PackageList.milestones(): entries with a milestoneName, excluding the
  // ci-build entry (version === "current"); package-list order (newest first) is kept
  function milestoneEntries(list) {
    var res = [];
    for (var i = 0; i < list.length; i++) {
      var e = list[i];
      if (e && e.milestoneName && e.version !== "current" && typeof e.path === "string") {
        res.push(e);
      }
    }
    return res;
  }

  function currentLink(cur, canonical, href) {
    var a = document.createElement("a");
    a.setAttribute("data-no-external", "true");
    // same rule as the server: link to the canonical root (which always serves the
    // current version) when the current version's path is under the canonical
    if (!href) {
      href = (canonical && cur.path && cur.path.indexOf(canonical) === 0) ? canonical : cur.path;
    }
    a.setAttribute("href", href);
    a.appendChild(document.createTextNode(cur.version));
    return a;
  }

  // The wordings here must match PublishBoxStatementGenerator.genFragment exactly
  function updateStatement(box, cur) {
    var pageVersion = box.getAttribute("data-pb-version");
    var bakedCurrent = box.getAttribute("data-pb-current") || "";
    if (!cur || !pageVersion || cur.version === bakedCurrent) {
      return; // the statement baked at publication time is still accurate
    }
    if (cur.status === "withdrawn") {
      return; // withdrawal notices are baked at publication time
    }
    var canonical = box.getAttribute("data-pb-canonical") || "";
    // if the baked statement deep-links into the current publication (a link under
    // the canonical, which always serves the current version), keep that href - it
    // remains this page's counterpart in whatever version is current
    var bakedLink = box.querySelector("a");
    var keepHref = null;
    if (bakedLink) {
      var h = bakedLink.getAttribute("href");
      if (h && canonical && h.indexOf(canonical) === 0) {
        keepHref = h;
      }
    }
    var d = cmpVer(cur.version, pageVersion);
    while (box.firstChild) {
      box.removeChild(box.firstChild);
    }
    if (d === 0) {
      box.appendChild(document.createTextNode("This is the current published version in its permanent home (it will always be available at this URL)"));
    } else if (d > 0) {
      box.appendChild(document.createTextNode("The current version which supersedes this version is "));
      box.appendChild(currentLink(cur, canonical, keepHref));
    } else {
      box.appendChild(document.createTextNode("This version is a pre-release. The current official version is "));
      box.appendChild(currentLink(cur, canonical, keepHref));
    }
  }

  // Adds milestones published after this page's list was baked. The baked list was
  // existence-checked at publication time; for milestones added since, this page's
  // counterpart may or may not exist, so each candidate (normally at most one) is
  // verified with a single HEAD request before a link is added. Needs a server -
  // skipped on file://, where the baked list stands.
  //
  // Candidates are probed and linked relative to the HOST THIS PAGE IS SERVED FROM
  // (derived from the page's own URL: it ends with <version>/<data-pb-rel>, or just
  // <data-pb-rel> for the copy at the IG root). At the canonical host that resolves to
  // the same URLs the baked links use; on a mirror (preview/staging) it keeps the probe
  // same-origin - a probe of the canonical host from a mirror would be blocked by CORS -
  // and keeps the reader on the mirror. Falls back to the canonical URL when the page's
  // URL does not have the expected shape or the milestone is not under the canonical.
  function igRootHere(span) {
    var rel = span.getAttribute("data-pb-rel") || "";
    var ver = span.getAttribute("data-pb-version") || "";
    var path = location.pathname;
    if (!rel || path.length <= rel.length || path.slice(-rel.length) !== rel) {
      return null;
    }
    path = path.slice(0, -rel.length).replace(/\/+$/, "");
    if (ver && path.slice(-(ver.length + 1)) === "/" + ver) {
      return path.slice(0, -(ver.length + 1)); // a page in the version's own folder
    }
    return path; // a page of the current version's copy at the IG root
  }

  function appendNewMilestones(span, list, canonical) {
    if (location.protocol !== "http:" && location.protocol !== "https:") {
      return;
    }
    var known = (span.getAttribute("data-pb-known") || "").split(" ");
    var rel = span.getAttribute("data-pb-rel") || "";
    var pageVersion = span.getAttribute("data-pb-version");
    var candidates = milestoneEntries(list).filter(function (m) {
      return m.version !== pageVersion && known.indexOf(m.version) === -1;
    });
    if (!candidates.length) {
      return;
    }
    var rootHere = igRootHere(span);
    Promise.all(candidates.map(function (m) {
      var base = m.path.replace(/\/+$/, "");
      if (rootHere !== null && canonical && base.indexOf(canonical) === 0) {
        base = rootHere + base.slice(canonical.length);
      }
      var href = base + "/" + rel;
      return fetch(href, { method: "HEAD" }).then(function (r) {
        return { m: m, href: href, ok: r.ok };
      }).catch(function () {
        return { m: m, href: href, ok: false };
      });
    })).then(function (results) {
      var links = [];
      for (var i = 0; i < results.length; i++) {
        if (results[i].ok) {
          var a = document.createElement("a");
          a.setAttribute("data-no-external", "true");
          a.setAttribute("href", results[i].href);
          a.appendChild(document.createTextNode(results[i].m.milestoneName));
          links.push(a);
        }
      }
      if (!links.length) {
        return;
      }
      // package-list order is newest first, so new milestones go at the front of the list
      var anchor = span.firstElementChild;
      if (!anchor && !span.textContent) {
        span.appendChild(document.createTextNode(". Page versions: "));
      }
      for (var j = 0; j < links.length; j++) {
        span.insertBefore(links[j], anchor);
        span.insertBefore(document.createTextNode(" "), anchor);
      }
    });
  }

  function init() {
    if (typeof fhirPackageList === "undefined" || !fhirPackageList || !fhirPackageList.list) {
      return; // package-list.js not available; the baked statements stand
    }
    var list = fhirPackageList.list;
    var cur = currentEntry(list);
    var boxes = document.querySelectorAll("span.fhir-pb");
    for (var i = 0; i < boxes.length; i++) {
      updateStatement(boxes[i], cur);
    }
    var canonical = boxes.length ? (boxes[0].getAttribute("data-pb-canonical") || "") : "";
    var spans = document.querySelectorAll("span.fhir-pb-versions");
    for (var j = 0; j < spans.length; j++) {
      appendNewMilestones(spans[j], list, canonical);
    }
  }

  if (document.readyState !== "loading") {
    init();
  } else {
    document.addEventListener("DOMContentLoaded", init);
  }
})();
