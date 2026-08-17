# Contributing

欢迎提交 Issue 与 Pull Request。请遵循以下原则：

1. 核心用户数据必须保持本地，不引入广告、统计、追踪、账号或云同步；新增网络功能必须具有明确用途，并同步更新隐私说明。
2. 新依赖应说明必要性，并优先使用 Android / Flutter 官方能力或维护状态良好的开源组件。
3. UI 不复制其他倒数日应用的版式、图标或品牌资产。
4. 核心日期算法应补充单元测试。
5. 修改用户可见功能、设置名称或发布行为时，同步检查 `README.md`、`release-notes.md` 和相关开发文档。应用内更新说明由 Gradle 在构建前从根目录 `release-notes.md` 自动生成，不需要单独维护。
6. 修改 Glass Flutter 代码后至少运行 `flutter analyze`；修改 Android/Kotlin 代码后运行 Gradle 测试与对应 Edition 构建。

首次构建 Glass 前先完成：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup_flutter_glass.ps1
```

常用检查：

```bash
cd glass_flutter
flutter analyze

# 回到仓库根目录
./gradlew test
./gradlew :app:assembleClassicDebug :app:assembleGlassDebug
```

Windows 下将 `./gradlew` 替换为 `gradlew.bat`。
