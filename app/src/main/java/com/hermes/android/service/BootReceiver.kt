package com.hermes.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Receiver that starts [HermesGatewayService] on device boot.
 *
 * Per ADR-004, this is a temporary migration dependency. In the future
 * (Step 12 — Embedded Runtime Migration), this receiver will start the
 * service directly without needing Termux:Boot.
 *
 * Note: Requires RECEIVE_BOOT_COMPLETED permission (declared in manifest).
 *
 * Reference: ADR-004 (Background Execution — Termux:Boot temporary)
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.i("[BootReceiver] Boot completed — starting Hermes gateway service")
            HermesGatewayService.start(context)
        }
    }
}
