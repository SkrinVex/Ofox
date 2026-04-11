package su.SkrinVex.ofox.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.yalantis.ucrop.UCrop
import ja.burhanrashid52.photoeditor.OnSaveBitmap
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoEditorView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class StickerEditorActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var photoEditorView: PhotoEditorView
    private lateinit var photoEditor: PhotoEditor

    companion object {
        const val EXTRA_IMAGE_URI = "image_uri"
        const val RESULT_STICKER_URI = "sticker_uri"

        fun createIntent(context: Context, imageUri: Uri): Intent =
            Intent(context, StickerEditorActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_URI, imageUri.toString())
            }
    }

    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            showEditor(UCrop.getOutput(result.data!!)!!)
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            setResult(Activity.RESULT_CANCELED); finish()
        } else {
            showEditor(Uri.parse(intent.getStringExtra(EXTRA_IMAGE_URI)))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.parseColor("#0D0D0D")
        window.navigationBarColor = android.graphics.Color.parseColor("#0D0D0D")

        val sourceUri = Uri.parse(intent.getStringExtra(EXTRA_IMAGE_URI) ?: run {
            setResult(Activity.RESULT_CANCELED); finish(); return
        })

        // Стилизованный диалог обрезки
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, su.SkrinVex.ofox.R.style.Theme_Ofox_Dialog)
            .setTitle("Обрезать изображение?")
            .setMessage("Хотите обрезать фото перед созданием стикера?")
            .setPositiveButton("Обрезать") { _, _ -> startCrop(sourceUri) }
            .setNegativeButton("Без обрезки") { _, _ -> showEditor(sourceUri) }
            .setOnCancelListener { setResult(Activity.RESULT_CANCELED); finish() }
            .create()
        dialog.show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(android.graphics.Color.parseColor("#FF6B35"))
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
            ?.setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
    }

    private fun startCrop(sourceUri: Uri) {
        val destFile = File(cacheDir, "crop_${System.currentTimeMillis()}.png")
        cropLauncher.launch(
            UCrop.of(sourceUri, Uri.fromFile(destFile))
                .withMaxResultSize(512, 512)
                .withOptions(UCrop.Options().apply {
                    setFreeStyleCropEnabled(true)
                    setStatusBarColor(android.graphics.Color.parseColor("#0D0D0D"))
                    setToolbarColor(android.graphics.Color.parseColor("#1A1A1A"))
                    setToolbarWidgetColor(android.graphics.Color.WHITE)
                    setActiveControlsWidgetColor(android.graphics.Color.parseColor("#FF6B35"))
                    setToolbarTitle("Обрезать")
                    setShowCropGrid(false)
                    setCircleDimmedLayer(false)
                    setCropFrameColor(android.graphics.Color.parseColor("#FF6B35"))
                    setCropFrameStrokeWidth(dpToPx(2))
                })
                .getIntent(this)
        )
    }

    private fun showEditor(imageUri: Uri) {
        val bg = android.graphics.Color.parseColor("#0D0D0D")
        val surface = android.graphics.Color.parseColor("#1C1C1E")
        val surfaceHigh = android.graphics.Color.parseColor("#2C2C2E")
        val accent = android.graphics.Color.parseColor("#FF6B35")
        val white = android.graphics.Color.WHITE
        val gray = android.graphics.Color.parseColor("#8E8E93")

        val root = android.widget.FrameLayout(this).apply {
            setBackgroundColor(bg)
        }

        photoEditorView = PhotoEditorView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { bottomMargin = dpToPx(88) }
        }

        // ── Нижний тулбар ────────────────────────────────────────────────────
        val toolbar = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(surface)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(88)
            ).apply { gravity = Gravity.BOTTOM }
            setPadding(dpToPx(12), dpToPx(8), dpToPx(88), dpToPx(8))
        }

        val scrollToolbar = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        val toolsRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        fun toolBtn(iconRes: Int, label: String, action: () -> Unit): android.widget.LinearLayout {
            val cornerPx = dpToPx(14).toFloat()
            return android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                layoutParams = android.widget.LinearLayout.LayoutParams(dpToPx(64), android.widget.LinearLayout.LayoutParams.MATCH_PARENT)
                    .apply { marginEnd = dpToPx(4) }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = cornerPx
                    setColor(surfaceHigh)
                }
                setOnClickListener { action() }
                addView(android.widget.ImageView(this@StickerEditorActivity).apply {
                    setImageResource(iconRes)
                    imageTintList = android.content.res.ColorStateList.valueOf(white)
                    layoutParams = android.widget.LinearLayout.LayoutParams(dpToPx(24), dpToPx(24))
                })
                addView(android.widget.TextView(this@StickerEditorActivity).apply {
                    text = label; textSize = 9f; gravity = Gravity.CENTER
                    setTextColor(gray)
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
            }
        }

        toolsRow.addView(toolBtn(android.R.drawable.ic_menu_revert, "Отмена") { photoEditor.undo() })
        toolsRow.addView(toolBtn(android.R.drawable.ic_menu_rotate, "Повтор") { photoEditor.redo() })
        toolsRow.addView(toolBtn(android.R.drawable.ic_menu_edit, "Рисовать") {
            val mode = photoEditor.brushDrawableMode != true
            photoEditor.setBrushDrawingMode(mode)
            if (mode) { photoEditor.brushColor = white; photoEditor.brushSize = 14f }
        })
        toolsRow.addView(toolBtn(android.R.drawable.ic_menu_add, "Текст") { showTextDialog(accent) })
        toolsRow.addView(toolBtn(android.R.drawable.ic_dialog_info, "Эмодзи") { showEmojiDialog() })
        toolsRow.addView(toolBtn(android.R.drawable.ic_menu_delete, "Очистить") { photoEditor.clearAllViews() })

        scrollToolbar.addView(toolsRow)
        toolbar.addView(scrollToolbar)

        // ── Кнопка сохранить ─────────────────────────────────────────────────
        val saveBtn = android.widget.TextView(this).apply {
            text = "✓"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(20).toFloat()
                setColor(accent)
            }
            layoutParams = android.widget.FrameLayout.LayoutParams(dpToPx(64), dpToPx(64)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                bottomMargin = dpToPx(12)
                rightMargin = dpToPx(12)
            }
            setOnClickListener { saveSticker() }
        }

        root.addView(photoEditorView)
        root.addView(toolbar)
        root.addView(saveBtn)
        setContentView(root)

        photoEditorView.source.setImageURI(imageUri)
        photoEditor = PhotoEditor.Builder(this, photoEditorView)
            .setPinchTextScalable(true)
            .build()
    }

    private fun showTextDialog(accent: Int) {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(8), dpToPx(20), dpToPx(16))
        }
        val input = android.widget.EditText(this).apply {
            hint = "Введите текст"
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.parseColor("#666666"))
            textSize = 16f
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setColor(android.graphics.Color.parseColor("#2C2C2E"))
            }
        }
        val colors = listOf("#FFFFFF","#FF6B35","#FF4444","#44FF88","#4488FF","#FFDD44","#FF44FF","#000000")
        val colorRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(14), 0, 0)
        }
        var selectedColor = android.graphics.Color.WHITE
        colors.forEach { hex ->
            val c = android.graphics.Color.parseColor(hex)
            val dot = android.view.View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply { marginEnd = dpToPx(8) }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(c)
                }
                setOnClickListener { selectedColor = c }
            }
            colorRow.addView(dot)
        }
        container.addView(input)
        container.addView(colorRow)

        androidx.appcompat.app.AlertDialog.Builder(this, su.SkrinVex.ofox.R.style.Theme_Ofox_Dialog)
            .setTitle("Добавить текст")
            .setView(container)
            .setPositiveButton("Добавить") { _, _ ->
                val text = input.text.toString()
                if (text.isNotBlank()) photoEditor.addText(text, selectedColor)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEmojiDialog() {
        val emojis = listOf(
            "😊","😂","❤️","🔥","👍","🎉","😎","🤔","💯","✨",
            "🙏","😍","🤣","😭","🥰","👀","💀","🫡","🤯","🥶",
            "🌟","🎯","💪","🤝","👑","🦊","🐱","🌈","⚡","🎸"
        )
        val grid = android.widget.GridView(this).apply {
            numColumns = 6
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, su.SkrinVex.ofox.R.style.Theme_Ofox_Dialog)
            .setView(grid).create()
        grid.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_list_item_1, emojis)
        grid.setOnItemClickListener { _, _, pos, _ ->
            photoEditor.addEmoji(emojis[pos])
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun saveSticker() {
        val file = File(cacheDir, "sticker_${System.currentTimeMillis()}.png")
        photoEditor.saveAsBitmap(object : OnSaveBitmap {
            override fun onBitmapReady(saveBitmap: Bitmap) {
                CoroutineScope(Dispatchers.IO).launch {
                    ByteArrayOutputStream().also {
                        saveBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        FileOutputStream(file).use { f -> f.write(it.toByteArray()) }
                    }
                    val uri = FileProvider.getUriForFile(this@StickerEditorActivity, "${packageName}.provider", file)
                    withContext(Dispatchers.Main) {
                        setResult(Activity.RESULT_OK, Intent().putExtra(RESULT_STICKER_URI, uri.toString()))
                        finish()
                    }
                }
            }
        })
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}
