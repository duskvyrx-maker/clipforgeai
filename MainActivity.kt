package ai.clipforge

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.view.ViewGroup
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {
    private val purple = Color.rgb(157, 124, 255)
    private val bg = Color.rgb(10, 12, 19)
    private val panel = Color.rgb(21, 25, 38)
    private var videoUri: Uri? = null
    private var durationMs = 0L
    private val clips = mutableListOf<ClipRange>()
    private val models = mutableListOf<LocalModel>()
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var clipList: LinearLayout
    private val pickVideo = 1001
    private val pickModel = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg; window.navigationBarColor = bg
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        buildUi()
    }

    private fun text(s: String, size: Float = 14f, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 7, 0, 7)
        }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setTextColor(Color.WHITE)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(purple))
        setOnClickListener { action() }
    }

    private fun section(title: String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(18, 14, 18, 16)
            setBackgroundColor(panel)
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(14, 9, 14, 9)
        root.addView(box, lp)
        box.addView(text(title, 17f, Color.WHITE, true))
        return box
    }

    private fun buildUi() {
        root.setPadding(0, 12, 0, 28)
        root.addView(text("✦ CLIPFORGE AI", 23f, purple, true).apply { setPadding(20, 12, 20, 0) })
        root.addView(text("On-device video clipping studio · Android 10+", 13f, 0xffaab2c5.toInt()).apply { setPadding(20, 0, 20, 12) })
        val video = section("01  VIDEO SOURCE")
        video.addView(button("Choose video from device") { chooseVideo() })
        video.addView(text("Select a video you own or have permission to edit.", 12f, 0xffaab2c5.toInt()))
        val model = section("02  LOCAL MODEL MANAGER")
        model.addView(text("Import compatible model files. ONNX and TFLite files can be opened with bundled runtimes. GGUF and MediaPipe are catalogued for their dedicated adapters.", 12f, 0xffaab2c5.toInt()))
        model.addView(button("＋ Import model file") { chooseModel() })
        model.addView(button("Provider compatibility") { showProviders() })
        model.addView(text("No model is bundled. You choose and download models yourself.", 12f, 0xffaab2c5.toInt()))
        val edit = section("03  CLIP TIMELINE")
        edit.addView(text("Add candidate ranges by timestamp. AI highlight detection requires a compatible task-specific model pipeline; the provider manager validates model loading but does not guess model inputs.", 12f, 0xffaab2c5.toInt()))
        edit.addView(button("＋ Add clip range") { addClipDialog() })
        clipList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        edit.addView(clipList)
        val export = section("04  EXPORT")
        export.addView(button("Export selected clip as MP4") { exportSelected() })
        status = text("Ready. Choose a video to begin.", 12f, 0xffaab2c5.toInt())
        export.addView(status)
        val foot = text("LOCAL-FIRST BUILD · Video trimming remuxes existing streams without re-encoding. This preserves the source aspect ratio; vertical reframing and caption burn-in are not yet implemented.", 11f, 0xff7f899e.toInt())
        foot.setPadding(20, 18, 20, 12); root.addView(foot)
    }

    private fun chooseVideo() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "video/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        startActivityForResult(i, pickVideo)
    }
    private fun chooseModel() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        startActivityForResult(i, pickModel)
    }

    @Deprecated("Activity result API kept minimal for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == pickVideo) {
            videoUri = uri
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(this, uri)
                durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                status.text = "Video loaded · ${durationMs / 1000}s"
            } catch (e: Exception) { status.text = "Video selected, but metadata could not be read: ${e.message}" }
            finally { retriever.release() }
        } else if (requestCode == pickModel) {
            importModel(uri)
        }
    }

    private fun importModel(uri: Uri) {
        try {
            val name = queryName(uri)
            val ext = name.substringAfterLast('.', "").lowercase()
            val engine = Engine.values().firstOrNull { ext in it.extensions }
                ?: run { toast("Unsupported extension. Choose .onnx, .tflite, .gguf or .task"); return }
            val dir = File(filesDir, "models").apply { mkdirs() }
            val dest = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            contentResolver.openInputStream(uri)!!.use { input -> FileOutputStream(dest).use { output -> input.copyTo(output) } }
            val result = ProviderRegistry.forEngine(engine)?.let {
                try { it.validate(this, dest) } catch (e: Exception) { "Runtime validation failed: ${e.message}" }
            } ?: ProviderRegistry.supportText(engine)
            models.add(LocalModel(name, dest, engine, dest.length()))
            toast("$name imported")
            showModelReport(result)
        } catch (e: Exception) { toast("Import failed: ${e.message}") }
    }

    private fun queryName(uri: Uri): String {
        var name = "model_${System.currentTimeMillis()}"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ix = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (ix >= 0) name = c.getString(ix) ?: name
            }
        }
        return name
    }

    private fun showProviders() {
        val lines = Engine.values().joinToString("\n\n") { "${it.title}\n${ProviderRegistry.supportText(it)}" }
        android.app.AlertDialog.Builder(this).setTitle("AI provider catalog").setMessage(lines).setPositiveButton("OK", null).show()
    }
    private fun showModelReport(message: String) {
        android.app.AlertDialog.Builder(this).setTitle("Model import result").setMessage(message)
            .setPositiveButton("OK", null).show()
    }

    private fun addClipDialog() {
        if (videoUri == null || durationMs <= 0) { toast("Choose a video first"); return }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 8, 22, 0) }
        val start = EditText(this).apply { hint = "Start time in seconds"; inputType = 2 }
        val end = EditText(this).apply { hint = "End time in seconds"; inputType = 2 }
        val title = EditText(this).apply { hint = "Clip title"; setText("Highlight") }
        layout.addView(start); layout.addView(end); layout.addView(title)
        android.app.AlertDialog.Builder(this).setTitle("Add clip range").setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val a = start.text.toString().toLongOrNull() ?: -1
                val b = end.text.toString().toLongOrNull() ?: -1
                if (a >= 0 && b > a && b * 1000 <= durationMs) {
                    clips.add(ClipRange(a * 1000, b * 1000, title.text.toString()))
                    refreshClips()
                } else toast("Enter valid seconds within video duration")
            }.setNegativeButton("Cancel", null).show()
    }

    private var selectedClip = -1
    private fun refreshClips() {
        clipList.removeAllViews()
        clips.forEachIndexed { index, c ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(text("${c.title}: ${c.startMs / 1000}s–${c.endMs / 1000}s", 13f).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            row.addView(Button(this).apply {
                text = if (selectedClip == index) "Selected" else "Select"
                setOnClickListener { selectedClip = index; refreshClips() }
            })
            clipList.addView(row)
        }
    }

    private fun exportSelected() {
        val uri = videoUri
        if (uri == null || selectedClip !in clips.indices) { toast("Choose a video and select a clip"); return }
        status.text = "Exporting…"
        Thread {
            try {
                val out = VideoClipper.export(this, uri, clips[selectedClip])
                runOnUiThread {
                    status.text = "Export complete: ${out.name}\nSaved in app files: ${out.parent}"
                    toast("Clip exported")
                }
            } catch (e: Exception) { runOnUiThread { status.text = "Export failed: ${e.message}"; toast("Export failed") } }
        }.start()
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
