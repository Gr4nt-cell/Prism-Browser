package com.gr4nt.browser.browser

import android.Manifest
import android.content.Context
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What a website asked for, in plain terms. */
enum class SiteAccess(val label: String, val androidPermission: String?) {
    CAMERA("camera", Manifest.permission.CAMERA),
    MICROPHONE("microphone", Manifest.permission.RECORD_AUDIO),
    LOCATION("location", Manifest.permission.ACCESS_FINE_LOCATION),
    MIDI("MIDI devices", null),
    PROTECTED_MEDIA("protected media (DRM)", null)
}

/** A pending Safari-style "Allow “site” to use your camera?" prompt. */
class PermissionPrompt(
    val origin: String,
    val access: List<SiteAccess>,
    private val onDecision: (Boolean) -> Unit
) {
    fun decide(allow: Boolean) = onDecision(allow)
}

/**
 * Per-site camera / microphone / location decisions ("Ask", "Allow", "Deny"), like
 * Safari's Settings › Websites. Stored in SharedPreferences.
 */
object SitePermissions {
    enum class Decision { ASK, ALLOW, DENY }

    private lateinit var app: Context
    private val prefs by lazy { app.getSharedPreferences("site_permissions", Context.MODE_PRIVATE) }

    var prompt by mutableStateOf<PermissionPrompt?>(null)

    /** Set by MainActivity: request Android runtime permissions and report whether all were granted. */
    var requestRuntime: ((Array<String>, (Boolean) -> Unit) -> Unit)? = null

    fun init(ctx: Context) { app = ctx.applicationContext }

    private fun key(origin: String, a: SiteAccess) = "${hostKey(origin)}|${a.name}"
    private fun hostKey(origin: String) = runCatching { android.net.Uri.parse(origin).host ?: origin }.getOrDefault(origin)

    fun get(origin: String, a: SiteAccess): Decision =
        runCatching { Decision.valueOf(prefs.getString(key(origin, a), "ASK")!!) }.getOrDefault(Decision.ASK)

    fun set(origin: String, a: SiteAccess, d: Decision) {
        prefs.edit().putString(key(origin, a), d.name).apply()
    }

    /** All sites with a saved decision: host -> (access -> decision). */
    fun all(): Map<String, Map<SiteAccess, Decision>> {
        val out = sortedMapOf<String, MutableMap<SiteAccess, Decision>>()
        for ((k, v) in prefs.all) {
            val host = k.substringBefore('|')
            val a = runCatching { SiteAccess.valueOf(k.substringAfter('|')) }.getOrNull() ?: continue
            val d = runCatching { Decision.valueOf(v as String) }.getOrNull() ?: continue
            out.getOrPut(host) { mutableMapOf() }[a] = d
        }
        return out
    }

    fun clear(host: String) {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$host|") }.forEach { e.remove(it) }
        e.apply()
    }

    private fun mapResources(resources: Array<String>): List<SiteAccess> = resources.mapNotNull {
        when (it) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> SiteAccess.CAMERA
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> SiteAccess.MICROPHONE
            PermissionRequest.RESOURCE_MIDI_SYSEX -> SiteAccess.MIDI
            PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> SiteAccess.PROTECTED_MEDIA
            else -> null
        }
    }

    private fun resourceFor(a: SiteAccess) = when (a) {
        SiteAccess.CAMERA -> PermissionRequest.RESOURCE_VIDEO_CAPTURE
        SiteAccess.MICROPHONE -> PermissionRequest.RESOURCE_AUDIO_CAPTURE
        SiteAccess.MIDI -> PermissionRequest.RESOURCE_MIDI_SYSEX
        SiteAccess.PROTECTED_MEDIA -> PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
        SiteAccess.LOCATION -> null
    }

    /** Handle getUserMedia() and friends from a web page. */
    fun handleWebRequest(request: PermissionRequest, privateTab: Boolean) {
        val origin = request.origin.toString()
        val wanted = mapResources(request.resources)
        if (wanted.isEmpty()) { request.deny(); return }
        // Saved "Allow" for everything asked (never remembered in private tabs)
        val saved = wanted.map { if (privateTab) Decision.ASK else get(origin, it) }
        if (saved.any { it == Decision.DENY }) { request.deny(); return }
        if (saved.all { it == Decision.ALLOW }) { grantWithRuntime(request, wanted); return }
        prompt = PermissionPrompt(origin, wanted) { allow ->
            prompt = null
            if (!privateTab) wanted.forEach { set(origin, it, if (allow) Decision.ALLOW else Decision.DENY) }
            if (allow) grantWithRuntime(request, wanted) else request.deny()
        }
    }

    private fun grantWithRuntime(request: PermissionRequest, wanted: List<SiteAccess>) {
        val perms = wanted.mapNotNull { it.androidPermission }.toTypedArray()
        val grant = { ok: Boolean ->
            val res = wanted.filter { ok || it.androidPermission == null }.mapNotNull { resourceFor(it) }.toTypedArray()
            if (res.isEmpty()) request.deny() else request.grant(res)
        }
        val r = requestRuntime
        if (perms.isEmpty() || r == null) grant(perms.isEmpty()) else r(perms) { ok -> grant(ok) }
    }

    /** Handle navigator.geolocation from a web page. */
    fun handleGeolocation(origin: String, callback: GeolocationPermissions.Callback, privateTab: Boolean) {
        val saved = if (privateTab) Decision.ASK else get(origin, SiteAccess.LOCATION)
        val finish = { allow: Boolean ->
            if (!allow) callback.invoke(origin, false, false)
            else {
                val r = requestRuntime
                if (r == null) callback.invoke(origin, false, false)
                else r(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) { ok -> callback.invoke(origin, ok, false) }
            }
        }
        when (saved) {
            Decision.DENY -> callback.invoke(origin, false, false)
            Decision.ALLOW -> finish(true)
            Decision.ASK -> prompt = PermissionPrompt(origin, listOf(SiteAccess.LOCATION)) { allow ->
                prompt = null
                if (!privateTab) set(origin, SiteAccess.LOCATION, if (allow) Decision.ALLOW else Decision.DENY)
                finish(allow)
            }
        }
    }
}
