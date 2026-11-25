(function () {
  var MAC = "__MAC__";
  var NAME = "__NAME__";
  function send(o) { try { RouterBridge.onResult(JSON.stringify(o)); } catch (e) { } }
  function txt(el) { return ((el.innerText || el.textContent || el.value || "") + "").replace(/\s+/g, " ").trim(); }
  function setVal(el, v) {
    if (!el) { return; }
    try { el.focus(); } catch (e) { }
    el.value = v;
    try { el.dispatchEvent(new Event("input", { bubbles: true })); } catch (e) { }
    try { el.dispatchEvent(new Event("change", { bubbles: true })); } catch (e) { }
    try { el.dispatchEvent(new Event("blur", { bubbles: true })); } catch (e) { }
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
    var page = null;
    for (i = 0; i < docs.length; i++) {
      var dd = docs[i];
      if (!dd || !dd.querySelector) { continue; }
      if (/access\s*control/i.test(dd.body ? txt(dd.body) : "")) { page = dd; break; }
    }
    if (!page) { page = document; }
    var clicked = false;
    var els = page.querySelectorAll("a, span, td, div, input[type=button], button");
    for (i = 0; i < els.length; i++) {
      var t = txt(els[i]);
      if (!/create\s*new\s*item/i.test(t) || t.length > 40) { continue; }
      var target = els[i];
      if (target.tagName.toLowerCase() !== "a") { var inA = target.querySelector("a"); if (inA) { target = inA; } }
      try { target.click(); clicked = true; break; } catch (e) { }
    }
    if (!clicked) { send({ op: "add", ok: false, error: "create-item-not-found" }); return; }
    setTimeout(function () {
      try {
        var rows = page.querySelectorAll("tr");
        var row = null;
        for (var r = rows.length - 1; r >= 0; r--) {
          if (rows[r].querySelector("input[type=text], input:not([type])")) { row = rows[r]; break; }
        }
        if (!row) { send({ op: "add", ok: false, error: "new-row-not-found" }); return; }
        var inputs = row.querySelectorAll("input[type=text], input:not([type])");
        var nameDone = false;
        for (var k = 0; k < inputs.length; k++) {
          var hint = ((inputs[k].name || "") + " " + (inputs[k].id || "") + " " + (inputs[k].className || "")).toLowerCase();
          if (/mac/.test(hint) || inputs[k].maxLength === 2) { continue; }
          if (/name|remark|desc|comment/.test(hint) || !nameDone) { setVal(inputs[k], NAME); nameDone = true; break; }
        }
        var sels = row.querySelectorAll("select");
        var ssidPicked = "";
        for (var s = 0; s < sels.length; s++) {
          var opts = sels[s].options || [];
          for (var oi = 0; oi < opts.length; oi++) {
            var ot = (opts[oi].text || "").replace(/\s+/g, " ").trim();
            if (/^SSID\s*1\b/i.test(ot) || /\bSSID\s*1$/i.test(ot)) {
              sels[s].selectedIndex = oi;
              try { sels[s].dispatchEvent(new Event("change", { bubbles: true })); } catch (e) { }
              ssidPicked = ot;
              break;
            }
          }
          if (ssidPicked) { break; }
        }
        if (!ssidPicked) { send({ op: "add", ok: false, error: "ssid1-option-not-found" }); return; }
        var hex = MAC.split(/[^0-9a-fA-F]/).filter(function (x) { return x.length > 0; });
        var all = row.querySelectorAll("input");
        var byName = [];
        var shortOnes = [];
        for (var mi = 0; mi < all.length; mi++) {
          var h = ((all[mi].name || "") + " " + (all[mi].id || "") + " " + (all[mi].className || "")).toLowerCase();
          if (/mac/.test(h)) { byName.push(all[mi]); }
          if (all[mi].maxLength === 2) { shortOnes.push(all[mi]); }
        }
        var macDone = false;
        if (shortOnes.length >= 6) { for (var q = 0; q < 6; q++) { setVal(shortOnes[q], hex[q]); } macDone = true; }
        else if (byName.length === 1) { setVal(byName[0], MAC); macDone = true; }
        else if (byName.length >= 6) { for (var q2 = 0; q2 < 6; q2++) { setVal(byName[q2], hex[q2]); } macDone = true; }
        if (!macDone) { send({ op: "add", ok: false, error: "mac-field-not-found" }); return; }
        var applied = clickApply(page);
        send({ op: "add", ok: applied, applied: applied, ssid: ssidPicked, error: applied ? "" : "apply-not-found" });
      } catch (e) { send({ op: "add", ok: false, error: String(e) }); }
    }, 1400);
  } catch (e) { send({ op: "add", ok: false, error: String(e) }); }
})();