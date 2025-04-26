package com.example.beatbox.utils

import android.content.Context
import android.content.SharedPreferences

object PreferenceManager {
    private const val PREFS_NAME = "beatbox_prefs"
    private const val KEY_LAST_TRACK = "last_track"

    fun saveLastTrack(context: Context, uri: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_TRACK, uri).apply()
    }

    fun getLastTrack(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_TRACK, null)
    }
}