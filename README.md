# The Day

本地优先的 Android 倒数日应用。

<p align="center">
  <a href="https://developer.android.com/">
    <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&amp;logo=android&amp;logoColor=white">
  </a>
  <a href="https://kotlinlang.org/">
    <img alt="Kotlin 2.2.10" src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&amp;logo=kotlin&amp;logoColor=white">
  </a>
  <a href="https://developer.android.com/compose">
    <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack_Compose-2026.06.00-4285F4?style=for-the-badge&amp;logo=jetpackcompose&amp;logoColor=white">
  </a>
  <a href="https://m3.material.io/">
    <img alt="Material 3" src="https://img.shields.io/badge/Material_3-UI-6750A4?style=for-the-badge&amp;logo=materialdesign&amp;logoColor=white">
  </a>
</p>

<p align="center">
  <a href="https://gradle.org/">
    <img alt="Gradle 9.1" src="https://img.shields.io/badge/Gradle-9.1-02303A?style=for-the-badge&amp;logo=gradle&amp;logoColor=white">
  </a>
  <a href="https://developer.android.com/about/versions/oreo">
    <img alt="Minimum API 26" src="https://img.shields.io/badge/Min_API-26-34A853?style=for-the-badge&amp;logo=android&amp;logoColor=white">
  </a>
  <a href="./LICENSE">
    <img alt="Apache License 2.0" src="https://img.shields.io/badge/License-Apache_2.0-D22128?style=for-the-badge&amp;logo=apache&amp;logoColor=white">
  </a>
</p>

## 已实现

- 倒数日与正数日：今天、未来、过去日期均可显示。
- 每年重复：适合生日、纪念日等；2 月 29 日在非闰年按 2 月 28 日处理。
- 分类管理与分类封面。
- 日子背景图片和焦点调整。
- 草稿保存。
- 排序、筛选和置顶。
- 本地提醒：当天、提前 1 / 3 / 7 天；提醒时间在设置中统一调整。
- 十六套主题：暮蓝、朱砂、松烟、古金，以及 Bloom 的花瓣、雾蓝、草木、暖石、麦穗、水墨、琥珀、青金、涟漪、丹红、鼠尾草、紫语；支持浅色、深色、跟随系统。
- 可横向、纵向缩放的小组件：显示置顶事件或最近事件，日期变化后自动刷新。
- 小组件可使用当前事件背景图。
- 独立月历小组件：支持切换月份，并高亮包含日子事件的日期。
- 日子分享与纪念图：可通过系统分享面板分享文字或图片，并将纪念图保存到相册。
- 长按应用图标可请求添加小组件。
- 设置页主动检查 GitHub Release 更新：优先 latest.json，失败后使用 GitHub Releases API。
- 用户事件、分类、备注和图片不会上传。

## 技术结构

- Kotlin + Jetpack Compose + Material 3。
- `SharedPreferences + JSON` 本地存储，不依赖数据库或服务器。
- `AlarmManager + NotificationChannel` 本地提醒。
- `AppWidgetProvider + RemoteViews` 桌面小组件。
- 最低 Android 8.0（API 26）。

## 发布规则

每个正式 Release 必须包含：

1. 标签：`vX.Y.Z`

2. APK：`TheDay-vX.Y.Z.apk`

3. manifest：`latest.json`

   `latest.json` 中：
   - `tagName` 与 Release tag 一致
   - `versionName` 与 APK versionName 一致
   - `versionCode` 与 APK versionCode 一致
   - `size` 为 APK 实际字节数
   - `sha256` 为 APK 实际 SHA-256
   - APK URL 指向当前 Release

每次发布必须：
- 增加 versionCode
- 更新 versionName
- 使用同一发布密钥
- 创建非 draft、非 prerelease Release
- 同时上传 APK 和 latest.json

版本号来源于 `version.properties`。
正式 APK 由用户在 Android Studio 中手动签名。
`scripts/release.py` 只负责重命名 APK、计算大小和 SHA-256、生成 latest.json。
脚本不会构建、签名或上传 APK。

## 开发环境

- Android Studio（支持 Android Gradle Plugin 9.0.1）
- JDK 17 或更高
- Android SDK 36
- Gradle 9.1.0
- Kotlin 2.2.10（由 AGP 9 内置 Kotlin 配合 Compose Compiler 插件）
- Jetpack Compose BOM 2026.06.00

本项目已包含标准 Gradle Wrapper，可直接使用：

```bash
# Linux / macOS
./gradlew test
./gradlew assembleDebug

# Windows
gradlew.bat test
gradlew.bat assembleDebug
```

## 隐私边界

应用数据位于 Android 应用沙箱内，并关闭系统云备份与设备迁移备份；但它不是密码保险箱。获得设备 root 权限、调试权限或系统级访问权限的主体仍可能读取本机数据。

## 开源协议

Apache License 2.0。
