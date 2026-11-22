package com.cel.optimizador

import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings

class AppsActivity : Activity() {
    private class Row(
        val pkg: String,
        val label: String,
        val system: Boolean,
        val fg7: Long,
        val last: Long,
        val size: Long,
        val cache: Long,
    )

    private val handler = Handler(Looper.getMainLooper())
    private var rows: List<Row>? = null
    private var loading = false
    private var mode = 0

    override fun onResume() {
        super.onResume()
        title = "Apps"
        when {
            !hasUsageAccess() -> permissionPage()
            rows == null && !loading -> load()
            rows != null -> render()
        }
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        return ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun permissionPage() = page {
        card {
            label("Necesito acceso al uso de apps", 18f, true)
            label(
                "Android pide activar 'Acceso a datos de uso' para esta app. " +
                    "Sirve para saber cuánto usas cada app y cuánto pesa. " +
                    "Los datos se quedan en tu teléfono.",
                14f, dim = true,
            )
            btn("Dar acceso") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }
    }

    private fun load() {
        loading = true
        page { card { label("📊 Analizando tus apps…", 16f, true) } }
        Thread {
            val pm = packageManager
            val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val ssm = getSystemService(STORAGE_STATS_SERVICE) as StorageStatsManager
            val now = System.currentTimeMillis()
            val day = 24L * 3600 * 1000
            val s7 = usm.queryAndAggregateUsageStats(now - 7 * day, now)
            val s30 = usm.queryAndAggregateUsageStats(now - 30 * day, now)
            val user = Process.myUserHandle()

            val out = ArrayList<Row>()
            for (ai in pm.getInstalledApplications(0)) {
                if (ai.packageName == packageName) continue
                if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue
                var size = 0L
                var cache = 0L
                try {
                    val s = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, ai.packageName, user)
                    size = s.appBytes + s.dataBytes
                    cache = s.cacheBytes
                } catch (e: Exception) {
                }
                out.add(
                    Row(
                        pkg = ai.packageName,
                        label = ai.loadLabel(pm).toString(),
                        system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        fg7 = s7[ai.packageName]?.totalTimeInForeground ?: 0L,
                        last = s30[ai.packageName]?.lastTimeUsed ?: 0L,
                        size = size,
                        cache = cache,
                    ),
                )
            }
            handler.post {
                rows = out
                loading = false
                render()
            }
        }.start()
    }

    private fun render() {
        val all = rows ?: return
        val limit30 = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        page {
            row {
                btn("Más usadas") { mode = 0; render() }
                btn("Sin usar") { mode = 1; render() }
                btn("Más pesadas") { mode = 2; render() }
            }
            when (mode) {
                0 -> {
                    label("Tiempo en pantalla, últimos 7 días.", 13f, dim = true)
                    all.filter { it.fg7 > 0 }.sortedByDescending { it.fg7 }.take(20).forEach { r ->
                        card {
                            label(r.label, 16f, true)
                            label("Usada ${fmtDur(r.fg7)} · ocupa ${fmtBytes(r.size)}", 13f, dim = true)
                            btn("Ajustes de la app") { openDetails(r.pkg) }
                        }
                    }
                }
                1 -> {
                    label("Apps instaladas que no abres hace más de 30 días.", 13f, dim = true)
                    val unused = all.filter { !it.system && it.last < limit30 }.sortedByDescending { it.size }
                    if (unused.isEmpty()) label("No encontré apps sin usar 🎉")
                    unused.take(40).forEach { r ->
                        card {
                            label(r.label, 16f, true)
                            label("Ocupa ${fmtBytes(r.size)}", 13f, dim = true)
                            row {
                                btn("Ajustes") { openDetails(r.pkg) }
                                btn("Desinstalar") {
                                    startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${r.pkg}")))
                                }
                            }
                        }
                    }
                }
                else -> {
                    val totalCache = all.sumOf { it.cache }
                    card {
                        label("Caché total de tus apps: ${fmtBytes(totalCache)}", 15f, true)
                        label(
                            "Android no deja que una app limpie la caché de otras. " +
                                "Toca 'Limpiar caché' > Almacenamiento > Borrar caché en las más grandes.",
                            13f, dim = true,
                        )
                    }
                    all.sortedByDescending { it.size }.take(25).forEach { r ->
                        card {
                            label(r.label, 16f, true)
                            label("Total ${fmtBytes(r.size)} · caché ${fmtBytes(r.cache)}", 13f, dim = true)
                            btn("Limpiar caché") { openDetails(r.pkg) }
                        }
                    }
                }
            }
        }
    }

    private fun openDetails(pkg: String) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")),
        )
    }
}
