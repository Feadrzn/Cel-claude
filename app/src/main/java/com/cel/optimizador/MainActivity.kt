package com.cel.optimizador

import android.app.Activity
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.BatteryManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.widget.Toast
import kotlin.math.abs

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        title = "Optimizador"

        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val ramPct = ((mi.totalMem - mi.availMem) * 100 / mi.totalMem).toInt()

        val st = StatFs(Environment.getDataDirectory().path)
        val stPct = ((st.totalBytes - st.availableBytes) * 100 / st.totalBytes).toInt()

        val bi = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batPct = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val temp = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val volt = (bi?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000.0
        val status = when (bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Cargando"
            BatteryManager.BATTERY_STATUS_FULL -> "Completa"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descargando"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Sin cargar"
            else -> "—"
        }
        val health = when (bi?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "buena"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "sobrecalentada"
            BatteryManager.BATTERY_HEALTH_COLD -> "fría"
            BatteryManager.BATTERY_HEALTH_DEAD -> "dañada"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "sobrevoltaje"
            else -> "desconocida"
        }
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val cur = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val ma = if (cur == 0 || cur == Int.MIN_VALUE) null else {
            if (abs(cur) > 20000) abs(cur) / 1000 else abs(cur)
        }

        page {
            card {
                label("🔋 Batería", 18f, true)
                label("$batPct %  ·  $status")
                bar(batPct)
                label(
                    "Temperatura $temp °C · ${"%.2f".format(volt)} V · salud $health" +
                        (if (ma != null) " · $ma mA" else ""),
                    13f, dim = true,
                )
            }
            card {
                label("🧠 Memoria RAM", 18f, true)
                label("${fmtBytes(mi.totalMem - mi.availMem)} usados de ${fmtBytes(mi.totalMem)}")
                bar(ramPct)
                label("Libre: ${fmtBytes(mi.availMem)}", 13f, dim = true)
                btn("⚡ Liberar RAM ahora") { liberarRam() }
            }
            card {
                label("💾 Almacenamiento", 18f, true)
                label("${fmtBytes(st.totalBytes - st.availableBytes)} usados de ${fmtBytes(st.totalBytes)}")
                bar(stPct)
                label("Libre: ${fmtBytes(st.availableBytes)}", 13f, dim = true)
                btn("🗂 Archivos grandes y duplicados") {
                    startActivity(Intent(this@MainActivity, FilesActivity::class.java))
                }
                btn("📱 Apps: uso, peso y caché") {
                    startActivity(Intent(this@MainActivity, AppsActivity::class.java))
                }
            }
            card {
                label("⚙️ Ajustes del sistema", 18f, true)
                btn("Consumo de batería (Android)") {
                    safeStart(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
                }
                btn("Almacenamiento (Android)") {
                    safeStart(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                }
                btn("Cuidado del dispositivo (Samsung)") {
                    val i = packageManager.getLaunchIntentForPackage("com.samsung.android.lool")
                    if (i != null) safeStart(i) else toast("No encontré 'Cuidado del dispositivo'")
                }
            }
            card {
                label("💡 Consejos Samsung", 18f, true)
                label(
                    "• Ajustes > Batería > Límites de uso en segundo plano: pon las apps que casi no usas en reposo profundo.\n" +
                        "• Opciones de desarrollador: baja la escala de animación a 0.5x para sentirlo más rápido.\n" +
                        "• Reinicia el teléfono una vez por semana.\n" +
                        "• Desinstala o desactiva las apps que no uses (pestaña 'Sin usar' en Apps).",
                    14f, dim = true,
                )
            }
        }
    }

    private fun liberarRam() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val before = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        var n = 0
        for (ai in packageManager.getInstalledApplications(0)) {
            if (ai.packageName == packageName) continue
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            am.killBackgroundProcesses(ai.packageName)
            n++
        }
        toast("Cerrando procesos en segundo plano de $n apps…")
        handler.postDelayed({
            val after = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
            val diff = after - before
            toast(
                if (diff > 0) "Liberaste ${fmtBytes(diff)} de RAM"
                else "Ya estaba casi todo libre (Android reabre apps si las necesita)",
            )
            render()
        }, 1500)
    }

    private fun safeStart(i: Intent) {
        try {
            startActivity(i)
        } catch (e: ActivityNotFoundException) {
            toast("No se pudo abrir esa pantalla")
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
