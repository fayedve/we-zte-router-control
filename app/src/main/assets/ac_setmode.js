(function () {
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
  var rows = document.querySelectorAll('tr');
  var found = false;
  var changed = false;
  var mode = '';
  for (var i = 0; i < rows.length; i++) {
    var row = rows[i];
    if (!/SSID\s*1\b/i.test(txt(row))) { continue; }
    var radios = row.querySelectorAll('input[type=radio]');
    if (!radios.length) { continue; }
    found = true;
    var cells = row.querySelectorAll('td, th');
    for (var c = 0; c < cells.length; c++) {
      var radio = cells[c].querySelector('input[type=radio]');
      if (!radio) { continue; }
      if (!/black\s*list/i.test(txt(cells[c]))) { continue; }
      if (!radio.checked) { radio.click(); changed = true; }
      mode = 'Black List';
      break;
    }
    if (!mode) {
      for (var k = 0; k < radios.length; k++) {
        if (!/black\s*list/i.test(txt(radios[k].parentNode))) { continue; }
        if (!radios[k].checked) { radios[k].click(); changed = true; }
        mode = 'Black List';
        break;
      }
    }
    break;
  }
  var applied = false;
  if (changed) { applied = clickApply(); }
  var error = '';
  if (!found) { error = 'ssid1-row-not-found'; }
  else if (!mode) { error = 'black-list-option-not-found'; }
  else if (changed && !applied) { error = 'apply-not-found'; }
  report({ ok: found && mode !== '' && (!changed || applied), changed: changed, applied: applied, mode: mode, error: error });
})();
