package app.flux.downloader.monetization

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flux.downloader.ui.FluxOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Anuncio con recompensa SIMULADO (modo de prueba): muestra un cuadro con cuenta regresiva y da la recompensa al terminar.
 * Cuando se conecte AdMob ([Ads.provider]) esto deja de usarse y el anuncio lo dibuja el SDK.
 */
@Composable
fun RewardedAdOverlay(onOpenPremium: () -> Unit) {
    val req by Ads.pending.collectAsStateWithLifecycle()
    val r = req ?: return
    var left by remember(r) { mutableIntStateOf(5) }
    LaunchedEffect(r) {
        while (left > 0) {
            delay(1_000)
            left -= 1
        }
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Anuncio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mira este anuncio para ${r.purpose}. Es gratis.")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Aquí se mostrará el anuncio de AdMob\n(modo de prueba)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    if (left > 0) "Podrás continuar en $left s" else "Listo: ya puedes continuar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    Ads.finish(r, false)
                    onOpenPremium()
                }) { Text("Quitar los anuncios con Premium") }
            }
        },
        confirmButton = { TextButton(onClick = { Ads.finish(r, true) }, enabled = left == 0) { Text("Continuar") } },
        dismissButton = { TextButton(onClick = { Ads.finish(r, false) }) { Text("Cancelar") } },
    )
}

/**
 * Anuncio fijo (banner) de las pestañas Descargas y Progreso. Premium no lo ve.
 * Mientras [AdConfig.STUB_MODE] sea true se muestra un espacio reservado; con AdMob aquí va el AdView
 * (ver ADMOB_Y_PREMIUM.md).
 */
@Composable
fun AdBanner(placement: String, modifier: Modifier = Modifier) {
    val premium by PremiumState.isPremium.collectAsStateWithLifecycle()
    if (premium) return
    if (!AdConfig.STUB_MODE) {
        RealBanner(placement, modifier)
        return
    }
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Espacio reservado para un anuncio ($placement)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Banner real de AdMob (adaptable al ancho). Espera a que el SDK esté iniciado (tras el consentimiento). */
@Composable
private fun RealBanner(placement: String, modifier: Modifier) {
    val ready by Ads.ready.collectAsStateWithLifecycle()
    if (!ready) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val width = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val unit = if (placement == "progreso") AdConfig.BANNER_PROGRESS else AdConfig.BANNER_HOME
    val adView = remember(unit, width) {
        com.google.android.gms.ads.AdView(ctx).apply {
            setAdSize(com.google.android.gms.ads.AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, width))
            adUnitId = unit
            loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
        }
    }
    androidx.compose.runtime.DisposableEffect(adView) { onDispose { adView.destroy() } }
    androidx.compose.ui.viewinterop.AndroidView(factory = { adView }, modifier = modifier.fillMaxWidth())
}

/** Ventana que explica la carpeta privada y la desbloquea viendo un anuncio opcional (incluye 3 casillas gratis). */
@Composable
fun VaultUnlockDialog(onDismiss: () -> Unit, onUnlocked: () -> Unit, onOpenPremium: () -> Unit) {
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Carpeta privada") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Guarda tus videos con un código, fuera de la galería y de otras apps.")
                Text(
                    "Desbloquear carpeta privada (ver anuncio) — incluye ${VaultSlots.INITIAL_FREE} casillas gratis.",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "La carpeta privada se desbloquea viendo un anuncio opcional e incluye ${VaultSlots.INITIAL_FREE} casillas gratis. " +
                        "Una casilla es un video: más casillas, más videos.",
                )
                Text(
                    "Puedes ampliar las casillas con anuncios opcionales: 1 casilla por anuncio, hasta " +
                        "${VaultSlots.DAILY_AD_LIMIT} por día.",
                )
                Text("El gestor de archivos y las carpetas normales funcionan sin publicidad.")
                Text("Con Premium las casillas son ilimitadas.", color = FluxOrange, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onOpenPremium) { Text("Ver Flux Premium") }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    if (Ads.showRewarded(AdPlacement.VAULT_UNLOCK, "desbloquear la carpeta privada")) {
                        VaultSlots.unlock()
                        onUnlocked()
                    }
                }
            }) { Text("Desbloquear (ver anuncio)") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ahora no") } },
    )
}
