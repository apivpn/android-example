package io.apivpn.android.apivpn_example

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import io.apivpn.android.apivpn.*
import io.apivpn.android.apivpn_example.Constants.Companion.API_SERVER
import io.apivpn.android.apivpn_example.Constants.Companion.APP_TOKEN
import io.apivpn.android.apivpn_example.Constants.Companion.PREF_SELECTED_SERVER_ID
import io.apivpn.android.apivpn_example.Constants.Companion.VPN_CTL_PING
import io.apivpn.android.apivpn_example.Constants.Companion.VPN_CTL_PONG
import io.apivpn.android.apivpn_example.Constants.Companion.VPN_CTL_STARTED
import io.apivpn.android.apivpn_example.Constants.Companion.VPN_CTL_STOP
import io.apivpn.android.apivpn_example.Constants.Companion.VPN_CTL_STOPPED
import kotlin.concurrent.thread

class ApiVpnService : VpnService() {
    private var running = false

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(contxt: Context?, intent: Intent?) {
            when (intent?.action) {
                VPN_CTL_STOP -> {
                    ApiVpn.shared.stop()
                    stopSelf()
                    stopped()
                }
                VPN_CTL_PING -> {
                    if (running) {
                        sendBroadcast(Intent(VPN_CTL_PONG))
                    }
                }
            }
        }
    }

    private fun startAsForeground() {
        fun createNotificationChannel(channelId: String, channelName: String): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val chan = NotificationChannel(channelId,
                    channelName, NotificationManager.IMPORTANCE_HIGH)
                chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                service.createNotificationChannel(chan)
                channelId
            } else {
                ""
            }
        }
        val channelId = createNotificationChannel(getString(R.string.app_name), getString(R.string.app_name))
        val intent = Intent()
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, channelId)
        builder.setWhen(System.currentTimeMillis())
        builder.setSmallIcon(R.drawable.ic_launcher_foreground)
        val largeIconBitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        builder.setLargeIcon(largeIconBitmap)
        builder.priority = NotificationCompat.PRIORITY_HIGH
        builder.setFullScreenIntent(pendingIntent, true)
        val notification = builder.build()
        startForeground(1, notification)
    }

    private fun stopped() {
        running = false
        stopForeground(true)
        sendBroadcast(Intent(VPN_CTL_STOPPED))
    }

    private fun started() {
        running = true
        sendBroadcast(Intent(VPN_CTL_STARTED))
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(broadcastReceiver, IntentFilter(VPN_CTL_STOP))
        registerReceiver(broadcastReceiver, IntentFilter(VPN_CTL_PING))
        ApiVpn.shared.initialize(this, APP_TOKEN, API_SERVER, object: InitializationCallback {
            override fun onError(error: ApiVpnError) {
                println("ApiVpn initialization failed: ${error}")
            }
            override fun onSuccess() {
                println("ApiVpn initialized.")
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = Builder()

        startAsForeground()

        // Required for connections initiated by the tunnel to bypass the VPN interface.
        // TODO Allow per-app VPN settings via socket `protect`.
        builder.addDisallowedApplication(BuildConfig.APPLICATION_ID)

        val tun = builder.addAddress("10.255.0.1", 24)
            .addDnsServer("1.1.1.1")
            .addRoute("0.0.0.0", 0)
            .establish()

        tun?.let { fd ->
            val pref = getSharedPreferences(Constants.SHARED_PREF, Context.MODE_PRIVATE)
            val serverId = pref.getInt(PREF_SELECTED_SERVER_ID, 0)
            if (serverId == 0) {
                println("No selected server")
                stopped()
                return@let
            }
            ApiVpn.shared.initialize(this, APP_TOKEN, API_SERVER, object: InitializationCallback {
                override fun onError(error: ApiVpnError) {
                    println("ApiVpn initialization failed: ${error}")
                    stopped()
                }
                override fun onSuccess() {
                    thread {
                        println("Starting server ${serverId}")
                        val altRules = """
{
  "rules": [
    {
      "ip": ["1.1.1.1"],
      "outboundTag": "Proxy"
    }
  ]
}
                        """.trimIndent()
                        ApiVpn.shared.startV2Ray(serverId, fd, altRules, object: StartV2RayCallback {
                            override fun onSuccess() {
                                println("Server normal shutdown")
                            }
                            override fun onError(error: ApiVpnError) {
                                println("Start server failed: $error")
                                stopped()
                            }
                        })
                    }
                    thread {
                        for (i in 1..10) {
                            Thread.sleep(1000)
                            if (ApiVpn.shared.is_running()) {
                                started()
                                return@thread
                            }
                        }
                    }
                }
            })
        }
        return START_STICKY
    }
}
