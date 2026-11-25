(function () {
  var TARGET = "__TARGET__";
  function send(o) { try { RouterBridge.onResult(JSON.stringify(o)); } catch (e) { } }
  function txt(el) { return ((el.innerText || el.textContent || el.value || "") + "").replace(/\s+/g, " ").trim(); }
  var pats = [];
  if (TARGET === "client") {
    pats = [/wlan\s*client\s*status/i, /client\s*status/i, /associated\s*device/i, /أجهزة\s*الشبكة/];
  } else {
    pats = [/wlan\s*advanced/i, /access\s*control/i, /mac\s*filter/i];
  }
  try {
    var docs = [document];
    var i, j, k;
    for (i = 0; i < window.frames.length; i++) { try { docs.push(window.frames[i].document); } catch (e) { } }
    for (k = 0; k < pats.length; k++) {
      for (i = 0; i < docs.length; i++) {
        var d = docs[i];
        if (!d || !d.querySelectorAll) { continue; }
        var els = d.querySelectorAll("a, input[type=button], button, li, span, td, div");
        for (j = 0; j < els.length; j++) {
          var el = els[j];
          var t = txt(el);
          if (!t || t.length > 45) { continue; }
          if (!pats[k].test(t)) { continue; }
          var target = el;
          var p = el;
          var depth = 0;
          while (p && p.tagName && depth < 3) {
            var tag = p.tagName.toLowerCase();
            if (tag === "a" || tag === "button" || tag === "input") { target = p; break; }
            p = p.parentNode;
            depth++;
          }
          try { target.click(); } catch (e) { continue; }
          send({ op: "nav", clicked: true, label: t, target: TARGET, error: "" });
          return;
        }
      }
    }
    send({ op: "nav", clicked: false, label: "", target: TARGET, error: "link-not-found" });
  } catch (e) { send({ op: "nav", clicked: false, target: TARGET, error: String(e) }); }
})();