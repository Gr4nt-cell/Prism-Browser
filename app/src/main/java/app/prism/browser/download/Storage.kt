package app.prism.browser.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/** Moves finished downloads into the public Downloads/Prism folder. */
object Storage {

    fun mimeFor(name: String, fallback: String?): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: fallback?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: "application/octet-stream"
    }

    /** @return a content:// or file:// string pointing at the published file */
    fun publish(context: Context, src: File, name: String, mime: String?): String {
        val type = mimeFor(name, mime)
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, type)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Prism")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)!!.use { out -> src.inputStream().use { it.copyTo(out, 1 shl 20) } }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    return uri.toString()
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                }
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Prism")
                dir.mkdirs()
                val dest = unique(dir, name)
                if (!src.renameTo(dest)) src.copyTo(dest, overwrite = true)
                return Uri.fromFile(dest).toString()
            } catch (_: Exception) {
            }
        }
        // fallback: keep inside the app's own folder
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "")
        dir.mkdirs()
        val dest = unique(dir, name)
        if (!src.renameTo(dest)) src.copyTo(dest, overwrite = true)
        return Uri.fromFile(dest).toString()
    }

    private fun unique(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
        var i = 1
        while (f.exists()) f = File(dir, "$base ($i)$ext").also { i++ }
        return f
    }

    fun contentUri(context: Context, item: DownloadItem): Uri? {
        val s = item.outputUri ?: return null
        val uri = Uri.parse(s)
        return if (uri.scheme == "file") {
            runCatching { FileProvider.getUriForFile(context, context.packageName + ".files", File(uri.path!!)) }.getOrNull()
                ?: uri
        } else uri
    }

    fun open(context: Context, item: DownloadItem): Boolean {
        val uri = contentUri(context, item) ?: return false
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeFor(item.fileName, item.mime))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(Intent.createChooser(i, item.fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }

    fun share(context: Context, item: DownloadItem) {
        val uri = contentUri(context, item) ?: return
        val i = Intent(Intent.ACTION_SEND).setType(mimeFor(item.fileName, item.mime))
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(i, item.fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun delete(context: Context, item: DownloadItem) {
        val s = item.outputUri ?: return
        val uri = Uri.parse(s)
        runCatching {
            if (uri.scheme == "file") File(uri.path!!).delete() else context.contentResolver.delete(uri, null, null)
        }
    }
}
