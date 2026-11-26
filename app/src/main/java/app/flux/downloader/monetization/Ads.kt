package app.flux.downloader.monetization

import android.app.Activity
import app.flux.downloader.engine.Choice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference

/**
 * Configuración de anuncios. Los IDs salen de `gradle.properties` (ver ADMOB_Y_PREMIUM.md): con `admob.real=false`
 * (o sin ID de app) se usan los IDs de PRUEBA que publica Google; con `admob.real=true` y los IDs de tu cuenta se
 * usan los reales (en ese caso registra tu teléfono como dispositivo de prueba en AdMob y en `admob.testDevices`).
 */
object AdConfig {
    /** true = anuncios simulados y espacios reservados, sin SDK. Ahora el SDK real está conectado. */
    const val STUB_MODE = false

    /** true = esta compilación usa los IDs reales de tu cuenta de AdMob. */
    const val USE_REAL_IDS = app.flux.downloader.BuildConfig.ADMOB_REAL

    const val ADMOB_APP_ID = app.flux.downloader.BuildConfig.ADMOB_APP_ID

    const val REWARDED_DOWNLOAD = app.flux.downloader.BuildConfig.ADMOB_REWARDED_DOWNLOAD
    const val REWARDED_VAULT_UNLOCK = app.flux.downloader.BuildConfig.ADMOB_REWARDED_VAULT_UNLOCK
    const val REWARDED_VAULT_SLOT = app.flux.downloader.BuildConfig.ADMOB_REWARDED_VAULT_SLOT

    const val BANNER_HOME = app.flux.downloader.BuildConfig.ADMOB_BANNER_HOME
    const val BANNER_PROGRESS = app.flux.downloader.BuildConfig.ADMOB_BANNER_PROGRESS

    /** IDs de dispositivos de prueba (se ven en Logcat o en Ajustes de AdMob → Dispositivos de prueba). */
    val TEST_DEVICE_IDS: List<String> =
        app.flux.downloader.BuildConfig.ADMOB_TEST_DEVICES.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/** Dónde se muestra un anuncio con recompensa. Cada uno tiene su propio ID de bloque en AdMob. */
enum class AdPlacement(val adUnitId: String) {
    DOWNLOAD(AdConfig.REWARDED_DOWNLOAD),
    VAULT_UNLOCK(AdConfig.REWARDED_VAULT_UNLOCK),
    VAULT_SLOT(AdConfig.REWARDED_VAULT_SLOT),
}

/** Conexión con el SDK real de AdMob (se implementa cuando haya cuenta; ver ADMOB_Y_PREMIUM.md). */
interface RewardedAdProvider {
    /** Muestra el anuncio y devuelve true solo si el usuario lo vio completo y ganó la recompensa. */
    suspend fun show(placement: AdPlacement, activity: Activity?): Boolean
}

/** Un anuncio simulado pendiente de mostrarse (lo dibuja [RewardedAdOverlay]). */
class StubAdRequest(val placement: AdPlacement, val purpose: String) {
    val result = CompletableDeferred<Boolean>()
}

object Ads {
    /** null = modo de prueba (anuncio simulado). Con AdMob real se asigna un [RewardedAdProvider]. */
    var provider: RewardedAdProvider? = null

    private val started = java.util.concurrent.atomic.AtomicBoolean(false)
    private val _ready = MutableStateFlow(false)
    /** true cuando el SDK de AdMob ya se inicializó (los banners esperan a esto). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Inicia el SDK de AdMob una sola vez, en segundo plano, y solo después del consentimiento (UMP). */
    fun startSdk(app: android.content.Context) {
        if (AdConfig.STUB_MODE || !started.compareAndSet(false, true)) return
        if (AdConfig.USE_REAL_IDS && AdConfig.TEST_DEVICE_IDS.isEmpty()) {
            android.util.Log.w("Ads", "IDs REALES sin dispositivo de prueba: registra tu teléfono para no arriesgar la cuenta")
        }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            com.google.android.gms.ads.MobileAds.setRequestConfiguration(
                com.google.android.gms.ads.RequestConfiguration.Builder()
                    .setTestDeviceIds(AdConfig.TEST_DEVICE_IDS)
                    .build(),
            )
            com.google.android.gms.ads.MobileAds.initialize(app) {
                provider = AdMobRewardedProvider(app)
                _ready.value = true
            }
        }
    }

    private var activityRef: WeakReference<Activity>? = null
    val activity: Activity? get() = activityRef?.get()
    fun attach(a: Activity) { activityRef = WeakReference(a) }
    fun detach(a: Activity) { if (activityRef?.get() === a) activityRef = null }

    // Un anuncio a la vez: si se piden varios (p. ej. "descargar todos"), se muestran uno tras otro
    private val gate = Mutex()
    private val _pending = MutableStateFlow<StubAdRequest?>(null)
    val pending: StateFlow<StubAdRequest?> = _pending.asStateFlow()

    /** true = el usuario ganó la recompensa; false = cerró el anuncio antes de tiempo. */
    suspend fun showRewarded(placement: AdPlacement, purpose: String): Boolean = gate.withLock {
        // Con el SDK real nunca se usa el anuncio simulado: si no está listo o no carga, no hay recompensa
        if (!AdConfig.STUB_MODE) return@withLock provider?.show(placement, activity) ?: false
        val req = StubAdRequest(placement, purpose)
        _pending.value = req
        try {
            req.result.await()
        } finally {
            _pending.value = null
        }
    }

    fun finish(req: StubAdRequest, rewarded: Boolean) {
        req.result.complete(rewarded)
    }
}

/**
 * Regla de las descargas: cada video se descarga gratis viendo un anuncio con recompensa, salvo la calidad 240p
 * (gratis y sin anuncios). Premium descarga todo sin anuncios.
 */
object DownloadGate {
    const val FREE_MAX_HEIGHT = 240

    fun isFreeQuality(height: Int?, audioOnly: Boolean): Boolean =
        !audioOnly && height != null && height in 1..FREE_MAX_HEIGHT

    fun requiresAd(height: Int?, audioOnly: Boolean): Boolean =
        !PremiumState.isPremium.value && !isFreeQuality(height, audioOnly)

    /** true = se puede empezar la descarga ahora. */
    suspend fun authorize(title: String, choice: Choice): Boolean {
        if (!requiresAd(choice.height, choice.audioOnly)) return true
        return Ads.showRewarded(AdPlacement.DOWNLOAD, "descargar “" + title.take(60) + "”")
    }

    /** Mensaje corto que se muestra junto a cada calidad. */
    fun tag(height: Int?, audioOnly: Boolean = false, premium: Boolean = PremiumState.isPremium.value): String = when {
        premium -> "Sin anuncios · Premium"
        isFreeQuality(height, audioOnly) -> "Gratis · sin anuncios"
        else -> "Gratis viendo un anuncio"
    }
}
