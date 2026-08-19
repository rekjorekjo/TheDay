package io.github.thedayapp.update

import java.io.IOException

import io.github.thedayapp.BuildConfig

enum class UpdateEdition(val value: String) {
    CLASSIC("classic"),
    GLASS("glass"),
}

object UpdateChannel {
    private const val CLASSIC_MANIFEST_URL =
        "https://github.com/rekjorekjo/TheDay/releases/latest/download/latest.json"
    private const val GLASS_MANIFEST_URL =
        "https://github.com/rekjorekjo/TheDay/releases/latest/download/latest-glass.json"

    val currentEdition: UpdateEdition
        get() = if (BuildConfig.EDITION == "glass") UpdateEdition.GLASS else UpdateEdition.CLASSIC

    val edition: String
        get() = currentEdition.value

    fun manifestUrl(targetEdition: UpdateEdition = currentEdition): String =
        if (targetEdition == UpdateEdition.GLASS) GLASS_MANIFEST_URL else CLASSIC_MANIFEST_URL

    fun apkAssetName(
        tagName: String,
        targetEdition: UpdateEdition = currentEdition,
    ): String =
        if (targetEdition == UpdateEdition.GLASS) {
            "TheDay-Glass-$tagName.apk"
        } else {
            "TheDay-$tagName.apk"
        }

    fun validateManifestEdition(
        value: String,
        targetEdition: UpdateEdition = currentEdition,
    ) {
        if (targetEdition == UpdateEdition.GLASS) {
            if (value != "glass") {
                throw IOException("Manifest edition mismatch: expected glass")
            }
            return
        }

        // Classic 兼容缺少 edition 字段的旧版 latest.json，避免历史发布清单失效。
        if (value.isNotEmpty() && value != "classic") {
            throw IOException("Manifest edition mismatch: expected classic")
        }
    }
}
