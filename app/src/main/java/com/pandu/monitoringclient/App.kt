package com.pandu.monitoringclient

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Status bersama antara Activity, jembatan JavaScript, dan service pemantau konsol. */
object App {
    const val CH_WATCH = "watch"
    const val CH_DONE = "done"

    /** true selama aplikasi tampil di layar (onResume sampai onPause). */
    @Volatile
    var resumed = false

    /** Waktu terakhir halaman mengirim perintah konsol (0 = tidak ada yang ditunggu). */
    @Volatile
    var pendingSince = 0L

    val url: String get() = BuildConfig.SERVER_URL

    fun hasPending(): Boolean =
        pendingSince > 0 && System.currentTimeMillis() - pendingSince < 10 * 60_000

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_WATCH, "Menunggu perintah konsol", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Perintah konsol selesai", NotificationManager.IMPORTANCE_HIGH)
        )
    }
}
