package app.flux.downloader.monetization

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Ícono de la app: dos "alias" del manifiesto (LauncherDefault y LauncherWhite); solo uno está activo.
 * Algunos launchers tardan unos segundos en refrescar el ícono.
 */
object AppIcon {
    private const val DEFAULT = "app.flux.downloader.LauncherDefault"
    private const val WHITE = "app.flux.downloader.LauncherWhite"

    fun isWhite(ctx: Context): Boolean =
        ctx.packageManager.getComponentEnabledSetting(ComponentName(ctx.packageName, WHITE)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun setWhite(ctx: Context, white: Boolean) {
        val pm = ctx.packageManager
        val on = ComponentName(ctx.packageName, if (white) WHITE else DEFAULT)
        val off = ComponentName(ctx.packageName, if (white) DEFAULT else WHITE)
        pm.setComponentEnabledSetting(on, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(off, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
}
