package com.camera.app.utils

import android.content.Context
import android.graphics.Paint
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.makeText

fun View.show() {
    visibility = View.VISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

fun TextView.setUnderlined() {
    paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
}

fun Toast.showError(context: Context, message: String) =
    makeText(context, message, Toast.LENGTH_SHORT).show()