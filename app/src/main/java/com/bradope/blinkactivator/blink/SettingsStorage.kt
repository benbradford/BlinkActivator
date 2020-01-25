package com.bradope.blinkactivator.blink

import android.content.Context
import android.util.Log
import com.bradope.blinkactivator.R
import java.lang.Exception
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties
import com.google.gson.Gson
import kotlin.reflect.KProperty


fun writeSettingsToStorage(context: Context, blinkSettings: BlinkSettings) {
    try {
        val prefs = context.getSharedPreferences("blink_settings", Context.MODE_PRIVATE).edit()
        prefs.putBoolean("hasData", true)
        prefs.putString("settings", Gson().toJson(blinkSettings))
        prefs.apply()
    } catch ( e: Exception) {
        Log.e("bradope_log_ss", "Problem writing to storage " + e)
    }
}

 fun loadSettingsFromStorage(context: Context, blinkSettings: BlinkSettings) {
    try {
        val prefs = context.getSharedPreferences("blink_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("hasData", false)) return
        val newSettings = Gson().fromJson<BlinkSettings>(
            prefs.getString("settings", ""),
            BlinkSettings::class.java
        )
        for (prop in blinkSettings::class.memberProperties) {
            val other = newSettings::class.members.find { it.name == prop.name }
            val value = other!!.call(newSettings)
            @Suppress("UNCHECKED_CAST")
            (prop as KMutableProperty<R>).setter.call(blinkSettings, value)
        }
    } catch (e: Exception) {
        Log.e("bradope_log_ss", "unable to load settings " + e)
    }
}