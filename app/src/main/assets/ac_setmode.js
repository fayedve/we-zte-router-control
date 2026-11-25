(function () {
  var SSIDNO = __SSID__;
  var WANT = "__MODE__";
  function send(o) { try { RouterBridge.onResult(JSON.stringify(o)); } catch (e) { } }
  function txt(el) { return ((el.innerText || el.textContent || el.value || "") + "").replace(/\s+/g, " ").trim(); }
  function rowsWithRadios(d) {
    var res = [];
    var rows = d.querySelectorAll("tr");
    for (var i = 0; i < rows.length; i++) { if (rows[i].querySelectorAll("input[type=radio]").length) { res.push(rows[i]); } }
    return res;
  }
  function clickApply(d) {
    var els = d.querySelectorAll("input[type=button], input[type=submit], button");
    for (var i = els.length - 1; i >= 0; i--) {
      if (/^apply$/i.test(txt(els[i]))) { try { els[i].click(); return true; } catch (e) { } }
    }
    return false;
  }
  try {
    var docs = [document];
    var i;
    for (i = 0; i < window.frames.length; i++) { try { docs.push(window.frames[i].document); } catch (e) { } }
    for (i = 0; i < docs.length; i++) {
      var d = docs[i];
      if (!d || !d.querySelector) { continue; }
      var rows = rowsWithRadios(d);
      if (!rows.length) { continue; }
      var row = null;
      for (var r = 0; r < rows.length; r++) {
        var lab = txt(rows[r]).match(/SSID\s*(\d+)/i);
        if (lab && parseInt(lab[1], 10) === SSIDNO) { row = rows[r]; break; }
      }
      if (!row && SSIDNO - 1 < rows.length) { row = rows[SSIDNO - 1]; }
      if (!row) { continue; }
      var want = WANT.toLowerCase();
      var cells = row.querySelectorAll("td, th");
      var clicked = false;
      for (var c = 0; c < cells.length; c++) {
        var radio = cells[c].querySelector("input[type=radio]");
        if (!radio) { continue; }
        var label = txt(cells[c]).toLowerCase();
        if (label.indexOf(want) < 0) { label = txt(radio.parentNode).toLowerCase(); }
        if (label.indexOf(want) < 0) { continue; }
        if (!radio.checked) { try { radio.click(); } catch (e) { } }
        clicked = true;
        break;
      }
      if (!clicked) { send({ op: "setmode", ok: false, clicked: false, applied: false, ssid: SSIDNO, error: "mode-option-not-found" }); return; }
      var applied = clickApply(d);
      send({ op: "setmode", ok: applied, clicked: clicked, applied: applied, ssid: SSIDNO, mode: WANT, error: applied ? "" : "apply-not-found" });
      return;
    }
    send({ op: "setmode", ok: false, clicked: false, applied: false, ssid: SSIDNO, error: "ssid-row-not-found" });
  } catch (e) { send({ op: "setmode", ok: false, error: String(e) }); }
})();