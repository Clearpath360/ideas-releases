package uk.co.clearpath360.elevate

import android.app.Application
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Saves any crash to crash.txt so the next launch can show it. */
class ElevateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (x: Exception) { "?" }
                File(filesDir, "crash.txt").writeText(
                    "Elevate $ver, Android ${Build.VERSION.RELEASE} (${Build.MODEL}), thread ${t.name}\n$sw"
                )
            } catch (x: Exception) { }
            previous?.uncaughtException(t, e)
        }
    }

    companion object {
        fun takeCrash(app: Application): String? {
            val f = File(app.filesDir, "crash.txt")
            if (!f.exists()) return null
            val s = f.readText()
            f.delete()
            return s
        }
    }
}
