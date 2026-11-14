package app.prism.browser.download

import app.prism.browser.core.Obs
import app.prism.browser.core.ObsList
import kotlinx.coroutines.Job
import org.json.JSONArray
import org.json.JSONObject

enum class DStatus { QUEUED, RUNNING, PAUSED, DONE, FAILED, CANCELED }
enum class DKind { HTTP, HLS }

/** A byte range [start, end] (inclusive). [pos] is the next byte to write. end == -1 means unknown length. */
class Segment(val start: Long, @Volatile var end: Long, @Volatile var pos: Long) {
    val done get() = end >= 0 && pos > end
    val remaining get() = if (end < 0) Long.MAX_VALUE else end - pos + 1
    fun toJson() = JSONObject().put("s", start).put("e", end).put("p", pos)
    companion object {
        fun fromJson(o: JSONObject) = Segment(o.getLong("s"), o.getLong("e"), o.getLong("p"))
    }
}

class DownloadItem(
    val id: String,
    val url: String,
    fileName: String,
    val mime: String?,
    val kind: DKind,
    val headers: Map<String, String>,
    val createdAt: Long = System.currentTimeMillis(),
    val pageUrl: String? = null,
) {
    var fileName by Obs(fileName)
    var total by Obs(-1L)
    var downloaded by Obs(0L)
    var status by Obs(DStatus.QUEUED)
    var speed by Obs(0L)
    var error by Obs<String?>(null)
    var resumable by Obs(true)
    var activeThreads by Obs(0)
    var outputUri by Obs<String?>(null)
    var finishedAt by Obs(0L)

    // HLS progress
    var partsTotal by Obs(0)
    var partsDone by Obs(0)

    val segments = ArrayList<Segment>()
    @Volatile var job: Job? = null

    val progress: Float
        get() = when {
            status == DStatus.DONE -> 1f
            kind == DKind.HLS && partsTotal > 0 -> partsDone.toFloat() / partsTotal
            total > 0 -> (downloaded.toFloat() / total).coerceIn(0f, 1f)
            else -> -1f
        }

    val etaSeconds: Long
        get() {
            if (speed <= 0) return -1
            if (kind == DKind.HLS && partsDone > 0 && partsTotal > 0) {
                val est = downloaded * partsTotal / partsDone
                return (est - downloaded) / speed
            }
            return if (total > 0) (total - downloaded) / speed else -1
        }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("url", url).put("name", fileName).put("mime", mime ?: "")
        .put("kind", kind.name).put("created", createdAt).put("page", pageUrl ?: "")
        .put("headers", JSONObject(headers))
        .put("total", total).put("downloaded", downloaded)
        .put("status", (if (status == DStatus.RUNNING || status == DStatus.QUEUED) DStatus.PAUSED else status).name)
        .put("error", error ?: "").put("resumable", resumable)
        .put("out", outputUri ?: "").put("finished", finishedAt)
        .put("partsTotal", partsTotal).put("partsDone", partsDone)
        .put("segments", JSONArray().also { a -> synchronized(segments) { segments.forEach { a.put(it.toJson()) } } })

    companion object {
        fun fromJson(o: JSONObject): DownloadItem {
            val h = o.optJSONObject("headers") ?: JSONObject()
            val headers = h.keys().asSequence().associateWith { h.getString(it) }
            return DownloadItem(
                o.getString("id"), o.getString("url"), o.getString("name"),
                o.optString("mime").ifEmpty { null }, DKind.valueOf(o.getString("kind")),
                headers, o.optLong("created"), o.optString("page").ifEmpty { null },
            ).apply {
                total = o.optLong("total", -1)
                downloaded = o.optLong("downloaded")
                status = runCatching { DStatus.valueOf(o.getString("status")) }.getOrDefault(DStatus.PAUSED)
                error = o.optString("error").ifEmpty { null }
                resumable = o.optBoolean("resumable", true)
                outputUri = o.optString("out").ifEmpty { null }
                finishedAt = o.optLong("finished")
                partsTotal = o.optInt("partsTotal")
                partsDone = o.optInt("partsDone")
                val segs = o.optJSONArray("segments") ?: JSONArray()
                for (i in 0 until segs.length()) segments += Segment.fromJson(segs.getJSONObject(i))
            }
        }
    }
}
