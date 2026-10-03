package com.hermes.android.runtime.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts the server sign-in on every request to the signed-in server, and only there:
 *
 * - The gateway WebSocket (`…/api/ws`) gets a brand-new single-use ticket on EVERY dial — the
 *   first connect and each reconnect alike — so the socket URL never holds anything that works twice.
 * - Plain HTTP calls carry the access token as a Bearer header (never in the URL). When the server
 *   says it expired, it is refreshed once and the call retried.
 *
 * A `?token=` left over from the old static-token setup is dropped for that server.
 */
@Singleton
class RemoteAuthInterceptor @Inject constructor(
    private val auth: RemoteAuth,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val tokens = auth.tokensFor(request.url) ?: return chain.proceed(request)
        val url = request.url.newBuilder().removeAllQueryParameters("token").build()

        if (url.encodedPath.endsWith("/api/ws")) {
            val ticket = auth.mintTicketBlocking(tokens)
            val withTicket = url.newBuilder()
                .removeAllQueryParameters("ticket")
                .apply { if (ticket != null) addQueryParameter("ticket", ticket) }
                .build()
            // Without a ticket the server closes the socket with 4401 and the client reports it.
            return chain.proceed(request.newBuilder().url(withTicket).build())
        }

        val access = auth.accessTokenBlocking(tokens)
        val authed = request.newBuilder().url(url).header("Authorization", "Bearer $access").build()
        val response = chain.proceed(authed)
        if (response.code != 401) return response
        val fresh = auth.refreshBlocking(access) ?: return response
        response.close()
        return chain.proceed(authed.newBuilder().header("Authorization", "Bearer ${fresh.accessToken}").build())
    }
}
