# The Day 隐私说明

The Day 是纯本地倒数日应用。

- 不含广告、统计、登录、账号、云同步或第三方追踪 SDK。
- 事件、设置均保存在应用私有目录中。
- 已关闭 Android 系统云备份与设备迁移备份。
- 通知仅由系统本地闹钟触发；小组件只读取本机数据。
- 卸载应用或在系统设置中清除应用数据，会删除全部事件。

## 应用更新

- 只有用户在设置页主动点击"检查更新"才会访问网络。
- 优先访问 GitHub Release 的 latest.json 获取更新信息。
- manifest 获取失败时，fallback 到 GitHub Releases API。
- 下载更新时访问 GitHub Release 资产地址。
- GitHub 可能收到 IP、时间和 User-Agent 等信息。
- 不上传事件、日期、分类、笔记、提醒或图片等用户数据。
- APK 保存在应用专属目录，安装由 Android 系统确认。
- 不使用分析、广告或跟踪服务。
