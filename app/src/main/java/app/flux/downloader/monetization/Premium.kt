package app.flux.downloader.monetization

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Planes de Flux Premium. Los precios de referencia son los de la app; con Google Play Billing se mostrará el precio local. */
object PremiumPlans {
    const val MONTHLY_USD = 2.99
    const val ANNUAL_USD = 7.99

    /** IDs de producto que hay que crear en Google Play Console (suscripciones). */
    const val PRODUCT_MONTHLY = "flux_premium_monthly"
    const val PRODUCT_ANNUAL = "flux_premium_annual"

    /** Cuánto se ahorra pagando el plan anual frente a 12 meses sueltos (≈ 78 %). */
    val annualDiscountPercent: Int
        get() = Math.round((1.0 - ANNUAL_USD / (MONTHLY_USD * 12)) * 100).toInt()

    val annualPerMonthUsd: Double
        get() = ANNUAL_USD / 12
}

sealed interface PurchaseResult {
    data object Success : PurchaseResult
    data object Cancelled : PurchaseResult
    /** Todavía no hay una tienda conectada (modo de preparación). */
    data object NotAvailable : PurchaseResult
    data class Failed(val message: String) : PurchaseResult
}

/**
 * Punto de conexión con Google Play Billing. Mientras no haya cuenta de Google Play se usa un proveedor
 * vacío ([StubBilling]); ver ADMOB_Y_PREMIUM.md para conectar el real.
 */
interface BillingProvider {
    suspend fun purchase(activity: Activity?, productId: String): PurchaseResult
}

private object StubBilling : BillingProvider {
    override suspend fun purchase(activity: Activity?, productId: String): PurchaseResult = PurchaseResult.NotAvailable
}

object Billing {
    var provider: BillingProvider = StubBilling
}

/** Estado de Premium (se guarda en el teléfono). Google Play Billing llamará a [setPremium] al confirmar la compra. */
object PremiumState {
    private lateinit var app: Context
    private lateinit var sp: SharedPreferences

    private val _premium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _premium.asStateFlow()

    private val _whiteIcon = MutableStateFlow(false)
    val whiteIcon: StateFlow<Boolean> = _whiteIcon.asStateFlow()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        sp = app.getSharedPreferences("premium", Context.MODE_PRIVATE)
        _premium.value = sp.getBoolean("premium", false)
        _whiteIcon.value = runCatching { AppIcon.isWhite(app) }.getOrDefault(false)
        // Si ya no es Premium, el ícono vuelve al normal
        if (!_premium.value && _whiteIcon.value) setWhiteIcon(false)
    }

    fun setPremium(value: Boolean) {
        _premium.value = value
        sp.edit().putBoolean("premium", value).apply()
        if (!value && _whiteIcon.value) setWhiteIcon(false)
    }

    /** Cambia el ícono de la app entre el normal y la versión blanca (solo Premium). */
    fun setWhiteIcon(white: Boolean) {
        runCatching { AppIcon.setWhite(app, white) }
        _whiteIcon.value = white
    }
}
