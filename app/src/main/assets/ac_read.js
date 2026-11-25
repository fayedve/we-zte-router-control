(function () {
  var MAC_RE = /([0-9a-fA-F]{2}[:-]){5}[0-9a-fA-F]{2}/;
  function txt(el) {
    return (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
  }
  function report(o) {
    if (window.Android && window.Android.onRules) {
      window.Android.onRules(JSON.stringify(o));
    }
  }
  var out = { page: location.href, title: document.title, needsLogin: false, hasAccessControl: false, modes: [], rules: [] };
  var docs = [document];
  var f;
  for (f = 0; f < window.frames.length; f++) {
    try { docs.push(window.frames[f].document); } catch (e) { }
  }
  for (f = 0; f < docs.length; f++) {
    var pw = docs[f].querySelector('input[type=password]');
    if (pw) { out.needsLogin = true; }
  }
  var body = document.body ? txt(document.body) : '';
  for (f = 0; f < docs.length; f++) {
    var b = docs[f].body ? txt(docs[f].body) : '';
    if (b.length > body.length) { body = b; }
  }
  out.hasAccessControl = /access\s*control/i.test(body) && /(black\s*list|white\s*list|no\s*filter)/i.test(body);
  var rows = document.querySelectorAll('tr');
  var i;
  var c;
  for (i = 0; i < rows.length; i++) {
    var row = rows[i];
    var radios = row.querySelectorAll('input[type=radio]');
    if (!radios.length) { continue; }
    var rowText = txt(row);
    var ssidName = rowText.match(/SSID\s*\d+/i);
    if (!ssidName) { continue; }
    var mode = '';
    var cells = row.querySelectorAll('td, th');
    for (c = 0; c < cells.length; c++) {
      var radio = cells[c].querySelector('input[type=radio]');
      if (!radio || !radio.checked) { continue; }
      var label = txt(cells[c]).match(/(no\s*filter|black\s*list|white\s*list)/i);
      if (label) { mode = label[1]; break; }
      var near = txt(radio.parentNode).match(/(no\s*filter|black\s*list|white\s*list)/i);
      if (near) { mode = near[1]; break; }
    }
    out.modes.push({ ssid: ssidName[0].replace(/\s+/g, '').toUpperCase(), mode: mode });
  }
  for (i = 0; i < rows.length; i++) {
    var cells2 = rows[i].querySelectorAll('td');
    if (cells2.length < 2) { continue; }
    var mac = '';
    var name = '';
    var ssid = '';
    for (c = 0; c < cells2.length; c++) {
      var cellText = txt(cells2[c]);
      if (!cellText) { continue; }
      var macMatch = cellText.match(MAC_RE);
      if (macMatch && !mac) { mac = macMatch[0]; continue; }
      var ssidMatch = cellText.match(/SSID\s*\d+/i);
      if (ssidMatch && !ssid) { ssid = ssidMatch[0].replace(/\s+/g, '').toUpperCase(); continue; }
      if (!name) { name = cellText.slice(0, 40); }
    }
    if (!mac) { continue; }
    out.rules.push({ name: name, ssid: ssid, mac: mac });
  }
  report(out);
})();
