/*
 * FHIR IG Publisher - in-page accessibility check (local builds only).
 *
 * The IG publisher adds a small panel at the bottom of each page of a local build (never a ci-build or a
 * publication build, and not at all if the IG sets the 'accessibility-checks' parameter to false). Pressing
 * the button runs axe-core (https://github.com/dequelabs/axe-core, MPL-2.0) on the page as it is now - after
 * the page's scripts have run, with whatever tabs or sections the user has opened - restricted to WCAG 2.0 A
 * and AA, which is what the HHS Section 508 checklist is based on. It adds a check of its own for HHS 5B
 * (links distinguished from surrounding text by colour alone), and reports everything grouped by HHS
 * checklist id.
 *
 * axe-core is only loaded when the button is pressed, so this costs nothing on normal page loads.
 */
(function () {
  'use strict';

  var PANEL_ID = 'fhir-a11y-panel';

  // HHS checklist ids (Section 508 web checklist, version 03/2020) - short descriptions for the report
  var HHS = {
    '2A': 'Images, image buttons and image map areas have appropriate alternative text',
    '2B': 'Decorative images have null alt text',
    '2D': 'Embedded multimedia is identified via accessible text',
    '2E': 'Frames are appropriately titled',
    '2F': 'Content hidden from all users is also hidden from assistive technology',
    '3A': 'Time-based media has captions / transcripts',
    '4A': 'Semantic markup is used for headings, lists and emphasis',
    '4C': 'Data table headers are identified',
    '4D': 'Data cells are associated with their headers',
    '4E': 'Data table captions and summaries are used appropriately',
    '4F': 'Layout tables do not contain structural markup',
    '4G': 'Text labels are associated with form inputs',
    '4H': 'Related form elements are grouped with fieldset/legend',
    '4J': 'Reading and navigation order is logical',
    '5A': 'Colour is not the only way of conveying information',
    '5B': 'Colour alone does not distinguish links from surrounding text (unless 3:1 contrast with the text, plus a non-colour cue on hover and focus)',
    '5D': 'Audio that plays automatically can be stopped',
    '5E': 'Text has a contrast ratio of at least 4.5:1 (3:1 for large text)',
    '5F': 'The page is usable when text is resized to 200%',
    '6A': 'All functionality is available from the keyboard',
    '6B': 'Shortcut keys and accesskeys do not conflict',
    '7A': 'Time limits can be turned off, adjusted or extended',
    '7B': 'Moving, blinking or scrolling content can be paused',
    '9A': 'A mechanism to skip repeated blocks is provided',
    '9D': 'The page has a descriptive title',
    '9E': 'Focus order is logical',
    '9G': 'The purpose of each link can be determined',
    '9J': 'Headings and labels are informative',
    '10A': 'The language of the page is identified',
    '10B': 'Changes of language within the page are identified',
    '12C': 'Labels and instructions are provided for inputs',
    '13A': 'No significant HTML parsing errors (e.g. duplicate ids)',
    '13B': 'Name, role and value of controls can be determined programmatically',
    '?': 'Other WCAG 2.0 A/AA failures'
  };

  // axe rule -> HHS id, where the rule maps to one specific checklist row
  var RULE_TO_HHS = {
    'image-alt': '2A', 'input-image-alt': '2A', 'area-alt': '2A', 'role-img-alt': '2A', 'svg-img-alt': '2A',
    'image-redundant-alt': '2B',
    'object-alt': '2D', 'video-caption': '3A', 'audio-caption': '3A', 'no-autoplay-audio': '5D',
    'frame-title': '2E', 'frame-title-unique': '2E', 'frame-focusable-content': '6A',
    'aria-hidden-body': '2F', 'aria-hidden-focus': '2F',
    'list': '4A', 'listitem': '4A', 'definition-list': '4A', 'dlitem': '4A', 'empty-heading': '4A', 'p-as-heading': '4A',
    'th-has-data-cells': '4C', 'td-has-header': '4C', 'td-headers-attr': '4D', 'scope-attr-valid': '4D',
    'table-duplicate-name': '4E', 'table-fake-caption': '4E', 'layout-table': '4F', 'presentation-role-conflict': '4F',
    'label': '4G', 'label-title-only': '4G', 'form-field-multiple-labels': '4G', 'select-name': '4G',
    'color-contrast': '5E', 'link-in-text-block': '5B',
    'meta-viewport': '5F', 'meta-viewport-large': '5F',
    'scrollable-region-focusable': '6A', 'server-side-image-map': '6A',
    'accesskeys': '6B',
    'meta-refresh': '7A', 'meta-refresh-no-exceptions': '7A', 'blink': '7B', 'marquee': '7B',
    'bypass': '9A', 'document-title': '9D', 'tabindex': '9E',
    'link-name': '9G', 'identical-links-same-purpose': '9G',
    'html-has-lang': '10A', 'html-lang-valid': '10A', 'html-xml-lang-mismatch': '10A', 'valid-lang': '10B',
    'duplicate-id': '13A', 'duplicate-id-active': '13A', 'duplicate-id-aria': '13A'
  };
  // otherwise, WCAG success criterion (from the rule's tags) -> HHS id
  var SC_TO_HHS = {
    'wcag111': '2A', 'wcag121': '3A', 'wcag122': '3A', 'wcag123': '3A', 'wcag124': '3A', 'wcag125': '3A',
    'wcag131': '4A', 'wcag132': '4J', 'wcag141': '5A', 'wcag142': '5D', 'wcag143': '5E', 'wcag144': '5F',
    'wcag211': '6A', 'wcag212': '6A', 'wcag221': '7A', 'wcag222': '7B',
    'wcag241': '9A', 'wcag242': '9D', 'wcag243': '9E', 'wcag244': '9G', 'wcag246': '9J',
    'wcag311': '10A', 'wcag312': '10B', 'wcag332': '12C', 'wcag411': '13A', 'wcag412': '13B'
  };

  function hhsFor(rule) {
    if (RULE_TO_HHS[rule.id]) {
      return RULE_TO_HHS[rule.id];
    }
    var tags = rule.tags || [];
    for (var i = 0; i < tags.length; i++) {
      if (SC_TO_HHS[tags[i]]) {
        return SC_TO_HHS[tags[i]];
      }
    }
    return '?';
  }

  // ---- colour ------------------------------------------------------------------------------------------

  function parseColor(s) {
    var m = /rgba?\(\s*([\d.]+)[\s,]+([\d.]+)[\s,]+([\d.]+)(?:[\s,/]+([\d.]+%?))?\s*\)/.exec(s || '');
    if (!m) {
      return null;
    }
    var a = m[4] === undefined ? 1 : (m[4].indexOf('%') > 0 ? parseFloat(m[4]) / 100 : parseFloat(m[4]));
    return { r: +m[1], g: +m[2], b: +m[3], a: a };
  }

  function luminance(c) {
    function ch(v) {
      v = v / 255;
      return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
    return 0.2126 * ch(c.r) + 0.7152 * ch(c.g) + 0.0722 * ch(c.b);
  }

  function contrast(c1, c2) {
    var l1 = luminance(c1), l2 = luminance(c2);
    return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
  }

  function colorText(c) {
    function h(v) {
      var s = Math.round(v).toString(16);
      return s.length === 1 ? '0' + s : s;
    }
    return '#' + h(c.r) + h(c.g) + h(c.b);
  }

  // ---- HHS 5B: links distinguished from the surrounding text by colour alone ----------------------------

  function hasNonColourCue(style) {
    var deco = style.textDecorationLine || style.textDecoration || '';
    if (deco.indexOf('underline') >= 0 || deco.indexOf('overline') >= 0) {
      return true;
    }
    if (style.borderBottomStyle && style.borderBottomStyle !== 'none' && parseFloat(style.borderBottomWidth) > 0) {
      return true;
    }
    return false;
  }

  function blockAncestor(el) {
    var p = el.parentElement;
    while (p && p !== document.body) {
      var d = getComputedStyle(p).display;
      if (d !== 'inline' && d !== 'contents') {
        return p;
      }
      p = p.parentElement;
    }
    return p;
  }

  // is the link in a run of text? (a link alone in a table cell or a list item is not - there's nothing to confuse it with)
  // the text a sighted reader sees around the link: not other links (a run of links, like 'Reference(US Core
  // Patient)', is not a link in text), not text hidden off-screen for screen readers (e.g. the element paths
  // in the tree tables), and not our own badges
  function isVisuallyHidden(e) {
    if (e.classList && (e.classList.contains('sr-only') || e.classList.contains('fhir-a11y-badge'))) {
      return true;
    }
    if (e.getAttribute && e.getAttribute('aria-hidden') === 'true') {
      return false; // hidden from screen readers, but still seen
    }
    var cs = getComputedStyle(e);
    if (cs.display === 'none' || cs.visibility === 'hidden') {
      return true;
    }
    return cs.position === 'absolute' && (parseFloat(cs.width) <= 1 || parseFloat(cs.height) <= 1) && cs.overflow === 'hidden';
  }

  function surroundingText(block) {
    var out = [];
    (function walk(n) {
      for (var c = n.firstChild; c; c = c.nextSibling) {
        if (c.nodeType === 3) {
          out.push(c.nodeValue);
        } else if (c.nodeType === 1 && !/^(A|SCRIPT|STYLE|BUTTON|SELECT|TEXTAREA)$/.test(c.tagName) && !isVisuallyHidden(c)) {
          walk(c);
        }
      }
    })(block);
    return out.join(' ');
  }

  function inTextBlock(link, block) {
    var other = surroundingText(block).replace(/[\s.,;:()\[\]|\-]+/g, '');
    return other.length >= 3;
  }

  // does some stylesheet rule add an underline (or border) to this link on :hover / :focus?
  // returns true / false, or null if the stylesheets can't be read (e.g. pages opened as file:)
  function stateCue(link, state) {
    var unreadable = false;
    for (var i = 0; i < document.styleSheets.length; i++) {
      var rules;
      try {
        rules = document.styleSheets[i].cssRules;
      } catch (e) {
        unreadable = true;
        continue;
      }
      if (rules && ruleListHasCue(rules, link, state)) {
        return true;
      }
    }
    return unreadable ? null : false;
  }

  function ruleListHasCue(rules, link, state) {
    for (var j = 0; j < rules.length; j++) {
      var r = rules[j];
      if (!r.selectorText) {
        // a grouping rule (@media, @supports, @layer): look inside it if it applies. Note that style rules
        // also have cssRules now (css nesting), so this has to key off the selector, not cssRules
        if (r.cssRules && (!r.media || window.matchMedia(r.media.mediaText).matches) && ruleListHasCue(r.cssRules, link, state)) {
          return true;
        }
        continue;
      }
      if (r.selectorText.indexOf(':' + state) < 0) {
        continue;
      }
      var deco = r.style.textDecorationLine || r.style.textDecoration || '';
      var border = r.style.borderBottomStyle && r.style.borderBottomStyle !== 'none';
      if (deco.indexOf('underline') < 0 && !border) {
        continue;
      }
      var parts = r.selectorText.split(',');
      for (var k = 0; k < parts.length; k++) {
        if (parts[k].indexOf(':' + state) < 0) {
          continue;
        }
        var sel = parts[k].replace(/:(hover|focus-visible|focus-within|focus|active)/g, '').trim() || '*';
        try {
          if (link.matches(sel)) {
            return true;
          }
        } catch (e) {
          // a selector the browser can't match against (e.g. only a pseudo element) - ignore
        }
      }
    }
    return false;
  }

  function check5B() {
    var violations = [], review = [];
    var links = document.querySelectorAll('a[href]');
    for (var i = 0; i < links.length; i++) {
      var a = links[i];
      if (a.closest('#' + PANEL_ID) || !a.offsetParent || !(a.innerText || '').trim()) {
        continue; // the panel itself, hidden links, and links with no text
      }
      var ls = getComputedStyle(a);
      if (hasNonColourCue(ls)) {
        continue;
      }
      var block = blockAncestor(a);
      if (!block || !inTextBlock(a, block)) {
        continue;
      }
      var lc = parseColor(ls.color), tc = parseColor(getComputedStyle(block).color);
      if (!lc || !tc) {
        continue;
      }
      if (lc.r === tc.r && lc.g === tc.g && lc.b === tc.b) {
        // same colour as the text and no underline: nothing distinguishes it at all
        violations.push({ el: a, msg: 'The link is not distinguished from the surrounding text at all (same colour, no underline)' });
        continue;
      }
      var ratio = contrast(lc, tc);
      var ratioText = ratio.toFixed(2) + ':1 (link ' + colorText(lc) + ', text ' + colorText(tc) + ')';
      if (ratio < 3) {
        violations.push({ el: a, msg: 'The link is distinguished from the surrounding text by colour alone, and the contrast between the two is only ' + ratioText + '; it must be at least 3:1, or the link needs a non-colour cue such as an underline' });
        continue;
      }
      var hover = stateCue(a, 'hover'), focus = stateCue(a, 'focus');
      if (hover === false || focus === false) {
        violations.push({ el: a, msg: 'The link is distinguished from the surrounding text by colour alone (' + ratioText + ', which is enough), but gets no underline or other non-colour cue on ' + (hover === false && focus === false ? 'hover or focus' : hover === false ? 'hover' : 'focus') });
      } else if (hover === null || focus === null) {
        review.push({ el: a, msg: 'The link is distinguished from the surrounding text by colour alone (' + ratioText + '). Check that it gets an underline or other non-colour cue on hover and focus - the stylesheets could not be read to check (open the page over http rather than as a file to check this automatically)' });
      }
    }
    return { violations: violations, review: review };
  }

  // ---- contrast that axe could not decide -------------------------------------------------------------
  //
  // axe will not guess at the background when there's a background image (the tree lines in the hierarchy
  // tables) or when something else overlaps the text (fixed headers, tab bars), and files those under 'needs
  // review'. That makes the same link colour look like it passes in one place and fails in another. For these,
  // work the contrast out against the nearest solid background colour, and report it either way with the
  // assumption stated.

  var UNDECIDED = /background image|overlapped|obscured|could not be determined|pseudo|partially/i;

  function solidBackground(e) {
    var layers = [];
    for (var n = e; n && n.nodeType === 1; n = n.parentElement) {
      var c = parseColor(getComputedStyle(n).backgroundColor);
      if (c && c.a > 0) {
        layers.push(c);
        if (c.a >= 1) {
          break;
        }
      }
    }
    var bg = { r: 255, g: 255, b: 255, a: 1 };
    for (var i = layers.length - 1; i >= 0; i--) {
      bg = blend(layers[i], bg);
    }
    return bg;
  }

  function blend(top, under) {
    var a = top.a === undefined ? 1 : top.a;
    return { r: top.r * a + under.r * (1 - a), g: top.g * a + under.g * (1 - a), b: top.b * a + under.b * (1 - a), a: 1 };
  }

  function effectiveOpacity(e) {
    var o = 1;
    for (var n = e; n && n.nodeType === 1; n = n.parentElement) {
      o = o * parseFloat(getComputedStyle(n).opacity || '1');
    }
    return o;
  }

  function decideContrast(res) {
    var decided = [];
    res.incomplete = res.incomplete.filter(function (rule) {
      if (rule.id !== 'color-contrast') {
        return true;
      }
      var keep = [];
      rule.nodes.forEach(function (n) {
        var reason = (n.any || []).map(function (c) { return c.message || ''; }).join(' ');
        var target = null;
        try {
          target = n.target && document.querySelector(n.target[0]);
        } catch (e) {
          target = null;
        }
        if (!target || !UNDECIDED.test(reason)) {
          keep.push(n);
          return;
        }
        var cs = getComputedStyle(target);
        var fg = parseColor(cs.color);
        if (!fg) {
          keep.push(n);
          return;
        }
        var bg = solidBackground(target);
        fg.a = (fg.a === undefined ? 1 : fg.a) * effectiveOpacity(target);
        var text = blend(fg, bg);
        var ratio = contrast(text, bg);
        var px = parseFloat(cs.fontSize), bold = parseInt(cs.fontWeight, 10) >= 700;
        var large = px >= 24 || (bold && px >= 18.66);
        var needed = large ? 3 : 4.5;
        if (ratio < needed) {
          decided.push({
            html: n.html, target: n.target, impact: 'serious',
            failureSummary: 'Element has insufficient color contrast of ' + ratio.toFixed(2) + ' (foreground color: ' + colorText(text) +
              ', background color: ' + colorText(bg) + ', font size: ' + px + 'px, font weight: ' + (bold ? 'bold' : 'normal') + '). Expected contrast ratio of ' + needed +
              ':1. [background assumed: the checker could not see it directly (' + reason.replace(/^Element's /, '').replace(/\.$/, '') + '), so this uses the nearest solid background colour]'
          });
        }
        // and if it is enough, it passes - no need for a person to look at it
      });
      rule.nodes = keep;
      if (decided.length > 0) {
        var copy = { id: rule.id, tags: rule.tags, help: rule.help, helpUrl: rule.helpUrl, impact: 'serious', nodes: decided };
        var existing = res.violations.filter(function (v) { return v.id === 'color-contrast'; })[0];
        if (existing) {
          existing.nodes = existing.nodes.concat(decided);
        } else {
          res.violations.push(copy);
        }
      }
      return keep.length > 0;
    });
  }

  // ---- running ---------------------------------------------------------------------------------------

  function loadAxe(src, done) {
    if (window.axe) {
      done();
      return;
    }
    var s = document.createElement('script');
    s.src = src;
    s.onload = function () { done(); };
    s.onerror = function () { done('Could not load ' + src); };
    document.head.appendChild(s);
  }

  function run(panel) {
    var status = panel.querySelector('.fhir-a11y-status');
    var out = panel.querySelector('.fhir-a11y-results');
    status.textContent = 'Checking...';
    out.textContent = '';
    clearMarkers();
    loadAxe(panel.getAttribute('data-axe'), function (err) {
      if (err) {
        status.textContent = err;
        return;
      }
      window.axe.run({ exclude: [['#' + PANEL_ID]] }, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa'] }, rules: { 'link-in-text-block': { enabled: false } }, resultTypes: ['violations', 'incomplete'] })
        .then(function (res) {
          decideContrast(res);
          var own = check5B();
          render(panel, res, own);
        })
        .catch(function (e) {
          status.textContent = 'The check failed: ' + e;
        });
    });
  }

  function group(results, own5B, ownKey) {
    var g = {};
    function add(id, item) {
      (g[id] = g[id] || []).push(item);
    }
    results.forEach(function (rule) {
      rule.nodes.forEach(function (n) {
        add(hhsFor(rule), {
          rule: rule.id, help: rule.help, url: rule.helpUrl, impact: n.impact || rule.impact,
          detail: (n.failureSummary || '').replace(/^Fix (any|all) of the following:\s*/i, ''),
          html: n.html, target: n.target && n.target[0]
        });
      });
    });
    own5B[ownKey].forEach(function (f) {
      add('5B', { rule: 'hhs-5b', help: 'Links must be distinguishable from the surrounding text without relying on colour', detail: f.msg, html: f.el.outerHTML.slice(0, 200), el: f.el });
    });
    return g;
  }

  function sortedIds(g) {
    var order = Object.keys(HHS);
    return Object.keys(g).sort(function (a, b) {
      return order.indexOf(a) - order.indexOf(b);
    });
  }

  function el(tag, text, attrs) {
    var e = document.createElement(tag);
    if (text) {
      e.textContent = text;
    }
    for (var k in (attrs || {})) {
      e.setAttribute(k, attrs[k]);
    }
    return e;
  }

  // ---- markers on the page ---------------------------------------------------------------------------
  //
  // Each finding gets a number. The element it is about gets an outline and a small numbered badge next to
  // it (red for failures, orange for 'needs review'); the badge links back to the finding in the report,
  // and 'Show on page' in the report scrolls to the element. The markers are removed before each run so
  // they are never checked themselves, and 'Clear markers' takes them away.

  var markers = []; // { el, outline, outlineOffset, badge }

  // elements whose parent can't hold a span in front of them: the badge goes inside instead
  var BADGE_INSIDE_PARENTS = ['TABLE', 'THEAD', 'TBODY', 'TFOOT', 'TR', 'UL', 'OL', 'DL', 'SELECT', 'COLGROUP', 'OPTGROUP'];
  var VOID = ['IMG', 'INPUT', 'BR', 'HR', 'AREA', 'COL', 'EMBED', 'SOURCE', 'TRACK', 'WBR', 'META', 'LINK'];

  function resolve(item) {
    if (!item.el && typeof item.target === 'string') {
      try {
        item.el = document.querySelector(item.target);
      } catch (e) {
        item.el = null;
      }
    }
    return item.el;
  }

  function mark(item, review) {
    var target = resolve(item);
    if (!target || target === document.documentElement || target === document.body || target.closest('#' + PANEL_ID)) {
      return;
    }
    var colour = review ? '#b35900' : '#c00000';
    var m = { el: target, outline: target.style.outline, outlineOffset: target.style.outlineOffset };
    target.style.outline = '2px ' + (review ? 'dashed ' : 'solid ') + colour;
    target.style.outlineOffset = '1px';
    var badge = el('a', String(item.num), {
      'class': 'fhir-a11y-badge', href: '#fhir-a11y-item-' + item.num, 'aria-hidden': 'true', tabindex: '-1',
      title: '#' + item.num + ': ' + item.help,
      style: 'display: inline-block; margin: 0 2px; padding: 0 4px; border-radius: 8px; background-color: ' + colour +
        '; color: #ffffff; font: bold 11px/15px sans-serif; text-decoration: none; vertical-align: top; position: relative; z-index: 1000'
    });
    var parent = target.parentElement;
    if (parent && BADGE_INSIDE_PARENTS.indexOf(parent.tagName) >= 0 && VOID.indexOf(target.tagName) < 0) {
      target.insertBefore(badge, target.firstChild);
    } else if (parent) {
      parent.insertBefore(badge, target);
    }
    m.badge = badge;
    markers.push(m);
  }

  function clearMarkers() {
    markers.forEach(function (m) {
      m.el.style.outline = m.outline;
      m.el.style.outlineOffset = m.outlineOffset;
      if (m.badge && m.badge.parentNode) {
        m.badge.parentNode.removeChild(m.badge);
      }
    });
    markers = [];
  }

  function show(item) {
    var target = resolve(item);
    if (!target) {
      return;
    }
    if (!target.offsetParent && target !== document.body) {
      alertHidden(item);
      return;
    }
    target.scrollIntoView({ block: 'center' });
    // flash it so it stands out from the other marked elements
    var old = target.style.outline;
    target.style.outline = '4px solid #0050c0';
    setTimeout(function () {
      target.style.outline = old;
    }, 2500);
  }

  function alertHidden(item) {
    var li = document.getElementById('fhir-a11y-item-' + item.num);
    if (li && !li.querySelector('.fhir-a11y-hidden-note')) {
      li.appendChild(el('div', 'This element is not visible at the moment - it is probably in a tab or section that is not open.', { 'class': 'fhir-a11y-hidden-note', style: 'font-style: italic' }));
    }
  }

  function renderGroups(container, g, counter) {
    sortedIds(g).forEach(function (id) {
      var items = g[id];
      var sec = el('div', null, { 'class': 'fhir-a11y-group' });
      sec.appendChild(el('div', 'HHS ' + id + ' - ' + (HHS[id] || '') + ' (' + items.length + ')', { 'class': 'fhir-a11y-group-title', style: 'font-weight: bold; margin-top: 8px' }));
      var ul = el('ul');
      items.forEach(function (item) {
        item.num = ++counter.n;
        if (counter.markers) {
          mark(item, counter.review);
        }
        var li = el('li', null, { id: 'fhir-a11y-item-' + item.num });
        li.appendChild(el('b', '#' + item.num + ' ', { style: 'color: ' + (counter.review ? '#b35900' : '#c00000') }));
        li.appendChild(el('b', item.help + ' '));
        if (item.url) {
          li.appendChild(el('a', '[' + item.rule + ']', { href: item.url, target: '_blank', rel: 'noopener' }));
        } else {
          li.appendChild(el('span', '[' + item.rule + ']'));
        }
        if (item.detail) {
          li.appendChild(el('div', item.detail, { style: 'white-space: pre-wrap' }));
        }
        li.appendChild(el('code', item.html.length > 200 ? item.html.slice(0, 200) + '...' : item.html, { style: 'display: block; color: #333' }));
        var b = el('button', 'Show on page', { type: 'button' });
        b.addEventListener('click', function () {
          show(item);
        });
        li.appendChild(b);
        ul.appendChild(li);
      });
      sec.appendChild(ul);
      container.appendChild(sec);
    });
  }

  function count(g) {
    var n = 0;
    Object.keys(g).forEach(function (k) {
      n += g[k].length;
    });
    return n;
  }

  function render(panel, res, own) {
    var status = panel.querySelector('.fhir-a11y-status');
    var out = panel.querySelector('.fhir-a11y-results');
    var failed = group(res.violations, own, 'violations');
    var review = group(res.incomplete, own, 'review');
    var nf = count(failed), nr = count(review);
    status.textContent = nf === 0 ? 'No automatically detectable WCAG 2.0 A/AA failures on this page as it is now.' : nf + ' failure(s) found.';
    if (nr > 0) {
      status.textContent += ' ' + nr + ' item(s) need a person to review.';
    }
    // find every element before any badge goes in: axe's selectors use positions (nth-child), which the badges change
    [failed, review].forEach(function (g) {
      Object.keys(g).forEach(function (k) {
        g[k].forEach(resolve);
      });
    });
    var showMarkers = panel.querySelector('.fhir-a11y-markers');
    var counter = { n: 0, review: false, markers: !showMarkers || showMarkers.checked };
    renderGroups(out, failed, counter);
    if (nr > 0) {
      var d = el('details');
      d.appendChild(el('summary', 'Needs review (' + nr + ') - the checker could not decide these'));
      counter.review = true;
      var showReview = panel.querySelector('.fhir-a11y-markers-review');
      counter.markers = counter.markers && showReview && showReview.checked;
      renderGroups(d, review, counter);
      out.appendChild(d);
    }
    if (markers.length > 0) {
      status.textContent += ' The elements concerned are outlined and numbered on the page.';
    }
    out.appendChild(el('p', 'Automated checks find only some problems: whether text alternatives, headings and link text are meaningful, keyboard use, focus order and so on still need checking by hand. Content in tabs that are not showing is not checked - open the tab and check again.', { style: 'font-style: italic' }));
  }

  function init() {
    var panel = document.getElementById(PANEL_ID);
    if (!panel) {
      return;
    }
    var b = panel.querySelector('.fhir-a11y-run');
    if (b) {
      b.addEventListener('click', function () {
        run(panel);
      });
      var lbl = el('label', null, { style: 'margin-left: 6px' });
      var cb = el('input', null, { type: 'checkbox', 'class': 'fhir-a11y-markers' });
      cb.checked = true;
      lbl.appendChild(cb);
      lbl.appendChild(document.createTextNode(' mark failures on the page '));
      b.parentNode.insertBefore(lbl, b.nextSibling);
      // the 'needs review' items are mostly whole paragraphs and headings (contrast axe couldn't decide), so
      // marking them makes a lot of noise: off unless asked for
      var lbl2 = el('label', null, { style: 'margin-left: 6px' });
      var cb2 = el('input', null, { type: 'checkbox', 'class': 'fhir-a11y-markers-review' });
      lbl2.appendChild(cb2);
      lbl2.appendChild(document.createTextNode(' and items needing review '));
      lbl.parentNode.insertBefore(lbl2, lbl.nextSibling);
      var clr = el('button', 'Clear markers', { type: 'button' });
      clr.addEventListener('click', clearMarkers);
      lbl2.parentNode.insertBefore(clr, lbl2.nextSibling);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
