(function () {
  var USER = "__USER__";
  var PASS = "__PASS__";
  function send(o) { try { RouterBridge.onResult(JSON.stringify(o)); } catch (e) { } }
  function txt(el) { return ((el.innerText || el.textContent || el.value || "") + "").replace(/\s+/g, " ").trim(); }
  function setVal(el, v) {
    try { el.focus(); } catch (e) { }
    el.value = v;
    try { el.dispatchEvent(new Event("input", { bubbles: true })); } catch (e) { }
    try { el.dispatchEvent(new Event("change", { bubbles: true })); } catch (e) { }
    try { el.dispatchEvent(new Event("keyup", { bubbles: true })); } catch (e) { }
    try { el.dispatchEvent(new Event("blur", { bubbles: true })); } catch (e) { }
  }
  try {
    var docs = [document];
    var i, j;
    for (i = 0; i < window.frames.length; i++) { try { docs.push(window.frames[i].document); } catch (e) { } }
    for (i = 0; i < docs.length; i++) {
      var d = docs[i];
      if (!d || !d.querySelector) { continue; }
      var pw = d.querySelector("input[type=password]");
      if (!pw) { continue; }
      var user = d.querySelector("input#username") || d.querySelector("input[name=username]") || d.querySelector("input[name=Username]") || d.querySelector("input[name=user]") || d.querySelector("input[name=UserName]") || d.querySelector("input[type=text]");
      if (user && USER) { setVal(user, USER); }
      setVal(pw, PASS);
      var clicked = false;
      var btns = d.querySelectorAll("input[type=submit], button, input[type=button], a");
      for (j = 0; j < btns.length; j++) {
        var t = txt(btns[j]);
        if (/log\s*in|login|sign\s*in|دخول|تسجيل/i.test(t)) { try { btns[j].click(); clicked = true; break; } catch (e) { } }
      }
      if (!clicked && pw.form) { try { pw.form.submit(); clicked = true; } catch (e) { } }
      send({ op: "login", submitted: clicked, error: clicked ? "" : "login-button-not-found" });
      return;
    }
    send({ op: "login", submitted: false, error: "password-field-not-found" });
  } catch (e) { send({ op: "login", submitted: false, error: String(e) }); }
})();