package app.flux.downloader.ui

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import app.flux.downloader.engine.DownloadManager
import app.flux.downloader.engine.Status
import app.flux.downloader.monetization.PremiumState
import app.flux.downloader.monetization.RewardedAdOverlay
import app.flux.downloader.monetization.VaultSlots
import app.flux.downloader.monetization.VaultUnlockDialog
import app.flux.downloader.monetization.PremiumScreen
import kotlinx.coroutines.flow.collectLatest
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flux.downloader.browser.BrowserScreen
import app.flux.downloader.engine.Choice
import app.flux.downloader.settings.AppSettings
import app.flux.downloader.vault.Vault

@Composable
fun AppRoot(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var player by remember { mutableStateOf<PlayerSource?>(null) }
    var showVault by remember { mutableStateOf(false) }

    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val allowCaptures by AppSettings.allowVaultCaptures.collectAsStateWithLifecycle()

    // La carpeta privada se bloquea sola cuando la app deja de verse
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) Vault.lock()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Sin capturas ni grabación de pantalla dentro de lo privado (si el ajuste lo pide)
    val secure = (showVault || player?.isPrivate == true) && !allowCaptures
    DisposableEffect(secure) {
        val window = ctx.findActivity()?.window
        if (secure) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    val current = player
    var showPremium by remember { mutableStateOf(false) }
    var showVaultUnlock by remember { mutableStateOf(false) }
    val premium by PremiumState.isPremium.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val activeDownloads = downloads.count { it.status == Status.RUNNING || it.status == Status.QUEUED }
    val snackbar = remember { SnackbarHostState() }

    // Al empezar una descarga: aviso "Descarga iniciada · Ver" que lleva a la pestaña Progreso
    LaunchedEffect(Unit) {
        DownloadManager.started.collectLatest {
            if (tab != 2) {
                val r = snackbar.showSnackbar(
                    message = "Descarga iniciada",
                    actionLabel = "Ver",
                    duration = SnackbarDuration.Short,
                )
                if (r == SnackbarResult.ActionPerformed) tab = 2
            }
        }
    }

    // La carpeta privada se desbloquea una vez viendo un anuncio (Premium entra directo)
    val openVault: () -> Unit = { if (VaultSlots.isUnlocked) showVault = true else showVaultUnlock = true }

    // El navegador se crea la primera vez que se entra y desde entonces sigue vivo (oculto) para no recargar la página
    var browserLive by remember { mutableStateOf(tab == 1) }
    if (tab == 1 && !browserLive) browserLive = true
    val background = MaterialTheme.colorScheme.background
    val blockTouches = Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
    val navVisible = current == null && !showVault && !showPremium
    Box(Modifier.fillMaxSize().background(background)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.weight(1f)) {
                if (browserLive) {
                    BrowserScreen(
                        visible = tab == 1 && current == null && !showVault && !showPremium,
                        session = vm.browserSession,
                        pendingUrl = vm.browserRequest,
                        onPendingConsumed = { vm.browserRequest = null },
                        onPlayStream = { player = it },
                        onDownloadRequest = { url ->
                            vm.onSharedText(url)
                            tab = 0
                        },
                        onDownloadQuality = { info, height ->
                            vm.startDownload(info, Choice(false, height, AppSettings.audioFormat.value))
                        },
                    )
                }
                if (tab != 1) {
                    Box(Modifier.fillMaxSize().background(background).then(blockTouches)) {
                        when (tab) {
                            0 -> MainScreen(
                                vm = vm,
                                onPlay = { player = it },
                                onOpenVault = openVault,
                                onOpenInBrowser = { url -> vm.openInBrowser(url); tab = 1 },
                                onOpenPremium = { showPremium = true },
                            )
                            2 -> ProgressScreen(vm = vm, onPlay = { player = it }, onOpenVault = openVault)
                            3 -> FilesScreen(onOpenVault = openVault, onPlay = { player = it })
                            else -> SettingsScreen(onOpenVault = openVault, onOpenPremium = { showPremium = true })
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    label = { NavLabel("Descargas") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Public, contentDescription = null) },
                    label = { NavLabel("Navegador") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = {
                        BadgedBox(badge = { if (activeDownloads > 0) Badge { Text("$activeDownloads") } }) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                        }
                    },
                    label = { NavLabel("Progreso") },
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { NavLabel("Archivos") },
                )
                NavigationBarItem(
                    selected = tab == 4,
                    onClick = { tab = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { NavLabel("Ajustes") },
                )
            }
        }
        if (current != null) {
            Box(Modifier.fillMaxSize().background(background).then(blockTouches)) {
                PlayerScreen(
                    source = current,
                    onDownload = { info, height ->
                        vm.startDownload(info, Choice(false, height, AppSettings.audioFormat.value))
                    },
                    onClose = { player = null },
                )
            }
        } else if (showVault) {
            Box(Modifier.fillMaxSize().background(background).then(blockTouches)) {
                VaultScreen(
                    onClose = { Vault.lock(); showVault = false },
                    onPlay = { player = it },
                    onOpenPremium = { showPremium = true },
                )
            }
        }
        if (showPremium) {
            Box(Modifier.fillMaxSize().then(blockTouches)) {
                PremiumScreen(onClose = { showPremium = false })
            }
        }

        // Aviso "Descarga iniciada · Ver": encima de todo, sobre la barra de pestañas cuando se ve
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = if (navVisible) 72.dp else 16.dp, start = 12.dp, end = 12.dp),
        )

        if (showVaultUnlock) {
            VaultUnlockDialog(
                onDismiss = { showVaultUnlock = false },
                onUnlocked = { showVaultUnlock = false; showVault = true },
                onOpenPremium = { showVaultUnlock = false; showPremium = true },
            )
        }

        // Anuncio con recompensa simulado (hasta que se conecte AdMob); siempre por encima de todo lo demás
        RewardedAdOverlay(onOpenPremium = { showPremium = true })
    }
}


/**
 * Etiqueta de la barra de pestañas: una sola línea y con un tamaño fijo en dp (no crece con el tamaño de letra del sistema),
 * para que "Descargas" y "Navegador" no se partan en dos renglones.
 */
@Composable
private fun NavLabel(text: String) {
    val size = with(LocalDensity.current) { 11.dp.toSp() }
    Text(text, fontSize = size, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
}
