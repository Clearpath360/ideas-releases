package uk.co.clearpath360.elevate

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.view.WindowInsetsController
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var loaded = false
    private var voiceCb: String? = null
    private var importCb: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crash = ElevateApp.takeCrash(application)
        if (crash != null) {
            android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Elevate crashed last time")
                .setMessage("Tap Copy and paste it to Claude.\n\n" + crash.take(1500))
                .setPositiveButton("Copy") { _, _ ->
                    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Elevate crash", crash))
                    Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Close", null)
                .show()
        }
        try {
            window.statusBarColor = Color.parseColor("#ffffff")
            window.navigationBarColor = Color.parseColor("#ffffff")
        } catch (e: Exception) { }
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#f4f4f4"))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "Native")
        web.webChromeClient = android.webkit.WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                loaded = true
                val v = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
                web.evaluateJavascript("window.BUILD=${JSONObject.quote(v)};", null)
            }
        }
        setContentView(web)
        try {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } catch (e: Exception) { }
        web.loadUrl("file:///android_asset/app.html")
    }

    override fun onResume() {
        super.onResume()
        if (loaded) web.evaluateJavascript("window.onResumeApp && window.onResumeApp()", null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("window.onBack ? window.onBack() : false") { r ->
            if (r != "true") finish()
        }
    }

    private fun done(id: String, payload: String) {
        runOnUiThread {
            web.evaluateJavascript("window.__done(${JSONObject.quote(id)}, ${JSONObject.quote(payload)})", null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VOICE) {
            val cb = voiceCb ?: return
            voiceCb = null
            val text = if (resultCode == RESULT_OK)
                data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: "" else ""
            done(cb, text)
        } else if (requestCode == REQ_IMPORT) {
            val cb = importCb ?: return
            importCb = null
            val uri = data?.data
            if (resultCode != RESULT_OK || uri == null) { done(cb, ""); return }
            thread {
                val s = try { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "" } catch (e: Exception) { "" }
                done(cb, s)
            }
        }
    }

    inner class Bridge {
        @JavascriptInterface fun load(): String = Store.load(this@MainActivity)
        @JavascriptInterface fun save(s: String) { Store.save(this@MainActivity, s) }
        @JavascriptInterface fun takeInbox(): String = Store.takeInbox(this@MainActivity)
        @JavascriptInterface fun image(name: String): String = Store.imageDataUrl(this@MainActivity, name)

        @JavascriptInterface fun toast(m: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, m, Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface fun copy(text: String) {
            runOnUiThread {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Elevate", text))
            }
        }

        @JavascriptInterface fun openUrl(url: String) {
            runOnUiThread {
                try {
                    if (url.startsWith("http://") || url.startsWith("https://"))
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) { }
            }
        }

        @JavascriptInterface fun voice(cb: String) {
            runOnUiThread {
                voiceCb = cb
                try {
                    startActivityForResult(CaptureActivity.voiceIntent("Say your idea"), REQ_VOICE)
                } catch (e: ActivityNotFoundException) {
                    voiceCb = null
                    Toast.makeText(this@MainActivity, "Voice typing is not available, type it instead", Toast.LENGTH_LONG).show()
                    done(cb, "")
                }
            }
        }

        @JavascriptInterface fun importFile(cb: String) {
            runOnUiThread {
                importCb = cb
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                try { startActivityForResult(i, REQ_IMPORT) } catch (e: Exception) { importCb = null; done(cb, "") }
            }
        }

        @JavascriptInterface fun exportFile(name: String, content: String): String {
            return try {
                val v = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return "Could not save the backup"
                contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                "Backup saved to Downloads"
            } catch (e: Exception) { "Could not save the backup" }
        }

        /** req: {url, method, headers:{}, body}. Answers {ok, status, body}. */
        @JavascriptInterface fun http(reqJson: String, cb: String) {
            thread {
                val out = JSONObject()
                try {
                    val req = JSONObject(reqJson)
                    val url = req.getString("url")
                    if (!url.startsWith("https://api.anthropic.com/")) throw IllegalArgumentException("Blocked address")
                    val con = URL(url).openConnection() as HttpURLConnection
                    con.requestMethod = req.optString("method", "POST")
                    con.connectTimeout = 30000
                    con.readTimeout = 240000
                    val h = req.optJSONObject("headers")
                    h?.keys()?.forEach { k -> con.setRequestProperty(k, h.getString(k)) }
                    val body = req.optString("body", "")
                    if (body.isNotEmpty()) {
                        con.doOutput = true
                        con.outputStream.use { it.write(body.toByteArray()) }
                    }
                    val code = con.responseCode
                    val stream = if (code >= 400) con.errorStream else con.inputStream
                    val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                    out.put("ok", code in 200..299).put("status", code).put("body", text)
                } catch (e: Exception) {
                    out.put("ok", false).put("status", 0).put("body", e.message ?: "error")
                }
                done(cb, out.toString())
            }
        }
    }

    companion object {
        const val REQ_VOICE = 11
        const val REQ_IMPORT = 12
    }
}
