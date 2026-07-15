package com.pranjal.liveprogress

import android.app.Activity
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class NotificationChannelSelectionActivity : Activity() {
    private companion object {
        const val CONTENT_PADDING_DP = 20
        const val ASSISTANT_BIND_DELAY_MS = 500L
        const val ASSISTANT_RETRY_DELAY_MS = 700L
        const val MAX_ASSISTANT_REFRESH_ATTEMPTS = 5
    }

    private var categoryList: ListView? = null
    private var pendingListPosition: Int? = null
    private var pendingListTop: Int = 0
    private var shizukuRefreshRunning = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshExecutor = Executors.newSingleThreadExecutor()

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
        val textDisabled: Int,
        val disabledContainer: Int,
        val ripple: Int
    )

    private data class AppCategoryGroup(
        val packageName: String,
        val uid: Int,
        val appLabel: String,
        val isSystemApp: Boolean
    )

    private data class CategoryRefreshResult(
        val changed: Boolean,
        val newCategoryCount: Int,
        val failedAppCount: Int
    )

    private sealed interface CategoryListItem {
        data class AppGroup(
            val group: AppCategoryGroup,
            val selectedCount: Int,
            val totalCount: Int
        ) : CategoryListItem

        data class Category(
            val category: ObservedNotificationCategory,
            val settings: NotificationCategorySettings
        ) : CategoryListItem

        data class Behavior(
            val category: ObservedNotificationCategory,
            val settings: NotificationCategorySettings
        ) : CategoryListItem
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
        renderContent()
    }

    override fun onStop() {
        AppUiLifecycleTracker.onActivityStopped(this)
        super.onStop()
    }

    override fun onDestroy() {
        refreshExecutor.shutdownNow()
        super.onDestroy()
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

        val categoryPreferences = NotificationCategoryPreferences(this)
        val includeSystemApps = categoryPreferences.showSystemApps
        val allCategoryCount = categoryPreferences.observedCategories().size
        val categories = categoryPreferences.observedCategories(
            includeSystemApps = includeSystemApps
        )
        val canSuppressOriginal = PrivilegedAccess.canUseOriginalNotificationSuppression(this)
        root.addView(title, blockParams(bottom = 18.dp()))
        root.addView(actionButtons(categories), blockParams(bottom = 10.dp()))
        root.addView(
            systemAppsToggle(
                checked = includeSystemApps,
                categoryPreferences = categoryPreferences
            ),
            blockParams(bottom = 10.dp())
        )
        if (categories.isEmpty()) {
            categoryList = null
            val emptyText = if (allCategoryCount > 0 && !includeSystemApps) {
                getString(R.string.setting_system_categories_hidden_empty)
            } else {
                getString(R.string.setting_always_mirror_categories_empty)
            }
            root.addView(settingInfo(emptyText))
        } else {
            root.addView(
                categoryListView(
                    items = categoryListItems(categories, categoryPreferences),
                    categoryPreferences = categoryPreferences,
                    canSuppressOriginal = canSuppressOriginal
                ),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }

        return root
    }

    private fun categoryListItems(
        categories: List<ObservedNotificationCategory>,
        categoryPreferences: NotificationCategoryPreferences
    ): List<CategoryListItem> {
        val items = mutableListOf<CategoryListItem>()
        categories.groupBy {
            AppCategoryGroup(
                packageName = it.key.packageName,
                uid = it.key.uid,
                appLabel = it.appLabel,
                isSystemApp = it.isSystemApp
            )
        }.forEach { (group, appCategories) ->
            val enabledCount = appCategories.count { categoryPreferences.isSelected(it.key) }
            items.add(
                CategoryListItem.AppGroup(
                    group = group,
                    selectedCount = enabledCount,
                    totalCount = appCategories.size
                )
            )
            appCategories.forEach { category ->
                val settings = categoryPreferences.settingsFor(category.key)
                items.add(CategoryListItem.Category(category, settings))
                if (settings.enabled) {
                    items.add(CategoryListItem.Behavior(category, settings))
                }
            }
        }
        return items
    }

    private fun categoryListView(
        items: List<CategoryListItem>,
        categoryPreferences: NotificationCategoryPreferences,
        canSuppressOriginal: Boolean
    ): ListView {
        return ListView(this).apply {
            setBackgroundColor(palette().background)
            clipToPadding = false
            divider = null
            dividerHeight = 0
            isFastScrollEnabled = false
            isFastScrollAlwaysVisible = false
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
            adapter = CategoryAdapter(
                items = items,
                categoryPreferences = categoryPreferences,
                canSuppressOriginal = canSuppressOriginal
            )
            categoryList = this
            restoreListPosition(this)
        }
    }

    private inner class CategoryAdapter(
        private val items: List<CategoryListItem>,
        private val categoryPreferences: NotificationCategoryPreferences,
        private val canSuppressOriginal: Boolean
    ) : BaseAdapter() {
        override fun getCount(): Int = items.size

        override fun getItem(position: Int): CategoryListItem = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getViewTypeCount(): Int = 3

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is CategoryListItem.AppGroup -> 0
                is CategoryListItem.Category -> 1
                is CategoryListItem.Behavior -> 2
            }
        }

        override fun isEnabled(position: Int): Boolean = false

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup?
        ): View {
            return when (val item = items[position]) {
                is CategoryListItem.AppGroup -> {
                    val allEnabled = item.selectedCount == item.totalCount
                    appGroupToggle(
                        label = item.group.appLabel,
                        selectedCount = item.selectedCount,
                        totalCount = item.totalCount,
                        checked = allEnabled
                    ) {
                        categoryPreferences.setAppEnabled(
                            packageName = item.group.packageName,
                            uid = item.group.uid,
                            enabled = !allEnabled
                        )
                        onCategoryPreferenceChanged()
                    }
                }

                is CategoryListItem.Category -> {
                    categoryToggle(
                        label = item.category.displayName.ifBlank {
                            getString(R.string.setting_always_mirror_categories_unknown)
                        },
                        checked = item.settings.enabled
                    ) { selected ->
                        categoryPreferences.setSelected(item.category.key, selected)
                        onCategoryPreferenceChanged()
                    }
                }

                is CategoryListItem.Behavior -> {
                    categoryBehaviorControls(
                        category = item.category,
                        settings = item.settings,
                        canSuppressOriginal = canSuppressOriginal,
                        categoryPreferences = categoryPreferences
                    )
                }
            }
        }
    }

    private fun categoryBehaviorControls(
        category: ObservedNotificationCategory,
        settings: NotificationCategorySettings,
        canSuppressOriginal: Boolean,
        categoryPreferences: NotificationCategoryPreferences
    ): View {
        val colors = palette()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
            background = rounded(colors.surface, 20.dp(), colors.outline, 1.dp())
        }
        val firstRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        firstRow.addView(
            compactToggle(
                label = getString(R.string.setting_category_lock_short),
                contentDescription = getString(R.string.setting_show_category_lock_screen),
                checked = settings.showOnLockScreen,
                enabled = true
            ) { selected ->
                categoryPreferences.updateSettings(category.key) {
                    it.copy(showOnLockScreen = selected)
                }
                onCategoryPreferenceChanged()
            },
            compactToggleParams()
        )
        firstRow.addView(
            compactToggle(
                label = getString(R.string.setting_category_aod_short),
                contentDescription = getString(R.string.setting_show_category_aod),
                checked = settings.showOnAod,
                enabled = true
            ) { selected ->
                categoryPreferences.updateSettings(category.key) {
                    it.copy(showOnAod = selected)
                }
                onCategoryPreferenceChanged()
            },
            compactToggleParams(start = 6.dp())
        )
        val secondRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        secondRow.addView(
            compactToggle(
                label = getString(R.string.setting_category_hide_original_short),
                contentDescription = getString(R.string.setting_hide_category_original),
                checked = settings.hideOriginalNotification && canSuppressOriginal,
                enabled = canSuppressOriginal
            ) { selected ->
                categoryPreferences.updateSettings(category.key) {
                    it.copy(hideOriginalNotification = selected)
                }
                onCategoryPreferenceChanged()
            },
            compactToggleParams()
        )
        secondRow.addView(
            compactToggle(
                label = getString(R.string.setting_category_keep_after_dismiss_short),
                contentDescription = getString(R.string.setting_keep_category_after_dismiss),
                checked = settings.keepAfterOriginalDismissed,
                enabled = true
            ) { selected ->
                categoryPreferences.updateSettings(category.key) {
                    it.copy(keepAfterOriginalDismissed = selected)
                }
                onCategoryPreferenceChanged()
            },
            compactToggleParams(start = 6.dp())
        )
        column.addView(
            firstRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        column.addView(
            secondRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 6.dp()
            }
        )
        return listItemContainer(column, bottom = 10.dp(), start = 34.dp())
    }

    private fun appGroupToggle(
        label: String,
        selectedCount: Int,
        totalCount: Int,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ): View {
        val colors = palette()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 72.dp()
            setPadding(16.dp(), 12.dp(), 14.dp(), 12.dp())
            background = RippleDrawable(
                ColorStateList.valueOf(colors.ripple),
                rounded(colors.surfaceContainerHigh, 26.dp(), colors.outline, 1.dp()),
                rounded(colors.surfaceContainerHigh, 26.dp())
            )
            isClickable = true
            isFocusable = true
        }
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textColumn.addView(
            TextView(this).apply {
                text = label
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(colors.textPrimary)
                includeFontPadding = false
            }
        )
        textColumn.addView(
            TextView(this).apply {
                text = getString(R.string.additional_app_selected_count, selectedCount, totalCount)
                textSize = 13f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(colors.textSecondary)
                includeFontPadding = false
                setPadding(0, 5.dp(), 0, 0)
            }
        )
        row.addView(textColumn)
        row.addView(
            switchIndicator(checked, true, colors).apply {
                contentDescription = label
            },
            LinearLayout.LayoutParams(58.dp(), 36.dp()).apply {
                marginStart = 16.dp()
            }
        )
        row.setOnClickListener { onChanged(!checked) }
        return listItemContainer(row, top = 10.dp(), bottom = 8.dp())
    }

    private fun categoryToggle(
        label: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ): View {
        val colors = palette()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 66.dp()
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
            background = RippleDrawable(
                ColorStateList.valueOf(colors.ripple),
                rounded(colors.surfaceContainer, 20.dp(), colors.outline, 1.dp()),
                rounded(colors.surfaceContainer, 20.dp())
            )
            isClickable = true
            isFocusable = true
        }
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textColumn.addView(
            TextView(this).apply {
                text = label
                textSize = 16f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(colors.textPrimary)
                includeFontPadding = false
                setLineSpacing(2.dp().toFloat(), 1f)
            }
        )
        row.addView(textColumn)
        row.addView(
            switchIndicator(checked, true, colors).apply {
                contentDescription = label
            },
            LinearLayout.LayoutParams(58.dp(), 36.dp()).apply {
                marginStart = 16.dp()
            }
        )
        row.setOnClickListener { onChanged(!checked) }
        return listItemContainer(row, bottom = 8.dp(), start = 20.dp())
    }

    private fun actionButtons(categories: List<ObservedNotificationCategory>): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            actionButton(getString(R.string.action_turn_all_on)) {
                NotificationCategoryPreferences(this)
                    .setObservedEnabled(categories.map { it.key }, true)
                onCategoryPreferenceChanged()
            },
            LinearLayout.LayoutParams(0, 52.dp(), 1f).apply {
                marginEnd = 6.dp()
            }
        )
        row.addView(
            actionButton(getString(R.string.action_turn_all_off)) {
                NotificationCategoryPreferences(this)
                    .setObservedEnabled(categories.map { it.key }, false)
                onCategoryPreferenceChanged()
            },
            LinearLayout.LayoutParams(0, 52.dp(), 1f).apply {
                marginStart = 6.dp()
            }
        )
        column.addView(row)
        column.addView(
            actionButton(getString(R.string.action_refresh_categories_shizuku)) {
                refreshCategoriesWithShizuku()
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                52.dp()
            ).apply {
                topMargin = 10.dp()
            }
        )
        return column
    }

    private fun systemAppsToggle(
        checked: Boolean,
        categoryPreferences: NotificationCategoryPreferences
    ): View {
        val colors = palette()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 66.dp()
            setPadding(16.dp(), 10.dp(), 14.dp(), 10.dp())
            background = RippleDrawable(
                ColorStateList.valueOf(colors.ripple),
                rounded(colors.surfaceContainer, 22.dp(), colors.outline, 1.dp()),
                rounded(colors.surfaceContainer, 22.dp())
            )
            isClickable = true
            isFocusable = true
        }
        row.addView(
            TextView(this).apply {
                text = getString(R.string.setting_show_system_apps)
                textSize = 16f
                setTextColor(colors.textPrimary)
                setLineSpacing(2.dp().toFloat(), 1f)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )
        row.addView(
            switchIndicator(checked, true, colors).apply {
                contentDescription = getString(R.string.setting_show_system_apps)
            },
            LinearLayout.LayoutParams(58.dp(), 36.dp()).apply {
                marginStart = 16.dp()
            }
        )
        row.setOnClickListener {
            categoryPreferences.showSystemApps = !checked
            captureListPosition()
            renderContent()
        }
        return row
    }

    private fun actionButton(
        label: String,
        onClick: () -> Unit
    ): Button {
        val colors = palette()
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            minWidth = 0
            minimumWidth = 0
            setTextColor(colors.onPrimary)
            background = RippleDrawable(
                ColorStateList.valueOf(colors.ripple),
                rounded(colors.primary, 26.dp()),
                rounded(colors.primary, 26.dp())
            )
            setOnClickListener { onClick() }
        }
    }

    private fun refreshCategoriesWithShizuku() {
        if (shizukuRefreshRunning) return
        val state = PrivilegedAccess.currentState(this)
        if (!state.shizukuAvailable) {
            showToast(getString(R.string.category_refresh_failed, "Shizuku is not running"))
            return
        }
        if (!state.shizukuGranted) {
            showToast(PrivilegedAccess.requestShizukuPermission())
            return
        }

        shizukuRefreshRunning = true
        showToast(getString(R.string.category_refresh_starting))
        AppDiagnostics.verbose(
            this,
            "mirror",
            "Notification category Shizuku refresh started; temporaryAssistant=${state.temporaryAssistantActive}"
        )
        PrivilegedAccess.listInstalledNotificationAppsAsync(this) { result ->
            val apps = result.getOrElse {
                finishCategoryRefresh(
                    message = getString(R.string.category_refresh_failed, it.describeForUser()),
                    hadTemporaryAssistant = true,
                    changed = false
                )
                return@listInstalledNotificationAppsAsync
            }
            if (apps.isEmpty()) {
                finishCategoryRefresh(
                    message = getString(R.string.category_refresh_no_apps),
                    hadTemporaryAssistant = true,
                    changed = false
                )
                return@listInstalledNotificationAppsAsync
            }
            AppDiagnostics.verbose(
                this,
                "mirror",
                "Notification category Shizuku refresh app list loaded; apps=${apps.size}"
            )
            ensureAssistantAndRefreshCategories(
                apps = apps,
                hadTemporaryAssistant = state.temporaryAssistantActive
            )
        }
    }

    private fun ensureAssistantAndRefreshCategories(
        apps: List<InstalledNotificationApp>,
        hadTemporaryAssistant: Boolean
    ) {
        PrivilegedAccess.ensureTemporaryAssistantAsync(
            this,
            "refresh notification categories with Shizuku"
        ) { ready, message ->
            if (!ready) {
                finishCategoryRefresh(
                    message = getString(R.string.category_refresh_failed, message),
                    hadTemporaryAssistant = hadTemporaryAssistant,
                    changed = false
                )
                return@ensureTemporaryAssistantAsync
            }
            refreshCategoriesWhenAssistantReady(
                apps = apps,
                hadTemporaryAssistant = hadTemporaryAssistant,
                attempt = 1
            )
        }
    }

    private fun refreshCategoriesWhenAssistantReady(
        apps: List<InstalledNotificationApp>,
        hadTemporaryAssistant: Boolean,
        attempt: Int
    ) {
        val delay = if (attempt == 1) ASSISTANT_BIND_DELAY_MS else ASSISTANT_RETRY_DELAY_MS
        AppDiagnostics.verbose(
            this,
            "mirror",
            "Waiting for notification assistant bridge during category refresh; attempt=$attempt; delayMs=$delay"
        )
        mainHandler.postDelayed(
            {
                if (!NotificationAssistantBridgeService.isConnected()) {
                    if (attempt < MAX_ASSISTANT_REFRESH_ATTEMPTS) {
                        refreshCategoriesWhenAssistantReady(
                            apps = apps,
                            hadTemporaryAssistant = hadTemporaryAssistant,
                            attempt = attempt + 1
                        )
                    } else {
                        finishCategoryRefresh(
                            message = getString(
                                R.string.category_refresh_failed,
                                "notification assistant bridge is not connected"
                            ),
                            hadTemporaryAssistant = hadTemporaryAssistant,
                            changed = false
                        )
                    }
                    return@postDelayed
                }

                refreshExecutor.execute {
                    val result = scanInstalledAppCategories(apps)
                    runOnUiThread {
                        val message = if (result.failedAppCount > 0) {
                            getString(
                                R.string.category_refresh_complete_with_failures,
                                result.newCategoryCount,
                                result.failedAppCount
                            )
                        } else {
                            getString(
                                R.string.category_refresh_complete,
                                result.newCategoryCount
                            )
                        }
                        finishCategoryRefresh(
                            message = message,
                            hadTemporaryAssistant = hadTemporaryAssistant,
                            changed = result.changed
                        )
                    }
                }
            },
            delay
        )
    }

    private fun scanInstalledAppCategories(apps: List<InstalledNotificationApp>): CategoryRefreshResult {
        val preferences = NotificationCategoryPreferences(this)
        val beforeKeys = preferences.observedCategories().map { it.key }.toSet()
        var changed = false
        var failedApps = 0
        val now = System.currentTimeMillis()
        apps.forEach { app ->
            if (app.packageName == packageName) return@forEach
            val user = UserHandle.getUserHandleForUid(app.uid)
            val channels = NotificationAssistantBridgeService.getSourceChannels(
                packageName = app.packageName,
                user = user
            ).getOrElse {
                failedApps += 1
                AppDiagnostics.note(
                    this,
                    "mirror",
                    getString(R.string.category_refresh_failed, it.describeForUser())
                )
                return@forEach
            }
            AppDiagnostics.verbose(
                this,
                "mirror",
                "Scanned notification categories for ${app.packageName}; count=${channels.size}; system=${app.isSystemApp}"
            )
            channels.forEach { channel ->
                val channelId = channel.id.takeIf { it.isNotBlank() } ?: return@forEach
                val channelName = channel.name?.toString()?.takeIf { it.isNotBlank() }
                changed = preferences.observe(
                    packageName = app.packageName,
                    uid = app.uid,
                    channelId = channelId,
                    appLabel = AppLabelResolver.label(
                        this,
                        app.packageName,
                        uid = app.uid,
                        sourceDir = app.sourceDir
                    ),
                    channelName = channelName,
                    isSystemApp = app.isSystemApp,
                    nowMillis = now
                ) || changed
            }
        }
        val afterKeys = preferences.observedCategories().map { it.key }.toSet()
        return CategoryRefreshResult(
            changed = changed,
            newCategoryCount = (afterKeys - beforeKeys).size,
            failedAppCount = failedApps
        )
    }

    private fun finishCategoryRefresh(
        message: String,
        hadTemporaryAssistant: Boolean,
        changed: Boolean
    ) {
        shizukuRefreshRunning = false
        if (!hadTemporaryAssistant) {
            PrivilegedAccess.releaseTemporaryAssistantAsync(
                this,
                "notification category Shizuku refresh finished"
            )
        }
        showToast(message)
        AppDiagnostics.note(
            this,
            "mirror",
            getString(R.string.diagnostic_category_shizuku_refresh)
        )
        AppDiagnostics.verbose(
            this,
            "mirror",
            "Notification category Shizuku refresh finished; changed=$changed; temporaryAssistantWasActive=$hadTemporaryAssistant"
        )
        if (changed) {
            captureListPosition()
            AdditionalNotificationPreferenceEvents.notifyChanged()
            renderContent()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun onCategoryPreferenceChanged() {
        captureListPosition()
        AppDiagnostics.note(
            this,
            "mirror",
            getString(R.string.diagnostic_progress_category_selection_changed)
        )
        AdditionalNotificationPreferenceEvents.notifyChanged()
        renderContent()
    }

    private fun captureListPosition() {
        val list = categoryList ?: return
        pendingListPosition = list.firstVisiblePosition
        pendingListTop = list.getChildAt(0)?.top ?: 0
    }

    private fun restoreListPosition(list: ListView) {
        val position = pendingListPosition ?: return
        val top = pendingListTop
        pendingListPosition = null
        pendingListTop = 0
        list.post {
            val lastPosition = (list.count - 1).coerceAtLeast(0)
            list.setSelectionFromTop(position.coerceIn(0, lastPosition), top)
        }
    }

    private fun Throwable.describeForUser(): String {
        return listOfNotNull(
            javaClass.simpleName,
            message?.takeIf { it.isNotBlank() }
        ).joinToString(": ")
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
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            applySystemBarPadding(this)
        }
    }

    private fun blockParams(
        top: Int = 0,
        bottom: Int = 0,
        start: Int = 0,
        end: Int = 0
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(start, top, end, bottom)
        }
    }

    private fun compactToggleParams(start: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, 48.dp(), 1f).apply {
            marginStart = start
        }
    }

    private fun listItemContainer(
        child: View,
        top: Int = 0,
        bottom: Int = 0,
        start: Int = 0,
        end: Int = 0
    ): FrameLayout {
        return FrameLayout(this).apply {
            setPadding(start, top, end, bottom)
            layoutParams = AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT,
                AbsListView.LayoutParams.WRAP_CONTENT
            )
            addView(
                child,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
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

    private fun compactToggle(
        label: String,
        contentDescription: String,
        checked: Boolean,
        enabled: Boolean,
        onChanged: (Boolean) -> Unit
    ): TextView {
        val colors = palette()
        val fillColor = when {
            !enabled -> colors.disabledContainer
            checked -> colors.primary
            else -> colors.surfaceContainerHigh
        }
        val textColor = when {
            !enabled -> colors.textSecondary
            checked -> colors.onPrimary
            else -> colors.textPrimary
        }
        val strokeColor = when {
            !enabled -> colors.outline
            checked -> null
            else -> colors.textSecondary
        }
        return TextView(this).apply {
            text = label
            this.contentDescription = contentDescription
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            setTextColor(textColor)
            setLineSpacing(0f, 1f)
            setPadding(8.dp(), 0, 8.dp(), 0)
            isEnabled = enabled
            isClickable = enabled
            isFocusable = enabled
            background = RippleDrawable(
                ColorStateList.valueOf(colors.ripple),
                rounded(fillColor, 16.dp(), strokeColor, 1.dp()),
                rounded(fillColor, 16.dp())
            )
            setOnClickListener {
                if (enabled) onChanged(!checked)
            }
        }
    }

    private fun switchIndicator(
        checked: Boolean,
        enabled: Boolean,
        colors: UiPalette
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
        return FrameLayout(this).apply {
            isEnabled = enabled
            background = rounded(trackColor, 18.dp(), trackStroke, 1.dp())
            setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
            addView(
                View(this@NotificationChannelSelectionActivity).apply {
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
                textDisabled = systemColor(android.R.color.system_neutral2_500),
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
                textDisabled = systemColor(android.R.color.system_neutral2_400),
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
