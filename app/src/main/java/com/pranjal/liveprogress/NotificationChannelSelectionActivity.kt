package com.pranjal.liveprogress

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class NotificationChannelSelectionActivity : Activity() {
    private companion object {
        const val CONTENT_PADDING_DP = 20
        const val APP_GROUP_ITEM_TYPE = 0
        const val CATEGORY_ITEM_TYPE = 1
        const val BEHAVIOR_ITEM_TYPE = 2
    }

    private var categoryList: ListView? = null
    private var pendingListPosition: Int? = null
    private var pendingListTop: Int = 0
    private var pendingListAnchor: ListScrollAnchor? = null
    private var pageLoadVersion = 0L
    private val appIconCache = mutableMapOf<AppIconKey, Drawable?>()
    private val pendingAppIconKeys = mutableSetOf<AppIconKey>()
    private var renderedShizukuAvailability: Boolean? = null
    private var refreshBackCallbackRegistered = false
    private val refreshBackCallback = OnBackInvokedCallback {
        // A confirmed refresh is deliberately non-cancellable.
    }
    private val refreshStateObserver: (CategoryRefreshState) -> Unit = ::onRefreshStateChanged
    private val mainHandler = Handler(Looper.getMainLooper())
    private val categoryExecutor = Executors.newSingleThreadExecutor()
    private val iconExecutor = Executors.newFixedThreadPool(2)

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
        val userId: Int,
        val appLabel: String,
        val isSystemApp: Boolean
    )

    private data class ListScrollAnchor(
        val packageName: String,
        val userId: Int,
        val channelId: String?,
        val itemType: Int
    )

    private data class AppIconKey(
        val packageName: String,
        val userId: Int,
        val sourceDir: String?
    )

    private data class CategoryPageData(
        val includeSystemApps: Boolean,
        val allCategoryCount: Int,
        val categories: List<ObservedNotificationCategory>,
        val items: List<CategoryListItem>,
        val canSuppressOriginal: Boolean,
        val shizukuAvailable: Boolean
    )

    private sealed interface CategoryListItem {
        data class AppGroup(
            val group: AppCategoryGroup,
            val selectedCount: Int,
            val totalCount: Int,
            val sourceDir: String?
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
        renderForRefreshState(CategoryRefreshCoordinator.currentState())
    }

    override fun onStart() {
        super.onStart()
        AppUiLifecycleTracker.onActivityStarted()
        CategoryRefreshCoordinator.addObserver(refreshStateObserver)
    }

    override fun onStop() {
        CategoryRefreshCoordinator.removeObserver(refreshStateObserver)
        AppUiLifecycleTracker.onActivityStopped(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        val refreshInProgress = CategoryRefreshCoordinator.currentState().isRefreshInProgress()
        val shizukuAvailable = PrivilegedAccess.currentState(this).shizukuAvailable
        if (!refreshInProgress && renderedShizukuAvailability != null &&
            renderedShizukuAvailability != shizukuAvailable
        ) {
            renderContent()
        }
    }

    override fun onDestroy() {
        updateRefreshBackHandling(false)
        categoryExecutor.shutdownNow()
        iconExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun renderContent() {
        if (
            isFinishing ||
            isDestroyed ||
            CategoryRefreshCoordinator.currentState().isRefreshInProgress()
        ) {
            return
        }
        val version = ++pageLoadVersion
        categoryList = null
        setContentView(buildLoadingContent())
        categoryExecutor.execute {
            val pageData = loadCategoryPageData()
            mainHandler.post {
                if (
                    !isFinishing &&
                    version == pageLoadVersion &&
                    !CategoryRefreshCoordinator.currentState().isRefreshInProgress()
                ) {
                    setContentView(buildContent(pageData))
                }
            }
        }
    }

    private fun buildLoadingContent(): View {
        val colors = palette()
        return contentRoot().apply {
            addView(pageTitle(colors), blockParams(bottom = 18.dp()))
            addView(
                ProgressBar(this@NotificationChannelSelectionActivity).apply {
                    isIndeterminate = true
                },
                LinearLayout.LayoutParams(
                    48.dp(),
                    48.dp()
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            )
        }
    }

    private fun buildContent(pageData: CategoryPageData): View {
        renderedShizukuAvailability = pageData.shizukuAvailable
        val colors = palette()
        val root = contentRoot()
        root.addView(pageTitle(colors), blockParams(bottom = 18.dp()))

        val categoryPreferences = NotificationCategoryPreferences(this)
        root.addView(
            actionButtons(
                categories = pageData.categories,
                shizukuAvailable = pageData.shizukuAvailable
            ),
            blockParams(bottom = 10.dp())
        )
        root.addView(
            systemAppsToggle(
                checked = pageData.includeSystemApps,
                categoryPreferences = categoryPreferences
            ),
            blockParams(bottom = 10.dp())
        )
        if (pageData.categories.isEmpty()) {
            categoryList = null
            val emptyText = if (pageData.allCategoryCount > 0 && !pageData.includeSystemApps) {
                getString(R.string.setting_system_categories_hidden_empty)
            } else {
                getString(R.string.setting_always_mirror_categories_empty)
            }
            root.addView(settingInfo(emptyText))
        } else {
            root.addView(
                categoryListView(
                    items = pageData.items,
                    categoryPreferences = categoryPreferences,
                    canSuppressOriginal = pageData.canSuppressOriginal
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

    private fun onRefreshStateChanged(state: CategoryRefreshState) {
        if (isFinishing || isDestroyed) return
        renderForRefreshState(state)
    }

    private fun renderForRefreshState(state: CategoryRefreshState) {
        when (state) {
            CategoryRefreshState.Idle -> {
                updateRefreshBackHandling(false)
                renderContent()
            }

            CategoryRefreshState.Preparing,
            is CategoryRefreshState.Scanning -> {
                updateRefreshBackHandling(true)
                categoryList = null
                pageLoadVersion += 1
                setContentView(buildRefreshContent(state))
            }

            is CategoryRefreshState.Finished -> {
                updateRefreshBackHandling(false)
                CategoryRefreshCoordinator.acknowledgeFinished(state.result)
                showToast(refreshResultMessage(state.result))
                renderContent()
            }
        }
    }

    private fun buildRefreshContent(state: CategoryRefreshState): View {
        val colors = palette()
        val root = contentRoot()
        root.addView(pageTitle(colors), blockParams(bottom = 18.dp()))
        val progress = (state as? CategoryRefreshState.Scanning)?.progress
        val message = if (progress == null) {
            getString(R.string.category_refresh_preparing)
        } else {
            getString(
                R.string.category_refresh_progress,
                progress.completedApps,
                progress.totalApps,
                progress.percentage
            )
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        body.addView(
            TextView(this).apply {
                text = if (progress == null) "" else getString(
                    R.string.category_refresh_percentage,
                    progress.percentage
                )
                textSize = 40f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(colors.textPrimary)
                includeFontPadding = false
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 14.dp()
            }
        )
        body.addView(
            ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
            ).apply {
                isIndeterminate = progress == null
                if (progress != null) {
                    max = 100
                    this.progress = progress.percentage
                }
                progressTintList = ColorStateList.valueOf(colors.primary)
                progressBackgroundTintList = ColorStateList.valueOf(colors.surfaceContainerHigh)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                8.dp()
            ).apply {
                marginStart = 20.dp()
                marginEnd = 20.dp()
                bottomMargin = 18.dp()
            }
        )
        body.addView(
            TextView(this).apply {
                text = message
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(colors.textSecondary)
                setLineSpacing(2.dp().toFloat(), 1f)
            }
        )
        root.addView(
            body,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        )
        return root
    }

    private fun refreshResultMessage(result: CategoryRefreshResult): String {
        return when (result) {
            is CategoryRefreshResult.Completed -> {
                if (result.failedAppCount > 0) {
                    getString(
                        R.string.category_refresh_complete_with_failures,
                        result.newCategoryCount,
                        result.failedAppCount
                    )
                } else {
                    getString(R.string.category_refresh_complete, result.newCategoryCount)
                }
            }

            CategoryRefreshResult.NoAppsFound -> getString(R.string.category_refresh_no_apps)
            is CategoryRefreshResult.Failed -> getString(
                R.string.category_refresh_failed,
                result.detail
            )
        }
    }

    private fun updateRefreshBackHandling(refreshInProgress: Boolean) {
        if (refreshInProgress == refreshBackCallbackRegistered) return
        if (refreshInProgress) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                refreshBackCallback
            )
        } else {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(refreshBackCallback)
        }
        refreshBackCallbackRegistered = refreshInProgress
    }

    private fun pageTitle(colors: UiPalette): TextView {
        return TextView(this).apply {
            text = getString(R.string.notification_categories_title)
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colors.textPrimary)
            includeFontPadding = false
        }
    }

    private fun loadCategoryPageData(): CategoryPageData {
        val categoryPreferences = NotificationCategoryPreferences(this)
        val includeSystemApps = categoryPreferences.showSystemApps
        val categorySnapshot = categoryPreferences.snapshot()
        val allCategoryCount = categorySnapshot.categories.size
        val categories = categorySnapshot.categories.filter { includeSystemApps || !it.isSystemApp }
        val privilegedState = PrivilegedAccess.currentState(this)
        return CategoryPageData(
            includeSystemApps = includeSystemApps,
            allCategoryCount = allCategoryCount,
            categories = categories,
            items = categoryListItems(categories, categorySnapshot.settingsByKey),
            canSuppressOriginal = privilegedState.temporaryAssistantActive ||
                (privilegedState.shizukuAvailable && privilegedState.shizukuGranted),
            shizukuAvailable = privilegedState.shizukuAvailable
        )
    }

    private fun categoryListItems(
        categories: List<ObservedNotificationCategory>,
        settingsByKey: Map<NotificationCategoryKey, NotificationCategorySettings>
    ): List<CategoryListItem> {
        val items = mutableListOf<CategoryListItem>()
        categories.groupBy {
            AppCategoryGroup(
                packageName = it.key.packageName,
                userId = it.key.userId,
                appLabel = it.appLabel,
                isSystemApp = it.isSystemApp
            )
        }.forEach { (group, appCategories) ->
            val enabledCount = appCategories.count { category ->
                settingsByKey[category.key]?.enabled == true
            }
            items.add(
                CategoryListItem.AppGroup(
                    group = group,
                    selectedCount = enabledCount,
                    totalCount = appCategories.size,
                    sourceDir = appCategories.firstNotNullOfOrNull { it.sourceDir }
                )
            )
            appCategories.forEach { category ->
                val settings = settingsByKey[category.key] ?: NotificationCategorySettings()
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

        fun positionFor(anchor: ListScrollAnchor): Int? {
            val exactPosition = items.indexOfFirst { item ->
                item.scrollAnchor() == anchor
            }
            if (exactPosition >= 0) return exactPosition

            if (anchor.itemType == BEHAVIOR_ITEM_TYPE && anchor.channelId != null) {
                val categoryPosition = items.indexOfFirst { item ->
                    item.scrollAnchor() == anchor.copy(itemType = CATEGORY_ITEM_TYPE)
                }
                if (categoryPosition >= 0) return categoryPosition
            }
            return null
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
                        iconKey = AppIconKey(
                            packageName = item.group.packageName,
                            userId = item.group.userId,
                            sourceDir = item.sourceDir
                        ),
                        selectedCount = item.selectedCount,
                        totalCount = item.totalCount,
                        checked = allEnabled
                    ) {
                        categoryPreferences.setAppEnabled(
                            packageName = item.group.packageName,
                            userId = item.group.userId,
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
        iconKey: AppIconKey,
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
        row.addView(
            appIconView(iconKey),
            LinearLayout.LayoutParams(44.dp(), 44.dp()).apply {
                marginEnd = 12.dp()
            }
        )
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

    private fun appIconView(key: AppIconKey): ImageView {
        return ImageView(this).apply {
            tag = key
            contentDescription = null
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = rounded(palette().surface, 18.dp())
            if (appIconCache.containsKey(key)) {
                setImageDrawable(iconForDisplay(key))
            } else {
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(view: View) {
                        view.removeOnAttachStateChangeListener(this)
                        if (appIconCache.containsKey(key)) {
                            setImageDrawable(iconForDisplay(key))
                        } else {
                            loadAppIcon(key)
                        }
                    }

                    override fun onViewDetachedFromWindow(view: View) = Unit
                })
            }
        }
    }

    private fun loadAppIcon(key: AppIconKey) {
        if (!pendingAppIconKeys.add(key)) return
        iconExecutor.execute {
            val icon = AppLabelResolver.icon(
                context = this,
                packageName = key.packageName,
                userId = key.userId,
                sourceDir = key.sourceDir
            )
            mainHandler.post {
                pendingAppIconKeys.remove(key)
                appIconCache[key] = icon
                updateVisibleAppIcon(key)
            }
        }
    }

    private fun updateVisibleAppIcon(key: AppIconKey) {
        val list = categoryList ?: return
        repeat(list.childCount) { index ->
            list.getChildAt(index)
                .findViewWithTag<ImageView>(key)
                ?.setImageDrawable(iconForDisplay(key))
        }
    }

    private fun iconForDisplay(key: AppIconKey): Drawable? {
        val icon = appIconCache[key] ?: return null
        return icon.constantState?.newDrawable(resources)?.mutate() ?: icon
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

    private fun actionButtons(
        categories: List<ObservedNotificationCategory>,
        shizukuAvailable: Boolean
    ): View {
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
        if (shizukuAvailable) {
            column.addView(
                actionButton(getString(R.string.action_refresh_categories_shizuku)) {
                    onRefreshCategoriesClicked()
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    52.dp()
                ).apply {
                    topMargin = 10.dp()
                }
            )
        }
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

    private fun onRefreshCategoriesClicked() {
        val state = PrivilegedAccess.currentState(this)
        when (CategoryRefreshPolicy.actionFor(state.shizukuAvailable, state.shizukuGranted)) {
            CategoryRefreshAction.HIDDEN -> renderContent()
            CategoryRefreshAction.REQUEST_SHIZUKU_PERMISSION -> {
                showToast(PrivilegedAccess.requestShizukuPermission())
            }

            CategoryRefreshAction.CONFIRM_REFRESH -> showRefreshConfirmation()
        }
    }

    private fun showRefreshConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.category_refresh_confirmation_title)
            .setMessage(R.string.category_refresh_confirmation_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.category_refresh_confirm_action) { _, _ ->
                CategoryRefreshCoordinator.start(this)
            }
            .show()
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
        refreshCategoryList()
    }

    private fun refreshCategoryList() {
        val list = categoryList ?: run {
            renderContent()
            return
        }
        val version = ++pageLoadVersion
        categoryExecutor.execute {
            val pageData = loadCategoryPageData()
            mainHandler.post {
                if (isFinishing || version != pageLoadVersion || categoryList !== list) return@post
                if (pageData.categories.isEmpty()) {
                    renderContent()
                    return@post
                }
                list.adapter = CategoryAdapter(
                    items = pageData.items,
                    categoryPreferences = NotificationCategoryPreferences(this),
                    canSuppressOriginal = pageData.canSuppressOriginal
                )
                restoreListPosition(list)
            }
        }
    }

    private fun captureListPosition() {
        val list = categoryList ?: return
        pendingListPosition = list.firstVisiblePosition
        pendingListTop = list.getChildAt(0)?.top ?: 0
        pendingListAnchor = (list.adapter as? CategoryAdapter)
            ?.getItem(list.firstVisiblePosition)
            ?.scrollAnchor()
    }

    private fun restoreListPosition(list: ListView) {
        val fallbackPosition = pendingListPosition ?: return
        val top = pendingListTop
        val anchor = pendingListAnchor
        pendingListPosition = null
        pendingListTop = 0
        pendingListAnchor = null
        val adapter = list.adapter as? CategoryAdapter
        val position = adapter?.let { activeAdapter ->
            anchor?.let(activeAdapter::positionFor)
        } ?: fallbackPosition
        list.viewTreeObserver.addOnGlobalLayoutListener(object :
            android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                list.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val lastPosition = (list.count - 1).coerceAtLeast(0)
                list.setSelectionFromTop(position.coerceIn(0, lastPosition), top)
            }
        })
    }

    private fun CategoryListItem.scrollAnchor(): ListScrollAnchor {
        return when (this) {
            is CategoryListItem.AppGroup -> ListScrollAnchor(
                packageName = group.packageName,
                userId = group.userId,
                channelId = null,
                itemType = APP_GROUP_ITEM_TYPE
            )
            is CategoryListItem.Category -> ListScrollAnchor(
                packageName = category.key.packageName,
                userId = category.key.userId,
                channelId = category.key.channelId,
                itemType = CATEGORY_ITEM_TYPE
            )
            is CategoryListItem.Behavior -> ListScrollAnchor(
                packageName = category.key.packageName,
                userId = category.key.userId,
                channelId = category.key.channelId,
                itemType = BEHAVIOR_ITEM_TYPE
            )
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
