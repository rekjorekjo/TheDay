# The Day 3.0.0 — Classic / Glass

## Build variants

The Day 3.0.0 同时维护两个 Android Product Flavor：

- `classicDebug` / `classicRelease`：Kotlin + Jetpack Compose 原生界面。
- `glassDebug` / `glassRelease`：Flutter Add-to-App 的 Glass 全屏界面。

Classic 与 Glass 共用 application ID `io.github.thedayapp`，因此不能同时安装。使用同一签名证书时，Glass APK 可以直接覆盖 Classic，并继续使用原有应用私有数据、小组件配置和图片。

## Shared core

两个 Edition 共用 `app/src/main` 中的数据模型、仓库、日期计算、提醒、本地图片、桌面小组件、更新、分享和导出逻辑。

界面入口通过 flavor-specific `io.github.thedayapp.ui.AppEntry` 区分：

- Classic 进入 Compose UI。
- Glass 进入 Flutter UI。

## Glass architecture

`glass_flutter/` 保存 Glass 的 Flutter UI。Flutter 负责可见界面、导航、滚动和 Glass 材质；Kotlin 继续负责事件数据、提醒、小组件、文件、原生导出和 Android 系统能力。

Flutter 与 Kotlin 通过小型 `MethodChannel` 交换状态和执行系统操作。Glass 不使用 WebView，也不加载 HTML/CSS/JavaScript 作为主界面。

更多说明见 [`GLASS_ARCHITECTURE.md`](GLASS_ARCHITECTURE.md)。

## Glass clarity

`AppSettings.glassClarity` 持久化范围为 0–100。Flutter 设置页拖动滑块时只更新当前显示值，在用户结束拖动后提交设置，避免拖动过程中频繁触发全局状态写入。

`100%` 时 Glass 材质会尽量降低填充、模糊与阴影，仅保留主要轮廓层次。

## Release channels

Classic 与 Glass 共用相同语义版本和 Git tag，但使用不同的 APK 与更新 manifest：

```text
Classic: latest.json       -> TheDay-vX.Y.Z.apk
Glass:   latest-glass.json -> TheDay-Glass-vX.Y.Z.apk
```

普通更新检查根据当前 Edition 读取对应渠道。Classic 另提供“升级到 Glass”，允许用户切换到同版本或更高版本的 Glass 正式包。

## Signed dual release

复制 `keystore.properties.example` 为本机私有的 `keystore.properties`，填写正式发布密钥后执行：

```bash
python scripts/release.py --build --notes release-notes.md
```

脚本会构建 `classicRelease` 与 `glassRelease`，检查 APK 输出，并在 `dist/` 生成两个 APK 和两个更新 manifest。
