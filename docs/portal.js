/* Shared sign-in session and backend calls. Works from the site root and from GradeN/ folders. */
var Portal = (function () {
  var KEY = 'mathStudioSession';
  // Folder that holds portal.js (the site root), so links work from any page depth.
  var ROOT = (function () {
    var s = document.currentScript && document.currentScript.src;
    return s ? s.replace(/portal\.js(\?.*)?$/, '') : '';
  })();
  function home() { return ROOT + 'index.html'; }
  function api() { return window.PORTAL_API || ''; }
  function session() {
    try {
      var s = JSON.parse(sessionStorage.getItem(KEY) || 'null');
      if (s && s.token && s.exp > Date.now()) return s;
    } catch (e) {}
    return null;
  }
  function setSession(s) { try { sessionStorage.setItem(KEY, JSON.stringify(s)); } catch (e) {} }
  function clear() { try { sessionStorage.removeItem(KEY); } catch (e) {} }
  function signOut() { clear(); location.href = home(); }
  function call(body, cb) {
    if (!api() || !window.fetch) { cb({ ok: false, error: 'not_configured' }); return; }
    fetch(api(), { method: 'POST', body: JSON.stringify(body), redirect: 'follow' })
      .then(function (r) { return r.json(); })
      .then(function (j) { cb(j || { ok: false, error: 'network' }); })
      .catch(function () { cb({ ok: false, error: 'network' }); });
  }
  function login(grade, name, password, cb) {
    call({ action: 'login', grade: grade, name: name, password: password }, function (r) {
      if (r.ok) setSession({ token: r.token, name: r.name, role: r.role, grade: r.grade, exp: r.exp, tests: r.tests || [] });
      cb(r);
    });
  }
  /* A new student creates an account (name, password, boy or girl, grade) and is signed in straight away. */
  function register(grade, name, password, gender, cb) {
    call({ action: 'register', grade: grade, name: name, password: password, gender: gender }, function (r) {
      if (r.ok) setSession({ token: r.token, name: r.name, role: r.role, grade: r.grade, exp: r.exp, tests: r.tests || [] });
      cb(r);
    });
  }
  function refreshTests(cb) {
    var s = session();
    if (!s) { cb({ ok: false, error: 'signed_out' }); return; }
    call({ action: 'tests', token: s.token }, function (r) {
      if (r.ok) { s.tests = r.tests || []; setSession(s); }
      else if (r.error === 'signed_out') clear();
      cb(r);
    });
  }
  /* Top of every test page: visitors who are not signed in (or in another grade) go to the sign-in page. */
  function requireLogin(grade) {
    var s = session();
    if (!s || (s.role === 'student' && String(s.grade) !== String(grade))) { location.replace(home()); return null; }
    return s;
  }
  /* Sends the picked options; the server grades, saves the result and returns score, correct options and explanations. */
  function submitTest(data, cb) {
    var s = session();
    if (!s) { cb({ ok: false, error: 'signed_out' }); return; }
    data.action = 'submitTest'; data.token = s.token;
    call(data, function (r) { if (r.error === 'signed_out') clear(); cb(r); });
  }
  return { session: session, login: login, register: register, refreshTests: refreshTests, requireLogin: requireLogin, submitTest: submitTest, signOut: signOut, home: home };
})();
