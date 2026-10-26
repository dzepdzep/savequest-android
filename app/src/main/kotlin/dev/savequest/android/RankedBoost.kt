package dev.savequest.android

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

const val RB_LOCATIONS = "https://rankedboost.com/pokemon/location/gold-silver/"
const val RB_POKEDEX = "https://rankedboost.com/pokemon/pokedex/gold-silver/"

private fun isRankedBoost(u: Uri?) = u != null && u.scheme == "https" && (u.host == "rankedboost.com" || u.host == "www.rankedboost.com")

/** Explains the feature on the RankedBoost tab and opens the in-app browser. */
@Composable fun RankedBoostTab(hasSave: Boolean, onOpen: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Tick RankedBoost from your save", style = MaterialTheme.typography.titleMedium)
        Text("Opens RankedBoost's Gold/Silver pages inside SaveQuest. A SaveQuest panel appears on each page: Preview, then Apply ticks the Pokémon you caught and the item balls and hidden items you already picked up. Item ticks are per location page, so visit the pages you want (or switch on auto-apply in the panel). Existing ticks are never removed.", style = MaterialTheme.typography.bodySmall)
        Text("This is the only part of SaveQuest that uses the Internet, and only when you open this screen. Your save is handed to the page script once, stays on the phone, and is never uploaded. RankedBoost progress ticked here is stored inside SaveQuest, not in your normal browser.", style = MaterialTheme.typography.bodySmall)
        if (!hasSave) Text("Import a save first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = hasSave, onClick = { onOpen(RB_LOCATIONS) }) { Text("Locations") }
            OutlinedButton(enabled = hasSave, onClick = { onOpen(RB_POKEDEX) }) { Text("Pokédex") }
        }
    } }
}

/** Full-screen RankedBoost browser. Only rankedboost.com is shown in it; every other link opens in the real browser. */
@SuppressLint("SetJavaScriptEnabled")
@Composable fun RankedBoostScreen(raw: ByteArray?, startUrl: String, onUrl: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val script = remember { try { ctx.assets.open("rankedboost/savequest-rankedboost.js").bufferedReader().use { it.readText() } } catch (_: Exception) { "" } }
    val b64 by rememberUpdatedState(remember(raw) { raw?.let { Base64.encodeToString(it, Base64.NO_WRAP) } })
    val onUrlNow by rememberUpdatedState(onUrl)
    var web by remember { mutableStateOf<WebView?>(null) }
    BackHandler { val w = web; if (w != null && w.canGoBack()) w.goBack() else onClose() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("◂ Close") }
            TextButton(onClick = { web?.loadUrl(RB_LOCATIONS) }) { Text("Locations") }
            TextButton(onClick = { web?.loadUrl(RB_POKEDEX) }) { Text("Pokédex") }
            TextButton(onClick = { web?.url?.let { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } catch (_: Exception) {} } }) { Text("Browser") }
        }
        if (script.isEmpty()) Text("The SaveQuest script is missing from this build.", color = MaterialTheme.colorScheme.error)
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.setSupportMultipleWindows(false)
                    // no addJavascriptInterface: the page can never call into the app
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (isRankedBoost(request.url)) return false
                            try { c.startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (_: Exception) {}
                            return true
                        }
                        // the panel is only injected into the RankedBoost Gold/Silver pages it was written for
                        private fun inject(view: WebView, url: String?) {
                            val u = url?.let { Uri.parse(it) }
                            val save = b64
                            val path = u?.path.orEmpty()
                            if (save != null && script.isNotEmpty() && isRankedBoost(u) && (path.startsWith("/pokemon/location/gold-silver/") || path.startsWith("/pokemon/pokedex/gold-silver/")))
                                view.evaluateJavascript("window.__SQ_SAVE_B64='$save';\n$script", null)   // harmless if the panel is already there
                        }
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (url != null) onUrlNow(url)
                            inject(view, url)
                        }
                        // RankedBoost can change pages without a full load (history API): inject there too
                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                            if (url != null) onUrlNow(url)
                            inject(view, url)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                            AlertDialog.Builder(view.context).setMessage(message)
                                .setPositiveButton("OK") { _, _ -> result.confirm() }
                                .setNegativeButton("Cancel") { _, _ -> result.cancel() }
                                .setOnCancelListener { result.cancel() }.show()
                            return true
                        }
                    }
                    loadUrl(startUrl)
                    web = this
                }
            },
            onRelease = { it.destroy(); web = null },
        )
    }
}
