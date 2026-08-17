# The Day

本地优先的 Android 倒数日与纪念日应用。支持倒数/正数、分类、提醒、小组件、纪念册、里程碑、日期计算与图片导出；不需要账号，不提供广告或云同步。

<p align="center">
  <a href="https://developer.android.com/">
    <img alt="Android 12+" src="https://img.shields.io/badge/Android-12%2B-3DDC84?style=for-the-badge&amp;logo=android&amp;logoColor=white">
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
  <a href="https://flutter.dev/">
    <img alt="Flutter" src="https://img.shields.io/badge/Flutter-Glass_UI-02569B?style=for-the-badge&amp;logo=flutter&amp;logoColor=white">
  </a>
  <a href="https://gradle.org/">
    <img alt="Gradle 9.1" src="https://img.shields.io/badge/Gradle-9.1-02303A?style=for-the-badge&amp;logo=gradle&amp;logoColor=white">
  </a>
  <a href="https://developer.android.com/about/versions/12">
    <img alt="Minimum API 31" src="https://img.shields.io/badge/Min_API-31-34A853?style=for-the-badge&amp;logo=android&amp;logoColor=white">
  </a>
  <a href="./LICENSE">
    <img alt="Apache License 2.0" src="https://img.shields.io/badge/License-Apache_2.0-D22128?style=for-the-badge&amp;logo=apache&amp;logoColor=white">
  </a>
</p>

## 双版本架构（3.0.0）

本仓库同时包含两个 Android Product Flavor：

- `classic`：保留现有 Kotlin + Jetpack Compose / Material 风格，作为稳定基线。
- `glass`：采用 Flutter Add-to-App 的独立全屏 UI；Kotlin 继续负责数据、日期算法、提醒、Widget、文件和 Android 系统能力。Glass 不再使用 WebView。

Classic 与 Glass 现在使用同一个 Android application ID `io.github.thedayapp`。因此两者不能同时安装，但只要使用同一签名证书，Glass APK 可以直接覆盖 Classic 并保留应用私有目录中的日子、图片、设置和小组件绑定。Android Studio 的 **Build Variants** 中仍可切换 `classicDebug` / `glassDebug`。

Glass 已完成主要功能迁移，日子编辑与草稿、分类封面、分享与纪念图、导出、里程碑、日期计算器、纪念册、应用内文档和更新流程均使用正式 Flutter Glass 界面。首次构建 Glass 前请参阅 [`docs/GLASS_ARCHITECTURE.md`](docs/GLASS_ARCHITECTURE.md)。


## 已实现

- 倒数日与正数日：今天、未来、过去日期均可显示。
- 每年重复：适合生日、纪念日等；2 月 29 日在非闰年按 2 月 28 日处理。
- 分类管理与分类封面：选择图片时同时保留未裁剪原图与裁剪结果，后续可重新裁剪。
- 日子背景图片支持自由比例裁剪和重新裁剪；界面显示裁剪结果，重新裁剪时读取保留的未裁剪原图。首页主卡和详情大卡会在安全范围内跟随裁剪后的图片比例变化，详情大卡最低支持 0.6:1 的竖向比例。
- 草稿保存。
- 排序、筛选和置顶。
- 本地提醒：当天、提前 1 / 3 / 7 天；提醒时间在设置中统一调整。
- 十六套主题配色：Glass 版以 4×4 色板展示夜航、珊瑚、森境、暮金、樱雾、冰川、苔原、石墨、蜂蜜、墨蓝、琥珀、蓝宝、极光、绯红、薄荷、星紫；Glass 固定使用深色视觉体系。
- Glass 背景支持「静态 / 流光 / 极光」三种模式，并可叠加「纯净 / 雨水 / 雪花 / 流星 / 星河 / 爱心」六种纹理；动态背景保持缓慢、低干扰的节奏，静态模式会停止背景动画。
- 可横向、纵向缩放的小组件：显示置顶事件或最近事件，日期变化后自动刷新。
- 首页主卡和桌面“特殊日子”小组件的天数数字使用静态外发光，数字本体保持原有颜色。
- 纵向“特殊日子”小组件使用紧凑的居中内容组，避免标题、天数和日期被拉得过远。
- 小组件可使用当前事件背景图。
- 独立“月历”小组件：支持切换月份，并高亮包含日子事件的日期。
- 底部导航包含日子、分类、新增和设置；首页右上角的扳手按钮打开工具栏。
- “特殊日子”小组件的配置列表同时显示名称和分类，便于区分同名事件。
- 日子分享与纪念图：可通过系统分享面板分享文字或图片，并将纪念图保存到相册；有背景图片时，纪念图中间图片区域按 0.6:1 至 1.5:1 自适应，顶部品牌区和底部标语区不计入该比例。
- 工具栏集中放置导出、里程碑、日期计算器和纪念册入口；导出页支持返回工具栏。
- 里程碑：显示当前年份与当前月份进度，可新增仅在里程碑页可见的阶段节点；列表支持长按卡片上下拖动排序并保存顺序，点击卡片可进入管理态并切换选中，只有拖动中或手动选中后才高亮；底部图标工具栏支持删除、导出和完成，导出前支持再次拖动排序。
- 日期计算器：支持按天、周、月、年计算某日期之前或之后的日期，数字与单位在同一行，方向在下一行左侧单独选择；也支持计算两个日期之间的间隔。
- 纪念册：可从已有日子中选择多个条目组成纪念册，选择列表显示分类以便区分同名日子；详情页以卡片堆叠式图片浏览展示，支持左右滑动、首尾循环、圆点指示、设置封面和编辑纪念册，长按当前图片可移出纪念册或编辑对应日子。
- 应用内文档：通过设置页右上角的“关于”入口查看版本、更新说明、使用说明、隐私政策、开源许可和 GitHub 仓库。
- 长按应用图标可请求添加小组件。
- “关于”页主动检查 GitHub Release 更新：Classic 使用 `latest.json`，Glass 使用 `latest-glass.json`；manifest 失败后均回退到 GitHub Releases API。Classic 的“关于”页另提供“升级到 Glass”，会读取 Glass 渠道并下载同签名、同 application ID 的 Glass APK，在覆盖安装时保留现有数据。
- 用户事件、分类、备注和图片不会上传。

## 技术结构

- Classic：Kotlin + Jetpack Compose + Material 3。
- Glass：Flutter Add-to-App + Kotlin host；Flutter 负责可见 UI、滚动与玻璃材质，Kotlin 核心继续作为业务数据源。Glass Clarity 100% 时玻璃填充、模糊与阴影归零，仅保留轮廓边框。
- Yalantis uCrop：在设备本地裁剪用户选择的图片。
- `SharedPreferences + JSON` 本地存储，不依赖数据库或服务器。
- `AlarmManager + NotificationChannel` 本地提醒。
- `AppWidgetProvider + RemoteViews` 桌面小组件。
- 最低 Android 12（API 31）。

## 发布规则

The Day 3.0.0 起，同一个 GitHub Release 同时发布 Classic 和 Glass 两个 Edition。

每个正式 Release（标签 `vX.Y.Z`）必须包含：

1. Classic APK：`TheDay-vX.Y.Z.apk`
2. Classic manifest：`latest.json`
3. Glass APK：`TheDay-Glass-vX.Y.Z.apk`
4. Glass manifest：`latest-glass.json`

两个 manifest 都包含 `tagName`、`versionName`、`versionCode`、`releaseNotes`、APK 实际字节数和 SHA-256；`edition` 分别为 `classic` / `glass`。Classic 保留原 `latest.json` 文件名，以兼容旧版本客户端。

### Release 签名

根目录可创建本机私有的 `keystore.properties`（已加入 `.gitignore`），格式参考 `keystore.properties.example`：

```properties
storeFile=F:/path/to/theday-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

配置后，`classicRelease` 与 `glassRelease` 会使用同一发布密钥签名。不要提交真实的 `keystore.properties`、JKS/keystore 文件或密码。

### 一条命令构建并整理双版本 Release

```bash
python scripts/release.py --build --notes release-notes.md
```

> `release-notes.md` 是发布说明的唯一人工维护来源。每次 Android 构建前，Gradle 会自动将其转换为纯文本并写入 `app/src/main/res/raw/update_notes.txt`，供 APK 内“更新说明”页面使用。

脚本会先执行：

```text
:app:assembleClassicRelease
:app:assembleGlassRelease
```

然后在 `dist/` 生成：

```text
TheDay-vX.Y.Z.apk
latest.json
TheDay-Glass-vX.Y.Z.apk
latest-glass.json
```

如果 APK 已经通过 Android Studio 手动签名，也可以跳过 Gradle 构建，只让脚本整理发布文件：

```bash
python scripts/release.py \
  --classic-apk path/to/classic-release.apk \
  --glass-apk path/to/glass-release.apk \
  --notes release-notes.md
```

旧的 `--apk` 参数仍可作为 `--classic-apk` 的兼容别名，但只适用于 Classic，后续建议改用新参数。

### APK 体积对比

如果 Classic 的 Release APK 相比旧版明显变大，可直接对两个 APK 做 ZIP 组成对比：

```bash
python scripts/apk_size_report.py path/to/old.apk path/to/new.apk
```

脚本会分别统计 DEX、`res/`、`assets/`、`lib/`、`resources.arsc`、签名元数据等压缩后贡献，并列出增长最大的单个 APK 条目。Glass 的 Flutter UI 位于 `glass_flutter/`，Classic variant 不包含 Glass 的 Flutter 界面资源。

每次正式发布仍必须增加 `versionCode`、按需更新 `versionName`、使用同一发布密钥，并创建非 draft、非 prerelease 的 GitHub Release，同时上传上述四个文件。

## 开发环境

- Android Studio（支持 Android Gradle Plugin 9.0.1）
- Glass Edition 需要当前 Flutter stable（3.44 或更新稳定版）；首次 clone 后运行 `scripts/setup_flutter_glass.ps1`。
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


### 更新检查与权限

The Day 启动时会在后台检查新版本；发现更新时仅发送系统通知，不会自动下载 APK。用户仍可在“关于”中手动检查并决定是否下载/安装。Android 13 及以上的新安装会请求通知权限；图片选择与保存使用系统接口，不申请广泛存储权限。
