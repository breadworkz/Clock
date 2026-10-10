package app.materialclock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.materialclock.alarm.Notifications
import app.materialclock.ui.ClockApp

class MainActivity : ComponentActivity() {

    /**
     * Which tab to open on.
     *
     * Tapping a timer or stopwatch notification should land on that tab, not on Alarms. The
     * activity is `singleTask`, so a second tap while it is already open arrives at [onNewIntent]
     * rather than through `onCreate`. Writing to state that the composition reads covers both.
     */
    private var startTab by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startTab = intent?.getStringExtra(Notifications.EXTRA_TAB)
        // Edge-to-edge is enabled inside ClockTheme, where the effective dark/light state is known
        // and the system-bar icon contrast can be set to match it.
        setContent { ClockApp(startTab = startTab) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startTab = intent.getStringExtra(Notifications.EXTRA_TAB)
    }
}
