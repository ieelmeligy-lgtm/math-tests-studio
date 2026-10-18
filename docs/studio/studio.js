/*
 * Math Tests Studio on the web (iPad, phone, any browser) – courtesy of Miss Sarah Awad, Math Teacher.
 * Does what the desktop app does: import a test file made in Claude, review it, and publish it.
 * The page is rebuilt without the answers; the Apps Script saves it to GitHub and the answer key to the Sheet.
 *
 * StudioCore (import, clean, build) mirrors the desktop classes TestImporter, Html, PageBuilder and AnswerGuard,
 * so both produce the same page. studio/test-template.txt is a copy of app/src/main/resources/test-template.html.
 */
var StudioCore = (function () {
  'use strict';

  // ---------- Html: keep only the maths markup the test pages use ----------
  var SVG = ['svg', 'g', 'circle', 'ellipse', 'line', 'rect', 'path', 'polygon', 'polyline', 'text', 'tspan'];
  var SVG_ATTRS = ['class', 'cx', 'cy', 'r', 'rx', 'ry', 'x', 'y', 'x1', 'y1', 'x2', 'y2', 'dx', 'dy', 'width', 'height', 'd',
    'points', 'fill', 'stroke', 'stroke-width', 'stroke-dasharray', 'transform', 'text-anchor', 'font-size', 'font-weight',
    'viewbox', 'role', 'aria-label'];
  var CLASSES = ['frac', 'blank', 'circle', 'rep', 'ov', 'nw', 'm', 'mb', 'seq', 'rad', 'rt', 'ri', 'venn', 'vl', 'vm', 'vr', 'venn-svg', 'lab'];
  var PURIFY = {
    ALLOWED_TAGS: ['b', 'i', 'em', 'strong', 'u', 'sup', 'sub', 'small', 'br', 'span'].concat(SVG),
    ALLOWED_ATTR: ['dir'].concat(SVG_ATTRS),
    ALLOW_DATA_ATTR: false,
    ALLOW_ARIA_ATTR: true
  };
  var hooked = false;
  function hook() {
    if (hooked) return;
    hooked = true;
    DOMPurify.addHook('afterSanitizeAttributes', function (n) {
      if (!n.getAttribute) return;
      if (n.hasAttribute('class')) {
        var keep = String(n.getAttribute('class')).split(/\s+/).filter(function (c) { return CLASSES.indexOf(c) >= 0; });
        if (keep.length) n.setAttribute('class', keep.join(' ')); else n.removeAttribute('class');
      }
      if (n.hasAttribute('dir') && !/^(ltr|rtl)$/.test(n.getAttribute('dir'))) n.removeAttribute('dir');
      ['fill', 'stroke', 'transform'].forEach(function (a) {
        var v = n.getAttribute(a);
        if (v !== null && (!/^[#\w\s.,()\-%]*$/.test(v) || /url/i.test(v))) n.removeAttribute(a);
      });
    });
  }
  function sanitize(html) {
    if (!html || !String(html).trim()) return '';
    hook();
    return String(DOMPurify.sanitize(String(html), PURIFY)).trim();
  }
  function escape(s) {
    return s == null ? '' : String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }
  function text(html) {
    var d = new DOMParser().parseFromString('<body>' + html + '</body>', 'text/html');
    return (d.body.textContent || '').replace(/\s+/g, ' ').trim();
  }
  /** Plain readable text for the question list. */
  function toPlain(html) {
    if (!html) return '';
    var s = String(html).replace(/<span class="frac"><b>(.*?)<\/b><b>(.*?)<\/b><\/span>/g, '($1/$2)')
      .replace(/<sup[^>]*>(.*?)<\/sup>/g, '^$1')
      .replace(/<span class="blank"><\/span>/g, ' ____ ')
      .replace(/<span class="circle"><\/span>/g, ' \u25EF ')
      .replace(/<svg[\s\S]*?<\/svg>/g, ' [diagram] ');
    return text(s);
  }

  // ---------- AnswerGuard ----------
  function hasAnswers(html) {
    return /<input\b[^>]*\bdata-c\b/i.test(html) || /class="why"\s*>\s*(?!<\/div>)\S/.test(html);
  }

  // ---------- TestImporter ----------
  var GENERATOR = 'Math Tests Studio';
  var UNSURE = ' – tick the correct option.';

  function kind(html) {
    if (html.indexOf('<meta name="generator" content="' + GENERATOR + '"') >= 0) return 'PUBLISHED';
    return hasAnswers(html) ? 'NEW' : 'UNKNOWN';
  }
  function gradeFromPage(html) {
    var m = /requireLogin\(\s*'(\w+)'/.exec(html);
    return m ? m[1] : '';
  }
  function detectLang(doc) {
    var h = doc.documentElement;
    var lang = (h.getAttribute('lang') || '').toLowerCase();
    if (lang.indexOf('ar') === 0 || (h.getAttribute('dir') || '').toLowerCase() === 'rtl') return 'ar';
    if (lang.indexOf('en') === 0) return 'en';
    var t = doc.body ? doc.body.textContent : '', ar = 0, la = 0;
    for (var i = 0; i < t.length; i++) {
      var c = t.charCodeAt(i);
      if (c >= 0x0600 && c <= 0x06FF) ar++; else if ((c >= 65 && c <= 90) || (c >= 97 && c <= 122)) la++;
    }
    return ar > la ? 'ar' : 'en';
  }
  function optionOf(input) {
    var label = input.closest('label');
    if (label) return label.querySelector('.opt') || label;
    return input.nextElementSibling;
  }
  function read(html) {
    var doc = new DOMParser().parseFromString(html, 'text/html');
    var d = { lang: detectLang(doc), suggestedTitle: '', questions: [], skipped: [], notes: [] };
    var h1 = doc.querySelector('header h1') || doc.querySelector('h1');
    d.suggestedTitle = (h1 ? h1.textContent : doc.title || '').replace(/\s+/g, ' ').trim();

    var scope = doc.getElementById('quiz') || doc.body;
    var section = '', pos = 0;
    var els = scope.querySelectorAll('h2, h3, div.card');
    for (var e = 0; e < els.length; e++) {
      var el = els[e];
      if (el.tagName.toLowerCase() !== 'div') { section = sanitize(el.innerHTML); continue; }
      var stem = el.querySelector('.stem');
      if (!stem) continue;
      pos++;
      var num = el.querySelector('.num');
      var label = num && num.textContent.trim() ? num.textContent.trim() : String(pos);
      var plain = toPlain(stem.innerHTML);
      var shortText = plain.length > 50 ? plain.substring(0, 50) + '…' : plain;

      var inputs = el.querySelectorAll('input[type=radio]');
      if (!inputs.length) { d.skipped.push('Question ' + label + ' has no answer options (not multiple choice): ' + shortText); continue; }
      var digits = label.replace(/\D/g, '');
      var q = { number: digits ? parseInt(digits, 10) : pos, sectionHtml: section, stemHtml: sanitize(stem.innerHTML), optionsHtml: [],
        correctIndex: 0, explanationHtml: '', optionsWrittenByClaude: false, misprintNote: '' };
      var marked = 0, correct = -1;
      for (var k = 0; k < inputs.length; k++) {
        var opt = optionOf(inputs[k]);
        if (!opt) continue;
        var copy = opt.cloneNode(true);
        var lets = copy.querySelectorAll('.let');
        for (var x = 0; x < lets.length; x++) lets[x].parentNode.removeChild(lets[x]);
        q.optionsHtml.push(sanitize(copy.innerHTML));
        if (inputs[k].hasAttribute('data-c')) { marked++; correct = q.optionsHtml.length - 1; }
      }
      if (q.optionsHtml.length < 2) { d.skipped.push('Question ' + label + ': fewer than two options could be read: ' + shortText); continue; }
      if (q.optionsHtml.length > 4) {
        d.notes.push('Question ' + label + ' had ' + q.optionsHtml.length + ' options; only A to D were kept.');
        if (correct > 3) { correct = -1; marked = 0; }
        q.optionsHtml = q.optionsHtml.slice(0, 4);
      }
      if (marked === 1) q.correctIndex = correct;
      else {
        q.correctIndex = Math.max(0, correct);
        q.misprintNote = (marked === 0 ? 'No correct answer was marked in the file' : 'More than one option was marked correct in the file') + UNSURE;
      }
      var why = el.querySelector('.why');
      if (why) {
        var w = why.cloneNode(true);
        var pres = w.querySelectorAll('strong.pre');
        for (var y = 0; y < pres.length; y++) pres[y].parentNode.removeChild(pres[y]);
        var first = w.firstElementChild;
        if (first && first.tagName.toLowerCase() === 'strong' && /^\s*(Correct|الإجابة|إجابة|الاجابة)/.test(first.textContent)) first.parentNode.removeChild(first);
        q.explanationHtml = sanitize(w.innerHTML);
      }
      if (!q.explanationHtml.trim()) d.notes.push('Question ' + label + ' has no explanation.');
      var note = (el.getAttribute('data-note') || '').trim();
      if (note) q.misprintNote = q.misprintNote ? q.misprintNote + ' ' + note : note;
      q.optionsWrittenByClaude = (el.getAttribute('data-options') || '').trim().toLowerCase() === 'claude';
      d.questions.push(q);
    }
    return d;
  }

  function answerKey(d) {
    return d.questions.map(function (q, i) { return (i + 1) + '-' + 'ABCD'.charAt(q.correctIndex); }).join(', ');
  }

  // ---------- PageBuilder ----------
  var EN = {
    myTests: 'My tests',
    notice: 'You are viewing this test in preview mode. Open it online and sign in to submit your answers and see your score.',
    print: 'Print / save result', again: 'Try again', submit: 'Submit my answers',
    footer: 'Choose the correct answer for each question, then press Submit.',
    answered: '{n} of {N} answered', left1: 'You still have 1 question to answer.', leftN: 'You still have {n} questions to answer.',
    checking: 'Checking your answers…', expired: 'Your sign-in has expired. Please sign in again.',
    failed: 'Your answers could not be checked. Check your internet and press Submit again.',
    correct: 'Correct. ', correctAnswer: 'Correct answer: {L}. ', saved: 'Your result is saved.', signedIn: 'Signed in as {name}',
    note100: 'Excellent! Full marks.', note80: 'Very good work. Check the questions marked in red.',
    note50: 'Good try. Read the explanations and try again.', note0: 'Keep practising. Read each explanation below, then try again.'
  };
  var AR = {
    myTests: 'اختباراتي',
    notice: 'أنت تشاهد الاختبار في وضع المعاينة. افتحه عبر الإنترنت وسجّل الدخول لإرسال إجاباتك ومعرفة نتيجتك.',
    print: 'طباعة / حفظ النتيجة', again: 'حاول مرة أخرى', submit: 'أرسل إجاباتي',
    footer: 'اختر الإجابة الصحيحة لكل سؤال، ثم اضغط «أرسل إجاباتي».',
    answered: 'تمت الإجابة عن {n} من {N}', left1: 'بقي سؤال واحد بدون إجابة.', leftN: 'بقي {n} من الأسئلة بدون إجابة.',
    checking: 'جارٍ تصحيح إجاباتك…', expired: 'انتهت مدة تسجيل الدخول. يرجى تسجيل الدخول مرة أخرى.',
    failed: 'تعذّر تصحيح إجاباتك. تحقق من الإنترنت واضغط «أرسل إجاباتي» مرة أخرى.',
    correct: 'إجابة صحيحة. ', correctAnswer: 'الإجابة الصحيحة: {L}. ', saved: 'تم حفظ نتيجتك.', signedIn: 'تم تسجيل الدخول باسم {name}',
    note100: 'ممتاز! الدرجة كاملة.', note80: 'عمل جيد جدًا. راجع الأسئلة المحددة باللون الأحمر.',
    note50: 'محاولة جيدة. اقرأ الشرح وحاول مرة أخرى.', note0: 'استمر في التدريب. اقرأ شرح كل سؤال ثم حاول مرة أخرى.'
  };
  var LETTERS_EN = ['A', 'B', 'C', 'D'], LETTERS_AR = ['أ', 'ب', 'ج', 'د'];
  var AR_GRADES = { 1: 'الأول', 2: 'الثاني', 3: 'الثالث', 4: 'الرابع', 5: 'الخامس', 6: 'السادس', 7: 'السابع', 8: 'الثامن', 9: 'التاسع' };

  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function slug(name) {
    var s = String(name).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
    if (!s) { var t = new Date(); s = 'test-' + t.getFullYear() + pad(t.getMonth() + 1) + pad(t.getDate()) + '-' + pad(t.getHours()) + pad(t.getMinutes()); }
    return s.length > 50 ? s.substring(0, 50).replace(/-$/, '') : s;
  }
  function safeFileName(fileName) {
    var base = String(fileName).replace(/\.html?$/i, '');
    return (/^[\w-]+$/.test(base) ? base : slug(base)) + '.html';
  }
  function testId(grade, fileName) { return 'g' + grade + '-' + String(fileName).replace(/\.html$/i, '').toLowerCase(); }
  function rep(s, key, val) { return s.split(key).join(val); }

  function build(tpl, d, grade, title, id) {
    var ar = d.lang === 'ar', t = ar ? AR : EN, letters = ar ? LETTERS_AR : LETTERS_EN, n = d.questions.length;
    var qs = '', section = '';
    for (var i = 0; i < n; i++) {
      var q = d.questions[i], sec = sanitize(q.sectionHtml);
      if (sec && sec !== section) qs += '  <h2 class="sec">' + sec + '</h2>\n';
      section = sec;
      qs += '  <div class="card" id="q' + i + '"><div class="q-head"><div class="num">' + (i + 1) + '</div><div class="stem">' + sanitize(q.stemHtml) + '</div></div>\n';
      qs += '   <div class="opts" role="radiogroup" aria-label="' + (ar ? 'السؤال ' : 'Question ') + (i + 1) + '">\n';
      for (var k = 0; k < q.optionsHtml.length; k++) {
        qs += '    <label class="ch"><input type="radio" name="q' + i + '" value="' + k + '"><span class="opt"><span class="let">' + letters[k] +
          '</span><span>' + sanitize(q.optionsHtml[k]) + '</span></span></label>\n';
      }
      qs += '   </div>\n   <div class="why"></div></div>\n';
    }
    var gw = ar ? (AR_GRADES[grade] || grade) : grade;
    var pageTitle = ar ? 'اختبار رياضيات الصف ' + gw + ' – ' + title : 'Grade ' + grade + ' Math Test – ' + title;
    var subtitle = ar ? 'رياضيات الصف ' + gw + ' · عدد الأسئلة: ' + n : 'Grade ' + grade + ' Math · ' + n + ' multiple choice questions';
    var testTitle = (ar ? 'الصف ' + gw : 'Grade ' + grade) + ' - ' + title;
    var js = {};
    Object.keys(t).forEach(function (k) { js[k] = t[k]; });
    js.letters = letters;
    var json = JSON.stringify(js).replace(/</g, '\\u003c');
    var out = tpl;
    out = rep(out, '{{LANG}}', ar ? 'ar' : 'en');
    out = rep(out, '{{DIR}}', ar ? 'rtl' : 'ltr');
    out = rep(out, '{{PAGE_TITLE}}', escape(pageTitle));
    out = rep(out, '{{HEADER_TITLE}}', escape(title));
    out = rep(out, '{{SUBTITLE}}', escape(subtitle));
    out = rep(out, '{{T_MY_TESTS}}', escape(t.myTests));
    out = rep(out, '{{T_NOTICE}}', escape(t.notice));
    out = rep(out, '{{T_PRINT}}', escape(t.print));
    out = rep(out, '{{T_AGAIN}}', escape(t.again));
    out = rep(out, '{{T_SUBMIT}}', escape(t.submit));
    out = rep(out, '{{T_FOOTER}}', escape(t.footer));
    out = rep(out, '{{T_ANSWERED_0}}', escape(t.answered.replace('{n}', '0').replace('{N}', String(n))));
    out = rep(out, '{{TEST_TITLE_JS}}', testTitle.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/</g, '\\x3c'));
    out = rep(out, '{{TEST_ID}}', id);
    out = rep(out, '{{GRADE}}', grade);
    out = rep(out, '{{T_JSON}}', json);
    out = rep(out, '{{QUESTIONS}}', qs);
    return out;
  }

  /** Private preview: no sign-in, and Submit grades in the browser so the teacher can check answers and explanations. */
  function preview(pageHtml, d) {
    var key = d.questions.map(function (q) { return q.correctIndex; });
    var expl = d.questions.map(function (q) { return sanitize(q.explanationHtml); });
    var data = JSON.stringify([key, expl]).replace(/</g, '\\u003c');
    var stub = '<script>var Portal={requireLogin:function(){return {name:\'Preview\'};},submitTest:function(d,cb){' +
      'var k=' + data + ',c=k[0],s=0,i;for(i=0;i<c.length;i++){if(d.picks[i]===c[i])s++;}' +
      'cb({ok:true,saved:false,score:s,total:c.length,percent:Math.round(s/c.length*100),correct:c,explanations:k[1]});}};<\/script>';
    return pageHtml.split('<script src="../config.js"></script>').join('')
      .split('<script src="../portal.js"></script>').join(stub)
      .split('href="../index.html"').join('href="#"');
  }

  return { sanitize: sanitize, escape: escape, toPlain: toPlain, hasAnswers: hasAnswers, kind: kind, read: read,
    gradeFromPage: gradeFromPage, answerKey: answerKey, safeFileName: safeFileName, testId: testId, build: build,
    preview: preview, UNSURE: UNSURE };
})();
