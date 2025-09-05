package com.ssolstice.camera.manual.compose

import android.annotation.SuppressLint
import com.ssolstice.camera.manual.R
import kotlin.math.abs


@SuppressLint("DefaultLocale")
fun getAspectRatio(width: Int, height: Int): String {
    val ratio = width.toDouble() / height
    if (abs(ratio - 16.0 / 9) < 0.01) return "16:9"
    if (abs(ratio - 4.0 / 3) < 0.01) return "4:3"
    if (abs(ratio - 3.0 / 2) < 0.01) return "3:2"
    if (abs(ratio - 1.0) < 0.01) return "1:1"
    if (abs(ratio - 5.0 / 4) < 0.01) return "5:4"
    if (abs(ratio - 21.0 / 9) < 0.01) return "21:9"
    if (abs(ratio - 2) < 0.01) return "2:1"
    return String.format("%.2f:1", ratio) // fallback dạng 1.78:1
}

fun getFlashIcon(flashValue: String): Int = when (flashValue) {
    "flash_off" -> R.drawable.flash_off
    "flash_auto", "flash_frontscreen_auto" -> R.drawable.flash_auto
    "flash_on", "flash_frontscreen_on" -> R.drawable.flash_on
    "flash_torch", "flash_frontscreen_torch" -> R.drawable.baseline_highlight_white_48
    "flash_red_eye" -> R.drawable.baseline_remove_red_eye_white_48
    else -> R.drawable.flash_off
}

fun generateExposureLabels(
    min: Int,       // ví dụ -24
    max: Int,       // ví dụ +24
    stepsPerEv: Int // ví dụ 6 (tức 6 steps = 1 EV)
): ArrayList<String> {
    val minEv = min / stepsPerEv
    val maxEv = max / stepsPerEv

    val list = ArrayList<String>()
    for (ev in minEv..maxEv) {
        list.add(
            if (ev > 0) "+$ev" else ev.toString()
        )
    }
    return list
}

fun generateIsoLabels(
    minIso: Int, maxIso: Int
): ArrayList<String> {
    val labels = arrayListOf<String>()

    // Chọn ISO chuẩn gần min và max
    var iso = 50
    if (minIso > iso) iso = minIso

    while (iso <= maxIso) {
        labels.add(iso.toString())
        iso *= 2 // tăng theo bội số 2
    }

    // đảm bảo có giá trị min và max
    if (!labels.contains(minIso.toString())) labels.add(0, minIso.toString())
    if (!labels.contains(maxIso.toString())) labels.add(maxIso.toString())

    return labels
}

fun generateShutterSpeedLabels(
    minSpeed: Double, // giây, ví dụ 1/8000s = 0.000125
    maxSpeed: Double  // giây, ví dụ 30s
): ArrayList<String> {
    val labels = ArrayList<String>()
    var speed = minSpeed

    while (speed <= maxSpeed) {
        labels.add(formatShutter(speed))
        speed *= 2 // mỗi bước nhân đôi thời gian
    }
    return labels
}

private fun formatShutter(seconds: Double): String {
    return if (seconds >= 1) {
        "${seconds.toInt()}s"
    } else {
        "1/${(1 / seconds).toInt()}"
    }
}