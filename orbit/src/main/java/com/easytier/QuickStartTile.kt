package com.easytier

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.easytier.jni.EasyTierManager

/**
 * Quick Settings tile — one tap starts or stops the tunnel from the shade.
 *
 * Start path: the tile has no UI, so it cannot validate the saved config nor show
 * the system VPN consent dialog. It hands off to [MainActivity] with
 * [MainActivity.EXTRA_QUICK_TOGGLE]; the activity reuses the normal start chain.
 *
 * Stop path: [EasyTierManager.stop] only needs an application context, so the
 * tunnel is torn down directly (no activity needed) and the tile flips to inactive.
 */
class QuickStartTile : TileService() {

    companion object {
        private const val PREFS_STATE = "easytier_state"
        private const val PREFS_LANG = "easytier_lang"
        private const val TAG = "QuickStartTile"
    }

    private val langZh: Boolean
        get() = getSharedPreferences(PREFS_LANG, MODE_PRIVATE).getBoolean("lang_zh", true)

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        EasyTierManager.init(this)
        if (EasyTierManager.isRunning) {
            AppLogger.i(TAG, "Tile tap → stop")
            EasyTierManager.getInstance().stop()
            getSharedPreferences(PREFS_STATE, MODE_PRIVATE).edit()
                .putBoolean("is_running", false).apply()
            refreshTile()
        } else {
            // Silent start — load the saved config and bring the tunnel up in the
            // background; only fall back to the activity when the one-time VPN
            // grant or a valid config is missing.
            if (silentStart()) {
                AppLogger.i(TAG, "Tile tap → silent start OK")
                refreshTile()
                return
            }
            AppLogger.i(TAG, "Tile tap → start (handing off to MainActivity)")
            val launch = Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_QUICK_TOGGLE, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    // API 34+ requires a PendingIntent for the activity launch
                    startActivityAndCollapse(
                        PendingIntent.getActivity(
                            this, 0, launch,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(launch)
                }
            } catch (t: Throwable) {
                AppLogger.w(TAG, "startActivityAndCollapse failed: ${t.message}")
            }
        }
    }

    /** Sync label / state / icon with the real tunnel state. */
    /** Try to bring the tunnel up with no UI. Returns false when the activity must take over. */
    private fun silentStart(): Boolean {
        return try {
            val prefs = getSharedPreferences(PREFS_STATE, MODE_PRIVATE)
            val name = prefs.getString("last_config_name", "default") ?: "default"
            val safe = name.replace(Regex("[^\\w.-]"), "_")
            var json = java.io.File(filesDir, "configs/$safe.json")
            if (!json.exists()) {
                val ext = java.io.File(getExternalFilesDir(null) ?: filesDir, "configs/$safe.json")
                if (ext.exists()) json = ext else return false
            }
            val cfg = com.easytier.TomlUtils.jsonToConfigState(
                org.json.JSONObject(json.readText())
            )
            if (cfg.networkName.isBlank() || cfg.secret.isBlank() ||
                cfg.server.isBlank() || cfg.port.isBlank()) return false

            val mgr = EasyTierManager.getInstance()
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mgr.onNetworkChanged = { ip, cidrs ->
                mainHandler.post {
                    if (!cfg.noTun) {
                        val grant = android.net.VpnService.prepare(this@QuickStartTile)
                        if (grant == null) {
                            com.easytier.vpn.EasyTierVpnService.langZh = langZh
                            mgr.restartVpnService(ip, cidrs)
                        } else {
                            // One-time VPN consent still needed → hand over to the UI
                            val launch = Intent(this@QuickStartTile, MainActivity::class.java).apply {
                                putExtra(MainActivity.EXTRA_QUICK_TOGGLE, true)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            try { startActivity(launch) } catch (_: Exception) {}
                        }
                    }
                }
            }
            mgr.start(
                cfg.instanceName.ifEmpty { "default" },
                com.easytier.TomlUtils.buildToml(cfg),
                cfg.noTun,
                cfg.dnsServer.takeIf { cfg.dnsEnabled }
            )
            prefs.edit().putBoolean("is_running", true).apply()
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "silentStart failed: ${e.message}")
            false
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        EasyTierManager.init(this)
        val running = EasyTierManager.isRunning
        val zh = langZh
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (zh) "快速启动" else "Quick Start"
        tile.icon = Icon.createWithResource(this, android.R.drawable.ic_menu_manage)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when {
                running -> if (zh) "已连接" else "Connected"
                else -> if (zh) "已断开" else "Disconnected"
            }
        }
        tile.updateTile()
    }
}
