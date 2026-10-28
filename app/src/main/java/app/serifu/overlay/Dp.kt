package app.serifu.overlay

import android.content.Context
import android.util.TypedValue
import android.view.View
import kotlin.math.roundToInt

fun Context.dp(value: Int): Int = dp(value.toFloat())

fun Context.dp(value: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).roundToInt()

fun Context.dpF(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

fun Context.sp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

fun View.dp(value: Int): Int = context.dp(value)

fun View.dp(value: Float): Int = context.dp(value)

fun View.dpF(value: Float): Float = context.dpF(value)

fun View.sp(value: Float): Float = context.sp(value)
