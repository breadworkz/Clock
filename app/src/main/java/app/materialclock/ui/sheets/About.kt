package app.materialclock.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.materialclock.BuildConfig

/**
 * The About section: the things an app needs before it can be given to somebody else.
 *
 * Every claim below is checkable against the APK rather than being a promise. That is the whole
 * standard this section is held to. The suite's rule is that *a stale privacy claim is worse than
 * none*, because it is the one thing a user has no way to verify for themselves. So:
 *
 *  - **No `INTERNET` permission**, and not in any merged dependency manifest either. That was
 *    checked, not assumed. An app without it cannot open a socket: the platform refuses, so this
 *    is enforced by Android rather than by the app's good behaviour. It is the only privacy claim
 *    worth making, because it is the only one that does not depend on trusting the developer.
 *  - Every other permission is listed with what it is actually for. A permission list the user has
 *    to interpret is not disclosure.
 *  - The bundled fonts are named with their licences, because two of them are redistributed
 *    binaries and that carries obligations.
 */
@Composable
fun AboutRows() {
    var open by remember { mutableStateOf<AboutPage?>(null) }

    SectionLabel("About")
    NavigateRow(
        title = "Privacy",
        subtitle = "No accounts, no telemetry, no network",
        onClick = { open = AboutPage.PRIVACY },
    )
    NavigateRow(
        title = "Permissions",
        subtitle = "What each one is for",
        onClick = { open = AboutPage.PERMISSIONS },
    )
    NavigateRow(
        title = "Licences",
        subtitle = "Fonts and libraries",
        onClick = { open = AboutPage.LICENCES },
    )
    Text(
        "Clock ${BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp),
    )

    open?.let { page ->
        AlertDialog(
            onDismissRequest = { open = null },
            confirmButton = { TextButton(onClick = { open = null }) { Text("Close") } },
            title = { Text(page.title) },
            text = {
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    page.body.forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
        )
    }
}

private enum class AboutPage(val title: String, val body: List<String>) {

    PRIVACY(
        "Privacy",
        listOf(
            "This app collects nothing, sends nothing and stores nothing outside your device. " +
                "There is no account, no analytics, no crash reporting and no advertising ID.",
            "It cannot reach the network at all. The app does not hold the INTERNET permission, " +
                "neither in its own manifest nor through any library it is built on, so Android " +
                "refuses any connection it might try to open. This is enforced by the operating " +
                "system, not by the app's good behaviour, and you can check it yourself: the full " +
                "permission list is on the Permissions page and in the app's entry in Settings.",
            "Your alarms, timers, world clocks and preferences live in one file in the app's own " +
                "private storage. Nothing else on the device can read it. Uninstalling the app " +
                "deletes it.",
            "Android's own backup may copy that file to your Google account if you have device " +
                "backup switched on. That is a setting you control, in Settings › Google › Backup, " +
                "and it is the only way any of this data leaves the phone.",
            "Alarm sounds are played from the ringtones already on your device. Choosing one " +
                "records which sound you picked, nothing more.",
        ),
    ),

    PERMISSIONS(
        "Permissions",
        listOf(
            "Alarms & reminders (USE_EXACT_ALARM) lets the app ring at the minute you set. " +
                "Inexact alarms are batched by the system and can drift by many minutes, which " +
                "is not an alarm.",
            "Notifications (POST_NOTIFICATIONS) covers the ringing alarm, and the live timer and " +
                "stopwatch you can control from the shade.",
            "Full-screen notifications (USE_FULL_SCREEN_INTENT) let a ringing alarm take over a " +
                "locked screen instead of arriving as a banner you have to find.",
            "Run at startup (RECEIVE_BOOT_COMPLETED) is needed because alarms are held by the " +
                "system and are lost on reboot, so the app re-arms them when the phone starts. " +
                "It does nothing else at boot and does not stay running.",
            "Foreground service (FOREGROUND_SERVICE, …_MEDIA_PLAYBACK) runs only while an alarm " +
                "is actually ringing, so the audio is not killed mid-ring. Timers and the " +
                "stopwatch use no service at all; their notifications tick by themselves.",
            "Vibrate (VIBRATE) is for alarms and timers set to vibrate.",
            "Keep awake (WAKE_LOCK) holds the screen on while an alarm rings.",
            "There is no INTERNET permission, no location, no contacts, no storage and no " +
                "microphone.",
        ),
    ),

    // The OFL asks that the copyright notice and the licence travel *with the font software*, and
    // an APK carrying two font binaries is a distribution of it. So the notices are here in the app
    // and not only in the repository's THIRD-PARTY-NOTICES.md.
    LICENCES(
        "Licences",
        listOf(
            "This app's own code is MIT licensed. The bundled fonts are not; they are under the " +
                "SIL Open Font License 1.1, reproduced in full at openfontlicense.org.",
            "Google Sans Flex. Copyright 2015 Google LLC. SIL Open Font License 1.1. Modified: " +
                "the per-script subsets Google serves are merged into one variable font, because " +
                "Android cannot load woff2. Outlines and all six axes are unchanged.",
            "Noto Sans Arabic. Copyright 2022 The Noto Project Authors. SIL Open Font License " +
                "1.1. Modified: a ten-glyph subset (U+0660–0669) for the widget's Eastern Arabic " +
                "numerals, because Google Sans Flex has no Arabic coverage.",
            "Jetpack Compose, AndroidX, Material Symbols and androidx.graphics.shapes are under " +
                "the Apache License 2.0, copyright The Android Open Source Project and Google LLC.",
            "Google, Google Sans, Material, Android and Pixel are trademarks of Google LLC. This " +
                "app is not affiliated with, sponsored by or endorsed by Google. It is an " +
                "independent recreation of a published Material 3 Expressive concept design.",
        ),
    ),
}
