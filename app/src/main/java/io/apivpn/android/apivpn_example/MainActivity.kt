package io.apivpn.android.apivpn_example

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.apivpn.android.apivpn.*
import io.apivpn.android.apivpn_example.Constants.Companion.API_SERVER
import io.apivpn.android.apivpn_example.Constants.Companion.VPN_CTL_PING
import io.apivpn.android.apivpn_example.Constants.Companion.VPN_CTL_PONG
import io.apivpn.android.apivpn_example.Constants.Companion.VPN_CTL_STARTED
import io.apivpn.android.apivpn_example.Constants.Companion.VPN_CTL_STOP
import io.apivpn.android.apivpn_example.Constants.Companion.VPN_CTL_STOPPED
import java.io.File
import java.io.InputStream

enum class State {
    STARTING, STARTED, STOPPING, STOPPED
}

class MainActivity : AppCompatActivity() {
    private var state: State = State.STOPPED

    val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                VPN_CTL_STOPPED -> {
                    stopped()

                }
                VPN_CTL_STARTED -> {
                    started()
                }
                VPN_CTL_PONG -> {
                    pong()
                }
            }
        }
    }

    private fun stop() {
        state = State.STOPPING
        findViewById<Button>(R.id.btn_connect).text = "Stopping"
        sendBroadcast(Intent(VPN_CTL_STOP))
    }

    private fun printConnections() {
        ApiVpn.shared.connectionLogFile(object : ReceiveConnectionLogFileCallback {
            override fun onError(error: ApiVpnError) {
                println("Get connection log file failed: $error")
            }

            override fun onReceived(path: String) {
                val input: InputStream = File(path).inputStream()
                input.bufferedReader().forEachLine {
                    println("{$it}")
                }
            }
        })
    }

    private fun stopped() {
        state = State.STOPPED
        findViewById<Button>(R.id.btn_connect).text = "Start"
        findViewById<Button>(R.id.btn_connect).setTextColor(Color.parseColor("#1679fa"))
        printConnections()
    }

    private fun start() {
        state = State.STARTING
        findViewById<Button>(R.id.btn_connect).text = "Starting"
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 1)
        } else {
            onActivityResult(1, Activity.RESULT_OK, null);
        }
    }

    private fun started() {
        state = State.STARTED
        findViewById<Button>(R.id.btn_connect).text = "Stop"
        findViewById<Button>(R.id.btn_connect).setTextColor(Color.parseColor("#fa3916"))
    }

    private fun ping() {
        sendBroadcast(Intent(VPN_CTL_PING))
    }

    private fun pong() {
        started()
    }

    private fun updateSelectedServerLabel(label: String) {
        runOnUiThread {
            findViewById<TextView>(R.id.lb_selected_server).text = label
        }
    }

    private fun updateSelectedServer() {
        val pref = getSharedPreferences(Constants.SHARED_PREF, Context.MODE_PRIVATE)
        val selectedServerId = pref.getInt(Constants.PREF_SELECTED_SERVER_ID, 0)
        if (selectedServerId == 0) {
            updateSelectedServerLabel("None")
            return
        }
        ApiVpn.shared.initialize(this,
            Constants.APP_TOKEN, API_SERVER, object : InitializationCallback {
                override fun onError(error: ApiVpnError) {
                    println("ApiVpn initialization failed: ${error}")
                }

                override fun onSuccess() {
                    ApiVpn.shared.servers(object : ReceiveServersCallback {
                        override fun onError(error: ApiVpnError) {
                            println("Receive server failed: $error")
                        }

                        override fun onReceived(receivedServers: Servers) {
                            for (server in receivedServers) {
                                if (server.id == selectedServerId) {
                                    updateSelectedServerLabel(server.name)
                                    return
                                }
                            }
                            updateSelectedServerLabel("None")
                        }
                    })
                }
            })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        registerReceiver(broadcastReceiver, IntentFilter(VPN_CTL_STOPPED))
        registerReceiver(broadcastReceiver, IntentFilter(VPN_CTL_STARTED))
        registerReceiver(broadcastReceiver, IntentFilter(VPN_CTL_PONG))
        findViewById<Button>(R.id.btn_connect).setOnClickListener {
            when (state) {
                State.STOPPED -> {
                    start()
                }
                State.STARTED -> {
                    stop()
                }
                else -> {}
            }
        }
        findViewById<Button>(R.id.btn_server_list).setOnClickListener {
            startActivity(Intent(this, ServerListActivity::class.java))
        }
        updateSelectedServer()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            val intent = Intent(this, ApiVpnService::class.java)
            startService(intent)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onResume() {
        super.onResume()
        ping()
        updateSelectedServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }
}