/* Tailorbird docs: shared by docs/index.html and docs/zh/index.html.
 * Everything here enhances the static page; without it the page still reads fine. */
(function () {
  'use strict';

  var root = document.documentElement;
  var zh = /^zh/i.test(root.lang);
  var T = zh ? {
    copy: '复制', copied: '已复制', search: '搜索文档', searchPlaceholder: '搜索配置项、章节和内容…',
    noResults: '没有找到结果', onThisPage: '本页内容', backToTop: '回到顶部',
    filter: '筛选配置项，例如 javadoc、blank、indent…', shown: function (n, t) { return n + ' / ' + t + ' 项'; },
    noSettings: '没有匹配的配置项', navigate: '选择', open: '打开', close: '关闭',
    lines: function (n) { return n + ' 行变化'; }, same: '相同', setting: '配置项', section: '章节', text: '内容',
    openMenu: '打开导航', closeMenu: '关闭导航'
  } : {
    copy: 'Copy', copied: 'Copied', search: 'Search docs', searchPlaceholder: 'Search settings, sections and text…',
    noResults: 'No results', onThisPage: 'On this page', backToTop: 'Back to top',
    filter: 'Filter settings, e.g. javadoc, blank, indent…', shown: function (n, t) { return n + ' of ' + t; },
    noSettings: 'No settings match', navigate: 'to navigate', open: 'to open', close: 'to close',
    lines: function (n) { return n + ' changed'; }, same: 'same', setting: 'Setting', section: 'Section', text: 'Text',
    openMenu: 'Open navigation', closeMenu: 'Close navigation'
  };

  var ICON = {
    copy: '<svg class="copy" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg>',
    check: '<svg class="check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>',
    search: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>',
    up: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 19V5M5 12l7-7 7 7"/></svg>'
  };

  function el(tag, cls, html) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (html != null) e.innerHTML = html;
    return e;
  }
  function esc(s) {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }
  function textOf(node) {
    var c = node.cloneNode(true);
    c.querySelectorAll('.anchor').forEach(function (a) { a.remove(); });
    return c.textContent.replace(/\s+/g, ' ').trim();
  }

  /* ---------- Theme ---------- */
  var toggle = document.getElementById('theme-toggle');
  if (toggle) {
    toggle.addEventListener('click', function () {
      var current = root.getAttribute('data-theme')
        || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
      var next = current === 'dark' ? 'light' : 'dark';
      root.setAttribute('data-theme', next);
      try { localStorage.setItem('theme', next); } catch (e) {}
    });
  }

  /* ---------- Syntax highlighting ---------- */
  var JAVA_KW = ('abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while var record yield sealed permits non-sealed true false null').split(' ');
  var GROOVY_KW = ('def plugins dependencies repositories maven mavenCentral mavenLocal credentials id version files true false null').split(' ');
  var kwJava = {}, kwGroovy = {};
  JAVA_KW.forEach(function (k) { kwJava[k] = kwGroovy[k] = 1; });
  GROOVY_KW.forEach(function (k) { kwGroovy[k] = 1; });

  // Each tokenizer returns a list of [className|null, text].
  function tokC(src, kw) {
    var re = /(\/\*[\s\S]*?\*\/|\/\/[^\n]*)|("(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*')|(@[A-Za-z_][\w.]*)|(\b\d[\d_]*(?:\.\d+)?[lLfFdD]?\b)|([A-Za-z_$][\w$]*)|(\s+)|([^\sA-Za-z_$\d])/g;
    var out = [], m;
    while ((m = re.exec(src))) {
      if (m[1]) {
        // Highlight JavaDoc tags inside comments.
        m[1].split(/(@[a-zA-Z]+)/).forEach(function (part, i) {
          if (part) out.push([i % 2 ? 'tok-tag' : 'tok-com', part]);
        });
      } else if (m[2]) out.push(['tok-str', m[2]]);
      else if (m[3]) out.push(['tok-ann', m[3]]);
      else if (m[4]) out.push(['tok-num', m[4]]);
      else if (m[5]) {
        var w = m[5], next = src.charAt(re.lastIndex);
        if (kw[w]) out.push(['tok-kw', w]);
        else if (next === '(') out.push(['tok-fn', w]);
        else if (/^[A-Z]/.test(w) && /[a-z]/.test(w)) out.push(['tok-type', w]);
        else out.push([null, w]);
      } else if (m[6]) out.push([null, m[6]]);
      else out.push([/[{}()[\];,.]/.test(m[7]) ? 'tok-punc' : null, m[7]]);
    }
    return out;
  }
  function tokYaml(src) {
    var out = [];
    src.split('\n').forEach(function (line, i) {
      if (i) out.push([null, '\n']);
      var m = /^(\s*)(- )?([\w.-]+)(:)(.*)$/.exec(line);
      var rest = line;
      if (/^\s*#/.test(line)) { out.push(['tok-com', line]); return; }
      if (m) {
        out.push([null, m[1]]);
        if (m[2]) out.push(['tok-punc', m[2]]);
        out.push(['tok-key', m[3]], ['tok-punc', m[4]]);
        rest = m[5];
      }
      var re = /(#.*$)|("(?:\\.|[^"\\])*"|'[^']*')|(\b(?:true|false|null)\b)|(\b\d+\b)|([[\]{},])|([^#"'[\]{},\d]+|.)/g, t;
      while ((t = re.exec(rest))) {
        if (t[1]) out.push(['tok-com', t[1]]);
        else if (t[2]) out.push(['tok-str', t[2]]);
        else if (t[3]) out.push(['tok-kw', t[3]]);
        else if (t[4]) out.push(['tok-num', t[4]]);
        else if (t[5]) out.push(['tok-punc', t[5]]);
        else out.push([/\S/.test(t[0]) && !m ? null : (m ? 'tok-str' : null), t[0]]);
      }
    });
    return out;
  }
  function tokBash(src) {
    var out = [];
    src.split('\n').forEach(function (line, i) {
      if (i) out.push([null, '\n']);
      var re = /(#.*$)|("(?:\\.|[^"\\])*"|'[^']*')|(\$\(|\$\{?[A-Za-z_]\w*\}?)|(\s--?[\w-]+(?:=\S*)?)|(\s+)|([^\s"'$#]+|.)/g, t, first = true;
      while ((t = re.exec(line))) {
        if (t[1] && (t.index === 0 || /\s/.test(line.charAt(t.index - 1)))) out.push(['tok-com', t[1]]);
        else if (t[1]) out.push([null, t[1]]);
        else if (t[2]) out.push(['tok-str', t[2]]);
        else if (t[3]) out.push(['tok-var', t[3]]);
        else if (t[4]) { out.push([null, t[4].charAt(0)], ['tok-flag', t[4].slice(1)]); }
        else if (t[5]) out.push([null, t[5]]);
        else {
          var w = t[6];
          if (first && /^[\w./:-]+$/.test(w) && w !== '\\') { out.push(['tok-cmd', w]); }
          else out.push([w === '\\' || w === '|' || w === '&&' || w === ';' ? 'tok-punc' : null, w]);
        }
        if (!t[5]) first = false;
      }
    });
    return out;
  }
  function detect(pre, src) {
    if (pre.closest('.compare') || pre.closest('details')) return 'java';
    if (/^\s*(\.\/gradlew|git |export |palantir-java-format|tailorbird-java-format)/m.test(src) && !/;\s*$/m.test(src.replace(/; done\)"/, ''))) return 'bash';
    if (/^\s*(\.\/gradlew|git )/.test(src)) return 'bash';
    if (/^\s*(repositories|dependencies|plugins)\s*\{/m.test(src)) return 'groovy';
    var lines = src.split('\n').filter(function (l) { return l.trim(); });
    if (lines.length && lines.every(function (l) { return /^\s*(#|-\s|[\w.-]+:(\s|$))/.test(l); })) return 'yaml';
    return 'java';
  }
  function tokenize(lang, src) {
    if (lang === 'yaml') return tokYaml(src);
    if (lang === 'bash') return tokBash(src);
    return tokC(src, lang === 'groovy' ? kwGroovy : kwJava);
  }
  // Turns tokens into one HTML string per line, so lines can be marked for diffs.
  function toLines(tokens) {
    var lines = [''];
    tokens.forEach(function (t) {
      t[1].split('\n').forEach(function (part, i) {
        if (i) lines.push('');
        if (part) lines[lines.length - 1] += t[0] ? '<span class="' + t[0] + '">' + esc(part) + '</span>' : esc(part);
      });
    });
    return lines;
  }

  function copyButton(getText) {
    var b = el('button', 'copy-btn', ICON.copy + ICON.check + '<span>' + T.copy + '</span>');
    b.type = 'button';
    b.setAttribute('aria-label', T.copy);
    b.addEventListener('click', function () {
      var text = getText();
      var done = function () {
        b.classList.add('copied');
        b.querySelector('span').textContent = T.copied;
        clearTimeout(b._t);
        b._t = setTimeout(function () {
          b.classList.remove('copied');
          b.querySelector('span').textContent = T.copy;
        }, 1600);
      };
      if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(done, function () { fallbackCopy(text); done(); });
      } else { fallbackCopy(text); done(); }
    });
    return b;
  }
  function fallbackCopy(text) {
    var ta = el('textarea');
    ta.value = text;
    ta.setAttribute('readonly', '');
    ta.style.position = 'fixed'; ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand('copy'); } catch (e) {}
    ta.remove();
  }

  var blocks = [];
  document.querySelectorAll('main pre > code').forEach(function (code) {
    var pre = code.parentNode;
    var src = code.textContent;
    var lang = detect(pre, src);
    var lines = toLines(tokenize(lang, src));
    code.innerHTML = lines.map(function (l) { return '<span class="ln">' + (l || '') + '</span>'; }).join('');
    code.classList.add('lined');
    var block = { pre: pre, code: code, src: src, lines: src.split('\n'), lang: lang };
    blocks.push(block);

    var pane = pre.parentNode;
    if (pane.parentNode && pane.parentNode.classList && pane.parentNode.classList.contains('compare')) {
      var label = pane.querySelector('.label');
      pane.classList.add('pane');
      if (label) {
        var inner = el('span', 'label-text');
        while (label.firstChild) inner.appendChild(label.firstChild);
        if (label.classList.contains('yaml')) {
          // Nested settings are written with <br>, which has no text: turn it into a newline first.
          inner.querySelectorAll('br').forEach(function (br) { br.replaceWith('\n'); });
          inner.innerHTML = toLines(tokYaml(inner.textContent)).join('<br>');
        }
        label.appendChild(inner);
        label.appendChild(copyButton(function () { return src; }));
      }
    } else {
      var wrap = el('div', 'code-wrap');
      pre.parentNode.insertBefore(wrap, pre);
      wrap.appendChild(pre);
      var tools = el('div', 'code-tools');
      tools.appendChild(el('span', 'code-lang', lang));
      tools.appendChild(copyButton(function () { return src; }));
      wrap.appendChild(tools);
    }
  });

  /* ---------- Line diffs in comparisons ---------- */
  // Longest common subsequence of lines, where a shared real line counts more than a shared blank
  // line, so a blank line never wins a tie against code. Returns, for each side, which lines are shared.
  function lcsKeep(a, b) {
    var n = a.length, m = b.length, i, j;
    var w = function (l) { return l.trim() ? 2 : 1; };
    var dp = [];
    for (i = 0; i <= n; i++) { dp.push(new Uint16Array(m + 1)); }
    for (i = n - 1; i >= 0; i--) {
      for (j = m - 1; j >= 0; j--) {
        var best = Math.max(dp[i + 1][j], dp[i][j + 1]);
        if (a[i] === b[j]) best = Math.max(best, dp[i + 1][j + 1] + w(a[i]));
        dp[i][j] = best;
      }
    }
    var ka = new Array(n).fill(false), kb = new Array(m).fill(false);
    i = 0; j = 0;
    while (i < n && j < m) {
      if (a[i] === b[j] && dp[i][j] === dp[i + 1][j + 1] + w(a[i])) { ka[i] = kb[j] = true; i++; j++; }
      else if (dp[i + 1][j] >= dp[i][j + 1]) i++;
      else j++;
    }
    return [ka, kb];
  }
  function blockFor(pane) {
    var pre = pane.querySelector('pre');
    for (var i = 0; i < blocks.length; i++) if (blocks[i].pre === pre) return blocks[i];
    return null;
  }
  function mark(block, keep, cls) {
    var spans = block.code.children, changed = 0;
    for (var i = 0; i < keep.length; i++) {
      if (!keep[i]) { spans[i].classList.add(cls); changed++; }
    }
    return changed;
  }
  function stat(pane, cls, text) {
    var label = pane.querySelector('.label');
    if (!label) return;
    var s = el('span', 'diff-stat ' + cls, text);
    label.insertBefore(s, label.querySelector('.copy-btn'));
  }
  document.querySelectorAll('main .compare').forEach(function (cmp) {
    var panes = Array.prototype.filter.call(cmp.children, function (c) { return c.querySelector('pre'); });
    var bs = panes.map(blockFor);
    if (bs.length < 2 || bs.some(function (b) { return !b; })) return;
    var isBeforeAfter = !!cmp.querySelector('.label.before');
    if (isBeforeAfter && bs.length === 2) {
      var k = lcsKeep(bs[0].lines, bs[1].lines);
      var del = mark(bs[0], k[0], 'del'), add = mark(bs[1], k[1], 'add');
      stat(panes[0], 'del', '−' + del);
      stat(panes[1], 'add', '+' + add);
      return;
    }
    // Setting variants: the first column is the default; mark what each other column changes.
    var baseKeep = new Array(bs[0].lines.length).fill(true), keeps = [baseKeep];
    for (var c = 1; c < bs.length; c++) {
      var kk = lcsKeep(bs[0].lines, bs[c].lines);
      kk[0].forEach(function (v, i) { if (!v) baseKeep[i] = false; });
      keeps.push(kk[0]);
      var n = mark(bs[c], kk[1], 'chg');
      stat(panes[c], n ? 'chg' : 'same', n ? T.lines(n) : T.same);
    }
    stat(panes[0], 'chg', '');
    // Marks the default column against one other column (or against all of them, for 0).
    cmp._markBase = function (against) {
      Array.prototype.forEach.call(bs[0].code.children, function (span) { span.classList.remove('chg'); });
      var nb = mark(bs[0], keeps[against] || baseKeep, 'chg');
      var st = panes[0].querySelector('.diff-stat');
      st.className = 'diff-stat ' + (nb ? 'chg' : 'same');
      st.textContent = nb ? T.lines(nb) : T.same;
    };
    cmp._markBase(0);
  });

  /* ---------- Three-way comparisons become tabs when narrow ---------- */
  document.querySelectorAll('main .compare.three').forEach(function (cmp, n) {
    var panes = Array.prototype.filter.call(cmp.children, function (c) { return c.querySelector('pre'); });
    var wrap = el('div', 'tri');
    cmp.parentNode.insertBefore(wrap, cmp);
    var bar = el('div', 'tri-tabs');
    bar.setAttribute('role', 'tablist');
    var buttons = panes.map(function (pane, i) {
      var label = pane.querySelector('.label-text');
      var b = el('button', null, '<span>' + (label ? label.innerHTML : String(i + 1)) + '</span>');
      b.type = 'button';
      b.setAttribute('role', 'tab');
      b.id = 'tri-' + n + '-' + i;
      var st = pane.querySelector('.diff-stat');
      if (st && i) b.appendChild(el('span', st.className, st.textContent));
      b.addEventListener('click', function () { show(i); });
      b.addEventListener('keydown', function (e) {
        if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
          // Skip tabs that are hidden at this width (the default's tab, next to the default column).
          var step = e.key === 'ArrowRight' ? 1 : -1, j = i;
          do { j = (j + step + panes.length) % panes.length; } while (buttons[j].offsetParent === null && j !== i);
          show(j); buttons[j].focus();
        }
      });
      bar.appendChild(b);
      return b;
    });
    var active = 1;
    function show(i) {
      active = i;
      panes.forEach(function (p, k) { p.classList.toggle('active', k === i); });
      buttons.forEach(function (b, k) { b.setAttribute('aria-selected', String(k === i)); b.tabIndex = k === i ? 0 : -1; });
      refresh();
    }
    // With tabs showing, the default column is compared with the chosen tab only.
    function refresh() {
      if (cmp._markBase) cmp._markBase(bar.offsetParent !== null ? active : 0);
    }
    wrap.appendChild(bar);
    wrap.appendChild(cmp);
    show(1 < panes.length ? 1 : 0);
    if (window.ResizeObserver) new ResizeObserver(refresh).observe(wrap);
  });

  /* ---------- Tables ---------- */
  var settingsHeading = document.getElementById('settings');
  document.querySelectorAll('main table:not(.rule-facts)').forEach(function (table) {
    var heads = Array.prototype.map.call(table.querySelectorAll('thead th'), function (th) { return th.textContent.trim(); });
    if (heads.length) {
      table.querySelectorAll('tbody tr').forEach(function (tr) {
        Array.prototype.forEach.call(tr.children, function (td, i) { if (heads[i]) td.setAttribute('data-label', heads[i]); });
      });
    }
    var wrap = el('div', 'table-wrap' + (heads.length ? ' stack' : ''));
    table.parentNode.insertBefore(wrap, table);
    wrap.appendChild(table);
  });

  /* ---------- Settings filter ---------- */
  var settingsTable = null;
  if (settingsHeading) {
    var n = settingsHeading.nextElementSibling;
    while (n && !n.matches('.table-wrap') && !/^H[23]$/.test(n.tagName)) n = n.nextElementSibling;
    if (n && n.matches('.table-wrap')) settingsTable = n.querySelector('table');
  }
  if (settingsTable) {
    settingsTable.classList.add('settings-table');
    settingsTable.parentNode.classList.add('settings');
    var rows = Array.prototype.slice.call(settingsTable.tBodies[0].rows);
    var cache = rows.map(function (r) {
      return { row: r, name: r.cells[0].textContent.toLowerCase(), text: r.textContent.toLowerCase(), html: r.cells[0].innerHTML };
    });
    var box = el('div', 'filter', ICON.search);
    var input = el('input');
    input.type = 'search';
    input.placeholder = T.filter;
    input.setAttribute('aria-label', T.filter);
    var count = el('span', 'count');
    count.setAttribute('aria-live', 'polite');
    box.appendChild(input);
    box.appendChild(count);
    var tw = settingsTable.parentNode;
    tw.parentNode.insertBefore(box, tw);
    var empty = el('tr', 'empty-row', '<td colspan="3">' + T.noSettings + '</td>');
    empty.hidden = true;
    settingsTable.tBodies[0].appendChild(empty);
    var update = function () {
      var q = input.value.trim().toLowerCase();
      var words = q.split(/\s+/).filter(Boolean);
      var shown = 0;
      cache.forEach(function (c) {
        var ok = words.every(function (w) { return c.text.indexOf(w) >= 0; });
        c.row.hidden = !ok;
        if (ok) shown++;
      });
      empty.hidden = shown > 0;
      count.textContent = q ? T.shown(shown, cache.length) : '';
    };
    input.addEventListener('input', update);
    input.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') { input.value = ''; update(); }
      if (e.key === 'Enter') {
        var first = cache.filter(function (c) { return !c.row.hidden; })[0];
        var a = first && first.row.querySelector('a[href^="#"]');
        if (a) location.hash = a.getAttribute('href');
      }
    });
  }

  /* ---------- Heading anchors ---------- */
  var headings = [];
  document.querySelectorAll('main h2[id], main h3[id], main .rule[id] > h4').forEach(function (h) {
    var id = h.id || h.parentNode.id;
    var a = el('a', 'anchor', '#');
    a.href = '#' + id;
    a.setAttribute('aria-hidden', 'true');
    a.tabIndex = -1;
    h.insertBefore(a, h.firstChild);
  });
  document.querySelectorAll('main header.hero[id], main h2[id], main h3[id], main .rule[id]').forEach(function (h) {
    var level = h.matches('.rule') ? 4 : h.tagName === 'H3' ? 3 : 2;
    var titleEl = h.matches('.rule') ? h.querySelector('h4') : h.matches('header') ? h.querySelector('h1') : h;
    headings.push({ el: h, id: h.id, level: level, title: textOf(titleEl), html: titleEl.innerHTML.replace(/<a class="anchor"[^>]*>#<\/a>/, '') });
  });
  // Group: each heading belongs to the closest preceding h2 (or the hero).
  var currentTop = null;
  headings.forEach(function (h) {
    if (h.level <= 2) currentTop = h;
    h.top = currentTop;
  });

  /* ---------- On this page ---------- */
  var toc = document.getElementById('toc');
  var tocLinks = {};
  function renderToc(top) {
    if (!toc || toc._top === top) return;
    toc._top = top;
    tocLinks = {};
    var subs = headings.filter(function (h) { return h.top === top && h.level > 2; });
    toc.innerHTML = '';
    toc.appendChild(el('p', 'toc-title', T.onThisPage));
    var ul = el('ul');
    [top].concat(subs).forEach(function (h) {
      if (!h) return;
      var li = el('li', 'depth-' + Math.max(0, h.level - 2));
      var a = el('a', null, h.html);
      a.href = '#' + h.id;
      li.appendChild(a);
      ul.appendChild(li);
      tocLinks[h.id] = a;
    });
    toc.appendChild(ul);
    var up = el('a', 'toc-top', ICON.up + T.backToTop);
    up.href = '#overview';
    toc.appendChild(up);
  }

  /* ---------- Scroll spy ---------- */
  var sideLinks = {};
  document.querySelectorAll('.sidebar a[href^="#"]').forEach(function (a) { sideLinks[a.getAttribute('href').slice(1)] = a; });
  var activeSide = null, activeToc = null, ticking = false;
  function spy() {
    ticking = false;
    var offset = parseInt(getComputedStyle(root).scrollPaddingTop, 10) + 12 || 90;
    var cur = headings[0];
    for (var i = 0; i < headings.length; i++) {
      if (headings[i].el.getBoundingClientRect().top - offset <= 0) cur = headings[i];
      else break;
    }
    if (window.innerHeight + window.scrollY >= document.body.scrollHeight - 4) cur = headings[headings.length - 1];
    if (!cur) return;
    // Sidebar: the nearest heading at or before the current one that has a sidebar link.
    var idx = headings.indexOf(cur), side = null;
    for (var j = idx; j >= 0; j--) { if (sideLinks[headings[j].id]) { side = sideLinks[headings[j].id]; break; } }
    if (side !== activeSide) {
      if (activeSide) activeSide.removeAttribute('aria-current');
      if (side) {
        side.setAttribute('aria-current', 'true');
        var sb = side.closest('.sidebar');
        if (sb && getComputedStyle(sb).position === 'sticky') {
          var r = side.getBoundingClientRect(), sr = sb.getBoundingClientRect();
          if (r.top < sr.top + 40 || r.bottom > sr.bottom - 40) sb.scrollTop += r.top - sr.top - sr.height / 2;
        }
      }
      activeSide = side;
    }
    renderToc(cur.top);
    var t = tocLinks[cur.id] || tocLinks[cur.top && cur.top.id];
    if (t !== activeToc) {
      if (activeToc) activeToc.removeAttribute('aria-current');
      if (t) {
        t.setAttribute('aria-current', 'true');
        var tr = t.getBoundingClientRect(), cr = toc.getBoundingClientRect();
        if (tr.top < cr.top + 30 || tr.bottom > cr.bottom - 30) toc.scrollTop += tr.top - cr.top - cr.height / 2;
      }
      activeToc = t;
    }
  }
  // The enhancements above change the layout after the browser's first jump to the URL's #anchor,
  // and so can web fonts; jump again once they are in place, unless the reader has scrolled since.
  function rejump() {
    var id = decodeURIComponent(location.hash.slice(1));
    var target = id && document.getElementById(id);
    if (target) target.scrollIntoView({ behavior: 'instant', block: 'start' });
  }
  if (location.hash) {
    var moved = false;
    var onUser = function () { moved = true; };
    ['wheel', 'touchstart', 'keydown', 'mousedown'].forEach(function (ev) { window.addEventListener(ev, onUser, { once: true, passive: true }); });
    rejump();
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(function () { if (!moved) rejump(); });
    window.addEventListener('load', function () { if (!moved) rejump(); });
  }
  window.addEventListener('scroll', function () { if (!ticking) { ticking = true; requestAnimationFrame(spy); } }, { passive: true });
  window.addEventListener('resize', spy);
  window.addEventListener('hashchange', function () { setTimeout(spy, 50); });
  spy();

  /* ---------- Mobile navigation ---------- */
  var sidebar = document.getElementById('sidebar');
  var menuBtn = document.getElementById('menu-toggle');
  var scrim = document.getElementById('scrim');
  function setMenu(open) {
    if (!sidebar) return;
    sidebar.classList.toggle('open', open);
    if (scrim) { scrim.classList.toggle('open', open); scrim.hidden = !open; }
    if (menuBtn) {
      menuBtn.setAttribute('aria-expanded', String(open));
      menuBtn.setAttribute('aria-label', open ? T.closeMenu : T.openMenu);
    }
    document.body.style.overflow = open ? 'hidden' : '';
    if (open) { var a = sidebar.querySelector('[aria-current]') || sidebar.querySelector('a'); if (a) a.focus({ preventScroll: true }); }
  }
  if (menuBtn) menuBtn.addEventListener('click', function () { setMenu(!sidebar.classList.contains('open')); });
  if (scrim) scrim.addEventListener('click', function () { setMenu(false); });
  if (sidebar) sidebar.addEventListener('click', function (e) { if (e.target.closest('a')) setMenu(false); });
  // The drawer only exists on narrow screens: close it (and unlock scrolling) when the screen gets wider.
  var desktopMq = matchMedia('(min-width: 1024px)');
  var onDesktop = function (e) { if (e.matches) setMenu(false); };
  if (desktopMq.addEventListener) desktopMq.addEventListener('change', onDesktop);
  else if (desktopMq.addListener) desktopMq.addListener(onDesktop);

  /* ---------- Search ---------- */
  var index = [];
  (function buildIndex() {
    var seen = {};
    if (settingsTable) {
      Array.prototype.forEach.call(settingsTable.tBodies[0].rows, function (r) {
        if (r.classList.contains('empty-row')) return;
        var a = r.cells[0].querySelector('a[href^="#"]');
        if (!a) return;
        var href = a.getAttribute('href');
        seen[href] = true;
        index.push({ kind: 'setting', href: href, title: r.cells[0].textContent.trim(), titleHtml: r.cells[0].innerHTML.replace(/<\/?a[^>]*>/g, ''),
          sub: r.cells[2].textContent.trim() + ' · ' + r.cells[1].textContent.trim(), body: '', section: T.setting });
      });
    }
    headings.forEach(function (h, i) {
      // The section's own text: everything until the next heading.
      var body = '';
      var start = h.el.matches('header') ? h.el.nextElementSibling : h.el;
      var stop = headings[i + 1] && headings[i + 1].el;
      if (h.el.matches('.rule')) body = h.el.querySelector('p').textContent;
      else {
        for (var n = h.el.matches('header') ? start : h.el.nextElementSibling; n && n !== stop && !(stop && n.contains(stop)); n = n.nextElementSibling) {
          if (n.matches('.compare, .code-wrap, details, .filter')) continue;
          body += ' ' + (n.innerText || n.textContent);
        }
      }
      body = body.replace(/\s+/g, ' ').trim();
      var href = '#' + h.id;
      if (seen[href]) {
        index.forEach(function (e) { if (e.href === href) e.body = body; });
        return;
      }
      index.push({ kind: 'section', href: href, title: h.title, titleHtml: h.html, sub: h.top && h.top !== h ? h.top.title : '', body: body,
        section: h.top && h.top !== h ? h.top.title : '' });
    });
  })();

  var dialog = el('div', 'search-dialog');
  dialog.setAttribute('role', 'dialog');
  dialog.setAttribute('aria-modal', 'true');
  dialog.setAttribute('aria-label', T.search);
  dialog.innerHTML =
    '<div class="search-panel">' +
      '<div class="search-head">' + ICON.search +
        '<input type="search" autocomplete="off" spellcheck="false" aria-controls="search-results" aria-autocomplete="list">' +
        '<kbd>Esc</kbd></div>' +
      '<ul class="search-results" id="search-results" role="listbox"></ul>' +
      '<div class="search-foot"><span><kbd>↑</kbd><kbd>↓</kbd> ' + T.navigate + '</span><span><kbd>↵</kbd> ' + T.open + '</span><span><kbd>Esc</kbd> ' + T.close + '</span></div>' +
    '</div>';
  document.body.appendChild(dialog);
  var sInput = dialog.querySelector('input');
  var sList = dialog.querySelector('.search-results');
  sInput.placeholder = T.searchPlaceholder;
  sInput.setAttribute('aria-label', T.search);
  var results = [], sel = 0, lastFocus = null;

  // Marks every word in one pass over the raw text, so a later word can't match inside markup or entities.
  function highlight(text, words) {
    words = words.filter(Boolean);
    if (!words.length) return esc(text);
    var re = new RegExp(words.map(function (w) { return w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); })
      .sort(function (a, b) { return b.length - a.length; }).join('|'), 'ig');
    var out = '', last = 0, m;
    while ((m = re.exec(text))) {
      if (!m[0]) { re.lastIndex++; continue; }
      out += esc(text.slice(last, m.index)) + '<mark>' + esc(m[0]) + '</mark>';
      last = m.index + m[0].length;
    }
    return out + esc(text.slice(last));
  }
  function snippet(body, w) {
    var i = body.toLowerCase().indexOf(w);
    if (i < 0) return body.slice(0, 120);
    var s = Math.max(0, i - 50);
    return (s ? '…' : '') + body.slice(s, i + 90) + (i + 90 < body.length ? '…' : '');
  }
  function runSearch() {
    var q = sInput.value.trim().toLowerCase();
    var words = q.split(/\s+/).filter(Boolean);
    if (!words.length) {
      results = index.filter(function (e) { return e.kind === 'section' && !e.sub; }).map(function (e) { return { e: e, sub: '' }; });
    } else {
      results = [];
      index.forEach(function (e) {
        var t = e.title.toLowerCase(), all = (e.title + ' ' + e.sub + ' ' + e.body).toLowerCase();
        if (!words.every(function (w) { return all.indexOf(w) >= 0; })) return;
        var score = 0;
        words.forEach(function (w) {
          if (t === w) score += 100;
          else if (t.indexOf(w) === 0) score += 60;
          else if (t.indexOf(w) >= 0) score += 40;
          else if (e.sub.toLowerCase().indexOf(w) >= 0) score += 15;
          else score += 4;
        });
        if (e.kind === 'setting') score += 5;
        var inTitle = words.every(function (w) { return (e.title + ' ' + e.sub).toLowerCase().indexOf(w) >= 0; });
        results.push({ e: e, score: score, sub: inTitle ? e.sub : snippet(e.body, words[0]) });
      });
      results.sort(function (a, b) { return b.score - a.score; });
      results = results.slice(0, 30);
    }
    sel = 0;
    sList.innerHTML = '';
    if (!results.length) {
      sList.appendChild(el('li', 'search-empty', T.noResults));
      return;
    }
    results.forEach(function (r, i) {
      var li = el('li');
      li.setAttribute('role', 'none');
      var a = el('a');
      a.href = r.e.href;
      a.id = 'sr-' + i;
      a.setAttribute('role', 'option');
      var icon = r.e.kind === 'setting' ? '{}' : '#';
      var title = words.length && r.e.kind === 'setting' ? '<code>' + highlight(r.e.title, words) + '</code>' : (words.length ? highlight(r.e.title, words) : r.e.titleHtml);
      a.innerHTML = '<span class="r-icon">' + icon + '</span><span class="r-body"><span class="r-title">' + title + '</span>' +
        (r.sub ? '<span class="r-sub">' + highlight(r.sub, words) + '</span>' : '') + '</span>' +
        (r.e.section && r.e.kind === 'setting' ? '' : '');
      a.addEventListener('mousemove', function () { select(i, false); });
      a.addEventListener('click', function () { closeSearch(false); });
      li.appendChild(a);
      sList.appendChild(li);
    });
    select(0, false);
  }
  function select(i, scroll) {
    var items = sList.querySelectorAll('a');
    if (!items.length) return;
    sel = (i + items.length) % items.length;
    items.forEach(function (a, k) { a.setAttribute('aria-selected', k === sel ? 'true' : 'false'); });
    sInput.setAttribute('aria-activedescendant', 'sr-' + sel);
    if (scroll !== false) items[sel].scrollIntoView({ block: 'nearest' });
  }
  function openSearch() {
    lastFocus = document.activeElement;
    dialog.classList.add('open');
    document.body.style.overflow = 'hidden';
    sInput.value = '';
    runSearch();
    setTimeout(function () { sInput.focus(); }, 0);
  }
  function closeSearch(restore) {
    dialog.classList.remove('open');
    document.body.style.overflow = '';
    if (restore !== false && lastFocus) lastFocus.focus();
  }
  sInput.addEventListener('input', runSearch);
  sInput.addEventListener('keydown', function (e) {
    if (e.key === 'ArrowDown') { e.preventDefault(); select(sel + 1); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); select(sel - 1); }
    else if (e.key === 'Enter') {
      var a = sList.querySelectorAll('a')[sel];
      if (a) { e.preventDefault(); closeSearch(false); location.hash = a.getAttribute('href'); }
    }
  });
  dialog.addEventListener('click', function (e) { if (e.target === dialog) closeSearch(); });
  document.addEventListener('keydown', function (e) {
    if (dialog.classList.contains('open')) {
      if (e.key === 'Escape') { e.preventDefault(); closeSearch(); }
      return;
    }
    var typing = /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement.tagName) || document.activeElement.isContentEditable;
    if ((e.key === 'k' && (e.metaKey || e.ctrlKey)) || (e.key === '/' && !typing)) { e.preventDefault(); openSearch(); }
    if (e.key === 'Escape' && sidebar && sidebar.classList.contains('open')) setMenu(false);
  });
  var trigger = document.getElementById('search-open');
  if (trigger) {
    trigger.addEventListener('click', openSearch);
    var k = trigger.querySelector('kbd');
    if (k && /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent)) k.textContent = '⌘K';
    else if (k) k.textContent = 'Ctrl K';
  }
})();
