package com.group15.notemate.model

/** MVVM: Model. One touch point of a handwriting stroke: where the finger was and when. */
data class StrokePoint(val x: Float, val y: Float, val time: Long)
