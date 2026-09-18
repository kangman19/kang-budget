package com.example.kangbudget.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kangbudget.R
import com.example.kangbudget.viewmodel.AppReadyState
import com.example.kangbudget.viewmodel.AppReadyViewModel

@Composable
fun AppRoot(
    appReadyViewModel: AppReadyViewModel = viewModel()
) {
    val state by appReadyViewModel.state.collectAsStateWithLifecycle()

    when (state) {
        is AppReadyState.Loading -> LoadingScreen()
        is AppReadyState.Ready -> MainScreen()
        is AppReadyState.OfflineReady -> MainScreen(offlineNotice = true)
        is AppReadyState.Error -> ErrorScreen(onRetry = appReadyViewModel::retry)
    }
}

/**
 * The app's own launcher icon, matching what shows on the phone's home screen.
 *
 * The foreground layer comes from `@mipmap`, not `@drawable`: `drawable/ic_launcher_foreground.xml`
 * is the leftover template bugdroid, while the per-density `mipmap` `ic_launcher_foreground.webp`
 * is the real artwork that `mipmap-anydpi-v26/ic_launcher.xml` composites on the home screen.
 *
 * Adaptive icons draw a 108dp foreground into a 72dp mask viewport, so the launcher only ever
 * shows the middle two thirds of the layer. Scaling it to 1.5x the badge and letting the clip
 * crop it reproduces exactly that framing instead of a zoomed-out, letterboxed version of it.
 */
@Composable
private fun AppLogoBadge(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(colorResource(id = R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = "KangBudget",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size * 1.5f)
        )
    }
}

@Composable
private fun LoadingScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppLogoBadge(size = 96.dp)
            Column(
                modifier = Modifier.padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun ErrorScreen(onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppLogoBadge(size = 72.dp)
            Text(
                text = "Couldn't connect to KangBudget",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "Check your connection and try again.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
