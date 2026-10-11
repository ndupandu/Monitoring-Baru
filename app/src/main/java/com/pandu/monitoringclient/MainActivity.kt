package com.pandu.monitoringclient

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private var web: WebView? = null
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.ensureChannels(this)
        if (getSharedPreferences("app", MODE_PRIVATE).getBoolean("intro", false)) showWeb() else showIntro()
    }

    // ------------------------------------------------------------ layar izin (sebelum login)
    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private fun showIntro() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.WHITE)
        }
        fun label(t: String, size: Float, bold: Boolean = false, bottom: Int = 12) = TextView(this).apply {
            text = t
            textSize = size
            setTextColor(0xFF182033.toInt())
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(bottom))
        }
        box.addView(label("Monitoring Client", 26f, true, 8))
        box.addView(label("Sebelum masuk, aplikasi membutuhkan dua izin:", 16f, false, 16))
        box.addView(label("\u2022 Lokasi\nUntuk menyimpan titik pelanggan dari posisi HP dan menampilkan peta.", 15f, false, 12))
        box.addView(label("\u2022 Notifikasi\nUntuk memberi tahu saat perintah konsol selesai ketika aplikasi sedang tidak dibuka.", 15f, false, 20))
        box.addView(label("Tekan Lanjutkan, lalu pilih Izinkan pada pop-up Android.", 14f, false, 20))
        box.addView(Button(this).apply {
            text = "Lanjutkan"
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF2F6FDB.toInt())
            setOnClickListener {
                val perms = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                requestPermissions(perms.toTypedArray(), 1)
            }
        })
        setContentView(box)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            getSharedPreferences("app", MODE_PRIVATE).edit().putBoolean("intro", true).apply()
            showWeb()
        }
    }

    // ------------------------------------------------------------ dashboard
    @SuppressLint("SetJavaScriptEnabled")
    private fun showWeb() {
        val w = WebView(this)
        web = w
        setContentView(w)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setGeolocationEnabled(true)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        w.addJavascriptInterface(Bridge(), "MonitorApp")
        w.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                callback?.invoke(origin, hasLoc(), false)
            }
        }
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.host == Uri.parse(App.url).host) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, u))   // Google Maps dll dibuka di aplikasinya
                } catch (_: Exception) {
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(INJECT, null)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    view.loadData(
                        "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                            "<body style='font-family:sans-serif;padding:24px'><h3>Tidak bisa terhubung</h3>" +
                            "<p>Periksa koneksi internet atau alamat server.</p>" +
                            "<button style='padding:12px 20px;font-size:16px' onclick=\"location.href='" + App.url + "'\">Coba lagi</button></body>",
                        "text/html", "utf-8"
                    )
                }
            }
        }
        w.loadUrl(App.url)
    }

    // ------------------------------------------------------------ jembatan ke halaman web
    inner class Bridge {
        /** Dipanggil halaman (lewat skrip suntikan) saat perintah konsol dikirim. */
        @JavascriptInterface
        fun watch() {
            App.pendingSince = System.currentTimeMillis()
        }

        @JavascriptInterface
        fun openSettings() {
            runOnUiThread {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }

        @JavascriptInterface
        fun getLocation(rawId: String) {
            val id = rawId.filter { it.isLetterOrDigit() }
            runOnUiThread { locate(id) }
        }
    }

    private fun hasLoc() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun reply(id: String, ok: Boolean, a: Double, b: Double = 0.0, c: Double = 0.0) {
        web?.evaluateJavascript("window.__monGeo&&window.__monGeo('$id',$ok,$a,$b,$c)", null)
    }

    @Suppress("DEPRECATION")
    private fun locate(id: String) {
        if (!hasLoc()) {
            reply(id, false, 1.0)                                   // 1 = izin ditolak
            return
        }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        fun enabled(p: String) = try { lm.isProviderEnabled(p) } catch (_: Exception) { false }
        val prov = if (enabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER
        else if (enabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER
        else null
        if (prov == null) {
            reply(id, false, 2.0)                                   // 2 = lokasi/GPS HP mati
            return
        }
        var done = false
        val holder = arrayOfNulls<LocationListener>(1)
        val timeout = Runnable {
            if (done) return@Runnable
            done = true
            holder[0]?.let { try { lm.removeUpdates(it) } catch (_: Exception) {} }
            val last: Location? = try {
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    .mapNotNull { try { lm.getLastKnownLocation(it) } catch (_: Exception) { null } }
                    .filter { System.currentTimeMillis() - it.time < 120_000 }
                    .maxByOrNull { it.time }
            } catch (_: Exception) { null }
            if (last != null) reply(id, true, last.latitude, last.longitude, last.accuracy.toDouble())
            else reply(id, false, 3.0)                              // 3 = terlalu lama
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) {
                if (done) return
                done = true
                ui.removeCallbacks(timeout)
                try { lm.removeUpdates(this) } catch (_: Exception) {}
                reply(id, true, l.latitude, l.longitude, l.accuracy.toDouble())
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        holder[0] = listener
        try {
            lm.requestLocationUpdates(prov, 0L, 0f, listener, Looper.getMainLooper())
        } catch (_: SecurityException) {
            reply(id, false, 1.0)
            return
        }
        ui.postDelayed(timeout, 20_000)
    }

    // ------------------------------------------------------------ siklus hidup
    override fun onResume() {
        super.onResume()
        App.resumed = true
        stopService(Intent(this, ConsoleWatchService::class.java))   // aplikasi terbuka: tidak perlu notifikasi
        web?.onResume()
    }

    override fun onPause() {
        super.onPause()
        App.resumed = false
        CookieManager.getInstance().flush()
        if (App.hasPending()) {
            try {
                startForegroundService(Intent(this, ConsoleWatchService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else super.onBackPressed()
    }

    companion object {
        /** Disuntikkan ke setiap halaman: GPS lewat Android + deteksi perintah konsol dikirim. */
        private const val INJECT = """
(function(){
if(window.__monInj)return;window.__monInj=1;
var cbs={},n=0;
window.__monGeo=function(id,ok,a,b,c){var f=cbs[id];delete cbs[id];if(!f)return;
 if(ok)f.ok({coords:{latitude:a,longitude:b,accuracy:c},timestamp:Date.now()});else f.err({code:a,message:'geolocation'})};
var gp=function(ok,err){var id='g'+(++n);cbs[id]={ok:ok,err:err||function(){}};MonitorApp.getLocation(id)};
try{Object.defineProperty(navigator,'geolocation',{value:{getCurrentPosition:gp,watchPosition:function(){return 0},clearWatch:function(){}},configurable:true})}catch(e){}
var f0=window.fetch;
window.fetch=function(u,o){
 try{var url=typeof u==='string'?u:(u&&u.url)||'';
  if(o&&String(o.method).toUpperCase()==='POST'&&(url.indexOf('/api/console/send')>=0||url.indexOf('/api/console/click')>=0))MonitorApp.watch()}catch(e){}
 return f0.apply(this,arguments)};
})();
"""
    }
}
