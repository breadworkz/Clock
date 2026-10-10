package app.materialclock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Ways to reach a widget that do not depend on the launcher offering them.
 *
 * `android:widgetFeatures="reconfigurable"` is documented as *"hints to the widget host, and do not
 * actually change the behavior of the widget"*, and in practice several shipping launchers have
 * never surfaced a reconfigure affordance at all. So the app carries its own list, and a placed
 * widget can always be edited from inside Settings even where the home screen offers no way in.
 */
object WidgetEntryPoints {

    /** Open the editor for an already-placed widget. The ignored result is harmless: the activity
     *  persists and pushes on Save, and the host is not waiting on anything. */
    fun reconfigure(context: Context, appWidgetId: Int) {
        context.startActivity(
            Intent(context, ClockWidgetConfigActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )
    }

    /** Whether the current launcher will honour a pin request at all. */
    fun canPin(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    /**
     * Ask the launcher to place one.
     *
     * Most hosts do **not** run the configure activity for a pinned widget (they just drop it), so
     * the success callback opens the editor itself. The callback carries the new
     * `EXTRA_APPWIDGET_ID`, which is the only place that id is ever offered to us.
     */
    fun requestPin(context: Context) {
        if (!canPin(context)) return
        val callback = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetPinnedReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        AppWidgetManager.getInstance(context).requestPinAppWidget(
            ClockWidgetProvider.component(context),
            null,
            callback,
        )
    }
}

/** Receives the pin callback and opens the editor for the id the launcher just created. */
class WidgetPinnedReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            WidgetEntryPoints.reconfigure(context.applicationContext, id)
        }
    }
}
