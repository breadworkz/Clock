package app.materialclock

import android.app.Application

/**
 * Nothing to set up yet: no image loader, no network client, no database. Declared because the
 * manifest names it and because the alarm scheduler will need a home when it lands.
 */
class ClockApplication : Application()
