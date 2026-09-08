package org.h1code2.english.notebook.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.h1code2.english.notebook.R
import org.h1code2.english.notebook.SyncActivity
import org.h1code2.english.notebook.data.AppDatabase
import org.h1code2.english.notebook.data.BackupManager
import java.net.NetworkInterface

/**
 * 电脑同步接收服务：前台服务包装 [SyncServer]，
 * 提供通知栏常驻提示与 WifiLock 保持链路活跃。
 */
class SyncServerService : Service() {

    private var server: SyncServer? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "电脑同步", NotificationManager.IMPORTANCE_LOW).apply {
                description = "接收来自电脑 Chrome 扩展的推送"
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (server != null) return START_STICKY

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null) ?: newToken().also {
            prefs.edit().putString(KEY_TOKEN, it).apply()
        }

        val appContext = applicationContext
        val dao = AppDatabase.get(appContext).entryDao()

        val newServer = SyncServer(
            port = PORT,
            token = token,
            onAdd = { body -> PushProtocol.parseAdd(body) },
            onStore = { request ->
                runBlocking {
                    withContext(Dispatchers.IO) {
                        val exists = dao.countSameTitle(request.title, request.type) > 0
                        if (exists) {
                            SyncServer.StoreResult(added = 0, duplicates = 1)
                        } else {
                            val now = System.currentTimeMillis()
                            dao.insert(
                                org.h1code2.english.notebook.data.EntryEntity(
                                    title = request.title,
                                    content = request.content,
                                    type = request.type,
                                    createdAt = now,
                                    updatedAt = now
                                )
                            )
                            BackupManager.autoBackup(appContext)
                            appendLog(appContext, request.title)
                            SyncServer.StoreResult(added = 1, duplicates = 0)
                        }
                    }
                }
            }
        )

        if (!newServer.start()) {
            stopSelf()
            return START_NOT_STICKY
        }
        server = newServer
        isRunning = true
        actualPort = newServer.actualPort

        startForeground(NOTIFICATION_ID, buildNotification(newServer.actualPort, token))

        val wifi = applicationContext.getSystemService(WifiManager::class.java)
        wifiLock = wifi?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "english-notebook-sync")?.apply {
            setReferenceCounted(false)
            acquire()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        isRunning = false
        wifiLock?.release()
        wifiLock = null
        super.onDestroy()
    }

    private fun buildNotification(port: Int, token: String): Notification {
        val ip = localIp() ?: "127.0.0.1"
        val text = "http://$ip:$port · 令牌 $token"
        val tapIntent = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, SyncActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_backup)
            .setContentTitle("电脑同步接收中")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    private fun newToken(): String {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        return (1..6).map { alphabet.random() }.joinToString("")
    }

    companion object {
        const val PREFS = "sync_prefs"
        const val KEY_TOKEN = "token"
        const val KEY_ENABLED = "enabled"
        const val KEY_LOG = "received_log"
        const val PORT = 8765
        const val CHANNEL_ID = "sync_server"
        const val NOTIFICATION_ID = 1001

        @Volatile var isRunning: Boolean = false
            private set
        @Volatile var actualPort: Int = PORT
            private set

        fun toggle(context: Context, enable: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enable).apply()
            if (enable) {
                context.startForegroundService(Intent(context, SyncServerService::class.java))
            } else {
                context.stopService(Intent(context, SyncServerService::class.java))
            }
        }

        /** 取局域网 IPv4：按常见网段优先级回退，避免个别路由器网段（如 100.x CGNAT）检测不到 */
        fun localIp(): String? {
            val ips = NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<java.net.Inet4Address>()
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                .map { it.hostAddress }
                .toList()
            return ips.firstOrNull { it.startsWith("192.168.") }     // 家用路由常见
                ?: ips.firstOrNull { it.startsWith("10.") }          // 部分路由 / 模拟器
                ?: ips.firstOrNull { it.startsWith("172.") }         // 172.16-31 私网段
                ?: ips.firstOrNull { it.startsWith("100.") }         // CGNAT（部分运营商 / 热点）
                ?: ips.firstOrNull()                                  // 兜底：任意非回环 IPv4
        }

        fun appendLog(context: Context, title: String) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val list = readLog(context).toMutableList()
            val stamp = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                .format(System.currentTimeMillis())
            list.add("$stamp · $title")
            prefs.edit().putString(KEY_LOG, list.takeLast(20).joinToString("\u0001")).apply()
        }

        fun readLog(context: Context): List<String> =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LOG, "")?.split("\u0001")?.filter { it.isNotBlank() }?.reversed()
                ?: emptyList()
    }
}
