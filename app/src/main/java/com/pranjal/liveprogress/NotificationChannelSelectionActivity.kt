package com.pranjal.liveprogress

import android.app.Activity
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

class NotificationChannelSelectionActivity : Activity() {
    private companion object {
        const val CONTENT_PADDING_DP = 20
    }

    private data class UiPalette(
        val background: Int,
        val surface: Int,
        val surfaceContainer: Int,
        val surfaceContainerHigh: Int,
        val primary: Int,
        val onPrimary: Int,
        val outline: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val disabledContainer: Int,
        val ripple: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderContent()
    }

    override fun onResume() {
        super.onResume()
        renderContent()
    }

    private fun renderContent() {
        setContentView(buildContent())
    }

    private fun buildContent(): View {
        val colors = palette()
        val root = contentRoot()
        val title = TextView(this).apply {
            text = getString(R.string.notification_categories_title)
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colors.textPrimary)
            includeFontPadding = false
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20.dp(), 0, 0)
        }

        root.addView(title, blockParams(bottom = 18.dp()))
        root.addView(list)

        val categoryPreferences = NotificationCategoryPreferences(this)
        val categories = categoryPreferences.observedCategories()
        if (categories.isEmpty()) {
            list.addView(settingInfo(getString(R.string.setting_always_mirror_categories_empty)))
        } else {
            categories.groupBy { it.appLabel }.forEach { (appLabel, appCategories) ->
                list.addView(settingGroupLabel(appLabel))
                appCategories.forEach { category ->
                    list.addView(
                        settingToggle(
                            label = category.displayName.ifBlank {
                                getString(R.string.setting_always_mirror_categories_unknown)
                            },
                            checked = categoryPreferences.isSelected(category.key)
                        ) { selected ->
                            categoryPreferences.setSelected(category.key, selected)
                            AppDiagnostics.note(
                                this,
                                "mirror",
                                getString(R.string.diagnostic_progress_category_selection_changed)
                            )
                            ProgressPreferenceEvents.notifyChanged()
                            renderContent()
                        }
                    )
                }
            }
        }

        return scrollContent(root)
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

    private fun contentRoot(): LinearLayout {
        val colors = palette()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.background)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            applySystemBarPadding(this)
        }
    }

    private fun scrollContent(root: LinearLayout): ScrollView {
        return ScrollView(this).apply {
            setBackgroundColor(palette().background)
            clipToPadding = false
            addView(
                root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun settingRow(): LinearLayout {
        val colors = palette()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 68.dp()
            setPadding(18.dp(), 12.dp(), 14.dp(), 12.dp())
            background = rowBackground(colors)
            isClickable = true
            isFocusable = true
            layoutParams = blockParams(bottom = 10.dp())
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

    private fun rowBackground(colors: UiPalette): Drawable {
        return RippleDrawable(
            ColorStateList.valueOf(colors.ripple),
            rounded(colors.surfaceContainer, 26.dp(), colors.outline, 1.dp()),
            rounded(colors.surfaceContainer, 26.dp())
        )
    }

    private fun rounded(
        color: Int,
        radius: Int,
        strokeColor: Int? = null,
        strokeWidth: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
            if (strokeColor != null && strokeWidth > 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }
    }

    private fun settingGroupLabel(text: String): TextView {
        val colors = palette()
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colors.textSecondary)
            includeFontPadding = false
            setPadding(4.dp(), 6.dp(), 0, 8.dp())
        }
    }

    private fun settingInfo(text: String): TextView {
        val colors = palette()
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(colors.textSecondary)
            setLineSpacing(2.dp().toFloat(), 1f)
            setPadding(18.dp(), 14.dp(), 18.dp(), 14.dp())
            background = rounded(colors.surface, 26.dp(), colors.disabledContainer, 1.dp())
            layoutParams = blockParams(bottom = 10.dp())
        }
    }

    private fun settingToggle(
        label: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ): View {
        val colors = palette()
        val row = settingRow()
        val labelView = TextView(this).apply {
            text = label
            textSize = 16f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(colors.textPrimary)
            setLineSpacing(2.dp().toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        val switch = switchIndicator(checked, colors).apply {
            contentDescription = label
        }

        row.addView(labelView)
        row.addView(
            switch,
            LinearLayout.LayoutParams(
                58.dp(),
                36.dp()
            ).apply {
                marginStart = 16.dp()
            }
        )
        row.setOnClickListener {
            onChanged(!checked)
        }
        return row
    }

    private fun switchIndicator(
        checked: Boolean,
        colors: UiPalette
    ): FrameLayout {
        val trackColor = if (checked) {
            colors.primary
        } else {
            blend(colors.textSecondary, colors.surfaceContainerHigh, 0.40f)
        }
        val trackStroke = if (checked) null else colors.outline
        val thumbColor = if (checked) colors.onPrimary else colors.surface
        return FrameLayout(this).apply {
            background = rounded(trackColor, 18.dp(), trackStroke, 1.dp())
            setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
            addView(
                View(this@NotificationChannelSelectionActivity).apply {
                    background = rounded(thumbColor, 14.dp())
                    elevation = 2.dp().toFloat()
                },
                FrameLayout.LayoutParams(28.dp(), 28.dp()).apply {
                    gravity = if (checked) {
                        Gravity.CENTER_VERTICAL or Gravity.END
                    } else {
                        Gravity.CENTER_VERTICAL or Gravity.START
                    }
                }
            )
        }
    }

    private fun palette(): UiPalette {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            UiPalette(
                background = systemColor(android.R.color.system_neutral1_900),
                surface = systemColor(android.R.color.system_neutral1_900),
                surfaceContainer = systemColor(android.R.color.system_neutral1_800),
                surfaceContainerHigh = systemColor(android.R.color.system_neutral1_700),
                primary = systemColor(android.R.color.system_accent1_200),
                onPrimary = systemColor(android.R.color.system_accent1_900),
                outline = systemColor(android.R.color.system_neutral2_700),
                textPrimary = systemColor(android.R.color.system_neutral1_100),
                textSecondary = systemColor(android.R.color.system_neutral2_200),
                disabledContainer = systemColor(android.R.color.system_neutral1_800),
                ripple = withAlpha(systemColor(android.R.color.system_neutral1_100), 28)
            )
        } else {
            UiPalette(
                background = systemColor(android.R.color.system_neutral1_10),
                surface = systemColor(android.R.color.system_neutral1_10),
                surfaceContainer = systemColor(android.R.color.system_neutral1_50),
                surfaceContainerHigh = systemColor(android.R.color.system_neutral1_100),
                primary = systemColor(android.R.color.system_accent1_600),
                onPrimary = systemColor(android.R.color.system_neutral1_10),
                outline = systemColor(android.R.color.system_neutral2_200),
                textPrimary = systemColor(android.R.color.system_neutral1_900),
                textSecondary = systemColor(android.R.color.system_neutral2_700),
                disabledContainer = systemColor(android.R.color.system_neutral1_100),
                ripple = withAlpha(systemColor(android.R.color.system_accent1_600), 34)
            )
        }
    }

    private fun systemColor(colorRes: Int): Int {
        return getColor(colorRes)
    }

    private fun blend(
        foreground: Int,
        background: Int,
        ratio: Float
    ): Int {
        val clamped = ratio.coerceIn(0f, 1f)
        val inverse = 1f - clamped
        return Color.rgb(
            (Color.red(foreground) * clamped + Color.red(background) * inverse).roundToInt(),
            (Color.green(foreground) * clamped + Color.green(background) * inverse).roundToInt(),
            (Color.blue(foreground) * clamped + Color.blue(background) * inverse).roundToInt()
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    private fun Int.dp(): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }
}
