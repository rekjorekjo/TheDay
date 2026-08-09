# Changelog

## 3.0.0 - Dual Edition foundation

- Glass final visual pass uses a dark-only Liquid Glass system with 16 palettes and three background modes: Static, a 36-second three-light Flow orbit around the outer edge, and Aurora reusing the same low-cost three-light engine while moving the orbit into the content area and giving each light a distinct elliptical aspect, rotation and slow organic shape breathing without changing the dark base; six full-screen textures remain available: Pure, Rain, Snowflakes, Meteors, Galaxy and Hearts. Rain uses fine drifting rain streaks, while Snowflakes uses gently falling flakes over the dark Glass background; Meteors streak downward from the upper screen; Galaxy keeps the previous constellation-style points and connecting lines. Layered glass rims and preserved depth shadows remain available.
- Glass exports snapshot the current background mode, ambient-light phase and selected texture so Flow preserves the live outer-orbit positions and Aurora preserves the same three lights on the inner orbit in memorial, long-image, list and milestone outputs.
- Bottom navigation now uses one shared Liquid Glass selection capsule that slides between the four main destinations over 480 ms with no stretch/squash or per-button press/selection animation; icon and label color track the capsule motion directly.
- Glass reverse navigation uses a 150 ms snapshot-assisted fade/scale settle, eliminating the brief ghost-frame effect seen in earlier builds.
- Removed device-tilt reflection and sensor-driven rim effects; Glass cards keep the stable fixed depth/rim treatment.
- Reduced explanatory/demo-like copy across Glass while retaining necessary state, risk-confirmation and error messaging.
- Added `classic` and `glass` Android product flavors.
- Classic keeps the original Compose UI.
- Glass now uses a Flutter Add-to-App full-screen UI; Kotlin remains the source of truth for events, sorting/date logic, reminders, widgets, files and Android system features.
- Glass currently validates Home, Categories, New Event, Event Detail, long Settings scrolling, bottom navigation and native data writes before the remaining screens are migrated.
- Added shared Glass Clarity persistence (0-100) and palette-driven ambient backgrounds.
- Raised minimum Android version to Android 12 / API 31.
- Added uninstall-time fragile user data preservation prompt.
- Classic and Glass now share the `io.github.thedayapp` application id. Same-key Glass builds can replace Classic in-place while preserving app-private data and widget state; the two editions therefore no longer install side by side.
- Added edition-aware release channels: Classic uses `latest.json` / `TheDay-vX.Y.Z.apk`, Glass uses `latest-glass.json` / `TheDay-Glass-vX.Y.Z.apk`.
- Classic About now exposes an explicit “Upgrade to Glass” path that reads the Glass channel and accepts an equal-or-newer Glass build for an in-place edition replacement.
- Added optional Gradle release signing through private `keystore.properties` plus a dual-edition `scripts/release.py` workflow.
- Added `scripts/setup_flutter_glass.ps1` / `.sh` to bootstrap the generated Flutter Android wrapper and run analysis.
- Added `scripts/apk_size_report.py` for side-by-side APK composition diagnostics.


## 2.4.1

- 工具栏新增“纪念册”入口，并将工具栏功能顺序更新为导出、里程碑、计算器、纪念册。
- 新增纪念册功能：可从已有日子中选择多个条目组成纪念册，选择列表显示分类以便区分同名日子。
- 纪念册详情页采用卡片堆叠式图片浏览，支持左右滑动切换、首尾循环和圆点指示。
- 纪念册详情页右上角支持设置封面和编辑纪念册；长按当前图片可打开底部悬浮工具栏，支持移出纪念册、编辑对应日子和完成操作。
- 里程碑列表支持长按卡片上下拖动排序并保存顺序；长按本身不再触发高亮，只有拖动中或手动选中后才变色。
- 设置页主题色选中态改为同色发光效果，去掉原先偏重的黑色外框。
- 更新发布说明和应用内使用说明。
## 2.3.3

- 首页右上角改为工具栏入口，底部导航保留“日子、分类、新增、设置”。
- 工具栏集中放置导出、里程碑和日期计算器，导出功能移入工具栏并支持返回。
- “新增”恢复为底部导航主页面，不再显示返回按钮，并保留底部导航栏。
- 新增里程碑页面：显示当前年份和月份进度，百分比保留两位小数，里程碑仅在该页面可见。
- 里程碑列表支持长按拖动排序并保存顺序；长按卡片进入管理态并高亮当前项，但不会自动勾选。
- 里程碑管理态底部悬浮工具栏改为删除、导出、完成三个纯图标按钮；单卡片右侧不再显示删除按钮。
- 里程碑导出新增排序页，支持长按拖动调整导出顺序，再选择装饰主题并分享或保存。
- 新增日期计算器：支持日期加减和日期间隔计算，数字与单位同一行，“之前/之后”单独放在下一行左侧。
- 应用中可见的删除、清空、移除类操作使用更醒目的错误色系。
- 更新 README 与应用内使用说明。

## 2.1.0

- 底部导航新增“导出”页面。
- 导出流程拆分为选择、拖动排序、主题与分享三个独立页面。
- 选择模式支持整行高亮；退出选择模式后导出类型按钮与日子条目同步恢复普通状态。
- 排序页支持长按整张卡片上下拖动，优化交换位移连续性，并显示所选日子总数。
- 导出主题按钮取消勾选图标，扩大点击面积并让名称保持居中。
- 设置页恢复预计导出页数，并将分享、保存按钮移动到主题设置下方。
- 长图卡片统一宽度、保留各自详情页比例与构图；单页最大高度由 2880 提高到 8192 像素。
- 长图和列表按实际内容动态计算高度，仅在完整卡片确实放不下时分页。
- 每次重新进入选择模式时清空上一次勾选，避免旧选择被自动继承。
- 选择页和排序页的高亮卡片取消阴影；排序让位动画改为无回弹弹簧，并按相邻卡片实际高度补偿位移。
- 分享和保存按钮增加确定进度圆环，显示当前页面渲染与写入进度。
- 图片加载加入跨页面内存缓存，并在首页列表可见时预热图片，减少详情页和首页大图反复解码造成的延迟。
- 外观设置不再无条件重排全部提醒，并延后桌面小组件刷新，使明暗主题优先完成界面重绘。
- 长图卡片间距扩大，修正导出图片采样算法，避免 2048 像素图片被过度降采样后再次放大；新导入裁剪图的长边上限提高到 3072 像素。
- 修复设置页切换明暗模式时顶部栏沿用旧颜色数帧的问题：顶部栏改为直接绘制当前主题背景，绕开 Material 顶栏容器颜色过渡。
- 导出长图与列表的日期不再作为标题下方副文本，改为复用单张纪念图的右上角金色日期绘制方式。

## 2.0.5

- 桌面小组件重新命名为“特殊日子”和“月历”。
- “特殊日子”小组件的事件选择列表在名称后显示分类，未填写分类时显示“未分类”，便于区分同名日子。
- 将“新建日子”入口迁移到首页右上角，使用更大的圆形加号按钮。
- 底部导航移除“新建”页，保留“日子、分类、设置”三个主要页面；首页右上角按钮打开完整的新建页面并继续支持草稿、图片、提醒、重复与置顶等全部功能。

## 2.0.4

- 将圆形、星星、爱心三种装饰主题中的实心/空心（描边）比例统一调整为 3:2。
- 改为按总数直接分配实心与空心数量后再随机打散，避免某次生成中空心元素过多导致视觉密度不足。

## 2.0.3

- 调整爱心主题中实心与空心爱心的整体比例为 3:2。
- 改为按总数直接分配实心/空心数量并打乱顺序，避免纯随机导致某次生成时空心过多、视觉密度不足。

## 2.0.2

- 无背景图片时，纪念图中央主卡片改为轻透明表面，使底层渐变与装饰元素能够自然透出。
- 保留主卡片边框、阴影和轻微高光，以维持文字可读性与三层结构。
- 爱心主题数量调整为：有图 20～25 个，无图 16～20 个。
- 装饰分布权重保持不变：有图中心与四周约为 1:9，无图约为 1:2。

## 2.0.1

- 纪念图新增“爱心”装饰主题，可通过纪念图预览右上角的模板切换按钮选择。
- 爱心主题同时包含实心与线框爱心，并根据明暗主题自动调整颜色、透明度和柔和光晕。
- 有背景图时，爱心主要分布在主视觉外围，减少对照片主体与文字的遮挡；无背景图时则保持更均衡的装饰密度。
- 大小、倾斜角度和分布均带有适度随机变化，使每次生成的纪念图保留自然层次感。

## 2.0.0

- 重构背景图片为非破坏性构图系统：保留原图与裁剪结果，并分别保存首页、详情页的焦点和缩放参数。
- 选择图片与“重新裁剪”统一直接进入 uCrop；保持默认完整裁剪框并点击右上角勾即可保留完整画面。
- 首页主卡与详情大卡中的图片默认支持单指拖动、双指缩放，手势停止片刻后自动保存。
- 右上角手势图标保留为功能提示和进入独立精细调整页面的入口。
- 桌面日子小组件沿用首页构图，纪念图沿用详情页构图。
- 详情页详细信息中的“原始日期”改为“日期”。

## 1.7.0

- 下线“调整显示位置”和“调整封面位置”，统一替换为“重新裁剪”。
- 选择或更换日子背景图片、分类封面时，同时保存一份未裁剪原图和一份裁剪结果。
- 事件卡片、详情页、小组件和纪念图继续读取裁剪结果；重新裁剪时从保留的原图重新打开 uCrop。
- 重新裁剪成功后只替换裁剪结果，原图继续保留，可反复重新裁剪而不会逐次损失画面范围。
- 图片移除、事件删除、草稿清除或封面更换时，会同时清理已不再引用的原图和裁剪图。
- 兼容旧版本图片：由于旧版没有保存未裁剪原图，首次重新裁剪会以旧版已有裁剪图作为源图，之后持续保留该源图。

## 1.6.3

- 去掉纪念图右上角日期的圆角徽标外框，避免过于厚重的装饰感。
- 将右上角日期改为直接显示的烫金文字样式，加入柔和金色渐变与微弱光泽高光。
- 保留上一版收紧后的顶部边距与整体三层结构。

## 1.6.2

- 缩小纪念图上方留白，减少顶部边距，让主卡片更贴近上方并进一步放大主体区域。
- 移除中层左侧日历图标，恢复更简洁的 “The Day” 品牌区。
- 在纪念图右上角新增日期标记，使用更精致的金色圆角徽标样式展示生成日期。
- 保持中层 “The Day” 与“记录值得记住的每一天”当前字号不变。

## 1.6.1

- 保持中层 “The Day” 与“记录值得记住的每一天”当前字号不变。
- 在中层 “The Day” 左侧新增日历图标，图标展示当日月/日信息，并使用与主题适配的配色。
- 调整纪念图主卡片比例与位置：适当缩小左右留白，使主体图片整体增大约 10% 左右。
- 主卡片略微下移，为中层品牌文字与图标留出更充足的上方空间。

## 1.6.0

- 纪念图改为全幅输出：去掉整张导出图外围的圆角与留白，不再出现额外黑边或白边。
- 纪念图整体升级为三层结构：底层为整张背景装饰层，中间为阴影承托层，顶层为居中的圆角主卡片。
- 主卡片不再占满整张纪念图宽度；有背景图时，图片与文字统一放入圆角主卡片中展示。
- 纪念图天数数字进一步缩小，尤其优化竖图模式下的观感。
- 保留并延续主题适配的柔和光晕与装饰元素，同时将装饰重点放回主卡片外部区域。

## 1.5.4

- 进一步缩小纪念图天数数字，重点改善竖图模式下数字过大的问题。
- 保留纪念图天数的主题适配双层柔和光晕。
- 为小米、Redmi 和 POCO 桌面增加小组件添加兼容流程。
- 优先向小米桌面传递官方 Widget 详情页参数和当前日子绑定信息。
- 当桌面不支持应用内直接添加时，提供手动添加引导；10 分钟内新添加的小组件会自动绑定当前日子。

## 1.5.2

- 区分有图纪念图和无图纪念图的装饰模式：无图保持现状，有图启用边缘强化型装饰。
- 有图纪念图的中间区域与四周区域装饰出现概率权重大致调整为 1:9。
- 适当增加有图模式下的流星、星形、波浪和极简装饰元素数量，使装饰更多分布在四周和四角。
- 中间图片主体区域继续保留少量点缀，但整体更干净，减少对照片和文字的遮挡感。

## 1.5.0

- 首页主卡的天数数字增加静态外发光：数字本体保持原有颜色，主题色外层光晕和浅色内层光晕用于增强轮廓。
- 移除此前试验性的全局水波、流星和波浪动态背景，不增加动态背景设置。
- 桌面日子小组件的天数改为预渲染静态光晕位图，使效果在 RemoteViews 中清晰可见。
- 为纵向日子小组件增加独立布局，将标题、天数和日期作为一个紧凑内容组居中显示，避免三行被拉到顶部、中部和底部。
- 横向与普通尺寸小组件继续使用原有布局。

## 1.4.5

- 缩小纪念图中的“天”字，使其不超过事件名称字号，并继续与大数字底部对齐。
- 首页主卡不再显示置顶符号；置顶排序与普通列表中的置顶标识保持不变。
- 撤回首页主卡和桌面日子小组件中试验性的流光、鎏金与发光效果，恢复清晰稳定的普通文字显示。
- 纪念图面板恢复原有的可拖动交互，不再禁用面板手势或增加额外关闭按钮。

## 1.4.3

- 将 uCrop 比例选项中的“原始比例”精简为“原始”。
- 为自由裁剪框增加四条边的独立拖动区域：拖动一条边时，其余三条边保持不动。
- 保留 uCrop 原生角点拖动行为：拖动一个角时，对面的两条边保持不动。
- 修正纪念图比例计算：0.6:1 至 1.5:1 的限制现在作用于纪念图中间的图片区域，而不是整张纪念图。
- 纪念图预览与最终输出使用同一套图片区域比例计算。

## 1.4.2

- 明确启用 uCrop 自由裁剪，裁剪框可通过拖动四角自由改变比例。
- 裁剪页工具栏、选中控件、裁剪框和网格会跟随 The Day 当前主题色。
- 日子详情大卡支持更高的竖图比例，极高图片的最低宽高比由 0.75:1 调整为 0.6:1。
- 有背景图片时，纪念图改为使用与日子详情相同的安全自适应比例；没有背景图片时继续使用默认比例。
- 保留 uCrop 原生自由裁剪行为，裁剪框允许超出当前图片显示区域。

## 1.4.1

- 为日子背景图片新增“系统图片选择器 → uCrop → 本地导入”的裁剪流程。
- 为分类封面新增相同的图片裁剪流程。
- 裁剪页面支持自由调整裁剪框、缩放、旋转和选择比例。
- 新图片只有在裁剪并导入成功后才会替换原图；取消或失败时保留原图。
- 裁剪临时文件仅保存在应用缓存中，并在取消、失败或导入完成后清理。
- 保留图片显示位置调整功能，并将入口文案明确为“调整显示位置”和“调整封面位置”。
- 首页主卡和详情大卡会在保证文字正常显示的前提下，尽量跟随裁剪后图片的比例改变大小。
- 对过宽或过高的图片自动限制卡片尺寸；无背景图片时继续使用默认布局。
- 调整位置对话框中的首页主卡和详情大卡预览同步使用新的自适应比例。
- 移除“关于”页面底部的本地优先说明文字。
- 补充 uCrop 的开源许可、使用说明和隐私说明。


## 1.4.0

- 新增独立的"关于"页面，在设置页右上角添加三个点按钮。
- 点击按钮进入"关于"页面，显示应用图标和版本信息。
- 将检查更新、Wi-Fi 下载选项从设置页迁移到"关于"页面。
- "关于"页面提供更新说明、使用说明、隐私政策、开源许可与第三方组件、GitHub 仓库入口。
- 前四个项目打开应用内文档阅读页，支持长按选择和复制文本。
- GitHub 仓库通过系统浏览器打开。
- 完善使用说明文档，涵盖创建日子、还有/已经、每年重复、背景图片、提醒、置顶、月历、桌面小组件、纪念图、应用更新和数据说明。
- 完善隐私政策文档，明确本地数据、图片、通知和小组件、应用更新、纪念图分享与保存、权限、数据删除、第三方服务和联系方式。
- 更新开源许可文档，补充 The Day、AndroidX、Jetpack Compose 的完整许可说明。
- 精简设置页内容。

## 1.3.5

- 缩小纪念图顶部品牌区和底部标语区。
- 扩大纪念图中间主视觉空间。
- 将装饰图形扩展到整张纪念卡片。
- 装饰图层位于用户背景图片下方。
- 新增圆影、星芒、流星、波纹和极简五种纪念图模板。
- 纪念图面板新增模板切换按钮。
- 永久保存用户上次选择的模板。
- 分享和保存使用当前预览模板。
- 将纪念图装饰分布调整为中心与外围约 1:4。
- 允许中心区域出现更小、更淡的装饰。
- 移除纸屑模板并新增极简模板。
- 优化圆影、星芒、流星和波纹模板的层次与留白。
- 修复切换模板时预览区域闪烁的问题。
- 使用双缓冲方式，新图生成完成后再替换旧预览。
- 修复装饰透明度累积衰减的问题。
- 根据明暗模式调整装饰和外边框透明度。
- 提高深色模式下纪念图装饰的可见度。
- 优化深色模式边框与分隔线。
- 默认纪念图背景支持随机渐变方向。

## 1.3.4

- 合并详情页分享与保存图片入口。
- 纪念图面板根据图片比例自适应高度。
- 纪念图支持横版和竖版布局。
- 将纪念图的顶部品牌区、主图和底部标语整合为一张连续圆角卡片。
- 修复装饰元素越过纪念图边界的问题。
- 修复纪念图预览高度中的像素与 dp 混用问题。

## 1.3.3

- 首页新增动态迷你日历。
- 首页标题区域新增本月进度条和百分比。
- 点击首页迷你日历可打开应用内月历面板。
- 月历面板高亮包含日子事件的日期。
- 新增可切换月份的桌面月历小组件。
- 桌面月历同步高亮事件日期。
- 优化首页大卡片状态文字布局。

## 1.3.2

- 首页增加显示星期与日期的动态迷你日历。
- 首页标题下方新增本月进度条和百分比。
- 优化首页大卡片，将"还有/已经"移至事件名称同行；首页筛选继续使用"倒数/正数"。

## 1.3.1

- 优化关于卡片标题布局。
- 将事件状态文案由"正数/倒数"调整为"已经/还有"。
- 事件卡片与桌面小组件将状态文字移至事件名称同行。

## 1.3.0

- 安装新版后自动清理旧安装包、旧下载状态与更新通知，不再继续显示“已准备好”。
- 保留暮蓝、朱砂、松烟、古金四套原始主题。
- 移除 Catppuccin、Rosé Pine、Nord、Solarized、Gruvbox 与 Dracula 配色。
- 新增 Bloom 的十二组浅色/深色主题：花瓣、雾蓝、草木、暖石、麦穗、水墨、琥珀、青金、涟漪、丹红、鼠尾草与紫语。
- 桌面小组件同步支持全部十六套主题。
## 1.1.0

- 更新日历应用图标，使用安全区内的透明前景与独立纯色背景，改善桌面返回动画。
- 修复长按应用图标点击“添加小组件”后只进入主界面的问题。
- 将小组件固定请求移入独立的透明 Activity，并兼容旧版快捷方式缓存。

## 1.0.0

- 新建、编辑、删除倒数日与正数日。
- 支持每年重复、置顶、分类、备注与本地提醒。
- 日子背景图片、分类封面、图片焦点调整。
- 新建草稿保存。
- 分类页面。
- 支持智能排序、日期排序、标题排序和创建时间排序。
- 支持十套配色、浅色/深色/跟随系统。
- 可横向、纵向缩放的桌面小组件。
- 小组件可使用当前事件背景图。
- 长按应用图标可请求添加小组件。
- 接入 07 | 24 日历应用图标与 Android 主题图标。
- 应用内更新：设置页检查更新，DownloadManager 下载，系统安装器确认。
- 核心用户数据保存在本地，无广告、无登录、无分析或默认事件。
