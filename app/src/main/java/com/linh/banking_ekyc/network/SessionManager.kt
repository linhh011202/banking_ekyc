package com.linh.banking_ekyc.network

import android.content.Context
import android.content.SharedPreferences

object SessionManager {

    private const val PREF_NAME = "banking_ekyc_session"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_EMAIL = "email"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveSession(context: Context, email: String, accessToken: String) {
        getPrefs(context).edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_EMAIL, email)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
    }

    fun getAccessToken(context: Context): String? {
        return getPrefs(context).getString(KEY_ACCESS_TOKEN, null)
    }

    fun getEmail(context: Context): String? {
        return getPrefs(context).getString(KEY_EMAIL, null)
    }

    fun isLoggedIn(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun clearSession(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}

