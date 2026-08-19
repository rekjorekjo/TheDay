import 'dart:async';

import 'package:flutter/material.dart';

import '../app_controller.dart';
import '../models.dart';
import '../ui_utils.dart';
import '../widgets/glass_surface.dart';

class EventEditorScreen extends StatefulWidget {
  const EventEditorScreen({
    super.key,
    required this.controller,
    required this.bottomInset,
    required this.onSaved,
    this.event,
    this.showBackButton = false,
  });

  final AppController controller;
  final double bottomInset;
  final ValueChanged<DayEventModel?> onSaved;
  final DayEventModel? event;
  final bool showBackButton;

  @override
  State<EventEditorScreen> createState() => _EventEditorScreenState();
}

class _EventEditorScreenState extends State<EventEditorScreen> {
  late final TextEditingController titleController;
  late final TextEditingController categoryController;
  late final TextEditingController noteController;
  late DateTime date;
  late bool yearly;
  late bool pinned;
  late int reminderDays;
  EventImageModel? image;
  bool saving = false;
  bool pickingImage = false;
  bool draftPersistenceEnabled = true;
  Timer? draftTimer;

  bool get isNewEvent => widget.event == null;
  DateTime get defaultNewDate => widget.controller.snapshot?.today ?? DateTime.now();

  bool get hasDraftContent {
    if (!isNewEvent) return false;
    return titleController.text.isNotEmpty ||
        categoryController.text.isNotEmpty ||
        noteController.text.isNotEmpty ||
        !_sameDay(date, defaultNewDate) ||
        yearly ||
        pinned ||
        reminderDays >= 0 ||
        image != null;
  }

  @override
  void initState() {
    super.initState();
    final event = widget.event;
    final draft = event == null ? widget.controller.snapshot?.newEventDraft : null;
    titleController = TextEditingController(text: event?.title ?? draft?.title ?? '');
    categoryController = TextEditingController(text: event?.category ?? draft?.category ?? '');
    noteController = TextEditingController(text: event?.note ?? draft?.note ?? '');
    date = event?.date ?? draft?.date ?? defaultNewDate;
    yearly = (event?.repeatMode ?? draft?.repeatMode) == 'YEARLY';
    pinned = event?.isPinned ?? draft?.isPinned ?? false;
    reminderDays = event?.reminderDaysBefore ?? draft?.reminderDaysBefore ?? -1;
    image = event?.backgroundImage ?? draft?.backgroundImage;
  }

  @override
  void dispose() {
    draftTimer?.cancel();
    titleController.dispose();
    categoryController.dispose();
    noteController.dispose();
    super.dispose();
  }

  NewEventDraftModel _draftModel() => NewEventDraftModel(
        title: titleController.text,
        date: date,
        category: categoryController.text,
        note: noteController.text,
        repeatMode: yearly ? 'YEARLY' : 'NONE',
        isPinned: pinned,
        reminderDaysBefore: reminderDays < 0 ? null : reminderDays,
        backgroundImage: image,
      );

  Future<void> _persistDraftNow() async {
    if (!isNewEvent || !draftPersistenceEnabled) return;
    draftTimer?.cancel();
    try {
      if (!hasDraftContent) {
        await widget.controller.clearNewEventDraft();
      } else {
        await widget.controller.saveNewEventDraft(_draftModel());
      }
    } catch (error) {
      // 草稿保存失败不阻塞当前编辑；正式保存时仍会再次提交完整数据。
      debugPrint('保存新建日子草稿失败: $error');
    }
  }

  void _scheduleDraft() {
    if (!isNewEvent) return;
    draftTimer?.cancel();
    draftTimer = Timer(const Duration(milliseconds: 420), _persistDraftNow);
  }

  Future<void> _pickDate() async {
    final picked = await showGlassCalendarDatePicker(
      context: context,
      initialDate: date,
      firstDate: DateTime(1900),
      lastDate: DateTime(2200),
    );
    if (picked == null || !mounted) return;
    setState(() => date = DateTime(picked.year, picked.month, picked.day));
    await _persistDraftNow();
  }

  Future<void> _pickImage() async {
    if (pickingImage) return;
    setState(() => pickingImage = true);
    try {
      final picked = await widget.controller.pickImage();
      if (picked != null && mounted) {
        setState(() => image = picked);
        await _persistDraftNow();
      }
    } catch (error) {
      debugPrint('选择事件图片失败: $error');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('图片处理失败')),
        );
      }
    } finally {
      if (mounted) setState(() => pickingImage = false);
    }
  }

  Future<void> _recropImage() async {
    final current = image;
    if (current == null || pickingImage) return;
    setState(() => pickingImage = true);
    try {
      final picked = await widget.controller.recropImage(current);
      if (picked != null && mounted) {
        setState(() => image = picked);
        await _persistDraftNow();
      }
    } catch (error) {
      debugPrint('重新裁剪事件图片失败: $error');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('图片处理失败')),
        );
      }
    } finally {
      if (mounted) setState(() => pickingImage = false);
    }
  }

  Future<void> _removeImage() async {
    setState(() => image = null);
    await _persistDraftNow();
  }

  Future<void> _discardDraft() async {
    if (!isNewEvent || !hasDraftContent) return;
    final discard = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('丢弃草稿？'),
        content: const Text('草稿内容将被清除，无法恢复。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('丢弃'),
          ),
        ],
      ),
    );
    if (discard != true || !mounted) return;
    draftTimer?.cancel();
    setState(() {
      titleController.clear();
      categoryController.clear();
      noteController.clear();
      date = defaultNewDate;
      yearly = false;
      pinned = false;
      reminderDays = -1;
      image = null;
    });
    await widget.controller.clearNewEventDraft();
  }

  Future<void> _save() async {
    final title = titleController.text.trim();
    if (title.isEmpty || saving) return;
    draftTimer?.cancel();
    draftPersistenceEnabled = false;
    setState(() => saving = true);
    try {
      // 与 Classic 保持一致：设置提醒时尝试申请通知权限，但用户拒绝权限不能阻止事件本身保存。
      if (reminderDays >= 0 && !widget.controller.snapshot!.notificationGranted) {
        try {
          await widget.controller.requestNotificationPermission();
        } catch (error) {
          // 保留提醒设置，用户之后仍可在设置页重新申请通知权限。
          debugPrint('请求通知权限失败: $error');
        }
      }
      final saved = await widget.controller.saveEvent(<String, dynamic>{
        'id': widget.event?.id,
        'title': title,
        'date': _iso(date),
        'dateYear': date.year,
        'dateMonth': date.month,
        'dateDay': date.day,
        'category': categoryController.text.trim(),
        'note': noteController.text.trim(),
        'repeatMode': yearly ? 'YEARLY' : 'NONE',
        'isPinned': pinned,
        'reminderDaysBefore': reminderDays < 0 ? null : reminderDays,
        'backgroundImage': image?.toNativeJson(),
        'createdAtEpochMillis': widget.event?.createdAtEpochMillis,
      });
      if (!_sameDay(saved.date, date)) {
        throw StateError('Native save returned a different event date');
      }
      if (mounted) widget.onSaved(saved);
    } catch (error) {
      debugPrint('保存事件失败: $error');
      draftPersistenceEnabled = true;
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('保存失败')),
        );
      }
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final snapshot = widget.controller.snapshot!;
    final clarity = snapshot.settings.glassClarity;
    final scheme = Theme.of(context).colorScheme;

    return CustomScrollView(
      key: PageStorageKey<String>(
        widget.event == null ? 'new-event-scroll' : 'edit-event-${widget.event!.id}',
      ),
      physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
      slivers: [
        SliverPadding(
          padding: EdgeInsets.fromLTRB(18, 10, 18, widget.bottomInset),
          sliver: SliverList(
            delegate: SliverChildListDelegate.fixed([
              SafeArea(
                bottom: false,
                child: Row(
                  children: [
                    if (widget.showBackButton) ...[
                      IconButton(
                        tooltip: '返回',
                        onPressed: () => Navigator.of(context).pop(),
                        icon: const Icon(Icons.arrow_back_rounded),
                      ),
                      const SizedBox(width: 4),
                    ],
                    Expanded(
                      child: Text(
                        isNewEvent ? '新建日子' : '编辑日子',
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                    ),
                    TextButton(
                      onPressed: saving || titleController.text.trim().isEmpty ? null : _save,
                      child: Text(saving ? '保存中' : '保存'),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),

              // 字段分组和顺序与 Classic 保持一致，减少双 Edition 之间的操作差异。
              GlassSurface(
                isDark: snapshot.isDark,
                clarity: clarity,
                blur: false,
                radius: 22,
                padding: const EdgeInsets.all(18),
                child: Column(
                  children: [
                    TextField(
                      controller: titleController,
                      textInputAction: TextInputAction.next,
                      maxLength: 60,
                      decoration: const InputDecoration(labelText: '名称', counterText: ''),
                      onChanged: (_) {
                        setState(() {});
                        _scheduleDraft();
                      },
                    ),
                    const SizedBox(height: 14),
                    TextField(
                      controller: categoryController,
                      textInputAction: TextInputAction.next,
                      maxLength: 24,
                      decoration: const InputDecoration(labelText: '分类（可选）', counterText: ''),
                      onChanged: (_) => _scheduleDraft(),
                    ),
                    const SizedBox(height: 14),
                    TextField(
                      controller: noteController,
                      minLines: 3,
                      maxLines: 6,
                      maxLength: 500,
                      decoration: const InputDecoration(labelText: '备注（可选）', counterText: ''),
                      onChanged: (_) => _scheduleDraft(),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),

              GlassSurface(
                isDark: snapshot.isDark,
                clarity: clarity,
                blur: false,
                radius: 22,
                padding: EdgeInsets.zero,
                child: Material(
                  color: Colors.transparent,
                  child: InkWell(
                    borderRadius: BorderRadius.circular(22),
                    onTap: _pickDate,
                    child: Padding(
                      padding: const EdgeInsets.all(18),
                      child: Row(
                        children: [
                          Icon(Icons.calendar_month_rounded, color: scheme.primary),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('日期', style: Theme.of(context).textTheme.labelLarge),
                                const SizedBox(height: 4),
                                Text(longDateText(date), style: Theme.of(context).textTheme.titleLarge),
                              ],
                            ),
                          ),
                          Text('选择', style: TextStyle(color: scheme.primary)),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 14),

              GlassSurface(
                isDark: snapshot.isDark,
                clarity: clarity,
                blur: false,
                radius: 22,
                padding: const EdgeInsets.all(18),
                child: Row(
                  children: [
                    Icon(Icons.image_rounded, color: scheme.primary),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('背景图片', style: Theme.of(context).textTheme.titleMedium),
                          const SizedBox(height: 2),
                          Text(
                            image == null ? '使用默认主题背景' : '已选择背景图片',
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                        ],
                      ),
                    ),
                    if (pickingImage)
                      const SizedBox.square(
                        dimension: 22,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    else if (image == null)
                      OutlinedButton(onPressed: _pickImage, child: const Text('选择图片'))
                    else
                      PopupMenuButton<String>(
                        tooltip: '管理背景图片',
                        onSelected: (value) {
                          if (value == 'crop') {
                            _recropImage();
                          } else if (value == 'replace') {
                            _pickImage();
                          } else if (value == 'remove') {
                            _removeImage();
                          }
                        },
                        itemBuilder: (_) => [
                          const PopupMenuItem(value: 'crop', child: Text('重新裁剪')),
                          const PopupMenuItem(value: 'replace', child: Text('更换图片')),
                          PopupMenuItem(
                            value: 'remove',
                            child: Text('移除图片', style: TextStyle(color: scheme.error)),
                          ),
                        ],
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(
                                '管理',
                                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                                      color: scheme.primary,
                                    ),
                              ),
                              const SizedBox(width: 2),
                              Icon(Icons.arrow_drop_down_rounded, color: scheme.primary),
                            ],
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 14),

              GlassSurface(
                isDark: snapshot.isDark,
                clarity: clarity,
                blur: false,
                radius: 22,
                padding: const EdgeInsets.symmetric(horizontal: 18),
                child: Column(
                  children: [
                    _SwitchRow(
                      title: '每年重复',
                      value: yearly,
                      onChanged: (value) {
                        setState(() => yearly = value);
                        _persistDraftNow();
                      },
                    ),
                    Divider(color: scheme.onSurface.withAlpha(28), height: 1),
                    _SwitchRow(
                      title: '置顶',
                      value: pinned,
                      onChanged: (value) {
                        setState(() => pinned = value);
                        _persistDraftNow();
                      },
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),

              GlassSurface(
                isDark: snapshot.isDark,
                clarity: clarity,
                blur: false,
                radius: 22,
                padding: const EdgeInsets.all(18),
                child: Row(
                  children: [
                    Icon(Icons.notifications_rounded, color: scheme.primary),
                    const SizedBox(width: 10),
                    Expanded(child: Text('提醒', style: Theme.of(context).textTheme.titleMedium)),
                    PopupMenuButton<int>(
                      initialValue: reminderDays,
                      tooltip: '提醒',
                      onSelected: (value) {
                        setState(() => reminderDays = value);
                        _persistDraftNow();
                      },
                      itemBuilder: (_) => const [
                        PopupMenuItem(value: -1, child: Text('不提醒')),
                        PopupMenuItem(value: 0, child: Text('当天提醒')),
                        PopupMenuItem(value: 1, child: Text('提前 1 天')),
                        PopupMenuItem(value: 3, child: Text('提前 3 天')),
                        PopupMenuItem(value: 7, child: Text('提前 7 天')),
                      ],
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(18),
                          border: Border.all(color: scheme.onSurface.withAlpha(40)),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              reminderDays < 0 ? '不提醒' : reminderText(reminderDays),
                              style: Theme.of(context).textTheme.labelLarge,
                            ),
                            const SizedBox(width: 2),
                            Icon(
                              Icons.arrow_drop_down_rounded,
                              color: scheme.onSurfaceVariant,
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),

              FilledButton(
                onPressed: saving || titleController.text.trim().isEmpty ? null : _save,
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Text(isNewEvent ? '创建日子' : '保存修改'),
                ),
              ),
              if (isNewEvent && hasDraftContent) ...[
                const SizedBox(height: 4),
                TextButton(
                  onPressed: _discardDraft,
                  child: Text('丢弃草稿', style: TextStyle(color: scheme.error)),
                ),
              ],
              const SizedBox(height: 28),
            ]),
          ),
        ),
      ],
    );
  }
}

class _SwitchRow extends StatelessWidget {
  const _SwitchRow({required this.title, required this.value, required this.onChanged});

  final String title;
  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) => ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 58),
        child: Row(
          children: [
            Expanded(child: Text(title, style: Theme.of(context).textTheme.bodyLarge)),
            Switch(value: value, onChanged: onChanged),
          ],
        ),
      );
}

String _iso(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

bool _sameDay(DateTime a, DateTime b) =>
    a.year == b.year && a.month == b.month && a.day == b.day;
