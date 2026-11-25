package com.fayed.routerctl

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MainActivity : Activity() {

    data class Item(val name: String, val mac: String, val ip: String)

    data class Rule(val name: String, val ssid: String, val mac: String)

    private lateinit var addressInput: EditText
    private lateinit var userInput: EditText
    private lateinit var passInput: EditText
    private lateinit var autoLoginBox: CheckBox
    private lateinit var web: WebView
    private lateinit var statusView: TextView
    private lateinit var adapter: ArrayAdapter<String>

    private val devices = ArrayList<Item>()
    private val rows = ArrayList<String>()
    private val rules = ArrayList<Rule>()
    private val handler = Handler(Looper.getMainLooper())

    private var needsLogin = false
    private var hasAccessControl = false
    private var ssid1Mode = ""
    private var pendingRead = ""
    private var pendingAction = ""
    private var pendingMac = ""
    private var pendingName = ""
    private var gotoFollow = "block"
    private var gotoAttempted = false
    private var loginTried = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            buildUi()
        } catch (e: Exception) {
            val fallback = TextView(this)
            fallback.text = "حصل خطأ في تجهيز الشاشة: " + e.message
            setContentView(fallback)
        }
    }

    private fun buildUi() {
        val pad = (12 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        val title = TextView(this)
        title.text = "مدير أجهزة شبكة الراوتر"
        title.textSize = 18f
        root.addView(title)

        val urlRow = LinearLayout(this)
        urlRow.orientation = LinearLayout.HORIZONTAL
        addressInput = EditText(this)
        addressInput.setSingleLine(true)
        addressInput.hint = DEFAULT_URL
        addressInput.setText(prefs().getString(KEY_ADDRESS, DEFAULT_URL))
        urlRow.addView(addressInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val openButton = Button(this)
        openButton.text = "فتح"
        openButton.setOnClickListener { openRouter() }
        urlRow.addView(openButton)
        root.addView(urlRow)

        val credsRow = LinearLayout(this)
        credsRow.orientation = LinearLayout.HORIZONTAL
        userInput = EditText(this)
        userInput.setSingleLine(true)
        userInput.hint = "اسم المستخدم"
        userInput.setText(prefs().getString(KEY_USER, "admin"))
        credsRow.addView(userInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        passInput = EditText(this)
        passInput.setSingleLine(true)
        passInput.hint = "باسورد الراوتر"
        passInput.inputType = 129
        credsRow.addView(passInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val saveButton = Button(this)
        saveButton.text = "حفظ"
        saveButton.setOnClickListener { saveCredentials() }
        credsRow.addView(saveButton)
        root.addView(credsRow)

        autoLoginBox = CheckBox(this)
        autoLoginBox.text = "دخول تلقائي بالباسورد المحفوظ"
        autoLoginBox.isChecked = prefs().getBoolean(KEY_AUTO, true)
        autoLoginBox.setOnCheckedChangeListener { _, isChecked ->
            prefs().edit().putBoolean(KEY_AUTO, isChecked).apply()
        }
        root.addView(autoLoginBox)

        statusView = TextView(this)
        statusView.textSize = 13f
        statusView.text = "اكتب بيانات الدخول واحفظها، وبعدها اضغط فتح. الباسورد بيتخزن مشفّر على الموبايل بس."
        root.addView(statusView)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        web.addJavascriptInterface(Bridge(), "Android")
        web.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(view: WebView?, sslHandler: SslErrorHandler?, error: SslError?) {
                try {
                    if (sslHandler == null) {
                        return
                    }
                    if (isPrivateHost(error?.url?.let { Uri.parse(it).host })) {
                        sslHandler.proceed()
                    } else {
                        sslHandler.cancel()
                    }
                } catch (e: Exception) {
                    statusView.text = "مشكلة في شهادة أمان الراوتر: " + e.message
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                loginTried = false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                try {
                    if (url != null) {
                        statusView.text = "الصفحة: " + url
                    }
                    view?.evaluateJavascript(EXTRACT_JS, null)
                } catch (e: Exception) {
                    statusView.text = "تعذر قراءة الصفحة: " + e.message
                }
                maybeAutoLogin()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request != null && request.isForMainFrame) {
                    statusView.text = "مش قادر أوصل لصفحة الراوتر. اتأكد إنك متصل بواي فاي الراوتر وإن العنوان صح، وبعدها اضغط فتح تاني."
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request != null && request.isForMainFrame) {
                    statusView.text = "الراوتر رد بخطأ " + (errorResponse?.statusCode ?: 0) + ". جرّب تحديث الصفحة أو سجّل الدخول تاني."
                }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                try {
                    if (message != null) {
                        statusView.text = message
                    }
                    result?.confirm()
                } catch (e: Exception) {
                    result?.confirm()
                }
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                try {
                    result?.confirm()
                } catch (e: Exception) {
                }
                return true
            }
        }
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f))

        val buttonRow = LinearLayout(this)
        buttonRow.orientation = LinearLayout.HORIZONTAL
        val refresh = Button(this)
        refresh.text = "تحديث الأجهزة"
        refresh.setOnClickListener { runExtract() }
        buttonRow.addView(refresh, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val rulesButton = Button(this)
        rulesButton.text = "قواعد الحظر"
        rulesButton.setOnClickListener {
            statusView.text = "بقرأ قواعد الحظر من صفحة الراوتر..."
            readRules("list")
        }
        buttonRow.addView(rulesButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val diag = Button(this)
        diag.text = "تشخيص"
        diag.setOnClickListener { runProbe() }
        buttonRow.addView(diag, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttonRow)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        val listView = ListView(this)
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            try {
                if (position >= 0 && position < devices.size) {
                    showDeviceDialog(devices[position])
                }
            } catch (e: Exception) {
                statusView.text = "تعذر فتح الجهاز: " + e.message
            }
        }
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f))

        setContentView(root)
    }

    private fun prefs() = getSharedPreferences("router_ctl", Context.MODE_PRIVATE)

    private fun openRouter() {
        try {
            val raw = addressInput.text.toString().trim()
            val url = when {
                raw.isEmpty() -> DEFAULT_URL
                raw.startsWith("http") -> raw
                else -> "https://" + raw
            }
            prefs().edit().putString(KEY_ADDRESS, url).apply()
            statusView.text = "جاري فتح " + url
            web.loadUrl(url)
        } catch (e: Exception) {
            statusView.text = "تعذر فتح الصفحة: " + e.message
        }
    }

    private fun runExtract() {
        try {
            statusView.text = "جاري قراءة الأجهزة من الصفحة المفتوحة..."
            web.evaluateJavascript(EXTRACT_JS, null)
            scheduleWatch("extract", "الصفحة ما ردّتش على تحديث الأجهزة. اضغط تحديث تاني أو أعد فتح الصفحة.")
        } catch (e: Exception) {
            statusView.text = "تعذر تحديث الأجهزة: " + e.message
        }
    }

    private fun runProbe() {
        try {
            statusView.text = "جاري فحص مسارات صفحة الحظر على الراوتر..."
            web.evaluateJavascript(PROBE_JS, null)
            scheduleWatch("probe", "الفحص ما رجعش نتيجة. جرّب تضغط تشخيص تاني بعد ما تتأكد إنك داخل الراوتر.")
        } catch (e: Exception) {
            statusView.text = "تعذر بدء الفحص: " + e.message
        }
    }

    private fun scheduleWatch(kind: String, message: String) {
        handler.postDelayed({
            if (pendingRead == kind || pendingAction == kind) {
                pendingRead = ""
                pendingAction = ""
                statusView.text = message
            }
        }, 10000)
    }

    private fun evaluateAsset(name: String) {
        try {
            val js = assets.open(name).bufferedReader().use { it.readText() }
            web.evaluateJavascript(js, null)
        } catch (e: Exception) {
            statusView.text = "تعذر تحميل " + name + ": " + e.message
        }
    }

    private fun evaluateAsset(name: String, mac: String, label: String) {
        try {
            val raw = assets.open(name).bufferedReader().use { it.readText() }
            val js = raw.replace("__MAC__", jsEscape(mac)).replace("__NAME__", jsEscape(label))
            web.evaluateJavascript(js, null)
        } catch (e: Exception) {
            statusView.text = "تعذر تحميل " + name + ": " + e.message
        }
    }

    private fun jsEscape(value: String): String {
        val sb = StringBuilder()
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\'' -> sb.append("\\'")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("")
                '<' -> sb.append("\\u003c")
                '>' -> sb.append("\\u003e")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun saveCredentials() {
        try {
            val user = userInput.text.toString().trim()
            val pass = passInput.text.toString()
            if (pass.isEmpty()) {
                statusView.text = "اكتب باسورد الراوتر الأول."
                return
            }
            prefs().edit()
                .putString(KEY_USER, if (user.isEmpty()) "admin" else user)
                .putBoolean(KEY_AUTO, autoLoginBox.isChecked)
                .apply()
            if (writeSecret(pass)) {
                passInput.setText("")
                statusView.text = "اتحفظت بيانات الدخول على الموبايل بس، مشفّرة."
            } else {
                statusView.text = "تعذر تشفير الباسورد وحفظه على الجهاز ده."
            }
        } catch (e: Exception) {
            statusView.text = "تعذر حفظ بيانات الدخول: " + e.message
        }
    }

    private fun secretKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            val entry = ks.getEntry(KEY_ALIAS, null)
            if (entry is KeyStore.SecretKeyEntry) {
                entry.secretKey
            } else {
                val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
                gen.init(spec)
                gen.generateKey()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun writeSecret(value: String): Boolean {
        return try {
            val key = secretKey() ?: return false
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val data = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val body = Base64.encodeToString(data, Base64.NO_WRAP)
            prefs().edit().putString(KEY_SECRET, iv + ":" + body).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun readSecret(): String? {
        return try {
            val stored = prefs().getString(KEY_SECRET, null) ?: return null
            val parts = stored.split(":")
            if (parts.size != 2) {
                return null
            }
            val key = secretKey() ?: return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun maybeAutoLogin() {
        try {
            if (!autoLoginBox.isChecked || loginTried) {
                return
            }
            loginTried = true
            handler.postDelayed({ injectLogin() }, 1500)
        } catch (e: Exception) {
        }
    }

    private fun injectLogin() {
        try {
            val user = prefs().getString(KEY_USER, "admin") ?: "admin"
            val pass = readSecret()
            if (pass == null) {
                statusView.text = "مفيش باسورد محفوظ. اكتبه في خانة الباسورد واضغط حفظ."
                return
            }
            val raw = assets.open("ac_login.js").bufferedReader().use { it.readText() }
            val js = raw.replace("__USER__", jsEscape(user)).replace("__PASS__", jsEscape(pass))
            web.evaluateJavascript(js, null)
        } catch (e: Exception) {
            statusView.text = "تعذر الدخول التلقائي: " + e.message
        }
    }

    private fun readRules(kind: String) {
        try {
            pendingRead = kind
            evaluateAsset("ac_read.js")
            scheduleWatch(kind, "الصفحة ما ردّتش. جرّب تحدّث الصفحة، ولو ظهرت شاشة دخول سجّل الدخول من جديد.")
        } catch (e: Exception) {
            statusView.text = "تعذر قراءة القواعد: " + e.message
        }
    }

    private fun renderDevices(json: String) {
        try {
            devices.clear()
            rows.clear()
            var message: String
            val arr = JSONObject(json).optJSONArray("devices") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val mac = obj.optString("mac")
                val ip = obj.optString("ip")
                val name = obj.optString("name")
                devices.add(Item(name, mac, ip))
                val label = StringBuilder()
                label.append(if (name.isEmpty()) "جهاز" else name)
                label.append("\n").append(mac)
                if (ip.isNotEmpty()) {
                    label.append("  ·  ").append(ip)
                }
                rows.add(label.toString())
            }
            message = if (devices.isEmpty()) {
                "مفيش أجهزة ظاهرة. افتح Local Network > WLAN > WLAN Client Status وسجّل الدخول الأول."
            } else {
                "عدد الأجهزة: " + devices.size + " — اضغط على أي جهاز للتحكم."
            }
            statusView.text = message
            adapter.notifyDataSetChanged()
        } catch (e: Exception) {
            statusView.text = "تعذر عرض قائمة الأجهزة: " + e.message
        }
    }

    private fun handleRules(json: String) {
        val kind = pendingRead
        pendingRead = ""
        try {
            val root = JSONObject(json)
            needsLogin = root.optBoolean("needsLogin", false)
            hasAccessControl = root.optBoolean("hasAccessControl", false)
            rules.clear()
            val arr = root.optJSONArray("rules") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                rules.add(Rule(o.optString("name"), o.optString("ssid"), o.optString("mac")))
            }
            ssid1Mode = ""
            val modes = root.optJSONArray("modes") ?: JSONArray()
            for (i in 0 until modes.length()) {
                val o = modes.getJSONObject(i)
                if (o.optString("ssid").equals("SSID1", true)) {
                    ssid1Mode = o.optString("mode")
                }
            }
        } catch (e: Exception) {
            statusView.text = "تعذر قراءة الصفحة: " + e.message
            return
        }
        if (needsLogin) {
            statusView.text = "الراوتر رجّعك لشاشة الدخول. سجّل الدخول (أو فعّل الدخول التلقائي واضغط فتح)، وبعدها جرّب تاني."
            return
        }
        when (kind) {
            "list" -> showRulesDialog()
            "block" -> continueBlock()
            "verifyBlock" -> verifyBlock()
            "verifyUnblock" -> verifyUnblock()
        }
    }

    private fun continueBlock() {
        if (!hasAccessControl) {
            if (!gotoAttempted) {
                gotoAttempted = true
                gotoFollow = "block"
                pendingAction = "goto"
                statusView.text = "بحاول أفتح صفحة WLAN Advanced من منيو الراوتر..."
                evaluateAsset("ac_goto.js")
                scheduleWatch("goto", "مقدرتش أفتح صفحة الحظر لوحدي. افتحها من Local Network ← WLAN ← WLAN Advanced وبعدها اضغط على الجهاز تاني.")
                return
            }
            statusView.text = "مش لاقي صفحة Access Control. افتح Local Network ← WLAN ← WLAN Advanced وبعدها اضغط على الجهاز تاني."
            return
        }
        if (rules.any { it.mac.equals(pendingMac, true) }) {
            statusView.text = "الـMAC ده موجود بالفعل في قواعد الحظر."
            return
        }
        if (!ssid1Mode.contains("black", true)) {
            pendingAction = "setmode"
            statusView.text = "بضبط SSID1 على Black List..."
            evaluateAsset("ac_setmode.js")
            scheduleWatch("setmode", "مقدرتش أضبط SSID1 على Black List. اضبطها يدويًا من Access Control-Mode Configuration.")
            return
        }
        addRule()
    }

    private fun addRule() {
        pendingAction = "add"
        statusView.text = "بضيف قاعدة حظر على SSID1 للجهاز " + pendingName + "..."
        evaluateAsset("ac_add.js", pendingMac, pendingName)
        scheduleWatch("add", "الصفحة ما ردّتش على إضافة القاعدة. أضفها يدويًا من Create New Item.")
    }

    private fun handleActionResult(json: String) {
        val action = pendingAction
        pendingAction = ""
        val o = try {
            JSONObject(json)
        } catch (e: Exception) {
            JSONObject()
        }
        val ok = o.optBoolean("ok", false)
        val error = o.optString("error")
        when (action) {
            "goto" -> {
                if (ok) {
                    statusView.text = "اتفتحت " + o.optString("label") + "، بستنى الصفحة..."
                    handler.postDelayed({ readRules(gotoFollow) }, 4000)
                } else {
                    statusView.text = "مقدرتش ألاقي لينك WLAN Advanced. افتحها من قايمة Local Network وبعدها اضغط على الجهاز تاني."
                }
            }
            "setmode" -> {
                if (!ok) {
                    statusView.text = "مقدرتش أضبط SSID1 على Black List من الصفحة دي: " + error
                    return
                }
                statusView.text = "SSID1 بقى Black List. بضيف القاعدة..."
                handler.postDelayed({ addRule() }, 3000)
            }
            "add" -> {
                if (!ok) {
                    statusView.text = "مقدرتش أضيف القاعدة من الصفحة دي: " + error
                    return
                }
                statusView.text = "اتبعتت القاعدة للراوتر، بتأكد من القايمة..."
                handler.postDelayed({ readRules("verifyBlock") }, 3000)
            }
            "delete" -> {
                if (!ok) {
                    statusView.text = "مقدرتش أحذف القاعدة من الصفحة دي: " + error
                    return
                }
                statusView.text = "اتبعت الحذف، بتأكد من القايمة..."
                handler.postDelayed({ readRules("verifyUnblock") }, 3000)
            }
        }
    }

    private fun verifyBlock() {
        val rule = rules.firstOrNull { it.mac.equals(pendingMac, true) }
        if (rule == null) {
            statusView.text = "الراوتر استقبل الطلب بس القاعدة مش ظاهرة في القايمة. راجع «قواعد الحظر» أو أضفها يدويًا."
            return
        }
        val onSsid1 = rule.ssid.isEmpty() || rule.ssid.equals("SSID1", true)
        statusView.text = if (onSsid1) {
            "تم وأكده الراوتر: القاعدة اتسجلت على SSID1 والقايمة فيها الـMAC. الجهاز هيفصل من الشبكة."
        } else {
            "القاعدة اتسجلت على " + rule.ssid + " مش SSID1، فمش هتحظر على الشبكة الشغالة. راجعها من «قواعد الحظر»."
        }
    }

    private fun verifyUnblock() {
        val still = rules.any { it.mac.equals(pendingMac, true) }
        statusView.text = if (still) {
            "القاعدة لسه موجودة بعد الحذف. جرّب تحذفها من صفحة الراوتر."
        } else {
            "تم وأكده الراوتر: القاعدة اتشالت من قايمة الحظر."
        }
    }

    private fun showRulesDialog() {
        if (!hasAccessControl) {
            statusView.text = "الصفحة المفتوحة مش صفحة WLAN Advanced. افتح Local Network ← WLAN ← WLAN Advanced وبعدها اضغط «قواعد الحظر»."
            return
        }
        if (rules.isEmpty()) {
            statusView.text = "مفيش قواعد حظر مسجلة على الراوتر دلوقتي."
            return
        }
        val labels = ArrayList<String>()
        for (r in rules) {
            val line = StringBuilder()
            line.append(if (r.name.isEmpty()) "بدون اسم" else r.name)
            if (r.ssid.isNotEmpty()) {
                line.append("  ·  ").append(r.ssid)
            }
            line.append("\n").append(r.mac).append("  —  اضغط لفك الحظر")
            labels.add(line.toString())
        }
        AlertDialog.Builder(this)
            .setTitle("قواعد الحظر (" + rules.size + ")")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which >= 0 && which < rules.size) {
                    confirmUnblock(rules[which])
                }
            }
            .setNegativeButton("إغلاق", null)
            .show()
        statusView.text = "قواعد الحظر: " + rules.size + " — SSID1 على " + (if (ssid1Mode.isEmpty()) "حالة غير معروفة" else ssid1Mode)
    }

    private fun confirmUnblock(rule: Rule) {
        val label = if (rule.name.isEmpty()) rule.mac else rule.name
        AlertDialog.Builder(this)
            .setTitle("فك الحظر")
            .setMessage("أشيل القاعدة " + label + " (" + rule.mac + ") من " + (if (rule.ssid.isEmpty()) "قايمة الحظر" else rule.ssid) + "؟")
            .setPositiveButton("اشيلها") { _, _ -> deleteRule(rule) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun deleteRule(rule: Rule) {
        try {
            pendingMac = rule.mac
            pendingName = rule.name
            pendingAction = "delete"
            statusView.text = "بحذف القاعدة " + rule.mac + "..."
            evaluateAsset("ac_delete.js", rule.mac, rule.name)
            scheduleWatch("delete", "الصفحة ما ردّتش على الحذف. احذف القاعدة يدويًا من WLAN Advanced.")
        } catch (e: Exception) {
            statusView.text = "تعذر بدء الحذف: " + e.message
        }
    }

    private fun showProbe(json: String) {
        try {
            val report = StringBuilder()
            val root = JSONObject(json)
            val links = root.optJSONArray("links") ?: JSONArray()
            if (links.length() > 0) {
                report.append("لينكات منيو الراوتر:\n")
            }
            for (i in 0 until links.length()) {
                val link = links.getJSONObject(i)
                val href = link.optString("href")
                val label = link.optString("text")
                report.append("  ").append(if (label.isEmpty()) href else label)
                report.append(" -> ").append(href).append("\n")
            }
            report.append("\n")
            val probes = root.optJSONArray("probes") ?: JSONArray()
            for (i in 0 until probes.length()) {
                val probe = probes.getJSONObject(i)
                val path = probe.optString("url")
                val status = probe.optInt("status", 0)
                val pageTitle = probe.optString("title")
                val hasMac = probe.optBoolean("macTable", false)
                val hasFilter = probe.optBoolean("filterForm", false)
                val error = probe.optString("error")
                report.append(path).append("\n")
                if (status == 0) {
                    report.append("  الحالة: فشل (").append(error).append(")\n")
                } else {
                    report.append("  الحالة: ").append(status).append("\n")
                }
                report.append("  العنوان: ").append(if (pageTitle.isEmpty()) "-" else pageTitle).append("\n")
                report.append("  جدول MAC: ").append(if (hasMac) "موجود" else "غير موجود")
                report.append("  |  فلتر MAC: ").append(if (hasFilter) "موجود" else "غير موجود").append("\n\n")
            }
            val box = TextView(this)
            box.text = report.toString()
            box.textSize = 12f
            val scroll = ScrollView(this)
            scroll.addView(box)
            AlertDialog.Builder(this)
                .setTitle("نتايج التشخيص")
                .setView(scroll)
                .setPositiveButton("تمام", null)
                .show()
            statusView.text = "التشخيص خلص. صفحة الحظر على الفيرموير ده هي WLAN Advanced تحت Local Network."
        } catch (e: Exception) {
            statusView.text = "تعذر عرض نتايج التشخيص: " + e.message
        }
    }

    private fun showDeviceDialog(item: Item) {
        try {
            val options = arrayOf("احظر الجهاز من الشبكة", "افتح صفحة الحظر في الراوتر", "انسخ عنوان MAC")
            val details = StringBuilder()
            details.append("MAC: ").append(item.mac)
            if (item.ip.isNotEmpty()) {
                details.append("\nIP: ").append(item.ip)
            }
            AlertDialog.Builder(this)
                .setTitle(if (item.name.isEmpty()) item.mac else item.name)
                .setMessage(details.toString())
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> beginBlock(item)
                        1 -> openFilterPage()
                        else -> {
                            copyMac(item.mac)
                            Toast.makeText(this, "اتنسخ عنوان MAC", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("إلغاء", null)
                .show()
        } catch (e: Exception) {
            statusView.text = "تعذر فتح الجهاز: " + e.message
        }
    }

    private fun beginBlock(item: Item) {
        try {
            pendingMac = item.mac
            pendingName = safeName(item.name, item.mac)
            gotoAttempted = false
            statusView.text = "بقرأ قواعد الحظر من صفحة الراوتر..."
            readRules("block")
        } catch (e: Exception) {
            statusView.text = "تعذر بدء الحظر: " + e.message
        }
    }

    private fun openFilterPage() {
        try {
            gotoFollow = "list"
            gotoAttempted = true
            pendingAction = "goto"
            statusView.text = "بحاول أفتح صفحة WLAN Advanced..."
            evaluateAsset("ac_goto.js")
            scheduleWatch("goto", "مقدرتش أفتح الصفحة لوحدي. افتحها من Local Network ← WLAN ← WLAN Advanced.")
        } catch (e: Exception) {
            statusView.text = "تعذر فتح صفحة الحظر: " + e.message
        }
    }

    private fun safeName(name: String, mac: String): String {
        val cleaned = name.replace("\n", " ").replace("\"", "").replace("'", "").replace("\\", "").trim()
        val picked = if (cleaned.isEmpty()) "blocked" else cleaned
        return if (picked.length > 24) picked.substring(0, 24) else picked
    }

    private fun copyMac(mac: String) {
        try {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText("MAC", mac))
        } catch (e: Exception) {
            statusView.text = "تعذر نسخ الـMAC: " + e.message
        }
    }

    private fun isPrivateHost(host: String?): Boolean {
        if (host == null || host.isEmpty()) {
            return false
        }
        if (host == "localhost" || host == "127.0.0.1") {
            return true
        }
        val parts = host.split(".")
        if (parts.size != 4) {
            return false
        }
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        if (first == 10) {
            return true
        }
        if (first == 192 && second == 168) {
            return true
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return true
        }
        return false
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onDevices(json: String) {
            try {
                runOnUiThread { renderDevices(json) }
            } catch (e: Exception) {
            }
        }

        @JavascriptInterface
        fun onProbe(json: String) {
            try {
                runOnUiThread { showProbe(json) }
            } catch (e: Exception) {
            }
        }

        @JavascriptInterface
        fun onRules(json: String) {
            try {
                runOnUiThread { handleRules(json) }
            } catch (e: Exception) {
            }
        }

        @JavascriptInterface
        fun onActionResult(json: String) {
            try {
                runOnUiThread { handleActionResult(json) }
            } catch (e: Exception) {
            }
        }

        @JavascriptInterface
        fun onLoginResult(json: String) {
            try {
                runOnUiThread { handleLoginResult(json) }
            } catch (e: Exception) {
            }
        }
    }

    private fun handleLoginResult(json: String) {
        try {
            val o = JSONObject(json)
            if (o.optBoolean("ok", false)) {
                statusView.text = "اتبعتت بيانات الدخول للراوتر. لو شاشة الدخول لسه ظاهرة يبقى الباسورد غلط — عدّله واضغط حفظ."
                return
            }
            val error = o.optString("error")
            if (error == "password-field-not-found") {
                return
            }
            statusView.text = "لقيت خانة الباسورد بس مش لاقي زرار دخول. سجّل الدخول بإيدك مرة واحدة."
        } catch (e: Exception) {
        }
    }

    companion object {
        private const val DEFAULT_URL = "https://192.168.1.1"
        private const val KEY_ADDRESS = "address"
        private const val KEY_USER = "user"
        private const val KEY_AUTO = "autologin"
        private const val KEY_SECRET = "secret"
        private const val KEY_ALIAS = "router_ctl_key"

        private const val EXTRACT_JS = "(function () {\n" +
            "var macRe = /^[0-9a-fA-F]{2}([:-][0-9a-fA-F]{1,2}){4,7}$/;\n" +
            "var ipRe = /^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$/;\n" +
            "var found = []; var seen = {};\n" +
            "var rows = document.querySelectorAll('tr');\n" +
            "for (var i = 0; i < rows.length; i++) {\n" +
            "var cells = rows[i].querySelectorAll('td, th');\n" +
            "var mac = ''; var ip = ''; var name = '';\n" +
            "for (var j = 0; j < cells.length; j++) {\n" +
            "var t = (cells[j].innerText || cells[j].textContent || '').trim();\n" +
            "if (t === '') { continue; }\n" +
            "if (mac === '' && macRe.test(t)) { mac = t; continue; }\n" +
            "if (ip === '' && ipRe.test(t)) { ip = t; continue; }\n" +
            "if (name === '') { name = t; }\n" +
            "}\n" +
            "if (mac !== '' && !seen[mac.toLowerCase()]) {\n" +
            "seen[mac.toLowerCase()] = 1;\n" +
            "found.push({ name: name, mac: mac, ip: ip });\n" +
            "}\n" +
            "}\n" +
            "Android.onDevices(JSON.stringify({ page: location.href, title: document.title, devices: found }));\n" +
            "})();"

        private const val PROBE_JS = "(function () {\n" +
            "var links = [];\n" +
            "function collect(doc) {\n" +
            "var anchors = doc.querySelectorAll('a');\n" +
            "for (var a = 0; a < anchors.length; a++) {\n" +
            "var href = anchors[a].getAttribute('href') || '';\n" +
            "var text = (anchors[a].innerText || anchors[a].textContent || '').trim();\n" +
            "if (href !== '' && (/(mac|filter|access|control|wlan)/i.test(href) || /(mac|filter|access|control|wlan)/i.test(text))) {\n" +
            "links.push({ href: href, text: text.slice(0, 80) });\n" +
            "}\n" +
            "}\n" +
            "}\n" +
            "collect(document);\n" +
            "for (var f = 0; f < window.frames.length; f++) {\n" +
            "try { collect(window.frames[f].document); } catch (err) { }\n" +
            "}\n" +
            "var paths = ['/getpage.gch?pid=1002&nextpage=net_wlan_adv_t.gch'," +
            "'/getpage.gch?pid=1002&nextpage=net_wlan_advanced_t.gch'," +
            "'/getpage.gch?pid=1002&nextpage=net_wlan_mac_filter_t.gch'," +
            "'/getpage.gch?pid=1002&nextpage=net_wlan_macfilter_t.gch'," +
            "'/getpage.gch?pid=1002&nextpage=net_wlan_access_control_t.gch'," +
            "'/getpage.gch?pid=1002&nextpage=net_security_mac_filter_t.gch'," +
            "'/getpage.gch?pid=1002&nextpage=net_wlan_multi_ssid_t.gch'," +
            "'/getpage.gch?pid=1002&nextpage=net_wlan_basic_t.gch'," +
            "'/getpage.gch?pid=1002&nextpage=localnet_lan_t.gch'," +
            "'/getpage.gch?pid=1002&nextpage=man_diag_t.gch'," +
            "'/', '/cgi-bin/luci'];\n" +
            "var results = []; var index = 0;\n" +
            "function step() {\n" +
            "if (index >= paths.length) { Android.onProbe(JSON.stringify({ probes: results, links: links })); return; }\n" +
            "var p = paths[index]; index = index + 1;\n" +
            "fetch(p, { credentials: 'include' }).then(function (r) {\n" +
            "return r.text().then(function (t) {\n" +
            "var pageTitle = '';\n" +
            "var m = t.match(/<title[^>]*>([\\s\\S]*?)<\\/title>/i);\n" +
            "if (m) { pageTitle = m[1].replace(/\\s+/g, ' ').trim(); }\n" +
            "var hasForm = /<form|<input/i.test(t);\n" +
            "var macFilter = /mac[\\s_\\-]*filter/i.test(t) || /access[\\s_\\-]*control/i.test(t);\n" +
            "results.push({ url: p, status: r.status, title: pageTitle,\n" +
            "macTable: /([0-9a-fA-F]{2}[:-]){5}[0-9a-fA-F]{2}/.test(t),\n" +
            "filterForm: hasForm && macFilter, error: '' });\n" +
            "});\n" +
            "}).catch(function (e) {\n" +
            "results.push({ url: p, status: 0, title: '', macTable: false, filterForm: false, error: String(e) });\n" +
            "}).then(step);\n" +
            "}\n" +
            "step();\n" +
            "})();"
    }
}
