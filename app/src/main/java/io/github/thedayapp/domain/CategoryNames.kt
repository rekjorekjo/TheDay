package io.github.thedayapp.domain

const val UNCLASSIFIED_CATEGORY_NAME = "未分类"

fun normalizedCategoryName(
    category: String,
): String {
    val trimmed = category.trim()

    return if (
        trimmed.isEmpty() ||
        trimmed == UNCLASSIFIED_CATEGORY_NAME
    ) {
        UNCLASSIFIED_CATEGORY_NAME
    } else {
        trimmed
    }
}