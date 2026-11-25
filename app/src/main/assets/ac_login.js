(function () {
  var USER = "__USER__";
  var PASS = "__PASS__";
  function txt(el) {
    return (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
  }
  function report(o) {
    if (window.Android && window.Android.onLoginResult) {
      window.Android.onLoginResult(JSON.stringify(o));
    }
  }
  function setVal(el, v) {
    el.focus();
    el.value = v;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }
  var docs = [document];
  for (var f = 0; f < window.frames.length; f++) {
    try { docs.push(window.frames[f].document); } catch (e) { }
  }
  for (var d = 0; d < docs.length; d++) {
    var doc = docs[d];
    var pw = doc.querySelector('input[type=password]');
    if (!pw) { continue; }
    var user = doc.querySelector('input#username') || doc.querySelector('input[name=username]') || doc.querySelector('input[name=user]') || doc.querySelector('input[type=text]');
    if (user) { setVal(user, USER); }
    setVal(pw, PASS);
    var cands = doc.querySelectorAll('input[type=submit], input[type=button], button, a');
    var clicked = false;
    for (var i = 0; i < cands.length; i++) {
      var t = (cands[i].value || txt(cands[i]) || '');
      if (/log\s*in|login|sign\s*in|دخول|تسجيل/i.test(t)) { cands[i].click(); clicked = true; break; }
    }
    report({ ok: clicked, submitted: clicked, error: clicked ? '' : 'login-button-not-found' });
    return;
  }
  report({ ok: false, error: 'password-field-not-found' });
})();
