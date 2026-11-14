package app.prism.browser

import android.app.Application
import app.prism.browser.adblock.AdBlocker
import app.prism.browser.data.Store
import app.prism.browser.download.DownloadService
import app.prism.browser.download.Downloader

class PrismApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        AdBlocker.init(this)
        Downloader.init(this)
        DownloadService.createChannels(this)
    }
}
