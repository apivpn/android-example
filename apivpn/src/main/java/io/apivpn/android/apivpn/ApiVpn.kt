package io.apivpn.android.apivpn

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
data class Country(val code: String, val name: String)

@Serializable
data class Server(
    val id: Int,
    val ip: String,
    val exit: String,
    val name: String,
    val icon: String?,
    val hostname: String,
    val location: String,
    val latitude: Double,
    val longitude: Double,
    val premium: Boolean,
    val country: Country,
    val sort: Int,
    val pin: Boolean,
    val group_id: Int?,
)

typealias Servers = Array<Server>

class ApiVpnError(
    val code: Int
) {
    override fun toString(): String {
        return "ApiVpnError(code=${code})"
    }
}

interface InitializationCallback {
    fun onSuccess()
    fun onError(error: ApiVpnError)
}

interface ReceiveServersCallback {
    fun onReceived(servers: Servers)
    fun onError(error: ApiVpnError)
}

interface ReceiveConnectionLogFileCallback {
    fun onReceived(path: String)
    fun onError(error: ApiVpnError)
}

interface StartV2RayCallback {
    fun onSuccess()
    fun onError(error: ApiVpnError)
}

public class ApiVpn {
    init {
        // Require permission android.permission.INTERNET
        System.loadLibrary("apivpncore")
    }

    private external fun apiVpnLastError(): Int
    private external fun apiVpnInit(appToken: String, apiServer: String, dataDir: String): Int
    private external fun apiVpnServers(): String?
    private external fun apiVpnConnectionLogFile(): String?
    private external fun apiVpnStartV2Ray(serverId: Int, tunFd: Int, altRules: String): Int
    private external fun apiVpnStop()
    private external fun apiVpnIsRunning(): Boolean

    companion object {
        val shared: ApiVpn by lazy {
            ApiVpn()
        }
    }

    fun initialize(context: Context, appToken: String, apiServer: String, cb: InitializationCallback) {
        val files = context.filesDir.list()
        if (!files.contains("geo.mmdb")) {
            val bytes = context.resources.openRawResource(R.raw.geo).readBytes()
            val fos = context.openFileOutput("geo.mmdb", Context.MODE_PRIVATE)
            fos.write(bytes)
            fos.close()
        }
        if (!files.contains("site.dat")) {
            val bytes = context.resources.openRawResource(R.raw.site).readBytes()
            val fos = context.openFileOutput("site.dat", Context.MODE_PRIVATE)
            fos.write(bytes)
            fos.close()
        }
        Os.setenv("ASSET_LOCATION", context.filesDir.absolutePath, true)
        GlobalScope.launch {
            val code = apiVpnInit(appToken, apiServer, context.filesDir.absolutePath)
            if (code == 0) {
                cb.onSuccess()
            } else {
                cb.onError(ApiVpnError(code))
            }
        }
    }

    fun servers(cb: ReceiveServersCallback) {
        GlobalScope.launch {
            val serialized = apiVpnServers()
            serialized?.let {
                println("Serialized servers ${it}")
                val servers = Json.decodeFromString<Servers>(it)
                cb.onReceived(servers)
            } ?: run {
                val code = apiVpnLastError()
                cb.onError(ApiVpnError(code))
            }
        }
    }

    fun connectionLogFile(cb: ReceiveConnectionLogFileCallback) {
        GlobalScope.launch {
            val path = apiVpnConnectionLogFile()
            path?.let {
                println("Connection log file ${it}")
                cb.onReceived(it)
            } ?: run {
                val code = apiVpnLastError()
                cb.onError(ApiVpnError(code))
            }
        }
    }

    fun startV2Ray(serverId: Int, tunFd: ParcelFileDescriptor, altRules: String, cb: StartV2RayCallback) {
        val code = apiVpnStartV2Ray(serverId, tunFd.detachFd().toInt(), altRules)
        if (code == 0) {
            cb.onSuccess()
        } else {
            cb.onError(ApiVpnError(code))
        }
    }

    fun stop() {
        apiVpnStop()
    }

    fun is_running(): Boolean {
        return apiVpnIsRunning()
    }
}