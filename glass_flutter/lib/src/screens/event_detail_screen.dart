import 'dart:io';

import 'package:flutter/material.dart';

import '../app_controller.dart';
import '../glass_route.dart';
import '../models.dart';
import '../ui_utils.dart';
import '../widgets/event_widgets.dart';
import '../widgets/glass_surface.dart';
import 'event_editor_screen.dart';
import 'image_transform_screen.dart';

class EventDetailScreen extends StatelessWidget {
  const EventDetailScreen({
    super.key,
    required this.controller,
    required this.eventId,
  });

  final AppController controller;
  final String eventId;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) => _buildContent(context),
    );
  }

  Future<void> _delete(BuildContext context, DayEventModel event) async {
    final shouldDelete = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('删除这个日子？'),
        content: const Text('此操作无法撤销。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text('删除', style: TextStyle(color: Theme.of(dialogContext).colorScheme.error)),
          ),
        ],
      ),
    );
    if (shouldDelete != true || !context.mounted) return;
    await controller.deleteEvent(event.id);
    if (context.mounted) Navigator.of(context).pop();
  }

  Future<void> _showShare(BuildContext context, DayEventModel event) async {
    final choice = await showModalBottomSheet<Map<String, String>>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _MemoryImageSheet(
        controller: controller,
        event: event,
      ),
    );
    if (choice == null) return;
    try {
      await controller.shareEventImage(
        event.id,
        choice['action'] ?? 'SHARE',
        choice['template'] ?? 'MINIMAL',
      );
      if (choice['action'] == 'SAVE' && context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('已保存到相册')),
        );
      }
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('分享失败')),
        );
      }
    }
  }

  Future<void> _adjustImage(BuildContext context, DayEventModel event) async {
    if (event.backgroundImage == null) return;
    final updated = await Navigator.of(context).push<EventImageModel>(
      glassRoute<EventImageModel>(
        controller: controller,
        builder: (_) => ImageTransformScreen(
          snapshot: controller.snapshot!,
          event: event,
          detail: true,
          title: '调整详情图片',
        ),
      ),
    );
    if (updated != null) {
      await controller.saveEvent(event.toNativeJson(imageOverride: updated));
    }
  }

  Future<void> _openEditor(BuildContext context, DayEventModel event) async {
    await Navigator.of(context).push(
      glassRoute<void>(
        controller: controller,
        builder: (_) => EventEditorScreen(
          controller: controller,
          event: event,
          showBackButton: true,
          bottomInset: 28 + MediaQuery.paddingOf(context).bottom,
          onSaved: (_) => Navigator.of(context).pop(),
        ),
      ),
    );
  }

  Widget _buildContent(BuildContext context) {
    final snapshot = controller.snapshot!;
    final event = snapshot.eventById(eventId);
    if (event == null) return const SizedBox.shrink();

    final scheme = Theme.of(context).colorScheme;
    final clarity = snapshot.settings.glassClarity;

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
                6,
                18,
                28 + MediaQuery.paddingOf(context).bottom,
              ),
              sliver: SliverList(
                delegate: SliverChildListDelegate.fixed([
                  Row(
                    children: [
                      IconButton(
                        tooltip: '返回',
                        onPressed: () => Navigator.of(context).maybePop(),
                        icon: const Icon(Icons.arrow_back_rounded),
                      ),
                      const SizedBox(width: 4),
                      Expanded(child: Text('日子', style: Theme.of(context).textTheme.titleLarge)),
                      IconButton(
                        tooltip: '删除',
                        onPressed: () => _delete(context, event),
                        icon: Icon(Icons.delete_rounded, color: scheme.error),
                      ),
                      IconButton(
                        tooltip: '编辑',
                        onPressed: () => _openEditor(context, event),
                        icon: const Icon(Icons.edit_rounded),
                      ),
                      _DetailsMenu(event: event),
                    ],
                  ),
                  const SizedBox(height: 10),
                  _DetailHero(
                    event: event,
                    snapshot: snapshot,
                    onAdjustImage: event.backgroundImage == null
                        ? null
                        : () => _adjustImage(context, event),
                  ),
                  if (event.note.trim().isNotEmpty) ...[
                    const SizedBox(height: 14),
                    GlassSurface(
                      isDark: snapshot.isDark,
                      clarity: clarity,
                      blur: false,
                      radius: 22,
                      padding: const EdgeInsets.all(18),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Icon(Icons.notes_rounded, color: scheme.primary),
                              const SizedBox(width: 8),
                              Text('备注', style: Theme.of(context).textTheme.titleMedium),
                            ],
                          ),
                          const SizedBox(height: 12),
                          Text(
                            event.note,
                            style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                                  color: scheme.onSurfaceVariant,
                                ),
                          ),
                        ],
                      ),
                    ),
                  ],
                  const SizedBox(height: 14),
                  Row(
                    children: [
                      Expanded(
                        child: _DetailActionButton(
                          icon: Icons.vertical_align_top_rounded,
                          text: '置顶',
                          selected: event.isPinned,
                          onTap: () => controller.togglePinned(event.id),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: _DetailActionButton(
                          icon: Icons.share_rounded,
                          text: '分享纪念图',
                          onTap: () => _showShare(context, event),
                        ),
                      ),
                      if (snapshot.canPinWidget) ...[
                        const SizedBox(width: 10),
                        Expanded(
                          child: _DetailActionButton(
                            icon: Icons.widgets_rounded,
                            text: '添加小组件',
                            onTap: () async {
                              try {
                                await controller.pinWidget(event.id);
                              } catch (_) {
                                if (context.mounted) {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    const SnackBar(content: Text('无法添加小组件')),
                                  );
                                }
                              }
                            },
                          ),
                        ),
                      ],
                    ],
                  ),
                  const SizedBox(height: 24),
                ]),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MemoryImageSheet extends StatefulWidget {
  const _MemoryImageSheet({
    required this.controller,
    required this.event,
  });

  final AppController controller;
  final DayEventModel event;

  @override
  State<_MemoryImageSheet> createState() => _MemoryImageSheetState();
}

class _MemoryImageSheetState extends State<_MemoryImageSheet> {
  static const String template = 'MINIMAL';
  String? previewPath;
  bool loading = true;
  bool failed = false;
  int renderGeneration = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _renderPreview();
    });
  }

  Future<void> _renderPreview() async {
    final generation = ++renderGeneration;
    if (mounted) {
      setState(() {
        loading = true;
        failed = false;
      });
    }
    try {
      final path = await widget.controller.renderEventImagePreview(widget.event.id, template);
      if (!mounted || generation != renderGeneration) return;
      setState(() {
        previewPath = path;
        loading = false;
      });
    } catch (_) {
      if (!mounted || generation != renderGeneration) return;
      setState(() {
        loading = false;
        failed = previewPath == null;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final snapshot = widget.controller.snapshot!;
    final image = widget.event.backgroundImage;
    final rawAspect = image != null && image.width > 0 && image.height > 0
        ? image.width / image.height
        : 0.75;
    final aspect = rawAspect.clamp(0.60, 1.50).toDouble();
    final maxPreviewHeight = MediaQuery.sizeOf(context).height * 0.56;

    return SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 0, 14, 14),
        child: GlassSurface(
          isDark: snapshot.isDark,
          clarity: snapshot.settings.glassClarity,
          blur: true,
          radius: 28,
          padding: const EdgeInsets.fromLTRB(20, 10, 20, 20),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                SizedBox(
                  height: 48,
                  child: Center(
                    child: Text(
                      '分享纪念图',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                ),
                const SizedBox(height: 14),
                ConstrainedBox(
                  constraints: BoxConstraints(maxHeight: maxPreviewHeight),
                  child: AspectRatio(
                    aspectRatio: aspect,
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(18),
                      child: Stack(
                        fit: StackFit.expand,
                        children: [
                          if (previewPath != null)
                            Image.file(
                              File(previewPath!),
                              fit: BoxFit.contain,
                              gaplessPlayback: true,
                            )
                          else
                            ColoredBox(
                              color: Theme.of(context).colorScheme.surfaceContainerHigh,
                            ),
                          if (loading)
                            ColoredBox(
                              color: Colors.black.withAlpha(previewPath == null ? 0 : 28),
                              child: const Center(
                                child: CircularProgressIndicator(strokeWidth: 2.4),
                              ),
                            ),
                          if (failed)
                            Center(
                              child: Text(
                                '纪念图生成失败',
                                style: TextStyle(color: Theme.of(context).colorScheme.error),
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 18),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: loading || failed
                            ? null
                            : () => Navigator.of(context).pop(<String, String>{
                                  'action': 'SHARE',
                                  'template': template,
                                }),
                        child: const Text('分享纪念图'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: FilledButton(
                        onPressed: loading || failed
                            ? null
                            : () => Navigator.of(context).pop(<String, String>{
                                  'action': 'SAVE',
                                  'template': template,
                                }),
                        child: const Text('保存到相册'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _DetailHero extends StatelessWidget {
  const _DetailHero({
    required this.event,
    required this.snapshot,
    required this.onAdjustImage,
  });

  final DayEventModel event;
  final AppSnapshot snapshot;
  final VoidCallback? onAdjustImage;

  @override
  Widget build(BuildContext context) {
    final image = event.backgroundImage;
    final hasImage = image?.filePath?.isNotEmpty == true;
    final scheme = Theme.of(context).colorScheme;
    final primaryText = hasImage ? Colors.white : scheme.onSurface;
    final secondaryText = hasImage ? Colors.white.withAlpha(214) : scheme.onSurfaceVariant;

    return LayoutBuilder(
      builder: (context, constraints) {
        var height = 300.0;
        if (hasImage) {
          height = constraints.maxWidth / detailImagePreviewAspectRatio(image!);
        }

        Widget content = SizedBox(
          height: height,
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (hasImage) EventImage(image: image!, detail: true),
              if (hasImage)
                DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [Colors.black.withAlpha(28), Colors.black.withAlpha(128)],
                    ),
                  ),
                ),
              Center(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 30),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Flexible(
                            child: Text(
                              event.title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              textAlign: TextAlign.center,
                              style: Theme.of(context).textTheme.headlineMedium?.copyWith(color: primaryText),
                            ),
                          ),
                          if (event.signedDays != 0) ...[
                            const SizedBox(width: 6),
                            Text(
                              event.signedDays > 0 ? '还有' : '已经',
                              style: Theme.of(context).textTheme.titleLarge?.copyWith(color: secondaryText),
                            ),
                          ],
                        ],
                      ),
                      const SizedBox(height: 24),
                      if (event.signedDays == 0)
                        Text('今天', style: Theme.of(context).textTheme.displayLarge?.copyWith(color: primaryText))
                      else
                        Row(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Text(
                              event.signedDays.abs().toString(),
                              style: Theme.of(context).textTheme.displayLarge?.copyWith(color: primaryText),
                            ),
                            Padding(
                              padding: const EdgeInsets.only(left: 6, bottom: 8),
                              child: Text(
                                '天',
                                style: Theme.of(context).textTheme.titleLarge?.copyWith(color: secondaryText),
                              ),
                            ),
                          ],
                        ),
                      const SizedBox(height: 18),
                      Text(
                        '${longDateText(event.effectiveDate)} · ${weekdayText(event.effectiveDate)}',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.bodyLarge?.copyWith(color: secondaryText),
                      ),
                    ],
                  ),
                ),
              ),
              if (hasImage && onAdjustImage != null)
                Positioned(
                  top: 10,
                  right: 10,
                  child: Material(
                    color: Colors.black.withAlpha(82),
                    shape: const CircleBorder(),
                    child: IconButton(
                      tooltip: '调整详情图片',
                      onPressed: onAdjustImage,
                      icon: const Icon(Icons.touch_app_rounded, color: Colors.white),
                    ),
                  ),
                ),
            ],
          ),
        );

        if (hasImage) {
          return GlassSurface(
            isDark: snapshot.isDark,
            clarity: 100,
            blur: false,
            radius: 24,
            borderOpacityScale: 0.92,
            child: content,
          );
        }
        return GlassSurface(
          isDark: snapshot.isDark,
          clarity: snapshot.settings.glassClarity,
          blur: false,
          radius: 24,
          child: content,
        );
      },
    );
  }
}

class _DetailsMenu extends StatelessWidget {
  const _DetailsMenu({required this.event});
  final DayEventModel event;

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton<void>(
      tooltip: '详细信息',
      icon: const Icon(Icons.more_horiz_rounded),
      constraints: const BoxConstraints(minWidth: 270, maxWidth: 340),
      itemBuilder: (_) => [
        PopupMenuItem<void>(
          enabled: false,
          child: Text('详细信息', style: Theme.of(context).textTheme.titleSmall),
        ),
        PopupMenuItem<void>(
          enabled: false,
          child: _DetailRow(icon: Icons.calendar_month_rounded, label: '日期', value: longDateText(event.date)),
        ),
        PopupMenuItem<void>(
          enabled: false,
          child: _DetailRow(icon: Icons.repeat_rounded, label: '重复', value: event.repeatMode == 'YEARLY' ? '每年' : '不重复'),
        ),
        PopupMenuItem<void>(
          enabled: false,
          child: _DetailRow(icon: Icons.notifications_rounded, label: '提醒', value: reminderText(event.reminderDaysBefore)),
        ),
        if (event.category.trim().isNotEmpty)
          PopupMenuItem<void>(
            enabled: false,
            child: _DetailRow(icon: Icons.label_rounded, label: '分类', value: event.category),
          ),
      ],
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({required this.icon, required this.label, required this.value});
  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => SizedBox(
        width: 300,
        child: Row(
          children: [
            Icon(icon, size: 20, color: Theme.of(context).colorScheme.primary),
            const SizedBox(width: 12),
            Expanded(child: Text(label, style: Theme.of(context).textTheme.bodyMedium)),
            const SizedBox(width: 10),
            Flexible(
              child: Text(
                value,
                textAlign: TextAlign.end,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ),
          ],
        ),
      );
}

class _DetailActionButton extends StatelessWidget {
  const _DetailActionButton({
    required this.icon,
    required this.text,
    required this.onTap,
    this.selected = false,
  });

  final IconData icon;
  final String text;
  final VoidCallback onTap;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: selected ? scheme.primary : scheme.onSurface.withAlpha(16),
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: onTap,
        child: SizedBox(
          height: 64,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 7),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(icon, size: 22, color: selected ? scheme.onPrimary : scheme.onSurfaceVariant),
                const SizedBox(height: 3),
                FittedBox(
                  fit: BoxFit.scaleDown,
                  child: Text(
                    text,
                    maxLines: 1,
                    style: Theme.of(context).textTheme.labelMedium?.copyWith(
                          color: selected ? scheme.onPrimary : scheme.onSurfaceVariant,
                        ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
