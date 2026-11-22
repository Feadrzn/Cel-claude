package com.cel.optimizador

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FilesActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val df = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    private val root = Environment.getExternalStorageDirectory()

    private var large: List<File>? = null
    private var dups: List<List<File>> = emptyList()
    private var old: List<File> = emptyList()
    private var scanning = false
    private var mode = 0
    private val selected = HashSet<String>()

    private lateinit var summary: TextView
    private lateinit var deleteBtn: Button

    override fun onResume() {
        super.onResume()
        title = "Archivos grandes y duplicados"
        when {
            !Environment.isExternalStorageManager() -> permissionPage()
            large == null && !scanning -> startScan()
            large != null -> render()
        }
    }

    private fun permissionPage() = page {
        card {
            label("Necesito acceso a tus archivos", 18f, true)
            label(
                "Para buscar archivos grandes y duplicados, Android pide que actives " +
                    "'Permitir acceso a todos los archivos' para esta app. " +
                    "Nada se borra sin que tú lo confirmes.",
                14f, dim = true,
            )
            btn("Dar acceso") {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName"),
                        ),
                    )
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        }
    }

    private fun startScan() {
        scanning = true
        selected.clear()
        page { card { label("🔎 Escaneando tu almacenamiento…", 16f, true); label("Puede tardar un minuto.", 13f, dim = true) } }
        Thread {
            val all = ArrayList<File>()
            walk(root, 0, all)

            val big = all.filter { it.length() >= 20_000_000L }.sortedByDescending { it.length() }.take(80)

            val bySize = all.groupBy { it.length() }.filter { it.value.size > 1 }
            val groups = ArrayList<List<File>>()
            for ((_, fs) in bySize) {
                for (g in fs.groupBy { quickHash(it) }.values) {
                    if (g.size > 1) groups.add(g.sortedBy { it.lastModified() })
                }
            }
            groups.sortByDescending { it[0].length() * (it.size - 1) }

            val limit = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
            val oldDl = all.filter {
                it.path.contains("/Download", ignoreCase = true) && it.lastModified() < limit
            }.sortedByDescending { it.length() }.take(100)

            handler.post {
                large = big
                dups = groups.take(40)
                old = oldDl
                scanning = false
                render()
            }
        }.start()
    }

    private fun walk(dir: File, depth: Int, out: MutableList<File>) {
        if (depth > 20) return
        val list = dir.listFiles() ?: return
        for (f in list) {
            if (f.isDirectory) {
                if (depth == 0 && f.name == "Android") continue
                walk(f, depth + 1, out)
            } else if (f.length() >= 1_000_000L) {
                out.add(f)
            }
        }
    }

    private fun quickHash(f: File): String = try {
        val md = MessageDigest.getInstance("MD5")
        RandomAccessFile(f, "r").use { raf ->
            val buf = ByteArray(256 * 1024)
            val len = raf.length()
            var n = raf.read(buf)
            if (n > 0) md.update(buf, 0, n)
            if (len > 3L * buf.size) {
                raf.seek(len / 2)
                n = raf.read(buf)
                if (n > 0) md.update(buf, 0, n)
                raf.seek(len - buf.size)
                n = raf.read(buf)
                if (n > 0) md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        "err-${f.path}"
    }

    private fun setMode(m: Int) {
        mode = m
        selected.clear()
        if (m == 1) dups.forEach { g -> g.drop(1).forEach { selected.add(it.path) } }
        render()
    }

    private fun render() {
        page {
            row {
                btn("Grandes") { setMode(0) }
                btn("Duplicados") { setMode(1) }
                btn("Descargas viejas") { setMode(2) }
            }
            label(
                when (mode) {
                    0 -> "Archivos de más de 20 MB, del más pesado al más ligero."
                    1 -> "Archivos probablemente idénticos. Dejo sin marcar el más antiguo (el original)."
                    else -> "Archivos en Descargas con más de 90 días."
                },
                13f, dim = true,
            )
            summary = label("", 15f, true)
            deleteBtn = btn("🗑 Eliminar seleccionados") { confirmDelete() }

            when (mode) {
                1 -> {
                    if (dups.isEmpty()) label("No encontré duplicados 🎉")
                    dups.forEachIndexed { i, g ->
                        label("Grupo ${i + 1} · ${fmtBytes(g[0].length())} c/u", 14f, true)
                        g.forEach { fileRow(it) }
                    }
                }
                else -> {
                    val list = if (mode == 0) large.orEmpty() else old
                    if (list.isEmpty()) label("Nada por aquí 🎉")
                    list.forEach { fileRow(it) }
                }
            }
            updateSummary()
        }
    }

    private fun LinearLayout.fileRow(f: File) {
        val folder = (f.parent ?: "").removePrefix(root.path).ifEmpty { "/" }
        val cb = CheckBox(context).apply {
            text = "${f.name}\n$folder · ${fmtBytes(f.length())} · ${df.format(Date(f.lastModified()))}"
            textSize = 14f
            isChecked = f.path in selected
            setOnCheckedChangeListener { _, c ->
                if (c) selected.add(f.path) else selected.remove(f.path)
                updateSummary()
            }
        }
        addView(cb, LinearLayout.LayoutParams(-1, -2))
    }

    private fun updateSummary() {
        val size = selected.sumOf { File(it).length() }
        summary.text = "${selected.size} seleccionados · ${fmtBytes(size)}"
        deleteBtn.isEnabled = selected.isNotEmpty()
    }

    private fun confirmDelete() {
        val size = selected.sumOf { File(it).length() }
        AlertDialog.Builder(this)
            .setTitle("¿Eliminar archivos?")
            .setMessage("Se eliminarán ${selected.size} archivos (${fmtBytes(size)}). No se pueden recuperar.")
            .setPositiveButton("Eliminar") { _, _ -> doDelete() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun doDelete() {
        val paths = selected.toList()
        scanning = true
        page { card { label("Eliminando…", 16f, true) } }
        Thread {
            val deleted = paths.filter { File(it).delete() }
            MediaScannerConnection.scanFile(this, paths.toTypedArray(), null, null)
            handler.post {
                Toast.makeText(
                    this,
                    "Eliminados ${deleted.size} de ${paths.size} archivos",
                    Toast.LENGTH_LONG,
                ).show()
                large = null
                scanning = false
                startScan()
            }
        }.start()
    }
}
