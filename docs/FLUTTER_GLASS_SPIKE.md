# Glass Flutter Architecture

The Day 3.0.0 的 Glass Edition 已从 WebView 视觉层切换到 Flutter Add-to-App。真机技术验证已经完成：页面滚动、底部导航与 Kotlin 数据桥接可稳定工作，当前分支继续按正式功能版推进。

## 架构边界

- `classic`：继续使用现有 Kotlin + Jetpack Compose UI。
- `glass`：启动独立 `FlutterActivity`，由 Flutter 负责可见 UI、滚动、页面切换和玻璃材质。
- Kotlin 继续负责 The Day 的数据模型、排序/重复日期算法、草稿、提醒、Widget、文件、更新与 Android 系统能力。
- 当前桥接使用小型 `MethodChannel`；如接口继续扩大，可再收敛为 Pigeon 强类型 API。
- Glass 不加载 HTML/CSS/JavaScript，也不使用 WebView。

## 首次准备（Windows）

1. 安装当前 Flutter stable（3.44 或更新稳定版），把 `flutter\bin` 加入 PATH。
2. 在项目根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup_flutter_glass.ps1
```

脚本会生成 Flutter module 的 Android wrapper、执行 `flutter pub get` 和 `flutter analyze`。`glass_flutter/.android/` 是 Flutter 生成目录，不提交到 Git。

3. 回到 Android Studio，执行 **Sync Project with Gradle Files**。
4. 在 **Build Variants** 中选择 `glassDebug`，运行到真机或模拟器。

Classic 不进入 Flutter UI；但根项目在 Gradle 设置阶段会载入 Glass module，因此首次 checkout 后仍应先执行一次 bootstrap 脚本再 Sync。

## 当前 Glass 功能范围

Flutter UI 已接入 Kotlin 真实数据和系统能力，包括：

- 首页、分类、分类封面、日子详情。
- 新建/编辑日子、持久化草稿、提醒、置顶、重复、背景图片、重新裁剪与图片位置调整。
- 分享文字、分享/保存纪念图。
- 工具栏、批量导出、里程碑、日期计算器、纪念册。
- 纪念册封面、编辑、首尾循环浏览与长按操作。
- 设置、4×4 Glass 主题色板、Glass Clarity、通知权限。
- 关于、应用内文档、GitHub 入口与当前 Edition 的更新流程。

界面不显示调试状态、桥接状态或技术验证说明。开发信息仅保留在开发工具和日志中。

## Glass Clarity

- 低值：更明显的半透明填充、阴影和模糊。
- 中间值：在材质感与背景穿透之间平衡。
- `100%`：玻璃填充、背景模糊和阴影归零，仅保留轮廓边框；固定底栏也遵循同一规则。

滚动中的内容卡片默认不使用实时 `BackdropFilter`，避免移动时产生亮度跳变；固定底部 Glass Bar 可以使用实时背景模糊。

## Release

完成一次 Flutter bootstrap 后，原双版本发布命令保持不变：

```powershell
python scripts/release.py --build --notes release-notes.md
```

它仍会构建 Classic + Glass，并生成两个 APK 与两个 update manifest。Glass APK 包含 Flutter engine，因此体积会明显大于 Classic。当前项目优先视觉质量、稳定性和流畅度，正式 Glass APK 尽量保持在 100 MB 以下。
