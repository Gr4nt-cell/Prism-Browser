package com.ibrowser

import android.app.Application
import com.ibrowser.adblock.AdBlocker
import com.ibrowser.adblock.FilterLists
import com.ibrowser.browser.Browser
import com.ibrowser.data.Store
import com.ibrowser.download.DownloadManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Browser.init(this)
        com.ibrowser.browser.SitePermissions.init(this)
        DownloadManager.init(this)
        com.ibrowser.player.Media.init(this)
        Thread {
            AdBlocker.instance.load(this)
            if (FilterLists.needsUpdate()) runCatching { FilterLists.update(this) }
        }.apply { priority = Thread.NORM_PRIORITY - 1 }.start()
    }
}
