import 'package:flutter/material.dart';

import '../app_controller.dart';
import '../models.dart';
import '../ui_utils.dart';
import '../widgets/glass_surface.dart';

enum _ExportStep { select, sort, settings }

class ExportScreen extends StatefulWidget {
  const ExportScreen({
    super.key,
    required this.controller,
    required this.onOpenEvent,
  });

  final AppController controller;
  final ValueChanged<DayEventModel> onOpenEvent;

  @override
  State<ExportScreen> createState() => _ExportScreenState();
}

class _ExportScreenState extends State<ExportScreen> {
  final selected = <String>{};
  final orderedSelected = <String>[];
  final titleController = TextEditingController(text: '此日');
  _ExportStep step = _ExportStep.select;
  bool selectionMode = false;
  String? workingAction;
  String mode = 'LONG_IMAGE';
  static const String template = 'MINIMAL';
  int estimatedPages = 0;

  @override
  void dispose() {
    titleController.dispose();
    super.dispose();
  }

  List<DayEventModel> get _visibleEvents => widget.controller.snapshot!.orderedEvents;

  void _toggleSelection(String id) {
    setState(() {
      if (!selected.add(id)) selected.remove(id);
    });
  }

  void _openSort(String nextMode) {
    if (selected.isEmpty) return;
    final events = _visibleEvents;
    setState(() {
      mode = nextMode;
      orderedSelected
        ..clear()
        ..addAll(selected.where((id) => events.any((event) => event.id == id)));
      step = _ExportStep.sort;
    });
  }

  Future<void> _openSettings() async {
    setState(() => step = _ExportStep.settings);
    try {
      final count = await widget.controller.estimateExportPages(
        eventIds: orderedSelected,
        mode: mode,
      );
      if (mounted) setState(() => estimatedPages = count);
    } catch (error) {
      debugPrint('估算导出页数失败: $error');
      if (mounted) setState(() => estimatedPages = 0);
    }
  }

  Future<void> _export(String action) async {
    if (orderedSelected.isEmpty || workingAction != null) return;
    setState(() => workingAction = action);
    try {
      await widget.controller.exportEvents(
        eventIds: orderedSelected,
        mode: mode,
        action: action,
        template: template,
        title: titleController.text.trim(),
      );
      if (mounted && action == 'SAVE') {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('已保存到相册')),
        );
      }
    } catch (error) {
      debugPrint('导出日子失败: $error');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('导出失败')),
        );
      }
    } finally {
      if (mounted) setState(() => workingAction = null);
    }
  }

  void _back() {
    if (step == _ExportStep.settings) {
      setState(() => step = _ExportStep.sort);
    } else if (step == _ExportStep.sort) {
      setState(() => step = _ExportStep.select);
    } else {
      Navigator.of(context).pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    final snapshot = widget.controller.snapshot!;
    return PopScope<Object?>(
      canPop: step == _ExportStep.select,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop) _back();
      },
      child: Scaffold(
        backgroundColor: Colors.transparent,
        body: SafeArea(
          bottom: false,
          child: Column(
            children: [
              _TopBar(
                step: step,
                mode: mode,
                onBack: _back,
                selectionMode: selectionMode,
                onToggleSelectionMode: step == _ExportStep.select
                    ? () => setState(() {
                          if (selectionMode) {
                            selectionMode = false;
                          } else {
                            selected.clear();
                            selectionMode = true;
                          }
                        })
                    : null,
                onConfirmSort: step == _ExportStep.sort ? _openSettings : null,
              ),
              Expanded(
                child: switch (step) {
                  _ExportStep.select => _SelectionPage(
                      snapshot: snapshot,
                      selected: selected,
                      selectionMode: selectionMode,
                      onToggle: _toggleSelection,
                      onOpenEvent: widget.onOpenEvent,
                      onLongImage: () => _openSort('LONG_IMAGE'),
                      onList: () => _openSort('LIST'),
                    ),
                  _ExportStep.sort => _SortPage(
                      snapshot: snapshot,
                      ids: orderedSelected,
                      onReorder: (oldIndex, newIndex) {
                        setState(() {
                          if (newIndex > oldIndex) newIndex -= 1;
                          final id = orderedSelected.removeAt(oldIndex);
                          orderedSelected.insert(newIndex, id);
                        });
                      },
                    ),
                  _ExportStep.settings => _SettingsPage(
                      snapshot: snapshot,
                      titleController: titleController,
                      estimatedPages: estimatedPages,
                      selectedCount: orderedSelected.length,
                      workingAction: workingAction,
                      onShare: () => _export('SHARE'),
                      onSave: () => _export('SAVE'),
                    ),
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TopBar extends StatelessWidget {
  const _TopBar({
    required this.step,
    required this.mode,
    required this.onBack,
    required this.selectionMode,
    this.onToggleSelectionMode,
    this.onConfirmSort,
  });

  final _ExportStep step;
  final String mode;
  final VoidCallback onBack;
  final bool selectionMode;
  final VoidCallback? onToggleSelectionMode;
  final VoidCallback? onConfirmSort;

  @override
  Widget build(BuildContext context) {
    final title = switch (step) {
      _ExportStep.select => '导出',
      _ExportStep.sort => '调整顺序',
      _ExportStep.settings => mode == 'LIST' ? '导出列表' : '导出长图',
    };
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 6, 18, 8),
      child: Row(
        children: [
          IconButton(
            tooltip: '返回',
            onPressed: onBack,
            icon: const Icon(Icons.arrow_back_rounded),
          ),
          const SizedBox(width: 4),
          Expanded(child: Text(title, style: Theme.of(context).textTheme.titleLarge)),
          if (onToggleSelectionMode != null)
            TextButton(
              onPressed: onToggleSelectionMode,
              child: Text(selectionMode ? '完成' : '选择'),
            ),
          if (onConfirmSort != null)
            TextButton(onPressed: onConfirmSort, child: const Text('确认')),
        ],
      ),
    );
  }
}

class _SelectionPage extends StatelessWidget {
  const _SelectionPage({
    required this.snapshot,
    required this.selected,
    required this.selectionMode,
    required this.onToggle,
    required this.onOpenEvent,
    required this.onLongImage,
    required this.onList,
  });

  final AppSnapshot snapshot;
  final Set<String> selected;
  final bool selectionMode;
  final ValueChanged<String> onToggle;
  final ValueChanged<DayEventModel> onOpenEvent;
  final VoidCallback onLongImage;
  final VoidCallback onList;

  @override
  Widget build(BuildContext context) {
    final events = snapshot.orderedEvents;
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(18, 10, 18, 12),
          child: Row(
            children: [
              Expanded(
                child: _ExportTypeButton(
                  icon: Icons.image_rounded,
                  title: '导出长图',
                  enabled: selected.isNotEmpty,
                  highlighted: selectionMode && selected.isNotEmpty,
                  onTap: onLongImage,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _ExportTypeButton(
                  icon: Icons.list_rounded,
                  title: '导出列表',
                  enabled: selected.isNotEmpty,
                  highlighted: selectionMode && selected.isNotEmpty,
                  onTap: onList,
                ),
              ),
            ],
          ),
        ),
        Expanded(
          child: events.isEmpty
              ? Center(
                  child: Text(
                    '暂无事件',
                    style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant),
                  ),
                )
              : ListView.separated(
                  physics: const BouncingScrollPhysics(),
                  padding: EdgeInsets.fromLTRB(
                    18,
                    0,
                    18,
                    24 + MediaQuery.paddingOf(context).bottom,
                  ),
                  itemCount: events.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 9),
                  itemBuilder: (context, index) {
                    final event = events[index];
                    final checked = selectionMode && selected.contains(event.id);
                    return _ExportEventRow(
                      snapshot: snapshot,
                      event: event,
                      selected: checked,
                      selectionMode: selectionMode,
                      onTap: () => selectionMode ? onToggle(event.id) : onOpenEvent(event),
                    );
                  },
                ),
        ),
      ],
    );
  }
}

class _ExportEventRow extends StatelessWidget {
  const _ExportEventRow({
    required this.snapshot,
    required this.event,
    required this.selected,
    required this.selectionMode,
    required this.onTap,
  });

  final AppSnapshot snapshot;
  final DayEventModel event;
  final bool selected;
  final bool selectionMode;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return GlassSurface(
      isDark: snapshot.isDark,
      clarity: snapshot.settings.glassClarity,
      blur: false,
      radius: 18,
      child: Material(
        color: selected ? scheme.primaryContainer.withAlpha(174) : Colors.transparent,
        borderRadius: BorderRadius.circular(18),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(18),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 13),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        event.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '${normalizedCategory(event.category)} · ${compactDateText(event.effectiveDate)}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                              color: scheme.onSurfaceVariant,
                            ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                Text(
                  dayDistanceLabel(event.signedDays),
                  maxLines: 1,
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        color: scheme.primary,
                        fontWeight: FontWeight.w600,
                      ),
                ),
                if (selectionMode) ...[
                  const SizedBox(width: 12),
                  Container(
                    width: 25,
                    height: 25,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: selected ? scheme.primary : scheme.surfaceContainerHighest,
                    ),
                    alignment: Alignment.center,
                    child: selected
                        ? Icon(Icons.check_rounded, size: 17, color: scheme.onPrimary)
                        : null,
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ExportSortRow extends StatelessWidget {
  const _ExportSortRow({required this.snapshot, required this.event});

  final AppSnapshot snapshot;
  final DayEventModel event;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return GlassSurface(
      isDark: snapshot.isDark,
      clarity: snapshot.settings.glassClarity,
      blur: false,
      radius: 18,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  event.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 3),
                Text(
                  '${normalizedCategory(event.category)} · ${compactDateText(event.effectiveDate)}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Text(
            dayDistanceLabel(event.signedDays),
            maxLines: 1,
            style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  color: scheme.primary,
                  fontWeight: FontWeight.w600,
                ),
          ),
        ],
      ),
    );
  }
}

class _ExportTypeButton extends StatelessWidget {
  const _ExportTypeButton({
    required this.icon,
    required this.title,
    required this.enabled,
    required this.highlighted,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final bool enabled;
  final bool highlighted;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => SizedBox(
        height: 58,
        child: highlighted
            ? FilledButton.tonalIcon(
                onPressed: enabled ? onTap : null,
                icon: Icon(icon),
                label: Text(title),
              )
            : OutlinedButton.icon(
                onPressed: enabled ? onTap : null,
                icon: Icon(icon),
                label: Text(title),
              ),
      );
}

class _SortPage extends StatelessWidget {
  const _SortPage({required this.snapshot, required this.ids, required this.onReorder});
  final AppSnapshot snapshot;
  final List<String> ids;
  final ReorderCallback onReorder;

  @override
  Widget build(BuildContext context) {
    final events = ids.map(snapshot.eventById).whereType<DayEventModel>().toList(growable: false);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(18, 10, 18, 12),
          child: Align(
            alignment: Alignment.centerRight,
            child: Text('总数：${events.length}', style: Theme.of(context).textTheme.bodyMedium),
          ),
        ),
        Expanded(
          child: ReorderableListView.builder(
            buildDefaultDragHandles: false,
            proxyDecorator: (child, index, animation) => Material(
              type: MaterialType.transparency,
              child: child,
            ),
            padding: EdgeInsets.fromLTRB(18, 0, 18, 24 + MediaQuery.paddingOf(context).bottom),
            itemCount: events.length,
            onReorder: onReorder,
            itemBuilder: (context, index) {
              final event = events[index];
              return Padding(
                key: ValueKey(event.id),
                padding: const EdgeInsets.only(bottom: 9),
                child: _ResponsiveReorderItem(
                  index: index,
                  child: _ExportSortRow(snapshot: snapshot, event: event),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}

class _ResponsiveReorderItem extends StatefulWidget {
  const _ResponsiveReorderItem({required this.index, required this.child});

  final int index;
  final Widget child;

  @override
  State<_ResponsiveReorderItem> createState() => _ResponsiveReorderItemState();
}

class _ResponsiveReorderItemState extends State<_ResponsiveReorderItem> {
  bool pressed = false;

  void _setPressed(bool value) {
    if (pressed == value || !mounted) return;
    setState(() => pressed = value);
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Listener(
      behavior: HitTestBehavior.opaque,
      onPointerDown: (_) => _setPressed(true),
      onPointerUp: (_) => _setPressed(false),
      onPointerCancel: (_) => _setPressed(false),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 55),
        curve: Curves.easeOutCubic,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(19),
          border: Border.all(
            color: pressed ? scheme.primary.withAlpha(112) : Colors.transparent,
            width: 1.2,
          ),
          boxShadow: pressed
              ? [
                  BoxShadow(
                    color: scheme.primary.withAlpha(34),
                    blurRadius: 16,
                    spreadRadius: -6,
                    offset: const Offset(0, 6),
                  ),
                ]
              : const [],
        ),
        child: ReorderableDelayedDragStartListener(
          index: widget.index,
          child: widget.child,
        ),
      ),
    );
  }
}

class _SettingsPage extends StatelessWidget {
  const _SettingsPage({
    required this.snapshot,
    required this.titleController,
    required this.estimatedPages,
    required this.selectedCount,
    required this.workingAction,
    required this.onShare,
    required this.onSave,
  });

  final AppSnapshot snapshot;
  final TextEditingController titleController;
  final int estimatedPages;
  final int selectedCount;
  final String? workingAction;
  final VoidCallback onShare;
  final VoidCallback onSave;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      padding: EdgeInsets.fromLTRB(18, 14, 18, 24 + MediaQuery.paddingOf(context).bottom),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextField(
            controller: titleController,
            decoration: const InputDecoration(labelText: '标题'),
          ),
          const SizedBox(height: 20),
          Text(
            '预计导出 $estimatedPages 页图片',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: SizedBox(
                  height: 76,
                  child: OutlinedButton.icon(
                    onPressed: selectedCount == 0 || (workingAction != null && workingAction != 'SHARE') ? null : onShare,
                    icon: workingAction == 'SHARE'
                        ? const SizedBox.square(dimension: 20, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.ios_share_rounded),
                    label: Text(workingAction == 'SHARE' ? '生成中' : '分享'),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: SizedBox(
                  height: 76,
                  child: FilledButton.icon(
                    onPressed: selectedCount == 0 || (workingAction != null && workingAction != 'SAVE') ? null : onSave,
                    icon: workingAction == 'SAVE'
                        ? const SizedBox.square(dimension: 20, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.done_all_rounded),
                    label: Text(workingAction == 'SAVE' ? '生成中' : '保存到相册'),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
