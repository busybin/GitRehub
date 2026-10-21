package io.github.busybin.gitrehub.helper

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView

/** The bundled UI typeface (assets/fonts/app_font.ttf). */
object TypeFaceHelper {
    private lateinit var appTypeface: Typeface

    @JvmStatic
    fun generateTypeface(context: Context) {
        appTypeface = Typeface.createFromAsset(context.assets, "fonts/app_font.ttf")
    }

    @JvmStatic
    fun applyTypeface(textView: TextView) {
        textView.typeface = appTypeface
    }
}
