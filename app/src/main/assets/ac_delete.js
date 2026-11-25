(function () {
  var MAC = "__MAC__";
  function send(o) { try { RouterBridge.onResult(JSON.stringify(o)); } catch (e) { } }
  function txt(el) { return ((el.innerText || el.textContent || el.value || "") + "").replace(/\s+/g, " ").trim(); }
  function clickApply(d) {
    var els = d.querySelectorAll("input[type=button], input[type=submit], button");
    for (var i = els.length - 1; i >= 0; i--) {
      if (/^apply$/i.test(txt(els[i]))) { try { els[i].click(); return true; } catch (e) { } }
    }
    return false;
  }
  try {
    var want = MAC.toLowerCase();
    var docs = [document];
    var i;
    for (i = 0; i < window.frames.length; i++) { try { docs.push(window.frames[i].document); } catch (e) { } }
    for (i = 0; i < docs.length; i++) {
      var d = docs[i];
      if (!d || !d.querySelector) { continue; }
      var rows = d.querySelectorAll("tr");
      var row = null;
      for (var r = 0; r < rows.length; r++) {
        if (txt(rows[r]).toLowerCase().indexOf(want) >= 0) { row = rows[r]; }
      }
      if (!row) { continue; }
      var cands = row.querySelectorAll("a, img, span, button, input[type=button], i, div");
      var clicked = false;
      for (var c = 0; c < cands.length; c++) {
        var el = cands[c];
        var hint = "";
        if (el.getAttribute) { hint = (el.getAttribute("title") || "") + " " + (el.getAttribute("alt") || "") + " " + (el.getAttribute("onclick") || "") + " " + (el.getAttribute("class") || ""); }
        hint = hint + " " + txt(el);
        if (/del|trash|remove|حذف/i.test(hint)) { try { el.click(); clicked = true; break; } catch (e) { } }
      }
      if (!clicked) { send({ op: "del", ok: false, error: "delete-icon-not-found" }); return; }
      var applied = clickApply(d);
      send({ op: "del", ok: applied, applied: applied, error: applied ? "" : "apply-not-found" });
      return;
    }
    send({ op: "del", ok: false, error: "rule-row-not-found" });
  } catch (e) { send({ op: "del", ok: false, error: String(e) }); }
})();