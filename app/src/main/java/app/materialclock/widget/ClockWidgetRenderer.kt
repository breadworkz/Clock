package app.materialclock.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import app.materialclock.MainActivity
import app.materialclock.R
import app.materialclock.data.ClockStore
import app.materialclock.data.DateMode
import app.materialclock.data.DatePosition
import app.materialclock.data.HandStyle
import app.materialclock.data.WidgetConfig
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Builds and pushes the widget.
 *
 * The one thing worth knowing here: the payload goes over binder as **compressed bytes wrapped in
 * an `Icon`**, never as a `Bitmap`. `RemoteViews.estimateMemoryUsage()` counts only its bitmap
 * cache, which `setImageViewBitmap` fills and `setIcon` does not, so icons stay outside the
 * system's per-widget bitmap ceiling. A lossless WEBP of a flat-coloured dial is also one to two
 * orders of magnitude smaller than the raw ARGB it replaces.
 */
object ClockWidgetRenderer {

    suspend fun update(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        val store = ClockStore(context)
        val config = store.widgetConfigNow(appWidgetId) ?: return
        val settings = store.settingsNow()
        val options = manager.getAppWidgetOptions(appWidgetId)

        val views = build(context, config, settings, options)
        manager.updateAppWidget(appWidgetId, views)
    }

    /**
     * One `RemoteViews` per candidate size.
     *
     * From API 31 the host hands us the exact sizes it will use and picks between them itself, with
     * no round trip on rotation. Below that the portrait box is min-width × max-height and the
     * landscape box is max-width × min-height, combined with the old two-layout constructor. An
     * empty list, which the docs say is the launcher's prerogative, takes the same path.
     *
     * Every dp in that bundle is already padding-corrected by `AppWidgetHostView`, so it is the
     * content box: adding padding of our own would shrink the face away from the edges, which is
     * the one thing the shape fitting exists to prevent.
     */
    private fun build(
        context: Context,
        config: WidgetConfig,
        settings: app.materialclock.data.ClockSettings,
        options: Bundle,
    ): RemoteViews {
        val density = context.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).roundToInt().coerceAtLeast(1)

        val sizes: List<SizeF> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                ?.takeIf { it.isNotEmpty() }
                ?: emptyList()
        } else {
            emptyList()
        }

        if (sizes.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val map = sizes.associateWith { size ->
                one(context, config, settings, px(size.width.roundToInt()), px(size.height.roundToInt()))
            }
            return RemoteViews(map)
        }

        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
        val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH)
        val portrait = one(context, config, settings, px(minW), px(maxH))
        val landscape = one(context, config, settings, px(maxW), px(minH))
        return RemoteViews(landscape, portrait)
    }

    private fun one(
        context: Context,
        config: WidgetConfig,
        settings: app.materialclock.data.ClockSettings,
        w: Int,
        h: Int,
    ): RemoteViews {
        val colours = WidgetColours.resolve(context, config, settings)
        val face = FaceRenderer.render(context, config, colours, w, h)
        val views = RemoteViews(context.packageName, R.layout.widget_clock)

        views.setImageViewIcon(R.id.face, face.bitmap.toIcon())

        // The dial itself is empty: the face underneath is what the user sees, and AnalogClock
        // would otherwise draw its own default clock face over it.
        views.setIcon(R.id.hands, "setDial", HandRenderer.blank().toIcon())

        // The pin rides the topmost hand that is switched on, because nothing of ours can be drawn
        // above AnalogClock's own painting order.
        val topmost = when {
            config.secondHand != HandStyle.OFF -> 2
            config.minuteHand != HandStyle.OFF -> 1
            else -> 0
        }
        val pinR = if (config.centrePin) DialGeometry.PIN_DIAMETER else 0f

        views.setIcon(
            R.id.hands, "setHourHand",
            HandRenderer.render(
                config.hourHand, colours.hourHand, face.rMin,
                DialGeometry.HOUR_LENGTH, DialGeometry.HOUR_WIDTH, DialGeometry.HOUR_TAIL,
                if (topmost == 0) pinR else 0f, colours.pin,
            ).toIcon(),
        )
        views.setIcon(
            R.id.hands, "setMinuteHand",
            HandRenderer.render(
                config.minuteHand, colours.minuteHand, face.rMin,
                DialGeometry.MINUTE_LENGTH, DialGeometry.MINUTE_WIDTH, DialGeometry.MINUTE_TAIL,
                if (topmost == 1) pinR else 0f, colours.pin,
            ).toIcon(),
        )
        if (config.secondHand == HandStyle.OFF) {
            // Null is legal only for the second hand, and it is also what stops the host's 1 Hz
            // tick, which is the point. The hour and minute hands must be transparent icons
            // instead, because AnalogClock dereferences those without a null check.
            views.setIcon(R.id.hands, "setSecondHand", null)
        } else {
            views.setIcon(
                R.id.hands, "setSecondHand",
                HandRenderer.render(
                    config.secondHand, colours.secondHand, face.rMin,
                    DialGeometry.SECOND_LENGTH, DialGeometry.SECOND_WIDTH, DialGeometry.SECOND_TAIL,
                    if (topmost == 2) pinR else 0f, colours.pin,
                ).toIcon(),
            )
        }

        applyDate(context, views, config, colours, face, w, h)

        views.setOnClickPendingIntent(
            android.R.id.background,
            android.app.PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        return views
    }

    private fun applyDate(
        context: Context,
        views: RemoteViews,
        config: WidgetConfig,
        colours: FaceRenderer.Colours,
        face: FaceRenderer.Face,
        w: Int,
        h: Int,
    ) {
        if (config.date == DateMode.NONE) {
            views.setViewVisibility(R.id.date, View.GONE)
            return
        }
        views.setViewVisibility(R.id.date, View.VISIBLE)

        val skeleton = when (config.date) {
            DateMode.DAY -> "d"
            DateMode.DAY_WEEKDAY -> "EEEd"
            DateMode.FULL -> "EEEdMMM"
            DateMode.NONE -> "d"
        }
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
        // Both, with the same pattern: a date has no 12/24-hour form, and leaving one unset makes
        // the view fall back to its own default on devices set the other way.
        views.setCharSequence(R.id.date, "setFormat12Hour", pattern)
        views.setCharSequence(R.id.date, "setFormat24Hour", pattern)
        views.setTextColor(R.id.date, colours.minor)

        val sizePx = 0.14f * face.rMin
        views.setTextViewTextSize(R.id.date, TypedValue.COMPLEX_UNIT_PX, sizePx)

        // Placed with padding rather than margins: setViewLayoutMargin is API 31 and this app
        // supports 26.
        val angle = when (config.datePosition) {
            DatePosition.THREE -> 90f
            DatePosition.FOUR_THIRTY -> 135f
            DatePosition.SIX -> 180f
        }
        val (x, y) = if (config.date == DateMode.FULL) {
            w / 2f to face.cy + 0.42f * face.rMin
        } else {
            val a = Math.toRadians(angle.toDouble()).toFloat()
            face.cx + sin(a) * 0.55f * face.rMin to face.cy - cos(a) * 0.55f * face.rMin
        }
        views.setViewPadding(
            R.id.date_slot,
            (x - sizePx * 1.2f).roundToInt().coerceAtLeast(0),
            (y - sizePx * 0.7f).roundToInt().coerceAtLeast(0),
            0,
            0,
        )
    }

    /**
     * Lossless WEBP, wrapped as data rather than as a bitmap.
     *
     * `Icon.createWithData` keeps the payload out of `RemoteViews`' bitmap cache, which is what the
     * system's per-widget memory ceiling actually measures, and compresses a flat dial by one to
     * two orders of magnitude before it crosses binder.
     */
    private fun Bitmap.toIcon(): Icon {
        val out = ByteArrayOutputStream(BUFFER)
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSLESS
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        compress(format, 100, out)
        val bytes = out.toByteArray()
        return Icon.createWithData(bytes, 0, bytes.size)
    }

    private const val BUFFER = 64 * 1024
}
