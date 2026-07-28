package io.github.thedayapp.ui.screens

import io.github.thedayapp.domain.UNCLASSIFIED_CATEGORY_NAME as DOMAIN_UNCLASSIFIED_CATEGORY_NAME
import io.github.thedayapp.domain.normalizedCategoryName as normalizeDomainCategoryName

internal const val UNCLASSIFIED_CATEGORY_NAME =
    DOMAIN_UNCLASSIFIED_CATEGORY_NAME

internal fun normalizedCategoryName(
    category: String,
): String {
    return normalizeDomainCategoryName(category)
}