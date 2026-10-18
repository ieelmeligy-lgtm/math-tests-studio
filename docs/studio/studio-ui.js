/* Studio screens: 1 Import → 2 Review → 3 Publish. Same steps as the desktop app, for iPad, phone or any browser. */
(function () {
  'use strict';
  var C = StudioCore;
  var $ = function (id) { return document.getElementById(id); };
  var REQUEST = 'Use the math-tests-portal skill. Make a self-grading multiple-choice test page from the attached photos ' +
    'for Grade <N>, in <English / Arabic>. Keep the book\'s wording, mark the correct option of each question ' +
    'with data-c, and write a short explanation in each .why. On a question card where you fixed a misprint ' +
    'add data-note="what you changed", and where you wrote the options yourself add data-options="claude". ' +
    'Give me the HTML file to download, and the answer key.';
  var ROOT = new URL('.', location.href).href;
  var S = { tpl: null, draft: null, grade: null, fileName: '', title: '', cur: -1, busy: false, published: false };

  var session = Portal.session();
  if (!session) { location.replace('index.html'); return; }
  if (session.role === 'student') { $('gate').hidden = false; return; }
  $('app').hidden = false;

  fetch('studio/test-template.txt', { cache: 'no-store' })
    .then(function (r) { if (!r.ok) throw new Error(); return r.text(); })
    .then(function (t) { S.tpl = t; ready(); })
    .catch(function () { msg('iErr', 'The page template could not be loaded. Check your internet and reload the page.'); });

  function msg(id, text) { $(id).textContent = text || ''; $(id).hidden = !text; }
  function step(n) {
    ['pImport', 'pReview', 'pPublish'].forEach(function (p, i) { $(p).hidden = i !== n - 1; });
    for (var i = 1; i <= 3; i++) { $('st' + i).className = 'step s' + i + (i === n ? ' on' : i < n ? ' done' : ''); }
    window.scrollTo(0, 0);
  }
  step(1);
  window.addEventListener('beforeunload', function (e) { if (S.draft && !S.published) { e.preventDefault(); e.returnValue = ''; } });

  // ---------- 1. Import ----------
  (window.PORTAL_GRADES || []).forEach(function (g) {
    var b = document.createElement('button');
    b.type = 'button'; b.className = 'gbtn'; b.textContent = g; b.dataset.g = g; b.setAttribute('aria-pressed', 'false');
    b.onclick = function () { S.grade = g; pressGrade(); ready(); };
    $('grades').appendChild(b);
  });
  function pressGrade() {
    var bs = $('grades').querySelectorAll('.gbtn');
    for (var i = 0; i < bs.length; i++) bs[i].setAttribute('aria-pressed', String(bs[i].dataset.g === S.grade));
  }
  function ready() { $('toReview').disabled = !(S.tpl && S.draft && S.grade); }

  $('copyReq').onclick = function () { copy(REQUEST, 'Copied. Paste it in Claude with your photos, and fill in the grade and language.'); };
  function copy(text, done) {
    if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(text).then(function () { alert(done); }, function () { prompt('Copy this:', text); });
    else prompt('Copy this:', text);
  }

  $('file').onchange = function () {
    var f = this.files && this.files[0];
    msg('iErr', '');
    $('fileInfo').hidden = true;
    S.draft = null; ready();
    if (!f) return;
    var reader = new FileReader();
    reader.onload = function () { load(f.name, String(reader.result || '')); };
    reader.onerror = function () { msg('iErr', 'The file could not be read. Save it again from Claude and try once more.'); };
    reader.readAsText(f, 'UTF-8');
    this.value = '';   // choosing the same file again still triggers a change
  };

  function load(name, html) {
    var kind = C.kind(html);
    if (kind === 'PUBLISHED') { msg('iErr', 'This page is already published and has no answers inside. To change the test, save the file from Claude again and choose that one.'); return; }
    if (kind === 'UNKNOWN') { msg('iErr', 'This file is not a test page from Claude: no correct answers are marked in it (data-c). Ask Claude again with the request from "Copy the request for Claude".'); return; }
    var d;
    try { d = C.read(html); } catch (e) { msg('iErr', 'The file could not be read: ' + e.message); return; }
    if (!d.questions.length) { msg('iErr', 'No multiple-choice questions were found in this file.\n' + d.skipped.join('\n')); return; }
    S.draft = d; S.cur = -1; S.published = false;
    S.title = d.suggestedTitle;
    S.fileName = C.safeFileName(name);
    $('iName').textContent = S.fileName;
    $('iLang').textContent = d.lang === 'ar' ? 'Arabic' : 'English';
    $('iCount').textContent = d.questions.length;
    $('iTitle').textContent = d.suggestedTitle || '–';
    var w = [];
    if (S.fileName !== name) w.push('The file name has spaces or letters a web address cannot use. It will be published as ' + S.fileName + '.');
    d.skipped.forEach(function (s) { w.push('⚠ Left out: ' + s); });
    msg('iWarn', w.join('\n'));
    var g = C.gradeFromPage(html);
    S.grade = (window.PORTAL_GRADES || []).indexOf(g) >= 0 ? g : null;
    pressGrade();
    $('fileInfo').hidden = false;
    ready();
  }
  $('toReview').onclick = function () { $('title').value = S.title; buildEditor(); step(2); };

  // ---------- 2. Review ----------
  var optInputs = [], radios = [];
  function buildEditor() {
    var ar = S.draft.lang === 'ar', box = $('opts'), letters = ['A', 'B', 'C', 'D'], arL = ['أ', 'ب', 'ج', 'د'];
    box.innerHTML = ''; optInputs = []; radios = [];
    for (var k = 0; k < 4; k++) {
      var row = document.createElement('div'); row.className = 'optrow';
      var lab = document.createElement('label');
      var r = document.createElement('input'); r.type = 'radio'; r.name = 'correct'; r.value = k;
      lab.appendChild(r); lab.appendChild(document.createTextNode(letters[k] + (ar ? ' ' + arL[k] : '')));
      var t = document.createElement('input'); t.type = 'text'; t.placeholder = k < 2 ? 'required' : 'optional';
      t.oninput = livePreview; r.onchange = livePreview;
      row.appendChild(lab); row.appendChild(t); box.appendChild(row);
      optInputs.push(t); radios.push(r);
    }
    ['stem', 'why', 'title'].forEach(function (id) { $(id).dir = ar ? 'rtl' : 'ltr'; });
    optInputs.forEach(function (t) { t.dir = ar ? 'rtl' : 'ltr'; });
    $('qprev').dir = ar ? 'rtl' : 'ltr';
    $('stem').oninput = livePreview; $('why').oninput = livePreview;
    S.cur = -1;
    renderList(); refreshTop(); show(0);
  }
  function label(i) {
    var q = S.draft.questions[i], mark = q.misprintNote ? '⚠ ' : q.optionsWrittenByClaude ? '✎ ' : '';
    var p = C.toPlain(q.stemHtml);
    return (i + 1) + '. ' + mark + (p.length > 38 ? p.substring(0, 38) + '…' : p);
  }
  function renderList() {
    var list = $('qlist'); list.innerHTML = '';
    S.draft.questions.forEach(function (q, i) {
      var b = document.createElement('button');
      b.type = 'button'; b.className = 'qi' + (i === S.cur ? ' sel' : '');
      var k = document.createElement('span'); k.className = 'k';
      k.textContent = q.misprintNote.slice(-C.UNSURE.length) === C.UNSURE ? '?' : 'ABCD'.charAt(q.correctIndex);
      b.appendChild(k); b.appendChild(document.createTextNode(label(i)));
      b.onclick = function () { show(i); };
      list.appendChild(b);
    });
  }
  function refreshTop() {
    $('key').textContent = 'Answer key: ' + C.answerKey(S.draft);
    var w = [];
    S.draft.skipped.forEach(function (s) { w.push('⚠ Left out: ' + s); });
    S.draft.questions.forEach(function (q, i) {
      if (q.misprintNote) w.push('⚠ Q' + (i + 1) + ' – check this: ' + q.misprintNote);
      if (q.optionsWrittenByClaude) w.push('✎ Q' + (i + 1) + ': the options were written by Claude, not the book.');
    });
    S.draft.notes.forEach(function (s) { w.push('ℹ ' + s); });
    msg('rWarn', w.join('\n'));
  }
  function unsure(q) { return q.misprintNote.slice(-C.UNSURE.length) === C.UNSURE; }

  function show(i) {
    var err = save();
    if (err && S.cur !== i) { msg('rErr', err); }
    else msg('rErr', '');
    if (i < 0 || i >= S.draft.questions.length) return;
    S.cur = i;
    var q = S.draft.questions[i];
    $('stem').value = q.stemHtml;
    for (var k = 0; k < 4; k++) {
      optInputs[k].value = k < q.optionsHtml.length ? q.optionsHtml[k] : '';
      radios[k].checked = !unsure(q) && k === q.correctIndex;
    }
    $('why').value = q.explanationHtml;
    $('flag').textContent = (q.misprintNote ? '⚠ ' + q.misprintNote + '   ' : '') + (q.optionsWrittenByClaude ? '✎ Options written by Claude   ' : '') +
      (q.sectionHtml ? 'Part: ' + C.toPlain(q.sectionHtml) : '');
    renderList();
    livePreview();
  }
  /** Copies the editor back into the draft. Returns an error message, or null. */
  function save() {
    var i = S.cur;
    if (i < 0 || i >= S.draft.questions.length) return null;
    var q = S.draft.questions[i], o = [], pick = -1;
    q.stemHtml = C.sanitize($('stem').value.trim());
    for (var k = 0; k < 4; k++) {
      var t = optInputs[k].value.trim();
      if (!t) continue;
      if (radios[k].checked) pick = o.length;
      o.push(C.sanitize(t));
    }
    q.optionsHtml = o;
    if (pick >= 0) { q.correctIndex = pick; if (unsure(q)) q.misprintNote = ''; }
    q.explanationHtml = C.sanitize($('why').value.trim());
    refreshTop();
    if (!q.stemHtml) return 'Question ' + (i + 1) + ' has no text.';
    if (o.length < 2) return 'Question ' + (i + 1) + ' needs at least two options.';
    if (pick < 0) return 'Question ' + (i + 1) + ': tick the correct option.';
    return null;
  }
  function livePreview() {
    var p = $('qprev'), html = '<div>' + C.sanitize($('stem').value) + '</div><div>';
    var letters = S.draft.lang === 'ar' ? ['أ', 'ب', 'ج', 'د'] : ['A', 'B', 'C', 'D'];
    for (var k = 0; k < 4; k++) {
      var v = optInputs[k].value.trim();
      if (v) html += '<span class="o' + (radios[k].checked ? ' c' : '') + '"><b>' + letters[k] + '</b> ' + C.sanitize(v) + '</span>';
    }
    html += '</div>';
    if ($('why').value.trim()) html += '<div class="muted" style="margin-top:8px">' + C.sanitize($('why').value) + '</div>';
    p.innerHTML = html;   // sanitized above
  }
  $('remove').onclick = function () {
    if (S.cur < 0 || !confirm('Remove question ' + (S.cur + 1) + '?')) return;
    S.draft.questions.splice(S.cur, 1);
    S.cur = -1;
    renderList(); refreshTop();
    if (S.draft.questions.length) show(0);
  };
  function validateAll() {
    var e = save();
    if (e) return e;
    if (!$('title').value.trim()) return 'Give the test a title.';
    if (!S.draft.questions.length) return 'The test has no questions.';
    for (var i = 0; i < S.draft.questions.length; i++) {
      var q = S.draft.questions[i];
      if (q.optionsHtml.length < 2 || q.correctIndex >= q.optionsHtml.length) return 'Check the options of question ' + (i + 1) + '.';
      if (unsure(q)) return 'Question ' + (i + 1) + ': tick the correct option.';
    }
    return null;
  }
  $('back').onclick = function () { save(); step(1); };
  $('previewBtn').onclick = function () {
    var e = validateAll();
    if (e) { msg('rErr', e); return; }
    msg('rErr', '');
    var page = C.build(S.tpl, S.draft, S.grade, $('title').value.trim(), 'preview');
    $('prevFrame').srcdoc = C.preview(page, S.draft);
    $('overlay').hidden = false;
  };
  $('closePrev').onclick = function () { $('overlay').hidden = true; $('prevFrame').srcdoc = ''; };
  $('approve').onclick = function () {
    var e = validateAll();
    if (e) { msg('rErr', e); return; }
    msg('rErr', '');
    var rel = 'Grade' + S.grade + '/' + S.fileName;
    var s = Portal.session();
    if (s && (s.tests || []).some(function (t) { return t.file === rel; }) &&
        !confirm('A test named ' + S.fileName + ' already exists for Grade ' + S.grade + '. Replace it? Students keep their earlier results.')) return;
    S.title = $('title').value.trim();
    publish();
  };

  // ---------- 3. Publish ----------
  var STEPS = ['Check the page', 'Save the page to GitHub', 'Write the test and answers to the Google Sheet', 'Wait for the live website to update'];
  function mark(i, state, detail) {
    var li = $('check').children[i];
    li.className = state;
    li.querySelector('.ic').textContent = state === 'ok' ? '✓' : state === 'fail' ? '✕' : state === 'run' ? '…' : String(i + 1);
    li.querySelector('small').textContent = detail || '';
  }
  var ERRORS = {
    network: 'No connection to the backend. Check the internet and try again.',
    not_configured: 'The site is not connected to its backend yet (docs/config.js).',
    signed_out: 'Your sign-in has expired. Sign in again on the home page, then come back.',
    not_staff: 'Only a Teacher or Admin account can publish.',
    unknown_action: 'The backend is an older version. In Apps Script: Deploy → Manage deployments → Edit → Version: New version.',
    github_not_set: 'Publishing from the browser is not set up yet: add GITHUB_REPO and GITHUB_TOKEN in Apps Script (see README).',
    github_token_rejected: 'GitHub did not accept the token in Apps Script. It may have expired – make a new one.',
    github_no_access: 'The GitHub token in Apps Script cannot write to the repo. Give it "Contents: Read and write".',
    github_conflict: 'The page changed on GitHub at the same moment. Press Try again.',
    answers_in_page: 'The page still contained answers, so it was not published.',
    unsafe_markup: 'The questions contain markup that is not allowed on the site.',
    server_error: 'The backend had a problem. Try again in a minute.'
  };
  function explain(code) { return ERRORS[code] || ('Stopped: ' + code); }

  function publish() {
    if (S.busy) return;
    S.busy = true;
    step(3);
    var list = $('check'); list.innerHTML = '';
    STEPS.forEach(function (t) {
      var li = document.createElement('li');
      li.innerHTML = '<span class="ic"></span><span><b></b><small></small></span>';
      li.querySelector('b').textContent = t;
      list.appendChild(li);
    });
    STEPS.forEach(function (t, i) { mark(i, '', ''); });
    msg('pErr', ''); $('pDone').hidden = true; $('openLive').hidden = true; $('another').hidden = true; $('retry').hidden = true;

    var d = S.draft, rel = 'Grade' + S.grade + '/' + S.fileName, id = C.testId(S.grade, S.fileName);
    mark(0, 'run', rel);
    var html = C.build(S.tpl, d, S.grade, S.title, id);
    if (C.hasAnswers(html)) return stop(0, 'The page still contained answers, so it was not published.');
    mark(0, 'ok', rel + ' · Test ID ' + id + ' · answers removed from the page');

    var s = Portal.session();
    if (!s) return stop(1, ERRORS.signed_out);
    if (!window.PORTAL_API) return stop(1, ERRORS.not_configured);
    mark(1, 'run', 'Sending…');
    mark(2, 'run', 'Waiting for GitHub');
    var body = {
      action: 'publishTest', token: s.token, grade: S.grade, testId: id, title: S.title, file: rel, html: html,
      questions: d.questions.map(function (q, i) { return { no: i + 1, correct: 'ABCD'.charAt(q.correctIndex), explanation: C.sanitize(q.explanationHtml) }; })
    };
    fetch(window.PORTAL_API, { method: 'POST', body: JSON.stringify(body), redirect: 'follow' })
      .then(function (r) { return r.json(); })
      .catch(function () { return { ok: false, error: 'network' }; })
      .then(function (r) {
        if (!r || !r.ok) {
          var code = (r && r.error) || 'network';
          if (code.indexOf('sheet_') === 0) {
            mark(1, 'ok', 'Saved' + (r.commit ? ' · ' + r.commit.substring(0, 7) : ''));
            return stop(2, 'The page is on GitHub, but the Sheet did not accept the test: ' + code.substring(6) + '. Press Try again.');
          }
          mark(2, '', '');
          return stop(1, explain(code));
        }
        mark(1, 'ok', 'Saved to GitHub' + (r.commit ? ' · ' + r.commit.substring(0, 7) : ''));
        mark(2, 'ok', 'Tests row + ' + r.questions + ' answers saved');
        Portal.refreshTests(function () {});
        waitLive(ROOT + rel, html);
      });
  }
  function waitLive(url, html) {
    mark(3, 'run', 'Waiting for ' + url);
    var until = Date.now() + 6 * 60000, want = html.replace(/\r\n/g, '\n');
    (function poll() {
      fetch(url + '?v=' + Date.now(), { cache: 'no-store' })
        .then(function (r) { return r.ok ? r.text() : ''; })
        .catch(function () { return ''; })
        .then(function (t) {
          if (t && t.replace(/\r\n/g, '\n') === want) {
            mark(3, 'ok', url);
            S.busy = false; S.published = true;
            $('pDone').textContent = '🎉 Published! Answer key: ' + C.answerKey(S.draft);
            $('pDone').hidden = false;
            $('openLive').href = url; $('openLive').hidden = false; $('another').hidden = false;
            return;
          }
          if (Date.now() > until) return stop(3, 'Not live after 6 minutes. Check that GitHub Pages is on (branch main, folder /docs). The test is saved – it will appear when Pages updates.');
          setTimeout(poll, 10000);
        });
    })();
  }
  function stop(i, text) {
    mark(i, 'fail', '');
    msg('pErr', text);
    S.busy = false;
    $('retry').hidden = false;
  }
  $('retry').onclick = publish;
  $('copyKey').onclick = function () { copy(C.answerKey(S.draft), 'Answer key copied.'); };
  $('another').onclick = function () {
    S.draft = null; S.published = false; S.grade = null; pressGrade();
    $('fileInfo').hidden = true; msg('iWarn', ''); ready(); step(1);
  };
})();
