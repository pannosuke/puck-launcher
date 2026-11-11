package com.eric.pucklauncher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent

/** Opens [url] in the user's default browser as a Custom Tab (Samsung Internet / Chrome), falling back to ACTION_VIEW. */
fun Context.openInBrowser(url: String) {
    val uri = Uri.parse(url)
    try {
        val colors = CustomTabColorSchemeParams.Builder().setToolbarColor(0xFF0A0F1A.toInt()).build()
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .setDefaultColorSchemeParams(colors)
            .build()
            .launchUrl(this, uri)
    } catch (e: ActivityNotFoundException) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: ActivityNotFoundException) {
            Toast.makeText(this, "No browser found", Toast.LENGTH_LONG).show()
        }
    }
}
