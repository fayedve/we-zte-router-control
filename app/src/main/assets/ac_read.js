(function () {
  var MACRE = /([0-9a-fA-F]{2}[:-]){5}[0-9a-fA-F]{2}/;
  function send(o) { try { RouterBridge.onResult(JSON.stringify(o)); } catch (e) { } }
  function txt(el) { return ((el.innerText || el.textContent || "") + "").replace(/\s+/g, " ").trim(); }
  try {
    var out = { op: "rules", needsLogin: false, hasAccess: false, modes: [], rules: [], error: "", title: String(document.title || "") };
    var docs = [document];
    var i;
    for (i = 0; i < window.frames.length; i++) { try { docs.push(window.frames[i].document); } catch (e) { } }
    var modeList = [];
    for (i = 0; i < docs.length; i++) {
      var d = docs[i];
      if (!d || !d.querySelector) { continue; }
      if (d.querySelector("input[type=password]")) { out.needsLogin = true; }
      var body = d.body ? txt(d.body) : "";
      if (/access\s*control/i.test(body) && /(black\s*list|white\s*list|no\s*filter)/i.test(body)) { out.hasAccess = true; }
      var rows = d.querySelectorAll("tr");
      var r, c;
      for (r = 0; r < rows.length; r++) {
        var radios = rows[r].querySelectorAll("input[type=radio]");
        if (!radios.length) { continue; }
        var cells = rows[r].querySelectorAll("td, th");
        var mode = "";
        for (c = 0; c < cells.length; c++) {
          var radio = cells[c].querySelector("input[type=radio]");
          if (!radio || !radio.checked) { continue; }
          var m = txt(cells[c]).match(/(no\s*filter|black\s*list|white\s*list)/i);
          if (m) { mode = m[1]; break; }
          var near = txt(radio.parentNode).match(/(no\s*filter|black\s*list|white\s*list)/i);
          if (near) { mode = near[1]; break; }
        }
        if (!mode) { continue; }
        var lab = txt(rows[r]).match(/SSID\s*(\d+)/i);
        modeList.push({ no: lab ? parseInt(lab[1], 10) : 0, mode: mode });
      }
      for (r = 0; r < rows.length; r++) {
        var cells2 = rows[r].querySelectorAll("td");
        if (cells2.length < 2) { continue; }
        var mac = "";
        var c2;
        for (c2 = 0; c2 < cells2.length; c2++) { var mm = txt(cells2[c2]).match(MACRE); if (mm) { mac = mm[0]; break; } }
        if (!mac) { continue; }
        var name = "", ssid = "";
        for (c2 = 0; c2 < cells2.length; c2++) {
          var t2 = txt(cells2[c2]);
          if (!t2) { continue; }
          if (t2.indexOf(mac) >= 0) { continue; }
          var sm = t2.match(/SSID\s*(\d+)/i);
          if (sm && !ssid) { ssid = "SSID" + sm[1]; continue; }
          if (!name && !/^SSID\d/i.test(t2) && t2.length <= 40 && !MACRE.test(t2)) { name = t2; }
        }
        var dup = false;
        for (var q = 0; q < out.rules.length; q++) { if (out.rules[q].mac.toLowerCase() === mac.toLowerCase()) { dup = true; } }
        if (!dup) { out.rules.push({ name: name, ssid: ssid, mac: mac }); }
      }
    }
    var modesByNo = {};
    var nextFree = 1;
    for (i = 0; i < modeList.length; i++) {
      var no = modeList[i].no;
      if (no >= 1 && no <= 8) { modesByNo[no] = modeList[i].mode; }
      else {
        while (modesByNo[nextFree]) { nextFree++; }
        if (nextFree <= 8) { modesByNo[nextFree] = modeList[i].mode; }
      }
    }
    for (i = 1; i <= 8; i++) { out.modes.push({ ssid: "SSID" + i, mode: modesByNo[i] || "" }); }
    send(out);
  } catch (e) { send({ op: "rules", needsLogin: false, hasAccess: false, modes: [], rules: [], error: String(e) }); }
})();