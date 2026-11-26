package app.flux.downloader.monetization

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Anuncios con recompensa reales de AdMob (con IDs de prueba o reales según `gradle.properties`). */
class AdMobRewardedProvider(private val ctx: Context) : RewardedAdProvider {

    /** true solo si el usuario vio el anuncio y ganó la recompensa; false si no cargó, falló o lo cerró antes. */
    override suspend fun show(placement: AdPlacement, activity: Activity?): Boolean {
        if (activity == null) return false
        return withContext(Dispatchers.Main) {
            val ad = withTimeoutOrNull(15_000) { load(placement.adUnitId) } ?: return@withContext false
            suspendCancellableCoroutine { cont ->
                var rewarded = false
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() { if (cont.isActive) cont.resume(rewarded) }
                    override fun onAdFailedToShowFullScreenContent(e: AdError) { if (cont.isActive) cont.resume(false) }
                }
                ad.show(activity) { rewarded = true }
            }
        }
    }

    private suspend fun load(unit: String): RewardedAd? = suspendCancellableCoroutine { cont ->
        RewardedAd.load(ctx, unit, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { if (cont.isActive) cont.resume(ad) }
            override fun onAdFailedToLoad(e: LoadAdError) {
                android.util.Log.w("Ads", "Rewarded no cargó (${e.code}): ${e.message}")
                if (cont.isActive) cont.resume(null)
            }
        })
    }
}
