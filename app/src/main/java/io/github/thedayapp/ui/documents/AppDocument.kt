package io.github.thedayapp.ui.documents

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import io.github.thedayapp.R

enum class AppDocument(
    @StringRes val titleRes: Int,
    @RawRes val resourceId: Int,
) {
    UPDATE_NOTES(
        titleRes = R.string.update_notes,
        resourceId = R.raw.update_notes,
    ),

    USAGE_GUIDE(
        titleRes = R.string.usage_guide,
        resourceId = R.raw.usage_guide,
    ),

    PRIVACY_POLICY(
        titleRes = R.string.privacy_policy,
        resourceId = R.raw.privacy_policy,
    ),

    OPEN_SOURCE_NOTICES(
        titleRes = R.string.open_source_notices,
        resourceId = R.raw.open_source_notices,
    ),
}