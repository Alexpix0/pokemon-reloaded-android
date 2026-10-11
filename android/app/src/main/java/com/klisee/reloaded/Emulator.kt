package com.klisee.reloaded

import android.content.Context
import android.content.Intent

/** Starts the native activity that runs the bundled `libgm8emulator.so`. */
object Emulator {
    private const val GAME_ACTIVITY = "com.klisee.reloaded.GameActivity"

    fun start(context: Context) {
        context.startActivity(Intent().setClassName(context.packageName, GAME_ACTIVITY))
    }
}
