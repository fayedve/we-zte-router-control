(function () {
  function txt(el) {
    return (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
  }
  function report(o) {
    if (window.Android && window.Android.onActionResult) {
      window.Android.onActionResult(JSON.stringify(o));
    }
  }
  var docs = [document];
  for (var f = 0; f < window.frames.length; f++) {
    try { docs.push(window.frames[f].document); } catch (e) { }
  }
  for (var d = 0; d < docs.length; d++) {
    var els = docs[d].querySelectorAll('a, li, span, div, td');
    for (var i = 0; i < els.length; i++) {
      var t = txt(els[i]);
      if (!t || t.length > 40) { continue; }
      if (!/advanced/i.test(t) || !/wlan/i.test(t)) { continue; }
      var el = els[i];
      if (el.tagName.toLowerCase() !== 'a') {
        var inner = el.querySelector('a');
        if (inner) { el = inner; }
      }
      el.click();
      report({ ok: true, label: t });
      return;
    }
  }
  report({ ok: false, error: 'link-not-found' });
})();
