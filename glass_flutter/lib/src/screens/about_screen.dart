import 'dart:async';

import 'package:flutter/material.dart';

import '../app_controller.dart';
import '../glass_route.dart';
import '../native_bridge.dart';
import '../widgets/glass_surface.dart';
import 'document_screen.dart';

class AboutScreen extends StatefulWidget {
  const AboutScreen({super.key, required this.controller});

  final AppController controller;

  @override
  State<AboutScreen> createState() => _AboutScreenState();
}

class _AboutScreenState extends State<AboutScreen> {
  UpdateStatusModel? updateStatus;
  Timer? updateTimer;
  bool updateBusy = false;

  @override
  void initState() {
    super.initState();
    _refreshUpdateStatus();
    updateTimer = Timer.periodic(const Duration(seconds: 2), (_) {
      final state = updateStatus?.state;
      if (state == 'WAITING' || state == 'DOWNLOADING' || state == 'VERIFYING') {
        _refreshUpdateStatus();
      }
    });
  }

  @override
  void dispose() {
    updateTimer?.cancel();
    super.dispose();
  }

  Future<void> _refreshUpdateStatus() async {
    try {
      final status = await widget.controller.getUpdateStatus();
      if (mounted) setState(() => updateStatus = status);
    } catch (_) {
      // Keep the About page quiet if update status cannot be read.
    }
  }

  Future<void> _handleUpdateTap() async {
    if (updateBusy) return;
    final state = updateStatus?.state ?? 'NONE';
    if (state == 'WAITING' || state == 'DOWNLOADING' || state == 'VERIFYING') return;

    setState(() => updateBusy = true);
    try {
      if (state == 'READY') {
        await widget.controller.requestInstallUpdate();
        await _refreshUpdateStatus();
      } else {
        final status = await widget.controller.checkForUpdate();
        if (mounted) setState(() => updateStatus = status);
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('检查更新失败')),
        );
      }
    } finally {
      if (mounted) setState(() => updateBusy = false);
    }
  }

  Future<void> _setWifiOnly(bool value) async {
    try {
      final status = await widget.controller.setUpdateWifiOnly(value);
      if (mounted) setState(() => updateStatus = status);
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final snapshot = widget.controller.snapshot!;
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SafeArea(
        bottom: false,
        child: CustomScrollView(
          physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
          slivers: [
            SliverPadding(
              padding: EdgeInsets.fromLTRB(
                18,
                10,
                18,
                28 + MediaQuery.paddingOf(context).bottom,
              ),
              sliver: SliverList(
                delegate: SliverChildListDelegate.fixed([
                  Row(
                    children: [
                      IconButton(
                        tooltip: '返回',
                        onPressed: () => Navigator.of(context).pop(),
                        icon: const Icon(Icons.arrow_back_rounded),
                      ),
                      const SizedBox(width: 4),
                      Text('关于', style: Theme.of(context).textTheme.titleLarge),
                    ],
                  ),
                  const SizedBox(height: 26),
                  Center(
                    child: Column(
                      children: [
                        ClipRRect(
                          borderRadius: BorderRadius.circular(22),
                          child: Image.asset('assets/app_icon.png', width: 82, height: 82),
                        ),
                        const SizedBox(height: 12),
                        Text('The Day', style: Theme.of(context).textTheme.headlineMedium),
                        const SizedBox(height: 4),
                        Text('当前版本 ${snapshot.versionName}', style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  _AboutCard(
                    isDark: snapshot.isDark,
                    clarity: snapshot.settings.glassClarity,
                    children: [
                      _AboutRow(
                        title: '检查更新',
                        trailing: _updateTrailing(context),
                        onTap: _handleUpdateTap,
                      ),
                      _Hairline(isDark: snapshot.isDark),
                      _SwitchRow(
                        title: '仅通过 Wi-Fi 下载更新',
                        value: updateStatus?.wifiOnly ?? true,
                        onChanged: _setWifiOnly,
                      ),
                      _Hairline(isDark: snapshot.isDark),
                      _AboutRow(
                        title: '更新说明',
                        onTap: () => _doc(context, '更新说明', 'UPDATE_NOTES'),
                      ),
                      _Hairline(isDark: snapshot.isDark),
                      _AboutRow(
                        title: '使用说明',
                        onTap: () => _doc(context, '使用说明', 'USAGE_GUIDE'),
                      ),
                      _Hairline(isDark: snapshot.isDark),
                      _AboutRow(
                        title: '隐私政策',
                        onTap: () => _doc(context, '隐私政策', 'PRIVACY_POLICY'),
                      ),
                      _Hairline(isDark: snapshot.isDark),
                      _AboutRow(
                        title: '开源许可与第三方组件',
                        onTap: () => _doc(context, '开源许可与第三方组件', 'OPEN_SOURCE_NOTICES'),
                      ),
                      _Hairline(isDark: snapshot.isDark),
                      _AboutRow(
                        title: 'GitHub 仓库',
                        external: true,
                        onTap: () => widget.controller.openExternal(
                          'https://github.com/rekjorekjo/TheDay',
                        ),
                      ),
                    ],
                  ),
                ]),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _updateTrailing(BuildContext context) {
    if (updateBusy) {
      return const SizedBox.square(
        dimension: 17,
        child: CircularProgressIndicator(strokeWidth: 1.8),
      );
    }
    final status = updateStatus;
    if (status == null) return const Text('当前已是最新版本');

    // A manual check result is newer information than a persisted download state.
    // This prevents an old FAILED download from making every later successful
    // check look like an immediate failure.
    if (status.extra == 'UP_TO_DATE') return const Text('当前已是最新版本');
    if (status.extra == 'CHECK_FAILED') return const Text('检查失败');
    if (status.extra == 'DOWNLOAD_FAILED') return const Text('重试');

    switch (status.state) {
      case 'WAITING':
        return const Text('等待下载');
      case 'DOWNLOADING':
        return Text(status.progressPercent == null ? '下载中' : '${status.progressPercent}%');
      case 'VERIFYING':
        return const Text('校验中');
      case 'READY':
        return const Text('安装更新');
      case 'FAILED':
        return const Text('重试');
      default:
        return const Icon(Icons.chevron_right_rounded, size: 20);
    }
  }

  void _doc(BuildContext context, String title, String key) {
    Navigator.of(context).push(
      glassRoute<void>(
        controller: widget.controller,
        builder: (_) => DocumentScreen(
          controller: widget.controller,
          title: title,
          keyName: key,
        ),
      ),
    );
  }
}

class _AboutCard extends StatelessWidget {
  const _AboutCard({
    required this.isDark,
    required this.clarity,
    required this.children,
  });

  final bool isDark;
  final int clarity;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => GlassSurface(
        isDark: isDark,
        clarity: clarity,
        blur: false,
        radius: 24,
        child: Column(children: children),
      );
}

class _AboutRow extends StatelessWidget {
  const _AboutRow({
    required this.title,
    required this.onTap,
    this.external = false,
    this.trailing,
  });

  final String title;
  final VoidCallback onTap;
  final bool external;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) => Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 17),
            child: Row(
              children: [
                Expanded(child: Text(title, style: Theme.of(context).textTheme.bodyLarge)),
                trailing ??
                    Icon(
                      external ? Icons.open_in_new_rounded : Icons.chevron_right_rounded,
                      size: 19,
                    ),
              ],
            ),
          ),
        ),
      );
}

class _SwitchRow extends StatelessWidget {
  const _SwitchRow({
    required this.title,
    required this.value,
    required this.onChanged,
    this.subtitle,
  });

  final String title;
  final String? subtitle;
  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: Theme.of(context).textTheme.bodyLarge),
                  if (subtitle != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      subtitle!,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: Theme.of(context).colorScheme.onSurfaceVariant,
                          ),
                    ),
                  ],
                ],
              ),
            ),
            Switch(value: value, onChanged: onChanged),
          ],
        ),
      );
}

class _Hairline extends StatelessWidget {
  const _Hairline({required this.isDark});

  final bool isDark;

  @override
  Widget build(BuildContext context) => Divider(
        height: 1,
        thickness: 0.5,
        indent: 18,
        endIndent: 18,
        color: Theme.of(context).colorScheme.onSurface.withAlpha(isDark ? 28 : 22),
      );
}
