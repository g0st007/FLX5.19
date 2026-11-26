package app.flux.downloader

import android.app.Application
import app.flux.downloader.adblock.FilterManager
import app.flux.downloader.engine.DownloadManager
import app.flux.downloader.engine.Engine
import app.flux.downloader.monetization.PremiumState
import app.flux.downloader.monetization.VaultSlots
import app.flux.downloader.settings.AppSettings
import app.flux.downloader.vault.Vault

class FluxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        Vault.init(this)
        PremiumState.init(this)
        VaultSlots.init(this)
        DownloadManager.init(this)
        FilterManager.init(this)
        Engine.init(this)
    }
}
