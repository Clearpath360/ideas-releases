package uk.co.clearpath360.elevate

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast

/** Invisible screen used by the widget, the quick-settings tile and the Share menu. */
class CaptureActivity : Activity() {
    private var captured = ""
    private var saved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) { captured = savedInstanceState.getString("captured", "") }
        if (intent?.action == Intent.ACTION_SEND) { handleShare(); return }
        if (savedInstanceState != null) return
        if (intent?.getStringExtra(EXTRA_MODE) == "type") typeDialog("") else listen()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("captured", captured)
    }

    private fun handleShare() {
        val text = listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
            .filter { it.isNotBlank() }.distinct().joinToString("\n")
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        val img = if (uri != null && (intent.type ?: "").startsWith("image/")) Store.saveImage(this, uri) else ""
        if (text.isBlank() && img.isBlank()) { finish(); return }
        Store.addInbox(this, text, "shared", img)
        Toast.makeText(this, "Saved to Elevate", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun listen() {
        try {
            startActivityForResult(voiceIntent(if (captured.isEmpty()) "Say your idea" else "Keep going"), REQ)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Voice typing is not available, type it instead", Toast.LENGTH_LONG).show()
            typeDialog(captured)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ) return
        val text = if (resultCode == RESULT_OK) data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: "" else ""
        if (text.isBlank()) {
            if (captured.isBlank()) finish() else confirm()
            return
        }
        captured = if (captured.isBlank()) text.trim() else captured + " " + text.trim()
        confirm()
    }

    private fun confirm() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("Idea captured")
            .setMessage(captured)
            .setPositiveButton("Save") { _, _ -> saveAndClose(captured) }
            .setNeutralButton("Keep talking") { _, _ -> listen() }
            .setNegativeButton("Edit") { _, _ -> typeDialog(captured) }
            .setOnCancelListener { saveAndClose(captured) }
            .show()
    }

    private fun typeDialog(pre: String) {
        val box = EditText(this).apply {
            setText(pre)
            hint = "Type your idea"
            minLines = 3
            setSelection(pre.length)
            setPadding(48, 32, 48, 32)
        }
        val d = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle(if (pre.isEmpty()) "New idea" else "Edit idea")
            .setView(box)
            .setPositiveButton("Save") { _, _ -> saveAndClose(box.text.toString()) }
            .setNegativeButton("Cancel") { _, _ -> if (pre.isNotBlank()) saveAndClose(pre) else finish() }
            .setOnCancelListener { if (pre.isNotBlank()) saveAndClose(pre) else finish() }
            .create()
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        d.show()
        box.requestFocus()
    }

    private fun saveAndClose(text: String) {
        if (saved) return
        saved = true
        if (text.isNotBlank()) {
            Store.addInbox(this, text.trim(), if (intent?.getStringExtra(EXTRA_MODE) == "type") "typed" else "voice")
            Toast.makeText(this, "Idea saved", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val REQ = 21
        fun voiceIntent(prompt: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }
    }
}
