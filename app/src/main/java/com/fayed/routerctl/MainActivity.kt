package com.fayed.routerctl

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {

    data class Item(val name: String, val mac: String, val ip: String)

    private lateinit var addressInput: EditText
    private lateinit var web: WebView
    private lateinit var statusView: TextView
    private lateinit var adapter: ArrayAdapter<String>

    private val devices = ArrayList<Item>()
    private val rows = ArrayList<String>()
    private var filterUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        statusView = TextView(this)
        statusView.textSize = 13f
        statusView.text = "افتح صفحة الراوتر وسجّل الدخول، وبعدها اضغط تحديث الأجهزة."
        root.addView(statusView)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        web.addJavascriptInterface(Bridge(), "Android")
        web.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                if (handler == null) {
                    return
                }
                if (isPrivateHost(error?.url?.let { Uri.parse(it).host })) {
                    handler.proceed()
                } else {
                    handler.cancel()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null) {
                    statusView.text = "الصفحة: " + url
                }
                view?.evaluateJavascript(EXTRACT_JS, null)
            }
        }
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f))

        val buttonRow = LinearLayout(this)
        buttonRow.orientation = LinearLayout.HORIZONTAL
        val refresh = Button(this)
        refresh.text = "تحديث الأجهزة"
        refresh.setOnClickListener { runExtract() }
        buttonRow.addView(refresh, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val diag = Button(this)
        diag.text = "تشخيص"
        diag.setOnClickListener { runProbe() }
        buttonRow.addView(diag, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttonRow)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        val listView = ListView(this)
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            if (position >= 0 && position < devices.size) {
                showDeviceDialog(devices[position])
            }
        }
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f))

        setContentView(root)
    }

    private fun prefs() = getSharedPreferences("router_ctl", Context.MODE_PRIVATE)

    private fun openRouter() {
        val raw = addressInput.text.toString().trim()
        val url = when {
            raw.isEmpty() -> DEFAULT_URL
            raw.startsWith("http") -> raw
            else -> "https://" + raw
        }
        prefs().edit().putString(KEY_ADDRESS, url).apply()
        statusView.text = "جاري فتح " + url
        web.loadUrl(url)
    }

    private fun runExtract() {
        statusView.text = "جاري قراءة الأجهزة من الصفحة المفتوحة..."
        web.evaluateJavascript(EXTRACT_JS, null)
    }

    private fun runProbe() {
        statusView.text = "جاري فحص صفحات فلترة MAC..."
        web.evaluateJavascript(PROBE_JS, null)
    }

    private fun renderDevices(json: String) {
        devices.clear()
        rows.clear()
        var message: String
        try {
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
        } catch (e: Exception) {
            message = "تعذر قراءة قائمة الأجهزة: " + e.message
        }
        statusView.text = message
        adapter.notifyDataSetChanged()
    }

    private fun showProbe(json: String) {
        val report = StringBuilder()
        var detected: String? = null
        try {
            val arr = JSONObject(json).optJSONArray("probes") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val path = obj.optString("url")
                val status = obj.optInt("status", 0)
                val pageTitle = obj.optString("title")
                val hasMac = obj.optBoolean("macTable", false)
                val hasFilter = obj.optBoolean("filterForm", false)
                val error = obj.optString("error")
                report.append(path).append("\n")
                if (status == 0) {
                    report.append("  الحالة: فشل (").append(error).append(")\n")
                } else {
                    report.append("  الحالة: ").append(status).append("\n")
                }
                report.append("  العنوان: ").append(if (pageTitle.isEmpty()) "-" else pageTitle).append("\n")
                report.append("  جدول MAC: ").append(if (hasMac) "موجود" else "غير موجود")
                report.append("  |  فلتر MAC: ").append(if (hasFilter) "موجود" else "غير موجود").append("\n\n")
                if (detected == null && status == 200 && hasFilter) {
                    detected = absolute(path)
                }
            }
        } catch (e: Exception) {
            report.append("تعذر قراءة نتايج الفحص: ").append(e.message)
        }
        filterUrl = detected
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
        statusView.text = if (filterUrl == null) {
            "مفيش مسار فلتر MAC متأكد منه. أضف الجهاز من صفحة الحظر في الراوتر."
        } else {
            "مسار محتمل لفلتر MAC: " + filterUrl
        }
    }

    private fun showDeviceDialog(item: Item) {
        val options = arrayOf(
            "افتح صفحة الحظر في الراوتر (إضافة الجهاز يدويًا)",
            "انسخ عنوان MAC"
        )
        val details = StringBuilder()
        details.append("MAC: ").append(item.mac)
        if (item.ip.isNotEmpty()) {
            details.append("\nIP: ").append(item.ip)
        }
        AlertDialog.Builder(this)
            .setTitle(if (item.name.isEmpty()) item.mac else item.name)
            .setMessage(details.toString())
            .setItems(options) { _, which ->
                if (which == 0) {
                    startManualBlock(item)
                } else {
                    copyMac(item.mac)
                    Toast.makeText(this, "اتنسخ عنوان MAC", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun startManualBlock(item: Item) {
        copyMac(item.mac)
        web.loadUrl(filterUrl ?: absolute("/"))
        val message = if (filterUrl != null) {
            "اتنسخ عنوان MAC. أضفه في صفحة فلتر MAC اللي اتفتحت."
        } else {
            "اتنسخ عنوان MAC. مسار فلترة MAC على الفيرموير ده مش متأكد منه، فأضف الجهاز من صفحة الحظر في الراوتر (Local Network أو Management & Diagnosis)."
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun copyMac(mac: String) {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("MAC", mac))
    }

    private fun absolute(path: String): String {
        if (path.startsWith("http")) {
            return path
        }
        val base = web.url ?: DEFAULT_URL
        val root = if (base.startsWith("http")) {
            val uri = Uri.parse(base)
            uri.scheme + "://" + uri.authority
        } else {
            DEFAULT_URL
        }
        return root + path
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
            runOnUiThread { renderDevices(json) }
        }

        @JavascriptInterface
        fun onProbe(json: String) {
            runOnUiThread { showProbe(json) }
        }
    }

    companion object {
        private const val DEFAULT_URL = "https://192.168.1.1"
        private const val KEY_ADDRESS = "address"

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
            "var paths = ['/getpage.gch?pid=1002&nextpage=net_wlan_mac_filter_t.gch'," +
            "'/getpage.gch?pid=1002&nextpage=net_wlan_macfilter_t.gch'," +
            "'/getpage.gch?pid=1002&nextpage=net_wlan_multi_ssid_t.gch'," +
            "'/getpage.gch?pid=1002&nextpage=net_wlan_basic_t.gch'," +
            "'/getpage.gch?pid=1002&nextpage=localnet_lan_t.gch'," +
            "'/getpage.gch?pid=1002&nextpage=man_diag_t.gch'," +
            "'/', '/cgi-bin/luci'];\n" +
            "var results = []; var index = 0;\n" +
            "function step() {\n" +
            "if (index >= paths.length) { Android.onProbe(JSON.stringify({ probes: results })); return; }\n" +
            "var p = paths[index]; index = index + 1;\n" +
            "fetch(p, { credentials: 'include' }).then(function (r) {\n" +
            "return r.text().then(function (t) {\n" +
            "var pageTitle = '';\n" +
            "var m = t.match(/<title[^>]*>([\\s\\S]*?)<\\/title>/i);\n" +
            "if (m) { pageTitle = m[1].replace(/\\s+/g, ' ').trim(); }\n" +
            "var hasForm = /<form|<input/i.test(t);\n" +
            "var macFilter = /mac[\\s_\\-]*filter/i.test(t);\n" +
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
