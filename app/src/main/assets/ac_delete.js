(function () {
  var MAC = "__MAC__";
  function txt(el) {
    return (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
  }
  function report(o) {
    if (window.Android && window.Android.onActionResult) {
      window.Android.onActionResult(JSON.stringify(o));
    }
  }
  function clickApply() {
    var els = document.querySelectorAll('input[type=button], input[type=submit], button, a');
    for (var i = els.length - 1; i >= 0; i--) {
      var t = (txt(els[i]) || els[i].value || '').trim();
      if (/^apply$/i.test(t)) { els[i].click(); return true; }
    }
    return false;
  }
  var target = MAC.toLowerCase();
  var rows = document.querySelectorAll('tr');
  var row = null;
  for (var i = 0; i < rows.length; i++) {
    if (txt(rows[i]).toLowerCase().indexOf(target) >= 0) { row = rows[i]; }
  }
  if (!row) { report({ ok: false, error: 'rule-not-found' }); return; }
  var cands = row.querySelectorAll('a, img, span, button, input[type=button], i');
  var clicked = false;
  for (var c = 0; c < cands.length; c++) {
    var el = cands[c];
    var hint = '';
    if (el.getAttribute) {
      hint = (el.getAttribute('title') || '') + ' ' + (el.getAttribute('alt') || '') + ' ' + (el.getAttribute('onclick') || '') + ' ' + (el.getAttribute('class') || '');
    }
    hint = hint + ' ' + txt(el);
    if (/del|trash|remove|حذف/i.test(hint)) { el.click(); clicked = true; break; }
  }
  if (!clicked) { report({ ok: false, error: 'delete-icon-not-found' }); return; }
  var applied = clickApply();
  report({ ok: applied, applied: applied, error: applied ? '' : 'apply-not-found' });
})();
