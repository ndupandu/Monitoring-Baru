package com.pandu.monitoringclient

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.webkit.CookieManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Berjalan hanya saat aplikasi masuk ke latar belakang SEDANG ada perintah konsol yang ditunggu.
 * Memantau /api/console/state sampai bot selesai, lalu menampilkan notifikasi "Konsol selesai".
 * Kalau aplikasi dibuka lagi, pemantauan berhenti dan notifikasi tidak dikirim.
 */
class ConsoleWatchService : Service() {

    @Volatile
    private var stopped = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        App.ensureChannels(this)
        val n = Notification.Builder(this, App.CH_WATCH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Menunggu perintah konsol selesai")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(1, n)
        if (worker?.isAlive != true) {
            worker = Thread { watch() }.also { it.start() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopped = true
        worker?.interrupt()
        super.onDestroy()
    }

    private fun fetchState(): Pair<Int, JSONObject?> {
        var code = -1
        var json: JSONObject? = null
        try {
            val c = URL(App.url.trimEnd('/') + "/api/console/state?since=0").openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            CookieManager.getInstance().getCookie(App.url)?.let { c.setRequestProperty("Cookie", it) }
            code = c.responseCode
            if (code == 200) json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            c.disconnect()
        } catch (_: Exception) {
        }
        return Pair(code, json)
    }

    private fun isBusy(d: JSONObject): Boolean = when (val b = d.opt("busy")) {
        is Boolean -> b
        is Number -> b.toInt() != 0
        else -> false
    }

    private fun lastBotText(d: JSONObject): String {
        var last = ""
        val arr = d.optJSONArray("messages") ?: return last
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("role") == "bot") last = o.optString("text")
        }
        return last
    }

    private fun watch() {
        val t0 = System.currentTimeMillis()
        var baseV: Long? = null
        var seenChange = false
        var sawBusy = false
        var calm = 0
        var fails = 0
        var last = ""
        var finished = false
        try {
            while (!stopped && System.currentTimeMillis() - t0 < 8 * 60_000) {
                if (App.resumed) return                              // aplikasi dibuka: berhenti tanpa notifikasi
                val (code, d) = fetchState()
                if (code == 401) break                               // sesi habis
                if (d == null) {
                    if (++fails > 6) break
                    Thread.sleep(3000)
                    continue
                }
                fails = 0
                val v = d.optLong("v", -1)
                if (baseV == null) baseV = v else if (v != baseV) seenChange = true
                val busy = isBusy(d)
                if (busy) sawBusy = true
                last = lastBotText(d)
                val elapsed = System.currentTimeMillis() - t0
                if (!seenChange && !sawBusy && elapsed > 10_000) break   // perintah sudah selesai sebelum ke latar belakang
                if ((seenChange || sawBusy) && !busy) calm++ else calm = 0
                if (calm >= 2) {
                    finished = true
                    break
                }
                Thread.sleep(3000)
            }
        } catch (_: InterruptedException) {
            return
        }
        if (finished && !App.resumed) notifyDone(last)
        if (finished || !stopped) App.pendingSince = 0
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyDone(botText: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val text = botText.trim().lines().filter { it.isNotBlank() }.take(4).joinToString("\n").take(220)
            .ifBlank { "Perintah di konsol sudah selesai." }
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pi = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, App.CH_DONE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Konsol selesai")
            .setContentText(text.lines().first())
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(2, n)
    }
}
