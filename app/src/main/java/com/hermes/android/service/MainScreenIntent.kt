package com.hermes.android.service

import android.content.Context
import android.content.Intent

/**
 * The app's main screen, which a tap on a notification opens. MainActivity is above this layer;
 * the app binds this, so [HermesNotifications] doesn't import it.
 */
fun interface MainScreenIntent {
    fun create(context: Context): Intent
}
