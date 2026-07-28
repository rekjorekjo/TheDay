# The Day

一个克制、庄重、纯本地的 Android 倒数日应用。

## 已实现

- 倒数日与正数日：今天、未来、过去日期均可显示。
- 每年重复：适合生日、纪念日等；2 月 29 日在非闰年按 2 月 28 日处理。
- 事件管理：标题、日期、可选分类、备注、置顶、提醒。
- 本地提醒：当天、提前 1 / 3 / 7 天；提醒时间在设置中统一调整。
- 排序与筛选：智能、日期、标题、创建时间；可隐藏过去事件。
- 原创界面：以“大数字 + 时间轴卡片”为核心，不使用封面图或信息流广告。
- 主题：暮蓝、朱砂、松烟、古金；支持浅色、深色、跟随系统。
- 桌面小组件：显示置顶事件或最近事件，日期变化后自动刷新。
- 隐私：无互联网权限、无账号、无广告、无分析 SDK、无云备份、无默认数据。

## 明确未实现

日期计算器、里程碑、事件封面、历史上的今天、登录注册、云同步、备份导入导出、广告、默认节日数据、农历。

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
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

## 本地校验

```bash
python3 tools/verify_project.py
```

核心日期与排序算法还可脱离 Android SDK 做烟雾测试：

```bash
kotlinc \
  app/src/main/java/io/github/thedayapp/data/Models.kt \
  app/src/main/java/io/github/thedayapp/domain/DayMath.kt \
  app/src/main/java/io/github/thedayapp/domain/EventOrdering.kt \
  tools/core-smoke/Main.kt \
  -include-runtime -d /tmp/the-day-core.jar
java -jar /tmp/the-day-core.jar
```

## 隐私边界

应用数据位于 Android 应用沙箱内，并关闭系统云备份与设备迁移备份；但它不是密码保险箱。获得设备 root 权限、调试权限或系统级访问权限的主体仍可能读取本机数据。

## 开源协议

Apache License 2.0。
