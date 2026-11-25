(function () {
  var MACRE = /([0-9a-fA-F]{2}[:-]){5}[0-9a-fA-F]{2}/;
  function send(o) { try { RouterBridge.onResult(JSON.stringify(o)); } catch (e) { } }
  function txt(el) { return ((el.innerText || el.textContent || "") + "").replace(/\s+/g, " ").trim(); }
  try {
    var out = { op: "devices", needsLogin: false, devices: [], error: "", title: String(document.title || "") };
    var docs = [document];
    var i;
    for (i = 0; i < window.frames.length; i++) { try { docs.push(window.frames[i].document); } catch (e) { } }
    var seen = {};
    for (i = 0; i < docs.length; i++) {
      var d = docs[i];
      if (!d || !d.querySelector) { continue; }
      if (d.querySelector("input[type=password]")) { out.needsLogin = true; }
      var rows = d.querySelectorAll("tr");
      var cols = null;
      for (var r = 0; r < rows.length; r++) {
        var cells = rows[r].querySelectorAll("td, th");
        if (!cells.length) { continue; }
        var texts = [];
        var c, k;
        for (c = 0; c < cells.length; c++) { texts.push(txt(cells[c])); }
        var joined = texts.join(" | ");
        if (!cols && /mac/i.test(joined) && texts.length >= 3) {
          var idxName = -1, idxIp = -1, idxMac = -1, idxClient = -1;
          for (k = 0; k < texts.length; k++) {
            if (idxClient < 0 && /^client(\s*index)?$/i.test(texts[k])) { idxClient = k; }
            if (idxName < 0 && /name|host/i.test(texts[k])) { idxName = k; }
            if (idxIp < 0 && /ipv4|ip\s*address/i.test(texts[k])) { idxIp = k; }
            if (idxMac < 0 && /mac/i.test(texts[k])) { idxMac = k; }
          }
          if (idxMac >= 0) { cols = { name: idxName, ip: idxIp, mac: idxMac, client: idxClient }; }
          continue;
        }
        var mac = "";
        for (c = 0; c < cells.length; c++) { var m = txt(cells[c]).match(MACRE); if (m) { mac = m[0]; break; } }
        if (!mac) { continue; }
        var key = mac.toLowerCase();
        if (seen[key]) { continue; }
        seen[key] = true;
        var name = "", ip = "";
        if (cols) {
          if (cols.name >= 0 && cells[cols.name]) { name = txt(cells[cols.name]); }
          if (cols.ip >= 0 && cells[cols.ip]) { ip = txt(cells[cols.ip]); }
        }
        if (!ip) {
          for (c = 0; c < cells.length; c++) {
            var t3 = txt(cells[c]);
            if (/^\d{1,3}(\.\d{1,3}){3}$/.test(t3)) { ip = t3; break; }
          }
        }
        if (!name) {
          var others = [];
          for (c = 0; c < cells.length; c++) {
            var t4 = txt(cells[c]);
            if (!t4) { continue; }
            if (t4.indexOf(mac) >= 0) { continue; }
            if (/^\d{1,3}(\.\d{1,3}){3}$/.test(t4)) { continue; }
            if (MACRE.test(t4)) { continue; }
            others.push(t4);
          }
          for (c = 0; c < others.length; c++) {
            if (!/^client[\s-]*\d+$/i.test(others[c])) { name = others[c]; break; }
          }
        }
        if (name.length > 60) { name = name.slice(0, 60); }
        out.devices.push({ name: name, mac: mac, ip: ip });
      }
    }
    send(out);
  } catch (e) { send({ op: "devices", needsLogin: false, devices: [], error: String(e) }); }
})();