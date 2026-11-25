package com.fayed.routerctl

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.InputType
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MainActivity : Activity() {

    private class Device(val name: String, val mac: String, val ip: String)
    private class Rule(val name: String, val ssid: String, val mac: String)

    private val handler = Handler(Looper.getMainLooper())
    private val devices = ArrayList<Device>()
    private val rules = ArrayList<Rule>()
    private val modes = ArrayList<String>()
    private val jsCache = HashMap<String, String>()

    private lateinit var web: WebView
    private lateinit var webHolder: LinearLayout
    private lateinit var addressInput: EditText
    private lateinit var userInput: EditText
    private lateinit var passInput: EditText
    private lateinit var autoBox: CheckBox
    private lateinit var statusText: TextView
    private lateinit var devicesTitle: TextView
    private lateinit var devicesBox: LinearLayout
    private lateinit var modesTitle: TextView
    private lateinit var modesBox: LinearLayout
    private lateinit var rulesTitle: TextView
    private lateinit var rulesBox: LinearLayout

    private var uiReady = false
    private var job = ""
    private var phase = ""
    private var afterLoad = ""
    private var tries = 0
    private var loginTries = 0
    private var timeoutSeq = 0
    private var blockMac = ""
    private var blockName = ""
    private var unblockMac = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            buildUi()
            setupWeb()
            uiReady = true
        } catch (e: Throwable) {
            val fallback = TextView(this)
            fallback.text = "حصل خطأ في تجهيز الشاشة: " + (e.message ?: "")
            fallback.textSize = 18f
            fallback.setPadding(dp(20), dp(40), dp(20), dp(20))
            setContentView(fallback)
        }
        handler.postDelayed({ try { autoStart() } catch (e: Throwable) { } }, 500)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.WHITE)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.VERTICAL
        head.setPadding(dp(12), dp(12), dp(12), dp(10))
        head.setBackgroundColor(Color.parseColor("#0F3D5C"))

        val title = TextView(this)
        title.text = "مدير شبكة الراوتر"
        title.textSize = 22f
        title.setTextColor(Color.WHITE)
        title.setTypeface(null, Typeface.BOLD)
        head.addView(title)

        addressInput = EditText(this)
        addressInput.setSingleLine(true)
        addressInput.hint = DEFAULT_URL
        addressInput.setText(prefs().getString(KEY_ADDRESS, DEFAULT_URL))
        addressInput.textSize = 16f
        head.addView(addressInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val credRow = LinearLayout(this)
        credRow.orientation = LinearLayout.HORIZONTAL
        userInput = EditText(this)
        userInput.setSingleLine(true)
        userInput.hint = "المستخدم"
        userInput.textSize = 16f
        userInput.setText(prefs().getString(KEY_USER, "admin"))
        credRow.addView(userInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.38f))
        passInput = EditText(this)
        passInput.setSingleLine(true)
        passInput.hint = "باسورد الراوتر"
        passInput.textSize = 16f
        passInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        credRow.addView(passInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.62f))
        head.addView(credRow)

        autoBox = CheckBox(this)
        autoBox.text = "دخول تلقائي بالباسورد المحفوظ"
        autoBox.textSize = 15f
        autoBox.setTextColor(Color.WHITE)
        autoBox.isChecked = prefs().getBoolean(KEY_AUTO, true)
        head.addView(autoBox)

        val saveRow = LinearLayout(this)
        saveRow.orientation = LinearLayout.HORIZONTAL
        val saveBtn = Button(this)
        saveBtn.text = "حفظ البيانات"
        saveBtn.textSize = 15f
        saveBtn.layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
        saveBtn.setOnClickListener { saveCredentials() }
        saveRow.addView(saveBtn)
        val loginBtn = Button(this)
        loginBtn.text = "دخول"
        loginBtn.textSize = 15f
        loginBtn.layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
        loginBtn.setOnClickListener { doLogin() }
        saveRow.addView(loginBtn)
        head.addView(saveRow)

        root.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        statusText = TextView(this)
        statusText.textSize = 17f
        statusText.setTextColor(Color.parseColor("#111111"))
        statusText.setBackgroundColor(Color.parseColor("#FFF4CE"))
        statusText.setPadding(dp(12), dp(10), dp(12), dp(10))
        statusText.text = "جاهز."
        root.addView(statusText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.setPadding(dp(8), dp(8), dp(8), dp(4))
        actions.addView(actionButton("تحديث الأجهزة", 1f) { startDevices() })
        actions.addView(actionButton("قواعد الحظر", 1f) { loadRules() })
        actions.addView(actionButton("صفحة الراوتر", 1f) { toggleWeb() })
        root.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(this)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(10), dp(6), dp(10), dp(10))

        devicesTitle = sectionTitle("الأجهزة المتصلة")
        content.addView(devicesTitle)
        devicesBox = LinearLayout(this)
        devicesBox.orientation = LinearLayout.VERTICAL
        content.addView(devicesBox)

        modesTitle = sectionTitle("حالة الحظر لكل SSID")
        content.addView(modesTitle)
        modesBox = LinearLayout(this)
        modesBox.orientation = LinearLayout.VERTICAL
        content.addView(modesBox)

        rulesTitle = sectionTitle("قواعد الحظر")
        content.addView(rulesTitle)
        rulesBox = LinearLayout(this)
        rulesBox.orientation = LinearLayout.VERTICAL
        content.addView(rulesBox)

        scroll.addView(content, ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        webHolder = LinearLayout(this)
        webHolder.orientation = LinearLayout.VERTICAL
        webHolder.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
        root.addView(webHolder)

        setContentView(root)
    }

    private fun sectionTitle(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 18f
        tv.setTypeface(null, Typeface.BOLD)
        tv.setTextColor(Color.parseColor("#0F3D5C"))
        tv.setPadding(0, dp(12), 0, dp(6))
        return tv
    }

    private fun actionButton(label: String, weight: Float, action: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 15f
        b.layoutParams = LinearLayout.LayoutParams(0, dp(48), weight)
        b.setOnClickListener { action() }
        return b
    }

    private fun setupWeb() {
        web = WebView(this)
        web.setBackgroundColor(Color.WHITE)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.javaScriptCanOpenWindowsAutomatically = true
        s.setSupportZoom(false)
        s.useWideViewPort = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        web.addJavascriptInterface(Bridge(), "RouterBridge")
        try {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        } catch (e: Throwable) { }
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                try { handler?.proceed() } catch (e: Throwable) { }
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                val next = afterLoad
                afterLoad = ""
                if (next.isNotEmpty()) {
                    handler.postDelayed({ try { runAfterLoad(next) } catch (e: Throwable) { } }, 800)
                }
            }
        }
        webHolder.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    inner class Bridge {
        @JavascriptInterface
        fun onResult(payload: String) {
            handler.post {
                try { handleResult(payload) } catch (e: Throwable) { setStatus("مشكلة في قراءة رد صفحة الراوتر.") }
            }
        }
    }

    private fun autoStart() {
        if (!uiReady) { return }
        if (passInput.text.toString().isEmpty()) {
            val saved = savedPass()
            if (saved.isNotEmpty()) { passInput.setText(saved) }
        }
        if (autoBox.isChecked) { openRouter() } else { setStatus("اضغط (فتح) أو (تحديث الأجهزة) عشان أبدأ.") }
    }

    private fun setStatus(msg: String) {
        if (!uiReady) { return }
        statusText.text = msg
    }

    private fun routerUrl(): String {
        var u = addressInput.text.toString().trim()
        if (u.isEmpty()) { u = DEFAULT_URL }
        if (!u.startsWith("http")) { u = "https://" + u }
        while (u.endsWith("/")) { u = u.substring(0, u.length - 1) }
        return u
    }

    private fun openRouter() {
        try {
            job = "probe"
            tries = 0
            loginTries = 0
            setStatus("بفتح صفحة الراوتر...")
            startTimeout("الراوتر مش بيرد. اتأكد إنك متصل بشبكة الراوتر وجرب تاني.")
            afterLoad = "probe"
            web.loadUrl(routerUrl())
        } catch (e: Throwable) { setStatus("مش قادر أفتح عنوان الراوتر. راجع العنوان.") }
    }

    private fun runAfterLoad(name: String) {
        when (name) {
            "probe" -> inject("ac_probe.js", hashMapOf("__INTENT__" to "start"))
            "devices" -> injectDevices()
            "rules" -> injectRules()
            else -> { }
        }
    }

    private fun toggleWeb() {
        try {
            if (webHolder.layoutParams.height == 0) {
                webHolder.layoutParams.height = dp(320)
                setStatus("اتنقل من صفحة الراوتر بنفسك، وبعدين اضغط (قراءة الصفحة) أو (قواعد الحظر).")
            } else {
                webHolder.layoutParams.height = 0
            }
            webHolder.requestLayout()
        } catch (e: Throwable) { }
    }

    private fun readAsset(name: String): String {
        val cached = jsCache[name]
        if (cached != null) { return cached }
        val text = try {
            assets.open(name).use { String(it.readBytes(), Charsets.UTF_8) }
        } catch (e: Throwable) { "" }
        jsCache[name] = text
        return text
    }

    private fun esc(s: String): String {
        var v = s.replace("\\", "\\\\")
        v = v.replace("\"", "\\\"")
        v = v.replace("\n", " ")
        v = v.replace("\r", " ")
        return v
    }

    private fun inject(name: String, vars: Map<String, String>) {
        try {
            var js = readAsset(name)
            if (js.isEmpty()) { setStatus("ملف داخلي ناقص: " + name); return }
            for (k in vars.keys) {
                val v = vars[k] ?: ""
                js = js.replace(k, v)
            }
            web.evaluateJavascript(js, null)
        } catch (e: Throwable) { setStatus("مقدرتش أنفذ الخطوة على صفحة الراوتر.") }
    }

    private fun startTimeout(msg: String) {
        timeoutSeq++
        val seq = timeoutSeq
        handler.postDelayed({
            if (seq == timeoutSeq) {
                timeoutSeq++
                job = ""
                phase = ""
                setStatus(msg)
            }
        }, 35000)
    }

    private fun clearTimeout() { timeoutSeq++ }

    private fun savedPass(): String {
        val typed = passInput.text.toString()
        if (typed.isNotEmpty()) { return typed }
        return try { decrypt(prefs().getString(KEY_PASS, "") ?: "") } catch (e: Throwable) { "" }
    }

    private fun saveCredentials() {
        try {
            val e = prefs().edit()
            e.putString(KEY_ADDRESS, routerUrl())
            e.putString(KEY_USER, userInput.text.toString().trim())
            e.putBoolean(KEY_AUTO, autoBox.isChecked)
            val pw = passInput.text.toString()
            if (pw.isNotEmpty()) { e.putString(KEY_PASS, encrypt(pw)) }
            e.apply()
            setStatus("اتحفظت البيانات على الموبايل بس.")
        } catch (e: Throwable) { setStatus("مقدرتش أحفظ البيانات على الجهاز.") }
    }

    private fun keyStoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val entry = ks.getEntry(KEY_ALIAS, null)
        if (entry is KeyStore.SecretKeyEntry) { return entry.secretKey }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        kg.init(spec)
        return kg.generateKey()
    }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyStoreKey())
        val iv = cipher.iv
        val enc = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val parts = value.split(":")
        if (parts.size != 2) { return "" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, keyStoreKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }

    private fun copyText(t: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("mac", t))
        } catch (e: Throwable) { }
    }

    private fun handleResult(payload: String) {
        val o = JSONObject(payload)
        when (o.optString("op", "")) {
            "probe" -> onProbe(o)
            "login" -> onLogin(o)
            "devices" -> onDevices(o)
            "rules" -> onRules(o)
            "add" -> onAdd(o)
            "del" -> onDel(o)
            "setmode" -> onSetMode(o)
            else -> { }
        }
    }

    private fun onProbe(o: JSONObject) {
        val intent = o.optString("intent", "")
        val needs = o.optBoolean("needsLogin", false)
        if (intent == "diag") {
            clearTimeout()
            val msg = "الصفحة: " + o.optString("title", "") + "\n" +
                "العنوان: " + o.optString("url", "") + "\n" +
                "طالب تسجيل دخول: " + (if (needs) "أيوة" else "لأ") + "\n" +
                "جدول أجهزة ظاهر: " + (if (o.optBoolean("hasClient", false)) "أيوة" else "لأ") + "\n" +
                "صفحة Access Control ظاهرة: " + (if (o.optBoolean("hasAccess", false)) "أيوة" else "لأ")
            showDialog("تشخيص الصفحة", msg)
            return
        }
        if (needs) {
            if (intent == "afterlogin") {
                job = ""
                clearTimeout()
                setStatus("الباسورد غلط أو الراوتر رجّع صفحة الدخول تاني. راجع الباسورد واضغط (حفظ) وبعدين (دخول).")
                return
            }
            if (autoBox.isChecked && savedPass().isNotEmpty() && loginTries < 2) {
                loginTries++
                doLogin()
                return
            }
            job = ""
            clearTimeout()
            setStatus("الراوتر طالب تسجيل دخول. اكتب باسورد الراوتر فوق، اضغط (حفظ)، وبعدين (دخول).")
            return
        }
        try { CookieManager.getInstance().flush() } catch (e: Throwable) { }
        loginTries = 0
        setStatus("تم الاتصال بالراوتر.")
        startDevices()
    }

    private fun onLogin(o: JSONObject) {
        if (!o.optBoolean("submitted", false)) {
            setStatus("مش لاقي خانة وزرار الدخول في الصفحة. اضغط (صفحة الراوتر) وسجّل الدخول بنفسك مرة واحدة.")
            return
        }
        handler.postDelayed({
            job = "probe"
            startTimeout("الدخول واخد وقت. جرب تاني.")
            inject("ac_probe.js", hashMapOf("__INTENT__" to "afterlogin"))
        }, 4000)
    }

    private fun doLogin() {
        val p = savedPass()
        if (p.isEmpty()) { setStatus("اكتب باسورد الراوتر الأول واضغط (حفظ)."); return }
        job = "login"
        setStatus("بسجّل الدخول للراوتر...")
        startTimeout("تسجيل الدخول واخد وقت. جرب تاني.")
        inject("ac_login.js", hashMapOf("__USER__" to esc(userInput.text.toString().trim()), "__PASS__" to esc(p)))
    }

    private fun startDevices() {
        job = "devices"
        phase = ""
        tries = 0
        setStatus("بقرأ قايمة الأجهزة المتصلة...")
        startTimeout("الراوتر اتأخر في الرد. جرب (تحديث الأجهزة) تاني.")
        injectDevices()
    }

    private fun injectDevices() { inject("ac_devices.js", hashMapOf()) }

    private fun onDevices(o: JSONObject) {
        if (job != "devices") { return }
        if (o.optBoolean("needsLogin", false)) {
            if (autoBox.isChecked && savedPass().isNotEmpty() && loginTries < 2) { loginTries++; doLogin(); return }
            clearTimeout()
            job = ""
            setStatus("الجلسة انتهت والراوتر طالب دخول تاني. اضغط (دخول).")
            return
        }
        val arr = o.optJSONArray("devices") ?: JSONArray()
        val found = ArrayList<Device>()
        var i = 0
        while (i < arr.length()) {
            val d = arr.optJSONObject(i)
            if (d != null) {
                val mac = d.optString("mac", "")
                if (mac.isNotEmpty()) { found.add(Device(d.optString("name", ""), mac, d.optString("ip", ""))) }
            }
            i++
        }
        if (found.isEmpty()) {
            if (tries < 1) {
                tries++
                setStatus("بدوّر على صفحة WLAN Client Status...")
                inject("ac_nav.js", hashMapOf("__TARGET__" to "client"))
                handler.postDelayed({ if (job == "devices") injectDevices() }, 3500)
                return
            }
            clearTimeout()
            job = ""
            devices.clear()
            renderDevices()
            setStatus("مفيش أجهزة ظاهرة في الصفحة الحالية. اضغط (صفحة الراوتر) وافتح Local Network ثم WLAN ثم WLAN Client Status، وبعدين اضغط (تحديث الأجهزة).")
            return
        }
        devices.clear()
        devices.addAll(found)
        clearTimeout()
        job = ""
        renderDevices()
        setStatus("الأجهزة المتصلة: " + devices.size + ". اضغط (احظر) جنب أي جهاز عشان تفصل النت عنه.")
    }

    private fun loadRules() {
        job = "rules"
        phase = ""
        tries = 0
        setStatus("بقرأ قواعد الحظر وحالة كل SSID...")
        startTimeout("الراوتر اتأخر في الرد على صفحة الحظر. جرب تاني.")
        injectRules()
    }

    private fun injectRules() { inject("ac_read.js", hashMapOf()) }

    private fun onRules(o: JSONObject) {
        if (job.isEmpty()) { return }
        if (o.optBoolean("needsLogin", false)) {
            if (autoBox.isChecked && savedPass().isNotEmpty() && loginTries < 2) { loginTries++; doLogin(); return }
            clearTimeout()
            job = ""
            phase = ""
            setStatus("الجلسة انتهت والراوتر طالب دخول. اضغط (دخول).")
            return
        }
        val hasAccess = o.optBoolean("hasAccess", false)
        if (job == "block" || job == "unblock") {
            handleWorkingRules(o, hasAccess)
            return
        }
        if (job != "rules") { return }
        if (!hasAccess) {
            if (tries < 2) {
                tries++
                setStatus("بدوّر على صفحة WLAN Advanced...")
                inject("ac_nav.js", hashMapOf("__TARGET__" to "advanced"))
                handler.postDelayed({ if (job == "rules") injectRules() }, 3500)
                return
            }
            clearTimeout()
            job = ""
            setStatus("مش لاقي صفحة Access Control. اضغط (صفحة الراوتر) وافتح Local Network ثم WLAN ثم WLAN Advanced، وبعدين اضغط (قواعد الحظر).")
            return
        }
        clearTimeout()
        job = ""
        applyRules(o)
        setStatus("قواعد الحظر: " + rules.size + " قاعدة. تحتها وضع كل SSID.")
    }

    private fun handleWorkingRules(o: JSONObject, hasAccess: Boolean) {
        if (!hasAccess) {
            if (tries < 2) {
                tries++
                setStatus("بدوّر على صفحة WLAN Advanced...")
                inject("ac_nav.js", hashMapOf("__TARGET__" to "advanced"))
                handler.postDelayed({ if (job == "block" || job == "unblock") injectRules() }, 3500)
                return
            }
            clearTimeout()
            job = ""
            phase = ""
            setStatus("مش لاقي صفحة Access Control. افتحها بنفسك من (صفحة الراوتر) وجرب تاني.")
            return
        }
        if (job == "unblock") { handleUnblock(o); return }
        if (phase == "verify") {
            val rule = ruleIn(o, blockMac)
            clearTimeout()
            job = ""
            phase = ""
            applyRules(o)
            if (rule != null && (rule.ssid.isEmpty() || rule.ssid.equals("SSID1", true))) {
                setStatus("تم حظر " + blockName + " على SSID1، والقاعدة ظاهرة في الراوتر.")
            } else if (rule != null) {
                setStatus("القاعدة اتسجلت بس على " + rule.ssid + " مش SSID1. شيلها وجرب تاني.")
            } else {
                setStatus("الراوتر مدّاش تأكيد إن القاعدة اتسجلت. افتح (صفحة الراوتر) وشوف Access Control.")
            }
            return
        }
        if (phase == "check") {
            val m = modeIn(o, "SSID1")
            if (m.isNotEmpty() && !m.equals("Black List", true)) {
                phase = "setmode"
                setStatus("بحوّل SSID1 لـBlack List الأول...")
                inject("ac_setmode.js", hashMapOf("__SSID__" to "1", "__MODE__" to "Black List"))
                handler.postDelayed({ if (job == "block" && phase == "setmode") injectRules() }, 2500)
                return
            }
            phase = "add"
            setStatus("بحظر " + blockName + " على SSID1...")
            inject("ac_add.js", hashMapOf("__MAC__" to esc(blockMac), "__NAME__" to esc(blockName)))
            handler.postDelayed({ if (job == "block" && phase == "add") injectRules() }, 4000)
            return
        }
        if (phase == "setmode") {
            val m = modeIn(o, "SSID1")
            if (m.isNotEmpty() && !m.equals("Black List", true)) {
                clearTimeout()
                job = ""
                phase = ""
                setStatus("مقدرتش أحوّل SSID1 لـBlack List. حوّله بنفسك من صفحة WLAN Advanced وجرب تاني.")
                return
            }
            phase = "add"
            setStatus("بحظر " + blockName + " على SSID1...")
            inject("ac_add.js", hashMapOf("__MAC__" to esc(blockMac), "__NAME__" to esc(blockName)))
            handler.postDelayed({ if (job == "block" && phase == "add") injectRules() }, 4000)
            return
        }
        if (phase == "add") {
            phase = "verify"
            setStatus("بتأكد إن القاعدة اتسجلت...")
            injectRules()
        }
    }

    private fun handleUnblock(o: JSONObject) {
        val mac = unblockMac
        if (phase == "verify") {
            clearTimeout()
            job = ""
            phase = ""
            applyRules(o)
            if (ruleIn(o, mac) == null) { setStatus("تم فك الحظر: القاعدة اتشالت من الراوتر.") }
            else { setStatus("القاعدة لسه موجودة. افتح (صفحة الراوتر) وشيلها من Access Control.") }
            return
        }
        if (phase == "check") {
            if (ruleIn(o, mac) == null) {
                clearTimeout()
                job = ""
                phase = ""
                applyRules(o)
                setStatus("مفيش قاعدة محظورة بالـMAC ده على الراوتر.")
                return
            }
            phase = "delete"
            setStatus("بشيل قاعدة الحظر...")
            inject("ac_delete.js", hashMapOf("__MAC__" to esc(mac)))
            handler.postDelayed({ if (job == "unblock" && phase == "delete") injectRules() }, 4000)
            return
        }
        if (phase == "delete") {
            phase = "verify"
            injectRules()
        }
    }

    private fun onAdd(o: JSONObject) {
        if (!o.optBoolean("ok", false)) {
            val err = o.optString("error", "")
            if (err.isNotEmpty()) { setStatus("خطوة الإضافة مش مكتملة: " + err) }
        }
    }

    private fun onDel(o: JSONObject) {
        if (!o.optBoolean("ok", false)) {
            val err = o.optString("error", "")
            if (err.isNotEmpty()) { setStatus("خطوة الحذف مش مكتملة: " + err) }
        }
    }

    private fun onSetMode(o: JSONObject) {
        if (!o.optBoolean("ok", false)) {
            val err = o.optString("error", "")
            if (err.isNotEmpty()) { setStatus("تعديل وضع SSID مش مكتمل: " + err) }
        }
    }

    private fun blockDevice(d: Device) {
        blockMac = d.mac
        blockName = if (d.name.isNotEmpty()) d.name else d.mac
        job = "block"
        phase = "check"
        tries = 0
        setStatus("بجهز حظر " + blockName + "...")
        startTimeout("الحظر واخد وقت أكتر من المتوقع. جرب تاني.")
        injectRules()
    }

    private fun unblockDevice(mac: String) {
        unblockMac = mac
        job = "unblock"
        phase = "check"
        tries = 0
        setStatus("بفك الحظر عن " + mac + "...")
        startTimeout("فك الحظر واخد وقت أكتر من المتوقع. جرب تاني.")
        injectRules()
    }

    private fun ruleIn(o: JSONObject, mac: String): Rule? {
        val arr = o.optJSONArray("rules")
        if (arr == null) { return null }
        var i = 0
        while (i < arr.length()) {
            val r = arr.optJSONObject(i)
            if (r != null && r.optString("mac", "").equals(mac, true)) {
                return Rule(r.optString("name", ""), r.optString("ssid", ""), r.optString("mac", ""))
            }
            i++
        }
        return null
    }

    private fun modeIn(o: JSONObject, ssid: String): String {
        val arr = o.optJSONArray("modes")
        if (arr == null) { return "" }
        var i = 0
        while (i < arr.length()) {
            val m = arr.optJSONObject(i)
            if (m != null && m.optString("ssid", "").equals(ssid, true)) { return m.optString("mode", "") }
            i++
        }
        return ""
    }

    private fun applyRules(o: JSONObject) {
        rules.clear()
        val arr = o.optJSONArray("rules") ?: JSONArray()
        var i = 0
        while (i < arr.length()) {
            val r = arr.optJSONObject(i)
            if (r != null) {
                val mac = r.optString("mac", "")
                if (mac.isNotEmpty()) { rules.add(Rule(r.optString("name", ""), r.optString("ssid", ""), mac)) }
            }
            i++
        }
        modes.clear()
        val marr = o.optJSONArray("modes") ?: JSONArray()
        i = 0
        while (i < marr.length()) {
            val m = marr.optJSONObject(i)
            modes.add(if (m == null) "" else m.optString("mode", ""))
            i++
        }
        renderRules()
        renderModes()
    }

    private fun renderDevices() {
        devicesBox.removeAllViews()
        if (devices.isEmpty()) {
            devicesTitle.text = "الأجهزة المتصلة: لا شيء لحد الآن"
            return
        }
        devicesTitle.text = "الأجهزة المتصلة (" + devices.size + ")"
        var i = 0
        while (i < devices.size) {
            val d = devices[i]
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(dp(10), dp(10), dp(10), dp(10))
            card.setBackgroundColor(Color.parseColor("#F2F5F8"))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(8)
            card.layoutParams = lp

            val name = TextView(this)
            name.text = if (d.name.isNotEmpty()) d.name else "جهاز بدون اسم"
            name.textSize = 18f
            name.setTextColor(Color.parseColor("#111111"))
            name.setTypeface(null, Typeface.BOLD)
            card.addView(name)

            val info = TextView(this)
            info.text = "MAC: " + d.mac + "\nIP: " + (if (d.ip.isNotEmpty()) d.ip else "غير معروف")
            info.textSize = 16f
            info.setTextColor(Color.parseColor("#333333"))
            info.setPadding(0, dp(2), 0, dp(6))
            card.addView(info)

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val blockBtn = Button(this)
            blockBtn.text = "احظر"
            blockBtn.textSize = 16f
            blockBtn.setTextColor(Color.WHITE)
            blockBtn.setBackgroundColor(Color.parseColor("#B3261E"))
            blockBtn.layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f)
            blockBtn.setOnClickListener { blockDevice(d) }
            row.addView(blockBtn)
            val gap = View(this)
            gap.layoutParams = LinearLayout.LayoutParams(dp(8), dp(1))
            row.addView(gap)
            val unblockBtn = Button(this)
            unblockBtn.text = "فك الحظر"
            unblockBtn.textSize = 16f
            unblockBtn.layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f)
            unblockBtn.setOnClickListener { unblockDevice(d.mac) }
            row.addView(unblockBtn)
            card.addView(row)

            card.setOnLongClickListener {
                copyText(d.mac)
                setStatus("اتنسخ الـMAC: " + d.mac)
                true
            }
            devicesBox.addView(card)
            i++
        }
    }

    private fun renderModes() {
        modesBox.removeAllViews()
        if (modes.isEmpty()) {
            modesTitle.text = "حالة الحظر لكل SSID: مش مقروءة"
            return
        }
        modesTitle.text = "حالة الحظر لكل SSID"
        val options = ArrayList<String>()
        options.add("No Filter")
        options.add("Black List")
        options.add("White List")
        var i = 0
        while (i < modes.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, dp(4), 0, dp(4))
            val label = TextView(this)
            label.text = "SSID" + (i + 1)
            label.textSize = 16f
            label.setTypeface(null, Typeface.BOLD)
            label.layoutParams = LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT)
            row.addView(label)
            val sp = Spinner(this)
            val ad = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, options)
            ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            sp.adapter = ad
            val cur = modes[i]
            var idx = 0
            if (cur.equals("Black List", true)) { idx = 1 } else if (cur.equals("White List", true)) { idx = 2 }
            sp.setSelection(idx)
            sp.layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            row.addView(sp)
            val applyBtn = Button(this)
            applyBtn.text = "تطبيق"
            applyBtn.textSize = 15f
            applyBtn.layoutParams = LinearLayout.LayoutParams(dp(96), dp(48))
            val ssidNo = i + 1
            applyBtn.setOnClickListener {
                var picked = "No Filter"
                val pos = sp.selectedItemPosition
                if (pos >= 0 && pos < options.size) { picked = options[pos] }
                job = ""
                phase = ""
                setStatus("بغيّر وضع SSID" + ssidNo + " لـ" + picked + "...")
                startTimeout("الراوتر اتأخر في تعديل وضع SSID.")
                inject("ac_setmode.js", hashMapOf("__SSID__" to ssidNo.toString(), "__MODE__" to picked))
                handler.postDelayed({ loadRules() }, 3000)
            }
            row.addView(applyBtn)
            modesBox.addView(row)
            i++
        }
    }

    private fun renderRules() {
        rulesBox.removeAllViews()
        if (rules.isEmpty()) {
            rulesTitle.text = "قواعد الحظر: لا توجد قواعد"
            return
        }
        rulesTitle.text = "قواعد الحظر (" + rules.size + ")"
        var i = 0
        while (i < rules.size) {
            val r = rules[i]
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(dp(10), dp(8), dp(10), dp(8))
            card.setBackgroundColor(Color.parseColor("#FFF8E6"))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(6)
            card.layoutParams = lp
            val t = TextView(this)
            t.text = (if (r.name.isNotEmpty()) r.name else "بدون اسم") + "\n" + r.ssid + "  |  " + r.mac
            t.textSize = 16f
            t.setTextColor(Color.parseColor("#111111"))
            card.addView(t)
            val b = Button(this)
            b.text = "امسح القاعدة (فك الحظر)"
            b.textSize = 15f
            b.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
            val mac = r.mac
            b.setOnClickListener { unblockDevice(mac) }
            card.addView(b)
            rulesBox.addView(card)
            i++
        }
    }

    private fun showDialog(title: String, msg: String) {
        try {
            AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("تمام", null).show()
        } catch (e: Throwable) { setStatus(msg) }
    }

    override fun onBackPressed() {
        try {
            if (webHolder.layoutParams.height != 0) {
                webHolder.layoutParams.height = 0
                webHolder.requestLayout()
                return
            }
        } catch (e: Throwable) { }
        super.onBackPressed()
    }

    override fun onDestroy() {
        try { web.destroy() } catch (e: Throwable) { }
        super.onDestroy()
    }

    companion object {
        private const val DEFAULT_URL = "https://192.168.1.1"
        private const val KEY_ALIAS = "routerctl_key"
        private const val PREFS = "routerctl"
        private const val KEY_ADDRESS = "address"
        private const val KEY_USER = "user"
        private const val KEY_PASS = "pass_enc"
        private const val KEY_AUTO = "auto_login"
    }
}
