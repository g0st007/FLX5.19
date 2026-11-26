package app.flux.downloader.monetization

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.TimeZone

/**
 * Casillas de la carpeta privada (una casilla = un video).
 *  - La función se desbloquea viendo un anuncio y trae [INITIAL_FREE] casillas.
 *  - Dentro de la carpeta se gana 1 casilla por anuncio, hasta [DAILY_AD_LIMIT] por día.
 *  - Premium: casillas ilimitadas y sin anuncios.
 */
object VaultSlots {
    const val INITIAL_FREE = 3
    const val DAILY_AD_LIMIT = 5

    data class State(val unlocked: Boolean, val extra: Int, val adsToday: Int)

    private lateinit var sp: SharedPreferences
    private val _state = MutableStateFlow(State(unlocked = false, extra = 0, adsToday = 0))
    val state: StateFlow<State> = _state.asStateFlow()

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences("vault_slots", Context.MODE_PRIVATE)
        load()
    }

    /** Número del día local (cambia a medianoche): reinicia el límite diario. */
    private fun today(): Long {
        val now = System.currentTimeMillis()
        return (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L
    }

    private fun load() {
        val adsToday = if (sp.getLong("day", 0L) == today()) sp.getInt("ads_today", 0) else 0
        _state.value = State(sp.getBoolean("unlocked", false), sp.getInt("extra", 0), adsToday)
    }

    /** Vuelve a leer el estado (por si pasó la medianoche con la app abierta). */
    fun refresh() = load()

    /** ¿Se puede entrar a la carpeta privada? (ya desbloqueada con un anuncio, o Premium) */
    val isUnlocked: Boolean
        get() = _state.value.unlocked || PremiumState.isPremium.value

    /** Casillas totales; null = ilimitadas (Premium). */
    fun capacity(): Int? = if (PremiumState.isPremium.value) null else INITIAL_FREE + _state.value.extra

    fun hasFreeSlot(used: Int): Boolean {
        val cap = capacity()
        return cap == null || used < cap
    }

    fun adsLeftToday(): Int = (DAILY_AD_LIMIT - _state.value.adsToday).coerceAtLeast(0)

    /** Desbloquea la función (tras ver el anuncio); incluye las [INITIAL_FREE] casillas de regalo. */
    fun unlock() {
        sp.edit().putBoolean("unlocked", true).apply()
        load()
    }

    /** Suma 1 casilla tras ver un anuncio. false si ya se llegó al límite de hoy. */
    fun grantSlot(): Boolean {
        load()
        if (adsLeftToday() <= 0) return false
        val cur = _state.value
        sp.edit()
            .putInt("extra", cur.extra + 1)
            .putLong("day", today())
            .putInt("ads_today", cur.adsToday + 1)
            .apply()
        load()
        return true
    }
}
