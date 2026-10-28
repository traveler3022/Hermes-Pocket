package com.hermes.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hermes.android.domain.cron.CronWake
import timber.log.Timber

/**
 * The phone's alarm for the next cron job (set by [CronWake.rearm]). It only wakes the gateway
 * service; the service boots Hermes, waits for the job's minute and fires it.
 */
class CronAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CronWake.ACTION_ALARM) return
        val dueAt = intent.getLongExtra(CronWake.EXTRA_DUE_AT, 0L)
        Timber.i("[CronAlarm] Alarm for the job at $dueAt — waking Hermes")
        HermesGatewayService.wakeForCron(context, dueAt)
    }
}
