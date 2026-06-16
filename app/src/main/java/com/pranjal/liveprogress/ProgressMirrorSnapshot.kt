package com.pranjal.liveprogress

data class ProgressMirrorSnapshot(
    val title: String?,
    val text: String?,
    val subText: String?,
    val appLabel: String,
    val progress: Int,
    val max: Int,
    val indeterminate: Boolean,
    val shortText: String,
    val color: Int,
    val actionsCount: Int,
    val sourceSmallIconKey: String?,
    val largeIconKey: String?,
    val locked: Boolean,
    val screenOff: Boolean,
    val shouldSuppressOriginal: Boolean,
    val priorityMode: MirrorPriorityMode
) {
    companion object {
        fun from(
            candidate: MirrorCandidate,
            locked: Boolean,
            screenOff: Boolean,
            shouldSuppressOriginal: Boolean,
            useSourceSmallIcon: Boolean,
            priorityMode: MirrorPriorityMode
        ): ProgressMirrorSnapshot {
            return ProgressMirrorSnapshot(
                title = candidate.title?.toString(),
                text = candidate.text?.toString(),
                subText = candidate.subText?.toString(),
                appLabel = candidate.appLabel,
                progress = candidate.progress.progress,
                max = candidate.progress.max,
                indeterminate = candidate.progress.indeterminate,
                shortText = candidate.progress.shortText,
                color = candidate.color,
                actionsCount = candidate.actions.size,
                sourceSmallIconKey = candidate.smallIcon?.toString().takeIf { useSourceSmallIcon },
                largeIconKey = candidate.largeIcon?.toString(),
                locked = locked,
                screenOff = screenOff,
                shouldSuppressOriginal = shouldSuppressOriginal,
                priorityMode = priorityMode
            )
        }
    }
}
