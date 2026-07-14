package com.example.kangbudget.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalAmountsHidden = compositionLocalOf { false }

/** Blurs the composable it's applied to when privacy mode (the eye toggle) is on. */
@Composable
fun Modifier.privacyBlur(radius: Dp = 10.dp): Modifier =
    if (LocalAmountsHidden.current) this.blur(radius) else this
