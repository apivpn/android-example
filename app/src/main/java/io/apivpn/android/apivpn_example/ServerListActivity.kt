package io.apivpn.android.apivpn_example

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import io.apivpn.android.apivpn.*
import io.apivpn.android.apivpn_example.Constants.Companion.API_SERVER
import io.apivpn.android.apivpn_example.Constants.Companion.PREF_SELECTED_SERVER_ID
import io.apivpn.android.apivpn_example.Constants.Companion.SHARED_PREF

class ServerListActivity : AppCompatActivity() {
    private lateinit var servers: ArrayList<Server>
    private lateinit var swipeView: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager

    private fun fetchServers(cb: (servers: Servers) -> Unit) {
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
                            cb(receivedServers)
                        }
                    })
                }
            })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_list)

        servers = arrayListOf<Server>()
        swipeView = findViewById<SwipeRefreshLayout>(R.id.server_list_swipe_container)
        viewAdapter = ServerAdapter(servers)
        viewManager = LinearLayoutManager(this)

        recyclerView = findViewById<RecyclerView>(R.id.list_server_list).apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        fun onSwipeRefresh() {
            fetchServers { receivedServers ->
                servers.clear()
                servers.addAll(receivedServers)
                runOnUiThread {
                    swipeView.isRefreshing = false
                    viewAdapter.notifyDataSetChanged()
                }
            }
        }

        swipeView.setOnRefreshListener {
            onSwipeRefresh()
        }

        // Trigger a refresh manually
        swipeView.post {
            swipeView.isRefreshing = true
            onSwipeRefresh()
        }
    }

    inner class ServerOnClickListener : View.OnClickListener {
        override fun onClick(v: View) {
            val serverName = v.findViewById<TextView>(R.id.lb_server_name).text.toString()
            val selectedServer = servers.filter { it.name == serverName }.single()
            val pref = getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE)
            pref.edit().putInt(PREF_SELECTED_SERVER_ID, selectedServer.id).commit()
            println("Selected server ID ${selectedServer.id}")
            NavUtils.navigateUpFromSameTask(v.context as Activity)
        }
    }

    inner class ServerAdapter(private val myDataset: ArrayList<Server>) :
        RecyclerView.Adapter<ServerAdapter.AppViewHolder>() {

        inner class AppViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            var serverNameLabel: TextView = view.findViewById(R.id.lb_server_name)

            fun bind(server: Server) {
                serverNameLabel.text = server.name
            }

            fun clear() {
            }
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ServerAdapter.AppViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.server_list_item, parent, false)
            view.setOnClickListener(ServerOnClickListener())
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            var server = servers[position]
            holder.bind(server)
        }

        override fun getItemCount() = myDataset.size
    }
}