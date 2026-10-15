package com.ibrowser.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Lossless remux of MPEG-TS / fragmented MP4 / separate audio+video tracks into a single MP4,
 * using the platform MediaExtractor + MediaMuxer (no re-encoding).
 */
object Muxer {
    private class Track(
        val ex: MediaExtractor,
        val inIndex: Int,
        var outIndex: Int = -1,
        var offset: Long = Long.MIN_VALUE,
        var lastPts: Long = -1,
        var frameDur: Long = 20_000
    )

    fun remux(inputs: List<File>, out: File): Boolean {
        val tracks = ArrayList<Track>()
        var muxer: MediaMuxer? = null
        try {
            var haveVideo = false
            var haveAudio = false
            for (f in inputs) {
                val ex = MediaExtractor()
                ex.setDataSource(f.absolutePath)
                var used = false
                for (i in 0 until ex.trackCount) {
                    val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/") && !haveVideo) {
                        ex.selectTrack(i); tracks.add(Track(ex, i)); haveVideo = true; used = true
                    } else if (mime.startsWith("audio/") && !haveAudio) {
                        ex.selectTrack(i); tracks.add(Track(ex, i)); haveAudio = true; used = true
                    }
                }
                if (!used) ex.release()
            }
            if (tracks.isEmpty()) return false

            if (out.exists()) out.delete()
            val m = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            var maxInput = 2 * 1024 * 1024
            for (t in tracks) {
                val fmt = t.ex.getTrackFormat(t.inIndex)
                if (fmt.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) maxInput = maxOf(maxInput, fmt.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                if (fmt.getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") && fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    t.frameDur = 1024L * 1_000_000L / fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
                } else t.frameDur = 33_333
                t.outIndex = m.addTrack(fmt)
            }
            m.start()

            val buf = ByteBuffer.allocateDirect(maxOf(maxInput, 8 * 1024 * 1024))
            val info = MediaCodec.BufferInfo()

            val extractors = tracks.map { it.ex }.distinct()
            val exDone = HashSet<MediaExtractor>()
            // global start = smallest first timestamp across extractors
            val base = extractors.minOf { it.sampleTime.takeIf { s -> s >= 0 } ?: Long.MAX_VALUE }
                .let { if (it == Long.MAX_VALUE) 0 else it }
            tracks.forEach { it.offset = -base }

            while (true) {
                // interleave: take the extractor whose next sample is earliest
                val ex = extractors.filter { it !in exDone }.minByOrNull { it.sampleTime } ?: break
                val ti = ex.sampleTrackIndex
                if (ti < 0) { exDone.add(ex); continue }
                val t = tracks.firstOrNull { it.ex === ex && it.inIndex == ti }
                if (t == null) { ex.advance(); continue }
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size < 0) { exDone.add(ex); continue }
                var pts = ex.sampleTime + t.offset
                if (t.lastPts >= 0 && pts <= t.lastPts) {
                    // discontinuity (e.g. TS timestamp reset): shift the rest of this track forward
                    val shift = t.lastPts + t.frameDur - pts
                    t.offset += shift
                    pts += shift
                }
                if (pts < 0) pts = 0
                info.offset = 0
                info.size = size
                info.presentationTimeUs = pts
                info.flags = if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                m.writeSampleData(t.outIndex, buf, info)
                t.lastPts = pts
                ex.advance()
            }
            m.stop()
            return out.length() > 0
        } catch (e: Throwable) {
            Log.w("Muxer", "remux failed", e)
            runCatching { muxer?.release() }
            muxer = null
            out.delete()
            return false
        } finally {
            runCatching { muxer?.release() }
            tracks.map { it.ex }.distinct().forEach { runCatching { it.release() } }
        }
    }
}
