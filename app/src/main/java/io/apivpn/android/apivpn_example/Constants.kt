package io.apivpn.android.apivpn_example

class Constants {
    companion object {
        const val APP_TOKEN = "1dc5d3126b46dc79b3908b28c32ac7b9909d1d6b"
        const val API_SERVER = "api.devop.pw"

        const val PREFIX = "APIVPN_EXAMPLE_"
        const val SHARED_PREF = PREFIX + "SHARED_PREF"
        const val PREF_SELECTED_SERVER_ID = PREFIX + "PREF_SELECTED_SERVER_ID"
        const val VPN_CTL_PING = PREFIX + "VPN_CTL_PING"
        const val VPN_CTL_PONG = PREFIX + "VPN_CTL_PONG"
        const val VPN_CTL_STARTED = PREFIX + "VPN_CTL_STARTED"
        const val VPN_CTL_STOP = PREFIX + "VPN_CTL_STOP"
        const val VPN_CTL_STOPPED = PREFIX + "VPN_CTL_STOPPED"
    }
}