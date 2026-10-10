package app.materialclock.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import app.materialclock.data.ClockStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The widget's only presence in this app's process.
 *
 * There is deliberately no periodic update. The hands are ticked by the host's own `AnalogClock`
 * and the date by its `TextClock`, both inside the launcher and both stopping the moment the home
 * screen is not visible, so the steady-state cost of a placed widget here is *zero* wakeups.
 *
 * What is left is redrawing when something the picture depends on actually changes. All four
 * broadcasts below are on the implicit-broadcast exception list and may be declared in the
 * manifest; `ACTION_DATE_CHANGED` and `ACTION_CONFIGURATION_CHANGED` are not, and are not used.
 */
class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        background(context) { store ->
            // A missing record means the config activity has not committed yet: the launcher binds
            // and calls onUpdate *before* the user has chosen anything. Leave the placeholder; do
            // not seed a default the user never picked.
            ids.forEach { ClockWidgetRenderer.update(context, manager, it) }
            store.sweepWidgetConfigs(manager.getAppWidgetIds(component(context)))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        background(context) { ClockWidgetRenderer.update(context, manager, appWidgetId) }
    }

    /** Also the cancelled-add path; the two are indistinguishable, and want the same cleanup. */
    override fun onDeleted(context: Context, ids: IntArray) {
        background(context) { it.deleteWidgetConfigs(ids) }
    }

    /**
     * Restore from backup.
     *
     * The remap has to *complete* before anything renders, because `AppWidgetProvider.onReceive`
     * calls `onUpdate(newIds)` the instant this returns. Read the records under their old ids and
     * they are already gone.
     */
    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        background(context) { store ->
            store.remapWidgetConfigs(oldIds, newIds)
            val manager = AppWidgetManager.getInstance(context)
            newIds.forEach { ClockWidgetRenderer.update(context, manager, it) }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> refreshAll(context)
        }
    }

    /**
     * Reading DataStore suspends and `onReceive` runs on the main thread with a ten-second
     * deadline; the pending result keeps the process alive across the coroutine. `finish()` must be
     * reached on every path or the receiver leaks and the system eventually kills the app for it.
     */
    private fun background(context: Context, work: suspend (ClockStore) -> Unit) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                work(ClockStore(app))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun component(context: Context) = ComponentName(context, ClockWidgetProvider::class.java)

        /** Redraw every placed widget, as the app does when the theme or a setting changes. */
        fun refreshAll(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(component(app))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.Default).launch {
                ids.forEach { ClockWidgetRenderer.update(app, manager, it) }
            }
        }

        /** The ids of every widget this app currently has on a home screen. */
        fun placedIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(component(context))
    }
}
