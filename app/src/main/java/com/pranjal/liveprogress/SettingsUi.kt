package com.pranjal.liveprogress

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/** Shared settings components, matching the original homepage and category screen. */
class SettingsUi(private val context: Context) {
    private val CONTENT_PADDING_DP = 20
    enum class ButtonStyle { Filled, Tonal }
    data class Palette(
        val background: Int,
        val surface: Int,
        val surfaceContainer: Int,
        val surfaceContainerHigh: Int,
        val primary: Int,
        val primaryPressed: Int,
        val onPrimary: Int,
        val secondaryContainer: Int,
        val onSecondaryContainer: Int,
        val outline: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val textDisabled: Int,
        val disabledContainer: Int,
        val ripple: Int
    )

    fun button(
        label: String,
        enabled: Boolean,
        style: ButtonStyle = ButtonStyle.Filled,
        action: () -> Unit
    ): Button {
        val colors = palette()
        return Button(context).apply {
            text = label
            isEnabled = enabled
            isAllCaps = false
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            minHeight = 56.dp()
            minimumHeight = 56.dp()
            minWidth = 0
            minimumWidth = 0
            maxLines = 2
            gravity = Gravity.CENTER
            setTextColor(buttonTextColors(style, colors))
            setPadding(20.dp(), 8.dp(), 20.dp(), 8.dp())
            background = buttonBackground(style, enabled, colors)
            setOnClickListener { action() }
        }
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

    fun contentRoot(): LinearLayout {
        val colors = palette()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.background)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            applySystemBarPadding(this)
        }
    }

    fun scrollContent(root: LinearLayout): ScrollView {
        return ScrollView(context).apply {
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

    private fun settingRow(enabled: Boolean): LinearLayout {
        val colors = palette()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 68.dp()
            setPadding(18.dp(), 12.dp(), 14.dp(), 12.dp())
            background = rowBackground(enabled, colors)
            isClickable = enabled
            isFocusable = enabled
            layoutParams = blockParams(bottom = 10.dp())
        }
    }

    fun blockParams(
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

    private fun buttonTextColors(
        style: ButtonStyle,
        colors: Palette
    ): ColorStateList {
        val normal = when (style) {
            ButtonStyle.Filled -> colors.onPrimary
            ButtonStyle.Tonal -> colors.onSecondaryContainer
        }
        return ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf()
            ),
            intArrayOf(colors.textDisabled, normal)
        )
    }

    private fun buttonBackground(
        style: ButtonStyle,
        enabled: Boolean,
        colors: Palette
    ): Drawable {
        if (!enabled) {
            return rounded(colors.disabledContainer, 28.dp())
        }
        val normal = when (style) {
            ButtonStyle.Filled -> colors.primary
            ButtonStyle.Tonal -> colors.secondaryContainer
        }
        val pressed = when (style) {
            ButtonStyle.Filled -> colors.primaryPressed
            ButtonStyle.Tonal -> blend(colors.primary, colors.secondaryContainer, 0.10f)
        }
        return RippleDrawable(
            ColorStateList.valueOf(colors.ripple),
            rounded(normal, 28.dp()),
            rounded(pressed, 28.dp())
        )
    }

    private fun rowBackground(
        enabled: Boolean,
        colors: Palette
    ): Drawable {
        val surface = if (enabled) colors.surfaceContainer else colors.surface
        if (!enabled) {
            return rounded(surface, 26.dp(), colors.disabledContainer, 1.dp())
        }
        return RippleDrawable(
            ColorStateList.valueOf(colors.ripple),
            rounded(surface, 26.dp(), colors.outline, 1.dp()),
            rounded(surface, 26.dp())
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

    fun palette(): Palette {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            Palette(
                background = Color.BLACK,
                surface = Color.BLACK,
                surfaceContainer = systemColor(android.R.color.system_neutral1_800),
                surfaceContainerHigh = systemColor(android.R.color.system_neutral1_700),
                primary = systemColor(android.R.color.system_accent1_200),
                primaryPressed = systemColor(android.R.color.system_accent1_100),
                onPrimary = systemColor(android.R.color.system_accent1_900),
                secondaryContainer = systemColor(android.R.color.system_accent2_800),
                onSecondaryContainer = systemColor(android.R.color.system_accent2_100),
                outline = systemColor(android.R.color.system_neutral2_700),
                textPrimary = systemColor(android.R.color.system_neutral1_100),
                textSecondary = systemColor(android.R.color.system_neutral2_200),
                textDisabled = systemColor(android.R.color.system_neutral2_500),
                disabledContainer = systemColor(android.R.color.system_neutral1_800),
                ripple = withAlpha(systemColor(android.R.color.system_neutral1_100), 28)
            )
        } else {
            Palette(
                background = systemColor(android.R.color.system_neutral1_10),
                surface = systemColor(android.R.color.system_neutral1_10),
                surfaceContainer = systemColor(android.R.color.system_neutral1_50),
                surfaceContainerHigh = systemColor(android.R.color.system_neutral1_100),
                primary = systemColor(android.R.color.system_accent1_600),
                primaryPressed = systemColor(android.R.color.system_accent1_700),
                onPrimary = systemColor(android.R.color.system_neutral1_10),
                secondaryContainer = systemColor(android.R.color.system_accent2_100),
                onSecondaryContainer = systemColor(android.R.color.system_accent2_900),
                outline = systemColor(android.R.color.system_neutral2_200),
                textPrimary = systemColor(android.R.color.system_neutral1_900),
                textSecondary = systemColor(android.R.color.system_neutral2_700),
                textDisabled = systemColor(android.R.color.system_neutral2_400),
                disabledContainer = systemColor(android.R.color.system_neutral1_100),
                ripple = withAlpha(systemColor(android.R.color.system_accent1_600), 34)
            )
        }
    }

    private fun systemColor(colorRes: Int): Int {
        return context.getColor(colorRes)
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

    fun sectionTitle(text: String): TextView {
        val colors = palette()
        return TextView(context).apply {
            this.text = text
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colors.primary)
            includeFontPadding = false
            setPadding(4.dp(), 24.dp(), 0, 10.dp())
        }
    }

    fun settingToggle(
        label: String,
        checked: Boolean,
        enabled: Boolean = true,
        onInfo: (() -> Unit)? = null,
        onChanged: (Boolean) -> Unit
    ): View {
        val colors = palette()
        val row = settingRow(true)
        val labelView = TextView(context).apply {
            text = label
            textSize = 16f
            setTextColor(colors.textPrimary)
            setLineSpacing(2.dp().toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        var selected = checked
        var switch = switchIndicator(selected, enabled, colors).apply {
            contentDescription = label
        }

        row.addView(labelView)
        onInfo?.let { row.addView(infoButton(label, it)) }
        row.addView(
            switch,
            LinearLayout.LayoutParams(
                58.dp(),
                36.dp()
            ).apply {
                marginStart = 16.dp()
            }
        )
        row.isEnabled = true
        row.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        row.contentDescription = label
        labelView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        switch.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        row.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Switch"
                info.isCheckable = true
                info.isChecked = selected
                info.isEnabled = enabled
            }
        }
        row.setOnClickListener {
            if (enabled) {
                selected = !selected
                val params = switch.layoutParams
                row.removeView(switch)
                switch = switchIndicator(selected, enabled, colors).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                row.addView(switch, params)
                onChanged(selected)
            }
        }
        return row
    }

    fun infoButton(label: String, action: () -> Unit): View = TextView(context).apply {
        text = "ⓘ"
        textSize = 22f
        gravity = Gravity.CENTER
        setTextColor(palette().textSecondary)
        contentDescription = context.getString(R.string.setting_prerequisites, label)
        layoutParams = LinearLayout.LayoutParams(48.dp(), 48.dp())
        isFocusable = true
        background = RippleDrawable(ColorStateList.valueOf(palette().ripple), null,
            rounded(Color.WHITE, 24.dp()))
        setOnClickListener { action() }
    }

    fun switchIndicator(
        checked: Boolean,
        enabled: Boolean,
        colors: Palette
    ): FrameLayout {
        val trackColor = when {
            !enabled -> colors.disabledContainer
            checked -> colors.primary
            else -> blend(colors.textSecondary, colors.surfaceContainerHigh, 0.40f)
        }
        val trackStroke = when {
            checked -> null
            enabled -> colors.outline
            else -> colors.textDisabled
        }
        val thumbColor = when {
            !enabled -> colors.textDisabled
            checked -> colors.onPrimary
            else -> colors.surface
        }
        return FrameLayout(context).apply {
            isEnabled = enabled
            background = rounded(trackColor, 18.dp(), trackStroke, 1.dp())
            setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
            addView(
                View(context).apply {
                    background = rounded(thumbColor, 14.dp())
                    elevation = if (enabled) 2.dp().toFloat() else 0f
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
    fun title(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 34f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(palette().textPrimary)
        includeFontPadding = false
        layoutParams = blockParams(bottom = 18.dp())
    }

    fun info(text: String): TextView = TextView(context).apply {
        val colors = palette()
        this.text = text
        textSize = 14f
        setTextColor(colors.textSecondary)
        setLineSpacing(2.dp().toFloat(), 1f)
        setPadding(18.dp(), 14.dp(), 18.dp(), 14.dp())
        background = rounded(colors.surface, 26.dp(), colors.disabledContainer, 1.dp())
        layoutParams = blockParams(bottom = 10.dp())
    }

    fun Int.dp(): Int = (this * context.resources.displayMetrics.density).roundToInt()
}
