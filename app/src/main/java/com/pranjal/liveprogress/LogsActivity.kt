package com.pranjal.liveprogress

import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class LogsActivity : ComponentActivity() {
    private companion object {
        const val CONTENT_PADDING_DP = 20
        const val LOG_EXPORT_MIME_TYPE = "text/plain"
    }

    private data class UiPalette(
        val background: Int,
        val surfaceContainer: Int,
        val primary: Int,
        val onPrimary: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val textDisabled: Int,
        val disabledContainer: Int
    )

    private var pendingExportText: String? = null
    private val createLogDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument(LOG_EXPORT_MIME_TYPE)
    ) { uri ->
        val exportText = pendingExportText
        pendingExportText = null
        if (uri == null || exportText == null) return@registerForActivityResult
        val result = runCatching {
            writeExportText(uri, exportText)
        }
        Toast.makeText(
            this,
            if (result.isSuccess) {
                getString(R.string.logs_export_success)
            } else {
                getString(R.string.logs_export_failed)
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderContent()
    }

    override fun onStart() {
        super.onStart()
        AppUiLifecycleTracker.onActivityStarted()
    }

    override fun onResume() {
        super.onResume()
        AppDiagnostics.pruneExpired(this)
        renderContent()
    }

    override fun onStop() {
        AppUiLifecycleTracker.onActivityStopped(this)
        super.onStop()
    }

    private fun renderContent() {
        setContentView(content())
    }

    private fun content(): LinearLayout {
        val colors = palette()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.background)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            applySystemBarPadding(this)
        }
        root.addView(
            TextView(this).apply {
                text = getString(R.string.logs_title)
                textSize = 34f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colors.textPrimary)
                includeFontPadding = false
            },
            blockParams(bottom = 18.dp())
        )

        val entries = AppDiagnostics.entries(this)
        root.addView(
            exportButton(entries),
            blockParams(bottom = 14.dp())
        )
        root.addView(logContent(entries, colors), weightedParams())
        return root
    }

    private fun logContent(
        entries: List<DiagnosticLogEntry>,
        colors: UiPalette
    ): View {
        return FrameLayout(this).apply {
            if (entries.isEmpty()) {
                addView(
                    emptyState(colors),
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            } else {
                addView(
                    ListView(this@LogsActivity).apply {
                        adapter = LogLineAdapter(
                            lines = entries.map(DiagnosticLogStore::format),
                            colors = colors
                        )
                        divider = ColorDrawable(colors.background)
                        dividerHeight = 8.dp()
                        cacheColorHint = colors.background
                        setBackgroundColor(colors.background)
                        clipToPadding = false
                        setPadding(0, 0, 0, 0)
                    },
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        }
    }

    private fun emptyState(colors: UiPalette): TextView {
        return TextView(this).apply {
            text = getString(R.string.logs_empty)
            textSize = 15f
            setTextColor(colors.textSecondary)
            setLineSpacing(2.dp().toFloat(), 1f)
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            background = rounded(colors.surfaceContainer, 24.dp())
        }
    }

    private fun exportButton(entries: List<DiagnosticLogEntry>): Button {
        val colors = palette()
        return Button(this).apply {
            text = getString(R.string.logs_export_action)
            isEnabled = entries.isNotEmpty()
            isAllCaps = false
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            minHeight = 56.dp()
            minimumHeight = 56.dp()
            minWidth = 0
            minimumWidth = 0
            maxLines = 2
            setTextColor(if (entries.isNotEmpty()) colors.onPrimary else colors.textDisabled)
            setPadding(20.dp(), 8.dp(), 20.dp(), 8.dp())
            background = rounded(
                if (entries.isNotEmpty()) colors.primary else colors.disabledContainer,
                28.dp()
            )
            setOnClickListener {
                if (entries.isNotEmpty()) exportLogs(entries)
            }
        }
    }

    private fun exportLogs(entries: List<DiagnosticLogEntry>) {
        val exportText = DiagnosticLogStore.exportText(entries)
        if (exportText.isBlank()) {
            Toast.makeText(
                this,
                getString(R.string.logs_export_failed),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        pendingExportText = exportText
        val result = runCatching { createLogDocument.launch(exportFileName()) }
        if (result.isFailure) {
            pendingExportText = null
            Toast.makeText(
                this,
                getString(R.string.logs_export_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun writeExportText(uri: Uri, exportText: String) {
        contentResolver.openOutputStream(uri, "w")?.use { output ->
            output.write(exportText.toByteArray(Charsets.UTF_8))
        } ?: error("Output stream unavailable")
    }

    private fun exportFileName(): String {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        return "live-progress-logs-$timestamp.txt"
    }

    private fun applySystemBarPadding(view: View) {
        val contentPadding = CONTENT_PADDING_DP.dp()
        view.setPadding(
            contentPadding,
            contentPadding,
            contentPadding,
            contentPadding
        )
        view.setOnApplyWindowInsetsListener { target, insets ->
            val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
            target.setPadding(
                contentPadding + systemBars.left,
                contentPadding + systemBars.top,
                contentPadding + systemBars.right,
                contentPadding + systemBars.bottom
            )
            insets
        }
    }

    private fun blockParams(
        top: Int = 0,
        bottom: Int = 0
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, top, 0, bottom)
        }
    }

    private fun weightedParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    }

    private inner class LogLineAdapter(
        private val lines: List<String>,
        private val colors: UiPalette
    ) : BaseAdapter() {
        override fun getCount(): Int = lines.size

        override fun getItem(position: Int): String = lines[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup
        ): View {
            val textView = convertView as? TextView ?: createLogLineView(colors)
            textView.text = getItem(position)
            return textView
        }
    }

    private fun createLogLineView(colors: UiPalette): TextView {
        return TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(colors.textSecondary)
            setLineSpacing(2.dp().toFloat(), 1f)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 1
            background = rounded(colors.surfaceContainer, 18.dp())
        }
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun palette(): UiPalette {
        return if (resources.configuration.isNightModeActive) {
            UiPalette(
                background = getColor(android.R.color.system_neutral1_900),
                surfaceContainer = getColor(android.R.color.system_neutral1_800),
                primary = getColor(android.R.color.system_accent1_200),
                onPrimary = getColor(android.R.color.system_accent1_900),
                textPrimary = getColor(android.R.color.system_neutral1_100),
                textSecondary = getColor(android.R.color.system_neutral2_200),
                textDisabled = getColor(android.R.color.system_neutral2_500),
                disabledContainer = getColor(android.R.color.system_neutral1_800)
            )
        } else {
            UiPalette(
                background = getColor(android.R.color.system_neutral1_10),
                surfaceContainer = getColor(android.R.color.system_neutral1_50),
                primary = getColor(android.R.color.system_accent1_600),
                onPrimary = getColor(android.R.color.system_neutral1_10),
                textPrimary = getColor(android.R.color.system_neutral1_900),
                textSecondary = getColor(android.R.color.system_neutral2_700),
                textDisabled = getColor(android.R.color.system_neutral2_400),
                disabledContainer = getColor(android.R.color.system_neutral1_100)
            )
        }
    }

    private fun Int.dp(): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }
}
