package uk.co.clearpath360.elevate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Ideas live in data.json (owned by the app screens). Quick captures land in inbox.json
 *  and are merged into data.json the next time the app screens open. */
object Store {
    private val lock = Any()

    private fun dataFile(c: Context) = File(c.filesDir, "data.json")
    private fun inboxFile(c: Context) = File(c.filesDir, "inbox.json")
    private fun imgDir(c: Context) = File(c.filesDir, "img").apply { mkdirs() }

    fun load(c: Context): String = synchronized(lock) {
        val f = dataFile(c)
        if (f.exists()) f.readText() else ""
    }

    fun save(c: Context, s: String) = synchronized(lock) {
        val tmp = File(c.filesDir, "data.json.tmp")
        tmp.writeText(s)
        tmp.renameTo(dataFile(c))
    }

    fun addInbox(c: Context, text: String, src: String, img: String = "") = synchronized(lock) {
        val f = inboxFile(c)
        val arr = try { JSONArray(if (f.exists()) f.readText() else "[]") } catch (e: Exception) { JSONArray() }
        arr.put(JSONObject().put("t", System.currentTimeMillis()).put("text", text).put("src", src).put("img", img))
        f.writeText(arr.toString())
    }

    fun takeInbox(c: Context): String = synchronized(lock) {
        val f = inboxFile(c)
        if (!f.exists()) return@synchronized "[]"
        val s = f.readText()
        f.delete()
        s
    }

    /** Copies a shared picture in, shrunk to 1280px on the long side. Returns its file name or "". */
    fun saveImage(c: Context, uri: Uri): String {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= 1280) sample *= 2
            val bmp = c.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return ""
            val scale = 1280f / maxOf(bmp.width, bmp.height)
            val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            val name = UUID.randomUUID().toString() + ".jpg"
            File(imgDir(c), name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            name
        } catch (e: Exception) { "" }
    }

    fun imageDataUrl(c: Context, name: String): String {
        if (name.isBlank() || name.contains("/")) return ""
        val f = File(imgDir(c), name)
        if (!f.exists()) return ""
        return "data:image/jpeg;base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
    }
}
