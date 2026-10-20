package com.gr4nt.browser

import android.app.Application
import com.gr4nt.browser.adblock.AdBlocker
import com.gr4nt.browser.adblock.FilterLists
import com.gr4nt.browser.browser.Browser
import com.gr4nt.browser.data.Store
import com.gr4nt.browser.download.DownloadManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Browser.init(this)
        com.gr4nt.browser.browser.SitePermissions.init(this)
        DownloadManager.init(this)
        com.gr4nt.browser.player.Media.init(this)
        Thread {
            AdBlocker.instance.load(this)
            if (FilterLists.needsUpdate()) runCatching { FilterLists.update(this) }
        }.apply { priority = Thread.NORM_PRIORITY - 1 }.start()
    }
}
