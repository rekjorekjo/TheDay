import 'package:flutter/material.dart';

import '../app_controller.dart';
import '../glass_theme.dart';
import '../models.dart';
import '../widgets/glass_surface.dart';

const Map<String, String> glassPaletteNames = <String, String>{
  'MIDNIGHT': '夜航',
  'CINNABAR': '珊瑚',
  'PINE': '森境',
  'ANTIQUE_GOLD': '暮金',
  'BLOOM_PETAL': '樱雾',
  'BLOOM_MIST': '冰川',
  'BLOOM_VERDANT': '苔原',
  'BLOOM_STONE': '石墨',
  'BLOOM_WHEAT': '蜂蜜',
  'BLOOM_INK': '墨蓝',
  'BLOOM_AMBER': '琥珀',
  'BLOOM_LAPIS': '蓝宝',
  'BLOOM_RIPPLE': '极光',
  'BLOOM_CINNABAR': '绯红',
  'BLOOM_SAGE': '薄荷',
  'BLOOM_SPRING': '星紫',
};


const Map<String, String> glassBackgroundModeNames = <String, String>{
  'STATIC': '静态',
  'FLOW': '流光',
  'AURORA': '极光',
};

const Map<String, IconData> glassBackgroundModeIcons = <String, IconData>{
  'STATIC': Icons.pause_circle_outline_rounded,
  'FLOW': Icons.blur_circular_rounded,
  'AURORA': Icons.gradient_rounded,
};

const Map<String, String> glassTextureNames = <String, String>{
  'NONE': '纯净',
  'DIAGONAL': '雨水',
  'WAVE': '雪花',
  'STARS': '流星',
  'CONSTELLATION': '星河',
  'HEART': '爱心',
};

const Map<String, IconData> glassTextureIcons = <String, IconData>{
  'NONE': Icons.block_rounded,
  'DIAGONAL': Icons.water_drop_rounded,
  'WAVE': Icons.ac_unit_rounded,
  'STARS': Icons.south_east_rounded,
  'CONSTELLATION': Icons.auto_awesome_rounded,
  'HEART': Icons.favorite_rounded,
};

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({
    super.key,
    required this.controller,
    required this.bottomInset,
    required this.onOpenAbout,
  });

  final AppController controller;
  final double bottomInset;
  final VoidCallback onOpenAbout;

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  late double clarity;
  bool draggingClarity = false;

  @override
  void initState() {
    super.initState();
    clarity = widget.controller.snapshot!.settings.glassClarity.toDouble();
  }

  @override
  void didUpdateWidget(covariant SettingsScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!draggingClarity) {
      clarity = widget.controller.snapshot!.settings.glassClarity.toDouble();
    }
  }

  Future<void> _commit(GlassSettings settings) async {
    await widget.controller.updateSettings(settings);
    if (mounted) {
      setState(() {
        clarity = widget.controller.snapshot!.settings.glassClarity.toDouble();
      });
    }
  }

  Future<void> _pickReminderTime(GlassSettings settings) async {
    final value = await showTimePicker(
      context: context,
      initialTime: TimeOfDay(
        hour: settings.reminderHour,
        minute: settings.reminderMinute,
      ),
    );
    if (value != null) {
      await _commit(
        settings.copyWith(
          reminderHour: value.hour,
          reminderMinute: value.minute,
        ),
      );
    }
  }

  Future<void> _confirmClear() async {
    final yes = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('清空全部事件？'),
        content: const Text('所有倒数日、正数日和对应提醒都会被删除，无法撤销。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text('清空', style: TextStyle(color: Theme.of(dialogContext).colorScheme.error)),
          ),
        ],
      ),
    );
    if (yes == true) await widget.controller.clearAllEvents();
  }

  String _sortDirectionDescription(GlassSettings settings) {
    final asc = settings.sortDirection == 'ASCENDING';
    switch (settings.sortMode) {
      case 'DATE':
        return asc ? '日期由早到晚' : '日期由晚到早';
      case 'TITLE':
        return asc ? '名称正序' : '名称倒序';
      case 'CREATED':
        return asc ? '最早创建优先' : '最近创建优先';
      case 'SMART':
      default:
        return asc ? '同组事件由近到远' : '同组事件由远到近';
    }
  }

  @override
  Widget build(BuildContext context) {
    final snapshot = widget.controller.snapshot!;
    final settings = snapshot.settings;
    final scheme = Theme.of(context).colorScheme;

    return CustomScrollView(
      key: const PageStorageKey<String>('settings-scroll'),
      physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
      slivers: [
        SliverPadding(
          padding: EdgeInsets.fromLTRB(18, 8, 18, widget.bottomInset),
          sliver: SliverList(
            delegate: SliverChildListDelegate.fixed([
              SafeArea(
                bottom: false,
                child: Row(
                  children: [
                    Expanded(
                      child: Text('设置', style: Theme.of(context).textTheme.titleLarge),
                    ),
                    IconButton(
                      tooltip: '关于 The Day',
                      onPressed: widget.onOpenAbout,
                      icon: const Icon(Icons.more_horiz_rounded),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),

              _SettingsCard(
                snapshot: snapshot,
                icon: Icons.palette_rounded,
                title: '外观',
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text('玻璃清晰度', style: Theme.of(context).textTheme.labelLarge),
                      ),
                      Text('${clarity.round()}%', style: Theme.of(context).textTheme.bodySmall),
                    ],
                  ),
                  Slider(
                    value: clarity,
                    min: 0,
                    max: 100,
                    onChangeStart: (_) => draggingClarity = true,
                    onChanged: (value) => setState(() => clarity = value),
                    onChangeEnd: (value) {
                      draggingClarity = false;
                      _commit(settings.copyWith(glassClarity: value.round()));
                    },
                  ),
                  const SizedBox(height: 6),
                  _Divider(snapshot: snapshot),
                  Text(
                    '背景模式',
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                  ),
                  const SizedBox(height: 10),
                  LayoutBuilder(
                    builder: (context, constraints) {
                      final tileWidth = (constraints.maxWidth - 16) / 3;
                      return Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: glassBackgroundModeNames.entries.map((entry) {
                          return SizedBox(
                            width: tileWidth,
                            child: _TextureTile(
                              label: entry.value,
                              icon: glassBackgroundModeIcons[entry.key] ?? Icons.blur_on_rounded,
                              selected: settings.backgroundMotionMode == entry.key,
                              onTap: () => _commit(
                                settings.copyWith(backgroundMotionMode: entry.key),
                              ),
                            ),
                          );
                        }).toList(growable: false),
                      );
                    },
                  ),
                  _Divider(snapshot: snapshot),
                  const SizedBox(height: 18),
                  Text(
                    '背景纹理',
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                  ),
                  const SizedBox(height: 10),
                  LayoutBuilder(
                    builder: (context, constraints) {
                      final tileWidth = (constraints.maxWidth - 16) / 3;
                      return Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: glassTextureNames.entries.map((entry) {
                          return SizedBox(
                            width: tileWidth,
                            child: _TextureTile(
                              label: entry.value,
                              icon: glassTextureIcons[entry.key] ?? Icons.blur_on_rounded,
                              selected: settings.backgroundTexture == entry.key,
                              onTap: () => _commit(
                                settings.copyWith(backgroundTexture: entry.key),
                              ),
                            ),
                          );
                        }).toList(growable: false),
                      );
                    },
                  ),
                  const SizedBox(height: 18),
                  Text(
                    '主题配色',
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                  ),
                  const SizedBox(height: 12),
                  LayoutBuilder(
                    builder: (context, constraints) {
                      final tileWidth = (constraints.maxWidth - 24) / 4;
                      return Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: glassPaletteNames.entries.map((entry) {
                          return SizedBox(
                            width: tileWidth,
                            child: _PaletteTile(
                              label: entry.value,
                              palette: entry.key,
                              selected: settings.paletteStyle == entry.key,
                              onTap: () => _commit(settings.copyWith(paletteStyle: entry.key)),
                            ),
                          );
                        }).toList(growable: false),
                      );
                    },
                  ),
                ],
              ),
              const SizedBox(height: 14),

              _SettingsCard(
                snapshot: snapshot,
                icon: Icons.sort_rounded,
                title: '排序',
                children: [
                  _ChoiceRow(
                    label: '智能排序',
                    selected: settings.sortMode == 'SMART',
                    onTap: () => _commit(settings.copyWith(sortMode: 'SMART')),
                  ),
                  _Divider(snapshot: snapshot),
                  _ChoiceRow(
                    label: '按日期',
                    selected: settings.sortMode == 'DATE',
                    onTap: () => _commit(settings.copyWith(sortMode: 'DATE')),
                  ),
                  _Divider(snapshot: snapshot),
                  _ChoiceRow(
                    label: '按标题',
                    selected: settings.sortMode == 'TITLE',
                    onTap: () => _commit(settings.copyWith(sortMode: 'TITLE')),
                  ),
                  _Divider(snapshot: snapshot),
                  _ChoiceRow(
                    label: '按创建时间',
                    selected: settings.sortMode == 'CREATED',
                    onTap: () => _commit(settings.copyWith(sortMode: 'CREATED')),
                  ),
                  _Divider(snapshot: snapshot),
                  _SettingRow(
                    title: '排序方向',
                    subtitle: _sortDirectionDescription(settings),
                    trailing: OutlinedButton(
                      onPressed: () => _commit(
                        settings.copyWith(
                          sortDirection: settings.sortDirection == 'ASCENDING'
                              ? 'DESCENDING'
                              : 'ASCENDING',
                        ),
                      ),
                      child: Text(settings.sortDirection == 'ASCENDING' ? '升序' : '降序'),
                    ),
                  ),
                  _Divider(snapshot: snapshot),
                  _SettingRow(
                    title: '显示正数日',
                    subtitle: '关闭后，首页和小组件只显示倒数日和今天',
                    trailing: Switch(
                      value: settings.showPastEvents,
                      onChanged: (value) => _commit(settings.copyWith(showPastEvents: value)),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),

              _SettingsCard(
                snapshot: snapshot,
                icon: Icons.notifications_rounded,
                title: '提醒',
                children: [
                  _SettingRow(
                    title: '提醒时间',
                    subtitle:
                        '${settings.reminderHour.toString().padLeft(2, '0')}:${settings.reminderMinute.toString().padLeft(2, '0')}',
                    trailing: OutlinedButton(
                      onPressed: () => _pickReminderTime(settings),
                      child: const Text('调整'),
                    ),
                  ),
                  _Divider(snapshot: snapshot),
                  _SettingRow(
                    title: '通知权限',
                    subtitle: snapshot.notificationGranted ? '已允许' : '未允许，提醒不会显示',
                    trailing: snapshot.notificationGranted
                        ? null
                        : OutlinedButton(
                            onPressed: widget.controller.requestNotificationPermission,
                            child: const Text('申请权限'),
                          ),
                  ),
                ],
              ),
              const SizedBox(height: 14),

              SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  onPressed: snapshot.events.isEmpty ? null : _confirmClear,
                  icon: const Icon(Icons.delete_rounded),
                  label: const Text('清空全部事件'),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: scheme.error,
                    padding: const EdgeInsets.symmetric(vertical: 13),
                  ),
                ),
              ),
              const SizedBox(height: 28),
            ]),
          ),
        ),
      ],
    );
  }
}

class _SettingsCard extends StatelessWidget {
  const _SettingsCard({
    required this.snapshot,
    required this.icon,
    required this.title,
    required this.children,
  });

  final AppSnapshot snapshot;
  final IconData icon;
  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => GlassSurface(
        isDark: snapshot.isDark,
        clarity: snapshot.settings.glassClarity,
        radius: 22,
        blur: false,
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Icon(icon, color: Theme.of(context).colorScheme.primary),
                const SizedBox(width: 10),
                Expanded(child: Text(title, style: Theme.of(context).textTheme.titleLarge)),
              ],
            ),
            const SizedBox(height: 16),
            ...children,
          ],
        ),
      );
}

class _Divider extends StatelessWidget {
  const _Divider({required this.snapshot});
  final AppSnapshot snapshot;

  @override
  Widget build(BuildContext context) => Divider(
        height: 1,
        thickness: 0.6,
        color: withOpacitySafe(
          Theme.of(context).colorScheme.onSurface,
          snapshot.isDark ? 0.12 : 0.10,
        ),
      );
}

class _SettingRow extends StatelessWidget {
  const _SettingRow({
    required this.title,
    this.subtitle,
    this.trailing,
    this.onTap,
  });

  final String title;
  final String? subtitle;
  final Widget? trailing;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 10),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(title, style: Theme.of(context).textTheme.bodyLarge),
                      if (subtitle != null) ...[
                        const SizedBox(height: 2),
                        Text(subtitle!, style: Theme.of(context).textTheme.bodySmall),
                      ],
                    ],
                  ),
                ),
                if (trailing != null) trailing!,
              ],
            ),
          ),
        ),
      );
}

class _ChoiceRow extends StatelessWidget {
  const _ChoiceRow({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 10),
          child: Row(
            children: [
              Expanded(
                child: Text(label, style: Theme.of(context).textTheme.bodyLarge),
              ),
              Radio<bool>(value: true, groupValue: selected, onChanged: (_) => onTap()),
            ],
          ),
        ),
      );
}

class _TextureTile extends StatelessWidget {
  const _TextureTile({
    required this.label,
    required this.icon,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final IconData icon;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 120),
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            color: selected
                ? withOpacitySafe(scheme.primary, 0.12)
                : withOpacitySafe(scheme.onSurface, 0.025),
            border: Border.all(
              color: selected
                  ? withOpacitySafe(scheme.primary, 0.68)
                  : withOpacitySafe(scheme.onSurface, 0.10),
              width: selected ? 1.0 : 0.8,
            ),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 16, color: selected ? scheme.primary : scheme.onSurfaceVariant),
              const SizedBox(width: 6),
              Flexible(
                child: Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.labelMedium?.copyWith(
                        color: selected ? scheme.primary : scheme.onSurface,
                        fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                      ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PaletteTile extends StatelessWidget {
  const _PaletteTile({
    required this.label,
    required this.palette,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final String palette;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final ambience = ambienceFor(palette);
    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onTap,
        child: Container(
          padding: EdgeInsets.all(selected ? 4 : 0),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            color: selected ? ambience.accent.withAlpha(72) : Colors.transparent,
          ),
          child: AspectRatio(
            aspectRatio: 1.6,
            child: DecoratedBox(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(10),
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [ambience.primary, ambience.secondary, ambience.tertiary],
                ),
                border: Border.all(color: Colors.white.withAlpha(selected ? 76 : 88)),
              ),
              child: Center(
                child: Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.clip,
                  style: Theme.of(context).textTheme.labelMedium?.copyWith(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                      ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
