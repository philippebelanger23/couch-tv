package app.couchtv.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * The Home button watch. The TV's own home stays installed and switched on (switching it off can
 * leave a Google TV in a boot loop, see README "If your TV loops after a restart"), so on many TVs
 * the Home key, and the TV at power on, still open that home. This watcher sees the TV's own home
 * come on screen and puts couch-tv back on top. On a Xiaomi TV it also catches the Home and Mi
 * keys as they are pressed; the window watch stays the fallback when the TV still starts its home.
 *
 * - Fire TV: Amazon's TVs start Amazon's launcher from the Home key by name. Amazon's launcher is
 *   never disabled. If Fire OS delivers Home to this service, a short press is swallowed so Amazon
 *   never starts. If it does not (common on Fire OS 7), a black overlay covers Amazon's home for
 *   the moment it is in front, then couch-tv is brought back with no activity animation. That flash
 *   is as short as Fire OS allows without root.
 * - Google TV and Android TV: the Google home outranks the default-home setting.
 *
 * Off until a computer switches it on over ADB (tv-home/tools/apply.sh, or guest-tv's
 * `tools/tv_app.py home`). They also grant WRITE_SECURE_SETTINGS, because Android drops the
 * watcher whenever the app is stopped or updated: with that grant the app puts it back by itself
 * (at start, after an update, after a reboot). The undo takes both away. It only sees which window
 * came forward and reads no screen content.
 *
 * If couch-tv itself keeps crashing (CrashGuard), the watch stands aside and leaves the TV's own
 * home on screen, so a broken couch-tv can never hold the TV in a loop.
 */
class FireWatch : AccessibilityService() {
    private var last = 0L
    private val handler = Handler(Looper.getMainLooper())
    /** True while the latest window change this service saw was the TV's own home (events only, no screen reading). */
    private var stockInFront = false
    private var recheckPending = false
    private val recheck = Runnable {
        recheckPending = false
        if (stockInFront && !CrashGuard.standingAside(this)) bringBack()
    }

    override fun onInterrupt() {}

    /**
     * Xiaomi (issue #54) and Fire OS: catch Home as it is pressed so the stock home never starts,
     * when the TV actually delivers the key. Long-press repeats are left to the system. Other TVs
     * keep the previous behaviour (window watch only).
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (isXiaomi()) {
            val homeKey = event.keyCode == KeyEvent.KEYCODE_HOME ||
                (event.keyCode == KeyEvent.KEYCODE_BUTTON_5 && event.scanCode == XIAOMI_MI_SCAN)
            if (!homeKey || CrashGuard.standingAside(this)) return false
            if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) bringBack()
            return true
        }
        if (!hasAmazonHome(this) || event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (CrashGuard.standingAside(this)) return false
        if (event.repeatCount != 0) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            bringBack()
            return true
        }
        return event.action == KeyEvent.ACTION_UP
    }

    override fun onServiceConnected() {
        connected = true
        // Key filtering on Xiaomi and Fire OS only, so a Google TV's Home key is left alone.
        if (isXiaomi() || hasAmazonHome(this)) runCatching {
            serviceInfo = serviceInfo.also { it.flags = it.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS }
        }
        homeClasses = homeActivitiesOf(this)
        Log.i("couchtv.firewatch", "the Home button watch is connected")
        // The TV's own home can come up before this service is bound, and then its window event is
        // never seen (issue #44: Amazon's home sat on screen about 30 s at boot). The service has no
        // permission to read windows (that would be reading the screen), so it goes by the clock
        // instead: bound in the first minutes after a restart, nothing but the TV's own home can be
        // in front, so couch-tv goes on top once. Fire OS, and TCL Google TVs (issue #94): TCL's own
        // boot controller opens the Google home at power on before this service is bound, so its
        // window event is never seen, and a Home press then changes nothing (the Google home is
        // already in front). Other Google TVs are left alone: one can start on an HDMI input or
        // live TV, and that must never be covered.
        if ((android.os.Build.MANUFACTURER.equals("Amazon", ignoreCase = true) || TclPlaceholderInput.isTcl()) &&
            SystemClock.elapsedRealtime() < BOOT_WINDOW_MS && !CrashGuard.standingAside(this)) {
            Log.i("couchtv.firewatch", "bound just after a restart: putting couch-tv in front")
            bringBack()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        handler.removeCallbacks(recheck)
        recheckPending = false
        hideCover()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        stockInFront = isStockHome(e.packageName?.toString(), e.className?.toString()) &&
            !(e.packageName?.toString() == AMAZON_HOME && settingsAllowed())
        if (!stockInFront) return
        if (CrashGuard.standingAside(this)) return
        val wait = DEBOUNCE_MS - (System.currentTimeMillis() - last)
        if (wait > 0) {
            // Skipped as a repeat press. Some Google TVs (seen on a Xiaomi) keep the Google home as the
            // resolved home, so quick Home presses can leave it in front with no further window event
            // (issue #54): look once more when the debounce ends. One re-check at most, never a loop.
            if (!recheckPending) {
                recheckPending = true
                handler.postDelayed(recheck, wait + 50)
            }
            return
        }
        bringBack()
    }

    private fun bringBack() {
        last = System.currentTimeMillis()
        if (hasAmazonHome(this)) showCover(this)
        runCatching { startActivity(homeIntent(this)) }
    }

    /** After an update or a reboot: put the watcher back if it was switched on. */
    class Restore : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_MY_PACKAGE_REPLACED) CrashGuard.reset(c)   // a new version gets a fresh try
            runCatching { ensure(c) }
        }
    }

    companion object {
        /** Set while Android has this service bound. */
        @Volatile var connected = false
        private const val DEBOUNCE_MS = 400L   // one jump per press; short so Amazon's home is covered quickly
        private const val BOOT_WINDOW_MS = 2 * 60_000L
        private const val COVER_MS = 1_500L
        private val coverHandler = Handler(Looper.getMainLooper())
        @Volatile private var coverView: View? = null
        private val hideCoverLater = Runnable { hideCover() }

        /**
         * The watch listed as on in Settings but not bound (what a force-stop leaves behind, the same
         * blind spot as issue #37): take it out of the list and put it back so Android binds it.
         * Call a few seconds after the app starts, once Android has had its chance to bind it itself.
         */
        fun rebindIfUnbound(c: Context) {
            if (connected) return
            if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) return
            Thread {
                runCatching {
                    val cr = c.contentResolver
                    val me = "${c.packageName}/${FireWatch::class.java.name}"
                    val list = (Settings.Secure.getString(cr, "enabled_accessibility_services") ?: "").split(':').filter { it.isNotEmpty() && it != "null" }
                    if (list.none { it == me || it == "${c.packageName}/.FireWatch" } || connected) return@runCatching
                    Log.i("couchtv.firewatch", "listed but not bound: rebinding the Home button watch")
                    val rest = list.filter { it != me && it != "${c.packageName}/.FireWatch" }
                    Settings.Secure.putString(cr, "enabled_accessibility_services", rest.joinToString(":"))
                    Thread.sleep(2000)
                    Settings.Secure.putString(cr, "enabled_accessibility_services", (rest + me).joinToString(":"))
                }
            }.start()
        }

        const val AMAZON_HOME = "com.amazon.tv.launcher"
        // MiTV-MOOQ1's Mi-logo key reports BUTTON_5 with this scan code, not KEYCODE_HOME.
        private const val XIAOMI_MI_SCAN = 580
        private fun isXiaomi() = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
        fun hasAmazonHome(c: Context) = runCatching { c.packageManager.getPackageInfo(AMAZON_HOME, 0) }.isSuccess
        const val GOOGLE_TV_HOME = "com.google.android.apps.tv.launcherx"
        const val ANDROID_TV_HOME = "com.google.android.tvlauncher"
        /** Fire OS's own Settings screen, opened by name (see [amazonSettings]). */
        const val AMAZON_SETTINGS = "$AMAZON_HOME.ui.MainSettingsActivity"
        /** Fire OS's Settings app package (the plain Settings action goes through it too). */
        const val AMAZON_SETTINGS_PKG = "com.amazon.tv.settings"
        private const val SETTINGS_GRACE_MS = 8_000L
        @Volatile private var settingsUntil = 0L

        /**
         * couch-tv just opened the TV's Settings itself (issue #123): for a few seconds Amazon's home
         * coming forward is Settings being drawn, not a Home press, so the watch leaves it alone.
         * Only Amazon's own windows are spared; the Google homes and a real Home press later are not.
         */
        fun allowSettingsNow() { settingsUntil = SystemClock.elapsedRealtime() + SETTINGS_GRACE_MS }
        fun settingsAllowed(): Boolean = SystemClock.elapsedRealtime() < settingsUntil
        private val STOCK_HOMES = listOf(AMAZON_HOME, GOOGLE_TV_HOME, ANDROID_TV_HOME)

        /** The activities of the Google homes that the TV itself lists as a HOME screen (read when the service binds). */
        @Volatile var homeClasses: Set<String> = emptySet()

        fun homeIntent(c: Context): Intent = Intent(c, MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )

        /**
         * Cover Amazon's home for the moment it is in front (SYSTEM_ALERT_WINDOW, already granted on
         * install). Removed when couch-tv resumes, or after [COVER_MS] if that never happens.
         */
        fun showCover(c: Context) {
            val app = c.applicationContext
            coverHandler.post {
                if (coverView != null) {
                    coverHandler.removeCallbacks(hideCoverLater)
                    coverHandler.postDelayed(hideCoverLater, COVER_MS)
                    return@post
                }
                val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                @Suppress("DEPRECATION")
                val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.OPAQUE
                )
                val v = View(app).apply { setBackgroundColor(Color.BLACK) }
                try {
                    wm.addView(v, lp)
                    coverView = v
                    coverHandler.removeCallbacks(hideCoverLater)
                    coverHandler.postDelayed(hideCoverLater, COVER_MS)
                } catch (e: Exception) {
                    Log.w("couchtv.firewatch", "home cover overlay not shown: ${e.message}")
                }
            }
        }

        fun hideCover() {
            coverHandler.post {
                val v = coverView ?: return@post
                coverView = null
                coverHandler.removeCallbacks(hideCoverLater)
                try {
                    (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
                } catch (_: Exception) {}
            }
        }

        /**
         * Google TV's home is not always `.home.HomeActivity`: a TCL on Android TV 11 starts
         * `.home.VanillaModeHomeActivity` (issue #51). Whatever the package declares as a HOME
         * activity is the home; its profile, account and setup screens are not HOME activities.
         */
        fun homeActivitiesOf(c: Context): Set<String> = runCatching {
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            c.packageManager.queryIntentActivities(q, 0)
                .filter { it.activityInfo.packageName == GOOGLE_TV_HOME || it.activityInfo.packageName == ANDROID_TV_HOME }
                .map { it.activityInfo.name }.toSet()
        }.getOrDefault(emptySet())

        /**
         * The TV's own home screen, not its other screens. Amazon's launcher package also holds
         * Fire OS's Settings screens, and the Google TV home package holds profile, account and
         * setup screens: only the home itself is replaced.
         */
        fun isStockHome(pkg: String?, cls: String?, homes: Set<String> = homeClasses): Boolean = when (pkg) {
            AMAZON_HOME -> cls?.contains("Settings") != true
            GOOGLE_TV_HOME -> cls == "$GOOGLE_TV_HOME.home.HomeActivity" || cls == "$GOOGLE_TV_HOME.home.VanillaModeHomeActivity" || (cls != null && cls in homes)
            ANDROID_TV_HOME -> cls == "$ANDROID_TV_HOME.MainActivity" || (cls != null && cls in homes)
            else -> false
        }

        /**
         * The TV's Settings, as an intent that does not go through Amazon's home. On Fire OS the plain
         * Settings action goes through Amazon's home on its way there (`HomeActivity_vNext` draws the
         * Settings panel inside itself), so [isStockHome] cannot tell it from a Home press and the
         * Home button watch puts couch-tv straight back (issue #75, the same as #5 in the on-TV app).
         * Fire OS's own Settings screen, started by name, stays on screen. Null on any other TV,
         * which keeps the plain Settings action.
         */
        fun amazonSettings(pm: PackageManager): Intent? {
            val i = Intent().setComponent(ComponentName(AMAZON_HOME, AMAZON_SETTINGS))
            return if (runCatching { pm.getPackageInfo(AMAZON_HOME, 0) }.isSuccess && pm.resolveActivity(i, 0) != null) i else null
        }

        /** Puts the watcher back on, only where a computer granted secure settings and the TV has a stock home. */
        fun ensure(c: Context) {
            if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) return
            if (STOCK_HOMES.none { p -> runCatching { c.packageManager.getPackageInfo(p, 0) }.isSuccess }) return
            runCatching {
                val cr = c.contentResolver
                val me = "${c.packageName}/${FireWatch::class.java.name}"
                val list = (Settings.Secure.getString(cr, "enabled_accessibility_services") ?: "").split(':').filter { it.isNotEmpty() && it != "null" }
                if (list.none { it == me || it == "${c.packageName}/.FireWatch" }) {
                    Settings.Secure.putString(cr, "enabled_accessibility_services", (list + me).joinToString(":"))
                    Log.i("couchtv.firewatch", "switched the Home button watch back on")
                }
                Settings.Secure.putInt(cr, "accessibility_enabled", 1)
            }.onFailure { Log.w("couchtv.firewatch", "could not switch the watch on: ${it.message}") }
        }
    }
}
