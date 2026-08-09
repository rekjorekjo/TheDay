# Glass Flutter Architecture

The Day 3.0.0 的 Glass Edition 使用 Flutter Add-to-App 作为完整可见界面，Kotlin 保留数据和 Android 系统集成职责。该架构已经用于正式 Glass 功能，不再是 WebView 或临时验证方案。

## 架构边界

- `classic`：Kotlin + Jetpack Compose UI。
- `glass`：启动 Flutter UI，由 Flutter 负责页面、滚动、导航、Glass 材质和动态背景。
- Kotlin 继续负责事件数据、排序与重复日期算法、草稿、提醒、Widget、文件、图片、更新、原生导出和 Android 系统能力。
- Flutter 与 Kotlin 通过 `MethodChannel` 交换状态并调用原生能力。
- Glass 不加载 HTML/CSS/JavaScript，也不使用 WebView 作为 UI 层。

## 首次准备（Windows）

1. 安装当前项目要求的 Flutter stable（3.44 或更新稳定版），并将 `flutter\bin` 加入 PATH。
2. 在项目根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup_flutter_glass.ps1
```

脚本会生成 Flutter module 的 Android wrapper、执行 `flutter pub get` 和 `flutter analyze`。`glass_flutter/.android/` 为 Flutter 生成目录，不提交到 Git。

3. 回到 Android Studio，执行 **Sync Project with Gradle Files**。
4. 在 **Build Variants** 中选择 `glassDebug`，运行到真机或模拟器。

根项目在 Gradle 设置阶段会载入 Glass module，因此首次 checkout 后应先完成 Flutter bootstrap 再 Sync。

## Glass 功能范围

Flutter UI 已接入 Kotlin 的真实数据与系统能力，包括：

- 首页、分类、分类封面和日子详情。
- 新建/编辑日子、草稿、提醒、置顶、重复、背景图片、重新裁剪和图片位置调整。
- 文字分享、纪念图分享与保存。
- 工具栏、批量导出、里程碑、日期计算器和纪念册。
- 纪念册封面、编辑、循环浏览与长按操作。
- 设置、16 套 Glass 主题、Glass Clarity、静态/流光/极光背景模式，以及纯净/雨水/雪花/流星/星河/爱心纹理。
- 关于、应用内文档、GitHub 入口和当前 Edition 的更新流程。

## Glass Clarity

- 较低数值：玻璃填充、阴影和背景模糊更明显。
- 中间数值：在材质感与背景透出之间平衡。
- `100%`：尽量降低玻璃填充、背景模糊和阴影，仅保留轮廓层次。

设置页只在滑块拖动结束后提交 Clarity 值，减少连续写入和全局刷新。

## 动态背景

Glass 背景由三种模式和独立纹理组成：

- 静态：固定当前背景构图，不播放动态背景。
- 流光：三组主题光源缓慢沿屏幕外围运动。
- 极光：复用轻量光源系统，在屏幕内部缓慢运动，并使用不同椭圆比例与轻微形变。

纹理可选纯净、雨水、雪花、流星、星河和爱心。动态纹理在静态模式下会停止动画并保留静态构图。

## Release

完成 Flutter bootstrap 后，双版本发布命令为：

```powershell
python scripts/release.py --build --notes release-notes.md
```

脚本构建 Classic + Glass，并生成两个 APK 和两个 update manifest。Glass APK 包含 Flutter engine，体积会高于 Classic；项目优先视觉质量、稳定性和流畅度，并尽量避免正式 Glass APK 增长到 100 MB 以上。
