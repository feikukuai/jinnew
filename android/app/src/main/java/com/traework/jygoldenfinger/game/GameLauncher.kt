package com.traework.jygoldenfinger.game

import android.content.Context
import android.content.Intent

object GameLauncher {

    fun isInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(GamePaths.GAME_PACKAGE) != null

    fun launch(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(GamePaths.GAME_PACKAGE)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}