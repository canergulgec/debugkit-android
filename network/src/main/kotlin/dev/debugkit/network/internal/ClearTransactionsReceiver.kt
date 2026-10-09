package dev.debugkit.network.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The notification's Clear action. May run in a fresh process, so it sets up storage first. */
internal class ClearTransactionsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        NetworkModule.init(context)
        TransactionStore.clear()
    }
}
