package io.github.thedayapp.sharing

import android.content.Context

object MemoryImageTemplatePreferences {
    private const val PREFERENCES_NAME = "memory_image_preferences"
    private const val KEY_LAST_TEMPLATE = "last_template"

    fun load(context: Context): MemoryImageTemplate {
        val storedName = context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_TEMPLATE, null)

        return storedName?.let { name ->
            runCatching {
                MemoryImageTemplate.valueOf(name)
            }.getOrNull()
        } ?: MemoryImageTemplate.CIRCLES
    }

    fun save(context: Context, template: MemoryImageTemplate) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_TEMPLATE, template.name)
            .apply()
    }
}