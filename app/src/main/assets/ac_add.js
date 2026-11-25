(function () {
  var MAC = "__MAC__";
  var NAME = "__NAME__";
  function txt(el) {
    return (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
  }
  function report(o) {
    if (window.Android && window.Android.onActionResult) {
      window.Android.onActionResult(JSON.stringify(o));
    }
  }
  function setVal(el, v) {
    el.focus();
    el.value = v;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    el.dispatchEvent(new Event('blur', { bubbles: true }));
  }
  function clickApply() {
    var els = document.querySelectorAll('input[type=button], input[type=submit], button, a');
    for (var i = els.length - 1; i >= 0; i--) {
      var t = (txt(els[i]) || els[i].value || '').trim();
      if (/^apply$/i.test(t)) { els[i].click(); return true; }
    }
    return false;
  }
  function createItem() {
    var els = document.querySelectorAll('a, input[type=button], button, td, span');
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      var t = (txt(el) || el.value || '');
      if (!/create\s*new\s*item/i.test(t)) { continue; }
      var target = el;
      var p = el;
      var depth = 0;
      while (p && p.tagName && depth < 4) {
        var tag = p.tagName.toLowerCase();
        if (tag === 'a' || tag === 'button' || tag === 'input') { target = p; break; }
        p = p.parentNode;
        depth = depth + 1;
      }
      target.click();
      return true;
    }
    return false;
  }
  function ruleRows() {
    var rows = document.querySelectorAll('tr');
    var out = [];
    for (var i = 0; i < rows.length; i++) {
      if (rows[i].querySelector('input[type=text], input:not([type]), select')) { out.push(rows[i]); }
    }
    return out;
  }
  function fillMac(row, mac) {
    var hex = mac.split(/[^0-9a-fA-F]/).filter(function (x) { return x.length > 0; });
    var inputs = row.querySelectorAll('input');
    var byName = [];
    var shortOnes = [];
    for (var i = 0; i < inputs.length; i++) {
      var el = inputs[i];
      var hint = ((el.name || '') + ' ' + (el.id || '') + ' ' + (el.className || '')).toLowerCase();
      if (/mac/.test(hint)) { byName.push(el); }
      if (el.maxLength === 2) { shortOnes.push(el); }
    }
    if (byName.length === 1 && shortOnes.length < 2) { setVal(byName[0], mac); return 'single'; }
    var group = shortOnes.length >= 6 ? shortOnes : byName;
    if (group.length < 6 || hex.length < 6) { return ''; }
    for (var k = 0; k < 6; k++) { setVal(group[k], hex[k]); }
    return 'six';
  }
  function fillName(row, name) {
    var inputs = row.querySelectorAll('input[type=text], input:not([type])');
    var i;
    for (i = 0; i < inputs.length; i++) {
      var el = inputs[i];
      var hint = ((el.name || '') + ' ' + (el.id || '')).toLowerCase();
      if (/mac/.test(hint) || el.maxLength === 2) { continue; }
      if (/name/.test(hint)) { setVal(el, name); return true; }
    }
    for (i = 0; i < inputs.length; i++) {
      if (inputs[i].maxLength === 2) { continue; }
      setVal(inputs[i], name);
      return true;
    }
    return false;
  }
  function pickSsid(row) {
    var selects = row.querySelectorAll('select');
    for (var i = 0; i < selects.length; i++) {
      var opts = selects[i].options || [];
      for (var j = 0; j < opts.length; j++) {
        var t = (opts[j].text || opts[j].innerText || '').trim();
        if (/SSID\s*1\b/i.test(t)) {
          selects[i].selectedIndex = j;
          selects[i].dispatchEvent(new Event('change', { bubbles: true }));
          return t;
        }
      }
    }
    return '';
  }
  if (!createItem()) { report({ ok: false, error: 'create-item-not-found' }); return; }
  setTimeout(function () {
    var rows = ruleRows();
    if (!rows.length) { report({ ok: false, error: 'no-rule-rows' }); return; }
    var row = rows[rows.length - 1];
    var ssid = pickSsid(row);
    if (!ssid) { report({ ok: false, error: 'ssid1-option-not-found' }); return; }
    var macKind = fillMac(row, MAC);
    if (!macKind) { report({ ok: false, error: 'mac-field-not-found', ssid: ssid }); return; }
    fillName(row, NAME);
    var applied = clickApply();
    report({ ok: applied, ssid: ssid, macFields: macKind, applied: applied, error: applied ? '' : 'apply-not-found' });
  }, 1200);
})();
