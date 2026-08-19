package io.github.thedayapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/** 获取可随系统配置变化而更新的 Java Locale，供 Compose 日期格式化使用。 */
@Composable
fun currentJavaLocale(): Locale {
    val configuration = LocalConfiguration.current
    return configuration.locales[0]
}
