import 'package:flutter/material.dart';

import '../app_controller.dart';
import '../models.dart';
import '../ui_utils.dart';
import '../widgets/glass_surface.dart';

enum _MilestoneStep { list, sort, settings }

class MilestoneScreen extends StatefulWidget {
  const MilestoneScreen({super.key, required this.controller});
  final AppController controller;

  @override
  State<MilestoneScreen> createState() => _MilestoneScreenState();
}

class _MilestoneScreenState extends State<MilestoneScreen> {
  _MilestoneStep step = _MilestoneStep.list;
  final selected = <String>{};
  final exportOrder = <String>[];
  final exportTitle = TextEditingController(text: '纪念碑');
  static const String template = 'MINIMAL';
  String? workingAction;
  bool managingSelection = false;

  bool get selectionMode => managingSelection;

  @override
  void dispose() {
    exportTitle.dispose();
    super.dispose();
  }

  void _toggle(String id) => setState(() {
        managingSelection = true;
        if (!selected.add(id)) selected.remove(id);
      });

  void _clearSelection() => setState(() {
        selected.clear();
        managingSelection = false;
      });

  void _openSort() {
    if (selected.isEmpty) return;
    final milestones = widget.controller.snapshot!.milestones;
    setState(() {
      exportOrder
        ..clear()
        ..addAll(selected.where((id) => milestones.any((item) => item.id == id)));
      step = _MilestoneStep.sort;
    });
  }

  Future<void> _deleteSelected() async {
    if (selected.isEmpty) return;
    final yes = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('删除所选纪念碑？'),
        content: Text('将删除 ${selected.length} 个纪念碑，此操作无法撤销。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('取消')),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text('删除', style: TextStyle(color: Theme.of(dialogContext).colorScheme.error)),
          ),
        ],
      ),
    );
    if (yes != true) return;
    final ids = selected.toList(growable: false);
    for (final id in ids) {
      await widget.controller.deleteMilestone(id);
    }
    if (mounted) _clearSelection();
  }

  Future<void> _export(String action) async {
    if (exportOrder.isEmpty || workingAction != null) return;
    setState(() => workingAction = action);
    try {
      await widget.controller.exportMilestones(
        milestoneIds: exportOrder,
        action: action,
        template: template,
        title: exportTitle.text.trim().isEmpty ? '纪念碑' : exportTitle.text.trim(),
      );
      if (action == 'SAVE' && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('已保存到相册')),
        );
      }
    } catch (error) {
      debugPrint('纪念碑导出失败: $error');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('导出失败')),
        );
      }
    } finally {
      if (mounted) setState(() => workingAction = null);
    }
  }

  Future<void> _createMilestone() async {
    final snapshot = widget.controller.snapshot!;
    final dialogResult = await showDialog<Map<String, dynamic>>(
      context: context,
      barrierColor: Colors.black.withAlpha(snapshot.isDark ? 112 : 72),
      builder: (_) => _NewMonumentDialog(snapshot: snapshot),
    );
    if (dialogResult != null) await widget.controller.saveMilestone(dialogResult);
  }

  void _back() {
    if (step == _MilestoneStep.settings) {
      setState(() => step = _MilestoneStep.sort);
    } else if (step == _MilestoneStep.sort) {
      setState(() => step = _MilestoneStep.list);
    } else if (selectionMode) {
      _clearSelection();
    } else {
      Navigator.of(context).pop();
    }
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: widget.controller,
        builder: (context, _) {
          final snapshot = widget.controller.snapshot!;
          return PopScope<Object?>(
            canPop: step == _MilestoneStep.list && !selectionMode,
            onPopInvokedWithResult: (didPop, result) {
              if (!didPop) _back();
            },
            child: Scaffold(
              backgroundColor: Colors.transparent,
              body: SafeArea(
                bottom: false,
                child: Column(
                  children: [
                    _MilestoneTopBar(
                      step: step,
                      selectedCount: selected.length,
                      selectionMode: selectionMode,
                      onBack: _back,
                      onAdd: step == _MilestoneStep.list && !selectionMode ? _createMilestone : null,
                      onConfirm: step == _MilestoneStep.sort
                          ? () => setState(() => step = _MilestoneStep.settings)
                          : null,
                    ),
                    Expanded(
                      child: switch (step) {
                        _MilestoneStep.list => _MilestoneListPage(
                            controller: widget.controller,
                            snapshot: snapshot,
                            selected: selected,
                            selectionMode: selectionMode,
                            onToggle: _toggle,
                            onExport: _openSort,
                            onDelete: _deleteSelected,
                            onDone: _clearSelection,
                          ),
                        _MilestoneStep.sort => _MilestoneSortPage(
                            snapshot: snapshot,
                            ids: exportOrder,
                            onReorder: (oldIndex, newIndex) {
                              setState(() {
                                if (newIndex > oldIndex) newIndex -= 1;
                                final id = exportOrder.removeAt(oldIndex);
                                exportOrder.insert(newIndex, id);
                              });
                            },
                          ),
                        _MilestoneStep.settings => _MilestoneSettingsPage(
                            snapshot: snapshot,
                            title: exportTitle,
                            selectedCount: exportOrder.length,
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
        },
      );
}


class _NewMonumentDialog extends StatefulWidget {
  const _NewMonumentDialog({required this.snapshot});

  final AppSnapshot snapshot;

  @override
  State<_NewMonumentDialog> createState() => _NewMonumentDialogState();
}

class _NewMonumentDialogState extends State<_NewMonumentDialog> {
  final title = TextEditingController();
  final note = TextEditingController();
  late DateTime date;

  @override
  void initState() {
    super.initState();
    date = widget.snapshot.today;
  }

  @override
  void dispose() {
    title.dispose();
    note.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final picked = await showGlassCalendarDatePicker(
      context: context,
      initialDate: date,
      firstDate: DateTime(1900),
      lastDate: DateTime(2200),
      title: '纪念碑日期',
    );
    if (picked != null && mounted) setState(() => date = picked);
  }

  void _save() {
    final trimmedTitle = title.text.trim();
    if (trimmedTitle.isEmpty) return;
    Navigator.of(context).pop(<String, dynamic>{
      'title': trimmedTitle,
      'date': _iso(date),
      'note': note.text.trim(),
    });
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Dialog(
      backgroundColor: Colors.transparent,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      insetPadding: const EdgeInsets.symmetric(horizontal: 22, vertical: 24),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 400),
        child: GlassSurface(
          isDark: widget.snapshot.isDark,
          clarity: widget.snapshot.settings.glassClarity,
          radius: 30,
          borderOpacityScale: 1.12,
          padding: const EdgeInsets.fromLTRB(22, 22, 22, 18),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Container(
                      width: 42,
                      height: 42,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: scheme.primary.withAlpha(34),
                        border: Border.all(color: scheme.primary.withAlpha(82)),
                      ),
                      child: Icon(Icons.account_balance_rounded, color: scheme.primary, size: 21),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('新增纪念碑', style: Theme.of(context).textTheme.titleLarge),
                          const SizedBox(height: 2),
                          Text(
                            '记录只属于纪念碑页面的阶段节点',
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 20),
                TextField(
                  controller: title,
                  autofocus: true,
                  maxLength: 60,
                  textInputAction: TextInputAction.next,
                  decoration: const InputDecoration(
                    labelText: '名称',
                    hintText: '例如：第一次旅行',
                    counterText: '',
                    prefixIcon: Icon(Icons.edit_rounded),
                  ),
                ),
                const SizedBox(height: 12),
                GlassSurface(
                  isDark: widget.snapshot.isDark,
                  clarity: widget.snapshot.settings.glassClarity,
                  blur: false,
                  radius: 18,
                  borderOpacityScale: 0.82,
                  child: Material(
                    color: Colors.transparent,
                    child: InkWell(
                      onTap: _pickDate,
                      borderRadius: BorderRadius.circular(18),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                        child: Row(
                          children: [
                            Icon(Icons.event_rounded, color: scheme.primary, size: 21),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    '日期',
                                    style: Theme.of(context).textTheme.bodySmall,
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    longDateText(date),
                                    style: Theme.of(context).textTheme.titleMedium,
                                  ),
                                ],
                              ),
                            ),
                            Icon(
                              Icons.chevron_right_rounded,
                              color: scheme.onSurfaceVariant,
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: note,
                  minLines: 2,
                  maxLines: 5,
                  maxLength: 160,
                  decoration: const InputDecoration(
                    labelText: '备注',
                    hintText: '可以留空',
                    counterText: '',
                    alignLabelWithHint: true,
                    prefixIcon: Padding(
                      padding: EdgeInsets.only(bottom: 44),
                      child: Icon(Icons.notes_rounded),
                    ),
                  ),
                ),
                const SizedBox(height: 18),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () => Navigator.of(context).pop(),
                        style: OutlinedButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 13),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                        ),
                        child: const Text('取消'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: FilledButton.icon(
                        onPressed: _save,
                        icon: const Icon(Icons.add_rounded, size: 19),
                        label: const Text('添加'),
                        style: FilledButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 13),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                        ),
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

class _MilestoneTopBar extends StatelessWidget {
  const _MilestoneTopBar({
    required this.step,
    required this.selectedCount,
    required this.selectionMode,
    required this.onBack,
    this.onAdd,
    this.onConfirm,
  });
  final _MilestoneStep step;
  final int selectedCount;
  final bool selectionMode;
  final VoidCallback onBack;
  final VoidCallback? onAdd;
  final VoidCallback? onConfirm;

  @override
  Widget build(BuildContext context) {
    final title = switch (step) {
      _MilestoneStep.list => selectionMode
          ? (selectedCount > 0 ? '已选 $selectedCount' : '选择纪念碑')
          : '纪念碑',
      _MilestoneStep.sort => '调整顺序',
      _MilestoneStep.settings => '导出纪念碑',
    };
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 6, 18, 8),
      child: Row(
        children: [
          IconButton(tooltip: '返回', onPressed: onBack, icon: const Icon(Icons.arrow_back_rounded)),
          const SizedBox(width: 4),
          Expanded(child: Text(title, style: Theme.of(context).textTheme.titleLarge)),
          if (onAdd != null) IconButton(tooltip: '新增纪念碑', onPressed: onAdd, icon: const Icon(Icons.add_rounded)),
          if (onConfirm != null) TextButton(onPressed: onConfirm, child: const Text('确认')),
        ],
      ),
    );
  }
}

class _MilestoneListPage extends StatelessWidget {
  const _MilestoneListPage({
    required this.controller,
    required this.snapshot,
    required this.selected,
    required this.selectionMode,
    required this.onToggle,
    required this.onExport,
    required this.onDelete,
    required this.onDone,
  });

  final AppController controller;
  final AppSnapshot snapshot;
  final Set<String> selected;
  final bool selectionMode;
  final ValueChanged<String> onToggle;
  final VoidCallback onExport;
  final VoidCallback onDelete;
  final VoidCallback onDone;

  @override
  Widget build(BuildContext context) {
    final now = snapshot.today;
    final dayOfYear = DateTime(now.year, now.month, now.day)
        .difference(DateTime(now.year, 1, 1))
        .inDays +
    1;
    final yearProgress = dayOfYear / _daysInYear(now.year);
    final monthProgress = now.day / DateTime(now.year, now.month + 1, 0).day;
    return Stack(
      children: [
        ReorderableListView.builder(
          buildDefaultDragHandles: false,
          padding: EdgeInsets.fromLTRB(
            18,
            4,
            18,
            (selectionMode ? 116 : 24) + MediaQuery.paddingOf(context).bottom,
          ),
          itemCount: snapshot.milestones.length + 2,
          onReorder: (oldIndex, newIndex) async {
            // 顶部说明、进度区域和可选空白行不参与拖动排序。
            if (oldIndex < 1 || oldIndex > snapshot.milestones.length) return;
            final milestoneOldIndex = oldIndex - 1;
            var milestoneNewIndex = newIndex - 1;
            if (milestoneNewIndex > milestoneOldIndex) milestoneNewIndex -= 1;
            milestoneNewIndex = milestoneNewIndex.clamp(0, snapshot.milestones.length - 1).toInt();
            if (milestoneOldIndex == milestoneNewIndex) return;
            await controller.moveMilestoneToIndex(snapshot.milestones[milestoneOldIndex].id, milestoneNewIndex);
          },
          itemBuilder: (context, index) {
            if (index == 0) {
              return Padding(
                key: const ValueKey('progress'),
                padding: const EdgeInsets.only(bottom: 14),
                child: Row(
                  children: [
                    Expanded(child: _ProgressCard(snapshot: snapshot, title: '${now.year}年', value: yearProgress)),
                    const SizedBox(width: 12),
                    Expanded(child: _ProgressCard(snapshot: snapshot, title: '${now.month}月', value: monthProgress)),
                  ],
                ),
              );
            }
            if (index == snapshot.milestones.length + 1) {
              return SizedBox(
                key: const ValueKey('tail'),
                height: snapshot.milestones.isEmpty ? 60 : 1,
                child: snapshot.milestones.isEmpty
                    ? Align(
                        alignment: Alignment.centerLeft,
                        child: Text(
                          '还没有纪念碑',
                          style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                                color: Theme.of(context).colorScheme.onSurfaceVariant,
                              ),
                        ),
                      )
                    : null,
              );
            }
            final milestoneIndex = index - 1;
            final item = snapshot.milestones[milestoneIndex];
            return Padding(
              key: ValueKey(item.id),
              padding: const EdgeInsets.only(bottom: 12),
              child: ReorderableDelayedDragStartListener(
                index: index,
                child: _MilestoneCard(
                  snapshot: snapshot,
                  item: item,
                  selected: selected.contains(item.id),
                  selectionMode: selectionMode,
                  onTap: () => onToggle(item.id),
                ),
              ),
            );
          },
        ),
        if (selectionMode)
          Positioned(
            left: 18,
            right: 18,
            bottom: 18 + MediaQuery.paddingOf(context).bottom,
            child: GlassSurface(
              isDark: snapshot.isDark,
              clarity: snapshot.settings.glassClarity,
              blur: true,
              radius: 24,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              child: Row(
                children: [
                  Expanded(
                    child: SizedBox(
                      height: 52,
                      child: FilledButton.tonal(
                        onPressed: selected.isEmpty ? null : onDelete,
                        style: FilledButton.styleFrom(
                          foregroundColor: Theme.of(context).colorScheme.onErrorContainer,
                          backgroundColor: Theme.of(context).colorScheme.errorContainer,
                        ),
                        child: const Icon(Icons.delete_rounded, semanticLabel: '删除'),
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: SizedBox(
                      height: 52,
                      child: FilledButton(
                        onPressed: selected.isEmpty ? null : onExport,
                        child: const Icon(Icons.ios_share_rounded, semanticLabel: '导出'),
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: SizedBox(
                      height: 52,
                      child: OutlinedButton(
                        onPressed: onDone,
                        child: const Icon(Icons.check_rounded, semanticLabel: '完成'),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

class _ProgressCard extends StatelessWidget {
  const _ProgressCard({required this.snapshot, required this.title, required this.value});
  final AppSnapshot snapshot;
  final String title;
  final double value;

  @override
  Widget build(BuildContext context) {
    final safe = value.clamp(0.0, 1.0);
    return GlassSurface(
      isDark: snapshot.isDark,
      clarity: snapshot.settings.glassClarity,
      blur: false,
      radius: 18,
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleSmall?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
          const SizedBox(height: 10),
          Text('${(safe * 100).toStringAsFixed(2)}%', style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold)),
          const SizedBox(height: 10),
          LinearProgressIndicator(value: safe, minHeight: 6, borderRadius: BorderRadius.circular(99)),
        ],
      ),
    );
  }
}

class _MilestoneCard extends StatelessWidget {
  const _MilestoneCard({
    required this.snapshot,
    required this.item,
    required this.selected,
    required this.selectionMode,
    required this.onTap,
  });
  final AppSnapshot snapshot;
  final DayMilestoneModel item;
  final bool selected;
  final bool selectionMode;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final delta = DateTime(item.date.year, item.date.month, item.date.day)
        .difference(DateTime(snapshot.today.year, snapshot.today.month, snapshot.today.day))
        .inDays;
    final relative = dayDistanceLabel(delta);
    return GlassSurface(
      isDark: snapshot.isDark,
      clarity: snapshot.settings.glassClarity,
      blur: false,
      radius: 20,
      child: Material(
        color: selected ? Theme.of(context).colorScheme.primary.withAlpha(20) : Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(20),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Container(width: 4, height: 62, decoration: BoxDecoration(color: Theme.of(context).colorScheme.primary, borderRadius: BorderRadius.circular(99))),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(item.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: 6),
                      Text(compactDateText(item.date), style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
                      if (item.note.trim().isNotEmpty) ...[
                        const SizedBox(height: 6),
                        Text(item.note, maxLines: 1, overflow: TextOverflow.ellipsis, style: Theme.of(context).textTheme.bodySmall),
                      ],
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Text(relative, style: Theme.of(context).textTheme.labelLarge?.copyWith(color: Theme.of(context).colorScheme.primary)),
                if (selectionMode) ...[
                  const SizedBox(width: 12),
                  CircleAvatar(
                    radius: 12.5,
                    backgroundColor: selected ? Theme.of(context).colorScheme.primary : Theme.of(context).colorScheme.surfaceContainerHighest,
                    child: selected ? Icon(Icons.check_rounded, size: 17, color: Theme.of(context).colorScheme.onPrimary) : null,
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

class _MilestoneSortPage extends StatelessWidget {
  const _MilestoneSortPage({required this.snapshot, required this.ids, required this.onReorder});
  final AppSnapshot snapshot;
  final List<String> ids;
  final ReorderCallback onReorder;

  @override
  Widget build(BuildContext context) {
    final items = ids.map(snapshot.milestoneById).whereType<DayMilestoneModel>().toList(growable: false);
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(18, 10, 18, 12),
          child: Align(
            alignment: Alignment.centerRight,
            child: Text('总数：${items.length}', style: Theme.of(context).textTheme.bodyMedium),
          ),
        ),
        Expanded(
          child: ReorderableListView.builder(
            buildDefaultDragHandles: false,
            padding: EdgeInsets.fromLTRB(18, 0, 18, 24 + MediaQuery.paddingOf(context).bottom),
            onReorder: onReorder,
            itemCount: items.length,
            itemBuilder: (context, index) {
              final item = items[index];
              final delta = item.date.difference(snapshot.today).inDays;
              return Padding(
                key: ValueKey(item.id),
                padding: const EdgeInsets.only(bottom: 10),
                child: ReorderableDelayedDragStartListener(
                  index: index,
                  child: GlassSurface(
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
                                item.title,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
                              const SizedBox(height: 3),
                              Text(
                                compactDateText(item.date),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                                    ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 12),
                        Text(
                          dayDistanceLabel(delta),
                          maxLines: 1,
                          style: Theme.of(context).textTheme.titleSmall?.copyWith(
                                color: Theme.of(context).colorScheme.primary,
                                fontWeight: FontWeight.w600,
                              ),
                        ),
                      ],
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}

class _MilestoneSettingsPage extends StatelessWidget {
  const _MilestoneSettingsPage({
    required this.snapshot,
    required this.title,
    required this.selectedCount,
    required this.workingAction,
    required this.onShare,
    required this.onSave,
  });
  final AppSnapshot snapshot;
  final TextEditingController title;
  final int selectedCount;
  final String? workingAction;
  final VoidCallback onShare;
  final VoidCallback onSave;

  @override
  Widget build(BuildContext context) {
    return ListView(
      physics: const BouncingScrollPhysics(),
      padding: EdgeInsets.fromLTRB(18, 4, 18, 24 + MediaQuery.paddingOf(context).bottom),
      children: [
        TextField(controller: title, decoration: const InputDecoration(labelText: '标题')),
        const SizedBox(height: 20),
        Text('已选择 $selectedCount 个纪念碑', textAlign: TextAlign.center, style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
        const SizedBox(height: 14),
        Row(
          children: [
            Expanded(
              child: SizedBox(
                height: 72,
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
                height: 72,
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
    );
  }
}

int _daysInYear(int year) => DateTime(year + 1, 1, 1).difference(DateTime(year, 1, 1)).inDays;
String _iso(DateTime d) => '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
