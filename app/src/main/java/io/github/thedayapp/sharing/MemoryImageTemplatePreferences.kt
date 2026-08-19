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
            try {
                MemoryImageTemplate.valueOf(name)
            } catch (_: IllegalArgumentException) {
                // 旧版本保存的模板名已不存在时回到默认模板。
                null
            }
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