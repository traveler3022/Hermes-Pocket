package com.hermes.android.runtime.remote.tailscale

import org.json.JSONArray
import org.json.JSONObject
import java.net.NetworkInterface
import java.util.Collections

/**
 * Gives Go the phone's network interfaces. Android blocks the netlink socket Go
 * would read them from, so the Tailscale Android app does the same from Java.
 */
internal object NetInfo : tsbridge.Platform {
    override fun interfacesJSON(): String {
        val out = JSONArray()
        val all = try {
            NetworkInterface.getNetworkInterfaces()?.let { Collections.list(it) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        for (nif in all) {
            try {
                val addrs = JSONArray()
                for (ia in nif.interfaceAddresses) {
                    val host = ia.address?.hostAddress ?: continue
                    addrs.put(JSONObject().put("ip", host).put("prefix", ia.networkPrefixLength.toInt()))
                }
                out.put(
                    JSONObject()
                        .put("name", nif.name)
                        .put("index", nif.index)
                        .put("mtu", nif.mtu)
                        .put("up", nif.isUp)
                        .put("loopback", nif.isLoopback)
                        .put("p2p", nif.isPointToPoint)
                        .put("multicast", nif.supportsMulticast())
                        .put("addrs", addrs),
                )
            } catch (_: Exception) {
                continue
            }
        }
        return out.toString()
    }
}
