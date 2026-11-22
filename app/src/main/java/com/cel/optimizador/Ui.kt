package com.cel.optimizador

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun fmtBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = -1
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024
        i++
    }
    return String.format(Locale.US, "%.1f %s", v, units[i])
}

fun fmtDur(ms: Long): String {
    val m = ms / 60000
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}

/** Construye una pantalla desplazable y la pone como contenido de la Activity. */
fun Activity.page(build: LinearLayout.() -> Unit) {
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(32))
    }
    col.build()
    setContentView(ScrollView(this).apply { addView(col) })
}

fun LinearLayout.card(build: LinearLayout.() -> Unit) {
    val c = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(16), context.dp(14), context.dp(16), context.dp(14))
        background = GradientDrawable().apply {
            cornerRadius = context.dp(16).toFloat()
            setColor(0x22808080)
        }
    }
    c.build()
    addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(12) })
}

fun LinearLayout.row(build: LinearLayout.() -> Unit) {
    val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    r.build()
    addView(r, LinearLayout.LayoutParams(-1, -2))
}

fun LinearLayout.label(
    s: String,
    sp: Float = 15f,
    bold: Boolean = false,
    dim: Boolean = false,
): TextView {
    val t = TextView(context).apply {
        text = s
        textSize = sp
        if (bold) setTypeface(typeface, Typeface.BOLD)
        if (dim) alpha = 0.7f
    }
    addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(2) })
    return t
}

fun LinearLayout.btn(text: String, onClick: () -> Unit): Button {
    val b = Button(context).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }
    val lp = if (orientation == LinearLayout.HORIZONTAL) {
        LinearLayout.LayoutParams(0, -2, 1f)
    } else {
        LinearLayout.LayoutParams(-1, -2)
    }
    addView(b, lp)
    return b
}

fun LinearLayout.bar(pct: Int) {
    val p = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        progress = pct.coerceIn(0, 100)
    }
    addView(p, LinearLayout.LayoutParams(-1, context.dp(10)).apply {
        topMargin = context.dp(6)
        bottomMargin = context.dp(6)
    })
}
