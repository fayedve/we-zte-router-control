(function () {
  var INTENT = "__INTENT__";
  function send(o) { try { RouterBridge.onResult(JSON.stringify(o)); } catch (e) { } }
  function txt(el) { return ((el.innerText || el.textContent || "") + "").replace(/\s+/g, " ").trim(); }
  try {
    var out = { op: "probe", intent: INTENT, url: String(location.href), title: String(document.title || ""), needsLogin: false, hasClient: false, hasAccess: false, links: 0, error: "" };
    var docs = [document];
    var i;
    for (i = 0; i < window.frames.length; i++) { try { docs.push(window.frames[i].document); } catch (e) { } }
    for (i = 0; i < docs.length; i++) {
      var d = docs[i];
      if (!d || !d.querySelector) { continue; }
      if (d.querySelector("input[type=password]")) { out.needsLogin = true; }
      var body = d.body ? txt(d.body) : "";
      if (/MAC\s*[Aa]ddress/.test(body) && /IPv4/.test(body)) { out.hasClient = true; }
      if (/Access\s*Control/i.test(body)) { out.hasAccess = true; }
      if (d.querySelectorAll) { out.links = out.links + d.querySelectorAll("a, input, button, li, span, td").length; }
    }
    send(out);
  } catch (e) { send({ op: "probe", intent: INTENT, needsLogin: false, hasClient: false, hasAccess: false, error: String(e) }); }
})();