package dev.projector.launcher

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var prefs: LauncherPrefs
    private lateinit var favoritesRow: RecyclerView
    private lateinit var favoritesHint: TextView
    private lateinit var allAppsRow: RecyclerView
    private lateinit var focusedTitle: TextView
    private lateinit var content: View
    private lateinit var glow: GradientDrawable
    private lateinit var favoritesAdapter: AppAdapter
    private lateinit var allAppsAdapter: AppAdapter

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var apps: List<AppEntry> = emptyList()
    private var hasPlacedInitialFocus = false
    private var glowColor = AppRepository.DEFAULT_ACCENT
    private var glowAnimator: ValueAnimator? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reloadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = LauncherPrefs(this)

        favoritesRow = findViewById(R.id.favorites_row)
        favoritesHint = findViewById(R.id.favorites_hint)
        allAppsRow = findViewById(R.id.all_apps)
        focusedTitle = findViewById(R.id.focused_title)
        content = findViewById(R.id.content)
        content.alpha = 0f

        // Soft light in the top-left corner that takes on the color of the focused app.
        glow = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(0.15f, 0f)
            gradientRadius = resources.displayMetrics.widthPixels * 0.9f
            setColors(glowColors(glowColor))
        }
        findViewById<View>(R.id.ambient_glow).background = glow

        favoritesAdapter = AppAdapter(true, ::launch, ::showAppOptions, ::onAppFocused)
        favoritesRow.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        favoritesRow.adapter = favoritesAdapter

        allAppsAdapter = AppAdapter(false, ::launch, ::showAppOptions, ::onAppFocused)
        allAppsRow.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        allAppsRow.adapter = allAppsAdapter

        bindQuickButtons()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        reloadApps()
    }

    override fun onDestroy() {
        glowAnimator?.cancel()
        unregisterReceiver(packageReceiver)
        executor.shutdown()
        super.onDestroy()
    }

    // Pressing HOME while already home jumps back to the top.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        goToTop()
    }

    // BACK must never leave the home screen; use it as "jump to top" instead.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            goToTop()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun bindQuickButtons() {
        findViewById<View>(R.id.btn_wifi).setOnClickListener {
            startSafely(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        findViewById<View>(R.id.btn_bluetooth).setOnClickListener {
            startSafely(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
        // Keystone, focus and picture settings live in the vendor's console app on HT13-style projectors.
        findViewById<View>(R.id.btn_projector).setOnClickListener {
            val console = Intent()
                .setComponent(ComponentName(PROJECTOR_CONSOLE_PACKAGE, "$PROJECTOR_CONSOLE_PACKAGE.MainActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startSafely(console, Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }
        findViewById<View>(R.id.btn_settings).setOnClickListener {
            startSafely(Intent(Settings.ACTION_SETTINGS))
        }
        findViewById<View>(R.id.btn_launcher).setOnClickListener { showLauncherOptions() }
    }

    private fun reloadApps() {
        executor.execute {
            val loaded = AppRepository.load(this)
            mainHandler.post {
                if (isDestroyed) return@post
                apps = loaded
                prefs.seedFavoritesOnce(loaded.map { it.key }.toSet())
                render()
            }
        }
    }

    private fun render() {
        val hidden = prefs.hidden
        val byKey = apps.associateBy { it.key }
        val favorites = prefs.favorites.mapNotNull { byKey[it] }

        favoritesAdapter.submit(favorites)
        allAppsAdapter.submit(apps.filter { it.key !in hidden })

        favoritesRow.visibility = if (favorites.isEmpty()) View.GONE else View.VISIBLE
        favoritesHint.visibility = if (favorites.isEmpty()) View.VISIBLE else View.GONE

        if (!hasPlacedInitialFocus || currentFocus == null) {
            hasPlacedInitialFocus = true
            goToTop()
            content.animate().alpha(1f).setDuration(400).start()
        }
    }

    private fun goToTop() {
        allAppsRow.scrollToPosition(0)
        favoritesRow.scrollToPosition(0)
        val target = if (favoritesRow.visibility == View.VISIBLE) favoritesRow else allAppsRow
        target.post { target.getChildAt(0)?.requestFocus() }
    }

    private fun onAppFocused(app: AppEntry) {
        if (focusedTitle.text != app.label) {
            focusedTitle.animate().cancel()
            focusedTitle.alpha = 0.3f
            focusedTitle.text = app.label
            focusedTitle.animate().alpha(1f).setDuration(200).start()
        }
        animateGlowTo(app.accent)
    }

    private fun animateGlowTo(target: Int) {
        if (target == glowColor) return
        glowAnimator?.cancel()
        glowAnimator = ValueAnimator.ofArgb(glowColor, target).apply {
            duration = 450
            addUpdateListener {
                glowColor = it.animatedValue as Int
                glow.setColors(glowColors(glowColor))
            }
            start()
        }
    }

    private fun glowColors(color: Int): IntArray = intArrayOf(
        withAlpha(color, 0x8C),
        withAlpha(color, 0x26),
        Color.TRANSPARENT,
    )

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun launch(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(app.category)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        startSafely(intent)
    }

    private fun startSafely(intent: Intent, fallback: Intent? = Intent(Settings.ACTION_SETTINGS)) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Projector firmware often strips individual settings screens; fall back to the main one.
            if (fallback != null && fallback.action != intent.action) {
                startSafely(fallback, null)
            } else {
                Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAppOptions(app: AppEntry) {
        val isFavorite = app.key in prefs.favorites
        val options = mutableListOf<Pair<String, () -> Unit>>()
        fun option(label: Int, action: () -> Unit) {
            options += getString(label) to action
        }

        if (isFavorite) {
            option(R.string.move_left) { moveFavorite(app, -1) }
            option(R.string.move_right) { moveFavorite(app, 1) }
            option(R.string.remove_favorite) {
                prefs.removeFavorite(app.key)
                render()
            }
        } else {
            option(R.string.add_favorite) {
                prefs.addFavorite(app.key)
                render()
            }
        }
        option(R.string.hide_app) {
            prefs.setHidden(app.key, true)
            render()
        }
        option(R.string.app_info) {
            startSafely(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
            )
        }
        option(R.string.uninstall) {
            startSafely(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null)), null)
        }

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(app.label)
            .setItems(options.map { it.first }.toTypedArray()) { _, which -> options[which].second() }
            .show()
    }

    private fun moveFavorite(app: AppEntry, delta: Int) {
        val position = prefs.moveFavorite(app.key, delta)
        if (position < 0) return
        render()
        favoritesRow.scrollToPosition(position)
        favoritesRow.post {
            favoritesRow.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
        }
    }

    private fun showLauncherOptions() {
        val items = arrayOf(getString(R.string.manage_hidden), getString(R.string.choose_default_home))
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.launcher_options)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showHiddenApps()
                    1 -> startSafely(Intent(Settings.ACTION_HOME_SETTINGS))
                }
            }
            .show()
    }

    private fun showHiddenApps() {
        val hiddenKeys = prefs.hidden
        val hiddenApps = apps.filter { it.key in hiddenKeys }
        if (hiddenApps.isEmpty()) {
            Toast.makeText(this, R.string.no_hidden_apps, Toast.LENGTH_SHORT).show()
            return
        }
        val stillHidden = BooleanArray(hiddenApps.size) { true }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.manage_hidden)
            .setMultiChoiceItems(hiddenApps.map { it.label }.toTypedArray(), stillHidden) { _, which, checked ->
                stillHidden[which] = checked
            }
            .setPositiveButton(R.string.save) { _, _ ->
                hiddenApps.forEachIndexed { i, app -> prefs.setHidden(app.key, stillHidden[i]) }
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private companion object {
        const val PROJECTOR_CONSOLE_PACKAGE = "com.cptp.console"
    }
}
