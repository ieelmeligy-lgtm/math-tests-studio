/**
 * Math Tests Studio – backend for the Google Sheet.
 * Deploy: Deploy > New deployment > Web app, Execute as: Me, Who has access: Anyone.
 * After code changes: Deploy > Manage deployments > edit > Version: New version (keeps the same URL).
 *
 * Tabs (created by setup()):
 *   Users     : Name | PW | Role | Grade | Gender   Role = Student, Teacher or Admin. Staff use Grade NA.
 *               Students can create their own account on the sign-in page (Role Student, Gender Boy or Girl).
 *   Tests     : Grade | Test ID | Title | Questions | File | Active
 *   Questions : Test ID | No. | Correct | Explanation      (the answer key – never sent to students before they submit)
 *   Results   : Timestamp | Name | Role | Grade | Test ID | Test title | Score | Total | Percent | Attempt | Answers
 *
 * Publishing from the iPad (Studio page): the page is saved to GitHub from here, so Safari needs no git.
 *   Project Settings > Script properties:
 *     GITHUB_REPO  = owner/math-tests-studio
 *     GITHUB_TOKEN = a fine-grained token for that one repo only, with "Contents: Read and write"
 *   Then run checkGitHub() once from the editor (it asks for permission to connect to GitHub).
 *
 * @OnlyCurrentDoc
 */

var TABS = {
  Users: ['Name', 'PW', 'Role', 'Grade', 'Gender'],
  Tests: ['Grade', 'Test ID', 'Title', 'Questions', 'File', 'Active'],
  Questions: ['Test ID', 'No.', 'Correct', 'Explanation'],
  Results: ['Timestamp', 'Name', 'Role', 'Grade', 'Test ID', 'Test title', 'Score', 'Total', 'Percent', 'Attempt', 'Answers']
};
var SESSION_HOURS = 12;
var MAX_TRIES = 5, LOCK_MINUTES = 10;

/** Run once from the editor: creates missing tabs and the signing secret. */
function setup() {
  var ss = SpreadsheetApp.getActive();
  Object.keys(TABS).forEach(function (name) {
    var sh = ss.getSheetByName(name) || ss.insertSheet(name);
    if (sh.getLastRow() === 0) {
      sh.appendRow(TABS[name]);
      sh.setFrozenRows(1);
      sh.getRange(1, 1, 1, TABS[name].length).setFontWeight('bold');
    }
  });
  var props = PropertiesService.getScriptProperties();
  if (!props.getProperty('SECRET')) props.setProperty('SECRET', Utilities.getUuid() + Utilities.getUuid());
}

function doGet() {
  return json_({ ok: true, app: 'Math Tests Studio backend' });
}

function doPost(e) {
  var body;
  try { body = JSON.parse(e.postData.contents); } catch (err) { return json_({ ok: false, error: 'bad_request' }); }
  try {
    switch (body.action) {
      case 'ping': return json_(ping_());
      case 'login': return json_(login_(body));
      case 'register': return json_(register_(body));
      case 'tests': return json_(withUser_(body, function (u) { return { ok: true, tests: testsFor_(u) }; }));
      case 'submitTest': return json_(withUser_(body, function (u) { return submitTest_(u, body); }));
      case 'addTest': return json_(withUser_(body, function (u) { return addTest_(u, body); }));
      case 'publishTest': return json_(withUser_(body, function (u) { return publishTest_(u, body); }));
      default: return json_({ ok: false, error: 'unknown_action' });
    }
  } catch (err) {
    console.error(err);
    return json_({ ok: false, error: 'server_error' });
  }
}

// ---------- actions ----------

function ping_() {
  var ss = SpreadsheetApp.getActive();
  return {
    ok: true,
    tabs: Object.keys(TABS).map(function (n) { return { name: n, ok: !!ss.getSheetByName(n) }; }),
    github: githubSet_(),
    time: new Date().toISOString()
  };
}

function login_(b) {
  var key = norm_(b.name);
  if (!key || !b.password) return { ok: false, error: 'missing' };
  var cache = CacheService.getScriptCache(), lockKey = 'tries:' + key;
  var tries = Number(cache.get(lockKey) || 0);
  if (tries >= MAX_TRIES) return { ok: false, error: 'locked' };

  var wantStaff = String(b.grade).toLowerCase() === 'staff';
  var found = null;
  rows_('Users').forEach(function (r) {
    if (found || norm_(r.Name) !== key || String(r.PW) !== String(b.password)) return;
    var role = String(r.Role).trim().toLowerCase();
    var staff = role === 'teacher' || role === 'admin';
    if (wantStaff ? staff : (!staff && String(r.Grade).trim() === String(b.grade).trim())) found = r;
  });
  if (!found) {
    cache.put(lockKey, String(tries + 1), LOCK_MINUTES * 60);
    return { ok: false, error: 'not_correct' };
  }
  cache.remove(lockKey);
  var user = { name: String(found.Name).trim(), role: String(found.Role).trim().toLowerCase(), grade: String(found.Grade).trim() };
  var exp = Date.now() + SESSION_HOURS * 3600 * 1000;
  return { ok: true, token: sign_({ n: user.name, r: user.role, g: user.grade, exp: exp }), name: user.name, role: user.role, grade: user.grade, exp: exp, tests: testsFor_(user) };
}

/**
 * A student creates an account: name, password, boy or girl, and the grade chosen on the sign-in page.
 * Always Role Student. Names must be unique (case and spaces ignored, as at sign-in). Signs the student in.
 */
function register_(b) {
  var name = String(b.name || '').replace(/\s+/g, ' ').trim();
  var pw = String(b.password || '');
  var gender = { boy: 'Boy', girl: 'Girl' }[String(b.gender || '').toLowerCase()];
  var grade = String(b.grade || '').trim();
  if (!/^[A-Za-z؀-ۿ][A-Za-z؀-ۿ0-9 .'_-]{1,39}$/.test(name)) return { ok: false, error: 'bad_name' };
  if (pw.length < 4 || pw.length > 30 || /[\u0000-\u001f]/.test(pw) || /^[=+\-@]/.test(pw)) return { ok: false, error: 'bad_password' };
  if (!gender) return { ok: false, error: 'bad_gender' };
  if (!/^\d{1,2}$/.test(grade)) return { ok: false, error: 'bad_grade' };

  // At most 40 new accounts every 10 minutes, so the Sheet cannot be flooded.
  var cache = CacheService.getScriptCache(), n = Number(cache.get('registrations') || 0);
  if (n >= 40) return { ok: false, error: 'too_many' };

  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    var key = norm_(name);
    if (rows_('Users').some(function (r) { return norm_(r.Name) === key; })) return { ok: false, error: 'name_taken' };
    var sh = sheet_('Users');
    var head = sh.getRange(1, 1, 1, Math.max(sh.getLastColumn(), 1)).getValues()[0].map(String);
    if (head.indexOf('Gender') < 0) { head.push('Gender'); sh.getRange(1, head.length).setValue('Gender').setFontWeight('bold'); }
    var values = { Name: name, PW: pw, Role: 'Student', Grade: grade, Gender: gender };
    var row = head.map(function (h) { return values[h] !== undefined ? values[h] : ''; });
    // Plain-text cells, so a password like 0123 keeps its leading zero.
    sh.getRange(sh.getLastRow() + 1, 1, 1, row.length).setNumberFormat('@').setValues([row]);
    cache.put('registrations', String(n + 1), 600);
  } finally { lock.releaseLock(); }
  return login_({ grade: grade, name: name, password: pw });
}

function testsFor_(u) {
  var staff = u.role !== 'student';
  var mine = rows_('Results').filter(function (r) { return norm_(r.Name) === norm_(u.name); });
  return rows_('Tests').filter(function (t) {
    return String(t.Active).trim().toLowerCase() !== 'no' && (staff || String(t.Grade).trim() === u.grade);
  }).map(function (t) {
    var id = String(t['Test ID']);
    var res = mine.filter(function (r) { return String(r['Test ID']) === id; });
    var best = res.reduce(function (m, r) { return Math.max(m, Number(r.Score) || 0); }, 0);
    return { grade: String(t.Grade), id: id, title: String(t.Title), questions: Number(t.Questions) || 0, file: String(t.File), attempts: res.length, best: best, total: Number(t.Questions) || 0 };
  });
}

/** Grades on the server against the Questions tab, saves a Results row, and returns the answers. */
function submitTest_(u, b) {
  var test = rows_('Tests').filter(function (t) { return String(t['Test ID']) === String(b.testId); })[0];
  if (!test) return { ok: false, error: 'no_test' };
  if (u.role === 'student' && String(test.Grade).trim() !== u.grade) return { ok: false, error: 'wrong_grade' };
  var key = rows_('Questions').filter(function (q) { return String(q['Test ID']) === String(b.testId); })
    .sort(function (a, c) { return Number(a['No.']) - Number(c['No.']); });
  if (!key.length) return { ok: false, error: 'no_answers' };
  var picks = Array.isArray(b.picks) ? b.picks : [];
  var L = 'ABCD', score = 0, parts = [], correct = [], explanations = [];
  key.forEach(function (q, i) {
    var c = L.indexOf(String(q.Correct).trim().toUpperCase());
    var p = Number(picks[i]);
    var ok = p === c;
    if (ok) score++;
    correct.push(c);
    explanations.push(String(q.Explanation || ''));
    parts.push((i + 1) + (L.charAt(p) || '-') + (ok ? '✓' : '✗(' + L.charAt(c) + ')'));
  });
  var total = key.length, pct = Math.round(score / total * 100);
  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    sheet_('Results').appendRow([new Date(), u.name, u.role, u.grade, String(b.testId), String(test.Title), score, total, pct, Number(b.attempt) || 1, parts.join(' ')]);
  } finally { lock.releaseLock(); }
  return { ok: true, saved: true, score: score, total: total, percent: pct, correct: correct, explanations: explanations };
}

/** Staff only: adds or replaces a test row and its answer key. Used by the desktop app. */
function addTest_(u, b) {
  if (u.role !== 'teacher' && u.role !== 'admin') return { ok: false, error: 'not_staff' };
  var id = String(b.testId || '').trim();
  if (!/^[\w-]+$/.test(id)) return { ok: false, error: 'bad_test_id' };
  if (!/^(Grade\d+\/)?[\w-]+\.html$/.test(String(b.file))) return { ok: false, error: 'bad_file' };
  var qs = Array.isArray(b.questions) ? b.questions : [];
  if (!qs.length) return { ok: false, error: 'no_questions' };
  for (var i = 0; i < qs.length; i++) if ('ABCD'.indexOf(String(qs[i].correct)) < 0 || String(qs[i].correct).length !== 1) return { ok: false, error: 'bad_answer_' + (i + 1) };

  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    deleteRows_('Tests', 'Test ID', id);
    deleteRows_('Questions', 'Test ID', id);
    sheet_('Tests').appendRow([String(b.grade), id, String(b.title), qs.length, String(b.file), 'Yes']);
    var rows = qs.map(function (q, k) { return [id, k + 1, String(q.correct), String(q.explanation || '')]; });
    var qsh = sheet_('Questions');
    qsh.getRange(qsh.getLastRow() + 1, 1, rows.length, 4).setValues(rows);
  } finally { lock.releaseLock(); }
  return { ok: true, testId: id, questions: qs.length };
}

/**
 * Staff only, used by the Studio page (iPad / Safari): saves the test page to GitHub, then writes the test row
 * and answer key like addTest. The page must be built by the Studio and must not contain the answers.
 */
function publishTest_(u, b) {
  if (u.role !== 'teacher' && u.role !== 'admin') return { ok: false, error: 'not_staff' };
  if (!githubSet_()) return { ok: false, error: 'github_not_set' };
  var file = String(b.file || ''), id = String(b.testId || '').trim(), html = String(b.html || '');
  if (!/^Grade\d+\/[\w-]+\.html$/.test(file)) return { ok: false, error: 'bad_file' };
  if (!/^[\w-]+$/.test(id)) return { ok: false, error: 'bad_test_id' };
  if (!/^\d+$/.test(String(b.grade)) || file.indexOf('Grade' + b.grade + '/') !== 0) return { ok: false, error: 'bad_grade' };
  if (html.length > 3000000) return { ok: false, error: 'too_big' };
  if (html.indexOf('<meta name="generator" content="Math Tests Studio">') < 0) return { ok: false, error: 'not_studio_page' };
  if (hasAnswers_(html)) return { ok: false, error: 'answers_in_page' };
  // The questions part may hold maths markup only: no scripts, frames, links or event handlers.
  var start = html.indexOf('<div id="quiz">'), end = html.indexOf('<div class="card jsonly" id="submitCard">');
  if (start < 0 || end < start) return { ok: false, error: 'not_studio_page' };
  if (/<script|<iframe|<object|<embed|<link|<style|<a\s|<[^>]*\son\w+\s*=|javascript:/i.test(html.substring(start, end))) return { ok: false, error: 'unsafe_markup' };
  var qs = Array.isArray(b.questions) ? b.questions : [];
  if (!qs.length) return { ok: false, error: 'no_questions' };
  for (var i = 0; i < qs.length; i++) if (!/^[ABCD]$/.test(String(qs[i].correct))) return { ok: false, error: 'bad_answer_' + (i + 1) };
  if (html.substring(start, end).split('<div class="card" id="q').length - 1 !== qs.length) return { ok: false, error: 'count_mismatch' };

  var saved = githubSave_('docs/' + file, html, 'Add test: Grade ' + b.grade + ' – ' + String(b.title || id) + ' (Studio, ' + u.name + ')');
  if (!saved.ok) return saved;
  var sheet = addTest_(u, b);
  if (!sheet.ok) return { ok: false, error: 'sheet_' + sheet.error, commit: saved.commit };
  return { ok: true, testId: id, questions: qs.length, commit: saved.commit };
}

/** Run once from the editor after adding GITHUB_REPO and GITHUB_TOKEN: checks the token can see the repo. */
function checkGitHub() {
  if (!githubSet_()) throw new Error('Add GITHUB_REPO and GITHUB_TOKEN in Project Settings > Script properties first.');
  var r = github_('get', '');
  if (r.code !== 200) throw new Error('GitHub answered ' + r.code + ': ' + (r.body && r.body.message));
  if (!r.body.permissions || !r.body.permissions.push) throw new Error('The token cannot write to ' + r.body.full_name + '. Give it "Contents: Read and write".');
  Logger.log('OK: ' + r.body.full_name + ' – commits will be made as ' + commitIdentity_().email);
}

function githubSet_() {
  var p = PropertiesService.getScriptProperties();
  return !!(p.getProperty('GITHUB_TOKEN') && /^[\w.-]+\/[\w.-]+$/.test(p.getProperty('GITHUB_REPO') || ''));
}

/** Creates or replaces one file on the main branch. Retries once if the file changed meanwhile. */
function githubSave_(path, text, message) {
  for (var attempt = 0; attempt < 2; attempt++) {
    var cur = github_('get', '/contents/' + path + '?ref=main');
    if (cur.code !== 200 && cur.code !== 404) return { ok: false, error: githubError_(cur) };
    var who = commitIdentity_();
    var body = { message: message, content: Utilities.base64Encode(text, Utilities.Charset.UTF_8), branch: 'main', committer: who, author: who };
    if (cur.code === 200) body.sha = cur.body.sha;
    var put = github_('put', '/contents/' + path, body);
    if (put.code === 200 || put.code === 201) return { ok: true, commit: put.body.commit && put.body.commit.sha };
    if (put.code !== 409) return { ok: false, error: githubError_(put) };
  }
  return { ok: false, error: 'github_conflict' };
}

/** Commits use the account's private GitHub address (id+login@users.noreply.github.com), never a real email. */
function commitIdentity_() {
  var p = PropertiesService.getScriptProperties(), email = p.getProperty('GITHUB_EMAIL');
  if (!email) {
    var r = github_('get', null, null, 'https://api.github.com/user');
    if (r.code !== 200) throw new Error('GitHub user lookup failed: ' + r.code);
    email = r.body.id + '+' + r.body.login + '@users.noreply.github.com';
    p.setProperty('GITHUB_EMAIL', email);
  }
  return { name: 'Math Tests Studio', email: email };
}

function github_(method, path, payload, fullUrl) {
  var p = PropertiesService.getScriptProperties();
  var url = fullUrl || ('https://api.github.com/repos/' + p.getProperty('GITHUB_REPO') + path);
  var opt = {
    method: method, muteHttpExceptions: true,
    headers: { Authorization: 'Bearer ' + p.getProperty('GITHUB_TOKEN'), Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28' }
  };
  if (payload) { opt.contentType = 'application/json'; opt.payload = JSON.stringify(payload); }
  var res = UrlFetchApp.fetch(url, opt), body = null;
  try { body = JSON.parse(res.getContentText() || 'null'); } catch (e) {}
  return { code: res.getResponseCode(), body: body };
}

function githubError_(r) {
  if (r.code === 401) return 'github_token_rejected';
  if (r.code === 403 || r.code === 404) return 'github_no_access';
  return 'github_failed_' + r.code;
}

/** Same check as the desktop app and the git hook: a correct option marked data-c, or a filled explanation. */
function hasAnswers_(html) {
  return /<input\b[^>]*\bdata-c\b/i.test(html) || /class="why"\s*>\s*(?!<\/div>)\S/.test(html);
}

// ---------- helpers ----------

function withUser_(b, fn) {
  var u = verify_(b.token);
  if (!u) return { ok: false, error: 'signed_out' };
  return fn(u);
}

function sign_(payload) {
  var data = Utilities.base64EncodeWebSafe(JSON.stringify(payload));
  return data + '.' + mac_(data);
}

function verify_(token) {
  if (!token || String(token).indexOf('.') < 0) return null;
  var parts = String(token).split('.');
  if (parts[1] !== mac_(parts[0])) return null;
  try {
    var p = JSON.parse(Utilities.newBlob(Utilities.base64DecodeWebSafe(parts[0])).getDataAsString());
    if (!p.exp || p.exp < Date.now()) return null;
    return { name: p.n, role: p.r, grade: p.g };
  } catch (e) { return null; }
}

function mac_(data) {
  var secret = PropertiesService.getScriptProperties().getProperty('SECRET');
  if (!secret) throw new Error('Run setup() first');
  return Utilities.base64EncodeWebSafe(Utilities.computeHmacSha256Signature(data, secret));
}

function sheet_(name) {
  var sh = SpreadsheetApp.getActive().getSheetByName(name);
  if (!sh) throw new Error('Missing tab ' + name + ' – run setup()');
  return sh;
}

function rows_(name) {
  var v = sheet_(name).getDataRange().getValues();
  var head = v.shift() || [];
  return v.filter(function (r) { return r.join('') !== ''; }).map(function (r) {
    var o = {};
    head.forEach(function (h, i) { o[h] = r[i]; });
    return o;
  });
}

function deleteRows_(name, col, value) {
  var sh = sheet_(name), v = sh.getDataRange().getValues(), c = v[0].indexOf(col);
  for (var i = v.length - 1; i >= 1; i--) if (String(v[i][c]) === value) sh.deleteRow(i + 1);
}

function norm_(s) { return String(s || '').toLowerCase().replace(/\s+/g, ''); }

function json_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}
