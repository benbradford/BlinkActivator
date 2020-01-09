package com.bradope.blinkactivator

import android.content.Context
import android.content.SharedPreferences

fun storeCredewntials(context: Context, credentials: Credentials) {
    val editor = credentialsEditor(context)
    editor.putString("email", decrypt(credentials.email)!!)
    editor.putString("pass", decrypt(credentials.pass)!!)
    editor.apply()
}

fun getCredentials(context: Context): Credentials? {
    val prefs = credentialsPreferenes(context)
    if (!hasCredentials(prefs))
        return null;
    return createCredentials(prefs.getString("email", null)!!, prefs.getString("pass", null)!!)
}

private fun hasCredentials(prefs: SharedPreferences) =  prefs.contains("email") && prefs.contains("pass")
private fun credentialsPreferenes(context: Context) = context.getSharedPreferences("cred", Context.MODE_PRIVATE)
private fun credentialsEditor(context: Context) =  credentialsPreferenes(context).edit()