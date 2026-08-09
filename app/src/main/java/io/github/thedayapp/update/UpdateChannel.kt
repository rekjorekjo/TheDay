package io.github.thedayapp.update

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
                throw Exception("Manifest edition mismatch: expected glass")
            }
            return
        }

        // Classic accepts an omitted edition field so older latest.json files remain valid.
        if (value.isNotEmpty() && value != "classic") {
            throw Exception("Manifest edition mismatch: expected classic")
        }
    }
}
