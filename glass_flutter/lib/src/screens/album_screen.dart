import 'package:flutter/material.dart';

import '../app_controller.dart';
import '../glass_route.dart';
import '../models.dart';
import '../ui_utils.dart';
import '../widgets/event_widgets.dart';
import '../widgets/glass_surface.dart';

class AlbumListScreen extends StatelessWidget {
  const AlbumListScreen({
    super.key,
    required this.controller,
    required this.onOpenEvent,
  });

  final AppController controller;
  final ValueChanged<DayEventModel> onOpenEvent;

  void _openEditor(BuildContext context, DayAlbumModel? album) {
    Navigator.of(context).push(
      glassRoute<void>(
        controller: controller,
        builder: (_) => AlbumEditorScreen(controller: controller, album: album),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: controller,
        builder: (context, _) {
          final snapshot = controller.snapshot!;
          return Scaffold(
            backgroundColor: Colors.transparent,
            body: SafeArea(
              bottom: false,
              child: Column(
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(18, 6, 18, 8),
                    child: Row(
                      children: [
                        IconButton(
                          tooltip: '返回',
                          onPressed: () => Navigator.of(context).pop(),
                          icon: const Icon(Icons.arrow_back_rounded),
                        ),
                        const SizedBox(width: 4),
                        Expanded(child: Text('纪念册', style: Theme.of(context).textTheme.titleLarge)),
                        IconButton(
                          tooltip: '新建纪念册',
                          onPressed: () => _openEditor(context, null),
                          icon: const Icon(Icons.add_rounded),
                        ),
                      ],
                    ),
                  ),
                  Expanded(
                    child: snapshot.albums.isEmpty
                        ? Center(
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  Icons.menu_book_rounded,
                                  size: 42,
                                  color: Theme.of(context).colorScheme.primary,
                                ),
                                const SizedBox(height: 14),
                                Text('暂无纪念册', style: Theme.of(context).textTheme.titleMedium),
                                const SizedBox(height: 14),
                                FilledButton(
                                  onPressed: () => _openEditor(context, null),
                                  child: const Text('新建纪念册'),
                                ),
                              ],
                            ),
                          )
                        : GridView.builder(
                            physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
                            padding: EdgeInsets.fromLTRB(
                              18,
                              12,
                              18,
                              24 + MediaQuery.paddingOf(context).bottom,
                            ),
                            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                              crossAxisCount: 2,
                              crossAxisSpacing: 12,
                              mainAxisSpacing: 14,
                              childAspectRatio: 0.72,
                            ),
                            itemCount: snapshot.albums.length,
                            itemBuilder: (context, index) {
                              final album = snapshot.albums[index];
                              final events = album.eventIds
                                  .map(snapshot.eventById)
                                  .whereType<DayEventModel>()
                                  .toList(growable: false);
                              final cover = snapshot.eventById(album.coverEventId) ??
                                  (events.isEmpty ? null : events.first);
                              return _AlbumBookCard(
                                snapshot: snapshot,
                                album: album,
                                cover: cover,
                                onTap: () => Navigator.of(context).push(
                                  glassRoute<void>(
                                    controller: controller,
                                    builder: (_) => AlbumDetailScreen(
                                      controller: controller,
                                      albumId: album.id,
                                      onOpenEvent: onOpenEvent,
                                    ),
                                  ),
                                ),
                              );
                            },
                          ),
                  ),
                ],
              ),
            ),
          );
        },
      );
}

class _AlbumBookCard extends StatelessWidget {
  const _AlbumBookCard({
    required this.snapshot,
    required this.album,
    required this.cover,
    required this.onTap,
  });

  final AppSnapshot snapshot;
  final DayAlbumModel album;
  final DayEventModel? cover;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final hasImage = cover?.backgroundImage?.filePath?.isNotEmpty == true;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: onTap,
        child: ClipRRect(
          borderRadius: BorderRadius.circular(20),
          child: Stack(
            fit: StackFit.expand,
            children: [
              GlassSurface(
                isDark: snapshot.isDark,
                clarity: snapshot.settings.glassClarity,
                blur: false,
                radius: 20,
                child: hasImage
                    ? EventImage(image: cover!.backgroundImage!, detail: true)
                    : DecoratedBox(
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                            colors: [
                              Theme.of(context).colorScheme.primary.withAlpha(58),
                              Theme.of(context).colorScheme.secondary.withAlpha(34),
                            ],
                          ),
                        ),
                      ),
              ),
              if (hasImage)
                const DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [Color(0x18000000), Color(0xB8000000)],
                    ),
                  ),
                ),
              Positioned(
                left: 0,
                top: 0,
                bottom: 0,
                child: Container(
                  width: 7,
                  color: hasImage
                      ? Colors.black.withAlpha(72)
                      : Theme.of(context).colorScheme.primary.withAlpha(120),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(18, 18, 14, 16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(
                      Icons.menu_book_rounded,
                      color: hasImage ? Colors.white.withAlpha(220) : Theme.of(context).colorScheme.primary,
                    ),
                    const Spacer(),
                    Text(
                      album.title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            color: hasImage ? Colors.white : null,
                          ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${album.eventIds.length} 个日子',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: hasImage ? Colors.white.withAlpha(190) : null,
                          ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class AlbumEditorScreen extends StatefulWidget {
  const AlbumEditorScreen({super.key, required this.controller, this.album});
  final AppController controller;
  final DayAlbumModel? album;

  @override
  State<AlbumEditorScreen> createState() => _AlbumEditorScreenState();
}

class _AlbumEditorScreenState extends State<AlbumEditorScreen> {
  late final TextEditingController title;
  late final Set<String> selected;
  bool saving = false;

  @override
  void initState() {
    super.initState();
    title = TextEditingController(text: widget.album?.title ?? '');
    selected = {...?widget.album?.eventIds};
  }

  @override
  void dispose() {
    title.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (title.text.trim().isEmpty || selected.isEmpty || saving) return;
    setState(() => saving = true);
    final currentCover = widget.album?.coverEventId;
    await widget.controller.saveAlbum(<String, dynamic>{
      'id': widget.album?.id,
      'title': title.text.trim(),
      'eventIds': selected.toList(),
      'coverEventId': currentCover != null && selected.contains(currentCover)
          ? currentCover
          : selected.first,
      'createdAtEpochMillis': widget.album?.createdAtEpochMillis,
    });
    if (mounted) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final snapshot = widget.controller.snapshot!;
    final events = sortEvents(snapshot.events, snapshot.settings);
    final canSave = title.text.trim().isNotEmpty && selected.isNotEmpty && !saving;
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(18, 6, 18, 8),
              child: Row(
                children: [
                  IconButton(
                    tooltip: '返回',
                    onPressed: () => Navigator.of(context).pop(),
                    icon: const Icon(Icons.arrow_back_rounded),
                  ),
                  const SizedBox(width: 4),
                  Expanded(
                    child: Text(
                      widget.album == null ? '新建纪念册' : '编辑纪念册',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                  TextButton(onPressed: canSave ? _save : null, child: Text(saving ? '保存中' : '保存')),
                ],
              ),
            ),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 18),
                child: Column(
                  children: [
                    const SizedBox(height: 8),
                    TextField(
                      controller: title,
                      decoration: const InputDecoration(labelText: '纪念册名称'),
                      onChanged: (_) => setState(() {}),
                    ),
                    const SizedBox(height: 14),
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            '选择日子',
                            style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
                          ),
                        ),
                        Text('${selected.length} 个', style: Theme.of(context).textTheme.bodyMedium),
                      ],
                    ),
                    const SizedBox(height: 10),
                    Expanded(
                      child: events.isEmpty
                          ? Center(
                              child: Text(
                                '暂无可选择的日子',
                                style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant),
                              ),
                            )
                          : ListView.separated(
                              physics: const BouncingScrollPhysics(),
                              padding: EdgeInsets.only(bottom: 18 + MediaQuery.paddingOf(context).bottom),
                              itemCount: events.length,
                              separatorBuilder: (_, __) => const SizedBox(height: 9),
                              itemBuilder: (context, index) {
                                final event = events[index];
                                final checked = selected.contains(event.id);
                                return GlassSurface(
                                  isDark: snapshot.isDark,
                                  clarity: snapshot.settings.glassClarity,
                                  blur: false,
                                  radius: 18,
                                  child: CheckboxListTile(
                                    value: checked,
                                    controlAffinity: ListTileControlAffinity.leading,
                                    title: Text(event.title, maxLines: 1, overflow: TextOverflow.ellipsis),
                                    subtitle: Text('${compactDateText(event.date)} · ${normalizedCategory(event.category)}'),
                                    onChanged: (_) => setState(() {
                                      if (checked) {
                                        selected.remove(event.id);
                                      } else {
                                        selected.add(event.id);
                                      }
                                    }),
                                  ),
                                );
                              },
                            ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class AlbumDetailScreen extends StatefulWidget {
  const AlbumDetailScreen({
    super.key,
    required this.controller,
    required this.albumId,
    required this.onOpenEvent,
  });

  final AppController controller;
  final String albumId;
  final ValueChanged<DayEventModel> onOpenEvent;

  @override
  State<AlbumDetailScreen> createState() => _AlbumDetailScreenState();
}

class _AlbumDetailScreenState extends State<AlbumDetailScreen> {
  static const int _basePage = 10000;
  int currentIndex = 0;
  bool selectionMode = false;
  late final PageController pageController;

  @override
  void initState() {
    super.initState();
    pageController = PageController(initialPage: _basePage);
  }

  @override
  void dispose() {
    pageController.dispose();
    super.dispose();
  }

  Future<void> _setCover(DayAlbumModel album, DayEventModel event) async {
    await widget.controller.saveAlbum(<String, dynamic>{
      'id': album.id,
      'title': album.title,
      'eventIds': album.eventIds,
      'coverEventId': event.id,
      'createdAtEpochMillis': album.createdAtEpochMillis,
    });
  }

  Future<void> _remove(DayAlbumModel album, DayEventModel event) async {
    final nextIds = album.eventIds.where((id) => id != event.id).toList(growable: false);
    await widget.controller.saveAlbum(<String, dynamic>{
      'id': album.id,
      'title': album.title,
      'eventIds': nextIds,
      'coverEventId': album.coverEventId == event.id
          ? (nextIds.isEmpty ? null : nextIds.first)
          : album.coverEventId,
      'createdAtEpochMillis': album.createdAtEpochMillis,
    });
    if (mounted) {
      setState(() {
        currentIndex = currentIndex.clamp(0, (nextIds.length - 1).clamp(0, 1 << 20)).toInt();
        selectionMode = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: widget.controller,
        builder: (context, _) {
          final snapshot = widget.controller.snapshot!;
          final album = snapshot.albumById(widget.albumId);
          if (album == null) return const SizedBox.shrink();
          final events = album.eventIds.map(snapshot.eventById).whereType<DayEventModel>().toList(growable: false);
          final safeCurrentIndex = events.isEmpty
              ? 0
              : currentIndex.clamp(0, events.length - 1).toInt();
          final currentEvent = events.isEmpty ? null : events[safeCurrentIndex];

          return Scaffold(
            backgroundColor: Colors.transparent,
            body: SafeArea(
              bottom: false,
              child: Stack(
                children: [
                  Column(
                    children: [
                      Padding(
                        padding: const EdgeInsets.fromLTRB(18, 6, 18, 8),
                        child: Row(
                          children: [
                            IconButton(
                              tooltip: '返回',
                              onPressed: () => Navigator.of(context).pop(),
                              icon: const Icon(Icons.arrow_back_rounded),
                            ),
                            const SizedBox(width: 4),
                            Expanded(
                              child: Text(
                                album.title,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: Theme.of(context).textTheme.titleLarge,
                              ),
                            ),
                            IconButton(
                              tooltip: '设为封面',
                              onPressed: currentEvent == null ? null : () => _setCover(album, currentEvent),
                              icon: Icon(
                                Icons.image_rounded,
                                color: currentEvent?.id == album.coverEventId
                                    ? Theme.of(context).colorScheme.primary
                                    : Theme.of(context).colorScheme.onSurfaceVariant,
                              ),
                            ),
                            IconButton(
                              tooltip: '编辑纪念册',
                              onPressed: () => Navigator.of(context).push(
                                glassRoute<void>(
                                  controller: widget.controller,
                                  builder: (_) => AlbumEditorScreen(controller: widget.controller, album: album),
                                ),
                              ),
                              icon: const Icon(Icons.edit_rounded),
                            ),
                          ],
                        ),
                      ),
                      Expanded(
                        child: events.isEmpty
                            ? Center(
                                child: Text(
                                  '这个纪念册还没有日子',
                                  style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                                      ),
                                ),
                              )
                            : Column(
                                children: [
                                  Expanded(
                                    // 详情使用循环 PageView 保持连续翻阅；长按当前卡片进入操作模式。
                                    child: PageView.builder(
                                      controller: pageController,
                                      onPageChanged: (page) {
                                        setState(() {
                                          currentIndex = page % events.length;
                                          selectionMode = false;
                                        });
                                      },
                                      itemBuilder: (context, page) {
                                        final event = events[page % events.length];
                                        return Padding(
                                          padding: const EdgeInsets.symmetric(horizontal: 18),
                                          child: GestureDetector(
                                            onLongPress: () => setState(() => selectionMode = true),
                                            child: _AlbumDeckCard(snapshot: snapshot, event: event),
                                          ),
                                        );
                                      },
                                    ),
                                  ),
                                  Padding(
                                    padding: const EdgeInsets.only(bottom: 22),
                                    child: Row(
                                      mainAxisAlignment: MainAxisAlignment.center,
                                      children: List.generate(events.length, (index) {
                                        final selected = index == safeCurrentIndex;
                                        return AnimatedContainer(
                                          duration: const Duration(milliseconds: 140),
                                          margin: const EdgeInsets.symmetric(horizontal: 3.5),
                                          width: selected ? 9 : 7,
                                          height: selected ? 9 : 7,
                                          decoration: BoxDecoration(
                                            shape: BoxShape.circle,
                                            color: selected
                                                ? Theme.of(context).colorScheme.primary
                                                : Theme.of(context).colorScheme.outlineVariant,
                                          ),
                                        );
                                      }),
                                    ),
                                  ),
                                ],
                              ),
                      ),
                    ],
                  ),
                  if (selectionMode && currentEvent != null)
                    Positioned(
                      left: 18,
                      right: 18,
                      bottom: 18 + MediaQuery.paddingOf(context).bottom,
                      child: _AlbumSelectionToolbar(
                        snapshot: snapshot,
                        onDelete: () => _remove(album, currentEvent),
                        onEdit: () {
                          setState(() => selectionMode = false);
                          widget.onOpenEvent(currentEvent);
                        },
                        onDone: () => setState(() => selectionMode = false),
                      ),
                    ),
                ],
              ),
            ),
          );
        },
      );
}

class _AlbumDeckCard extends StatelessWidget {
  const _AlbumDeckCard({required this.snapshot, required this.event});
  final AppSnapshot snapshot;
  final DayEventModel event;

  @override
  Widget build(BuildContext context) {
    final image = event.backgroundImage;
    final hasImage = image?.filePath?.isNotEmpty == true;
    return LayoutBuilder(
      builder: (context, constraints) {
        final aspect = hasImage ? detailImagePreviewAspectRatio(image!) : 0.76;
        final maxCardWidth = constraints.maxWidth * 0.98;
        final availableHeight = (constraints.maxHeight - 8).clamp(1.0, double.infinity).toDouble();
        var cardWidth = maxCardWidth;
        var cardHeight = cardWidth / aspect;
        if (cardHeight > availableHeight) {
          cardHeight = availableHeight;
          cardWidth = (cardHeight * aspect).clamp(1.0, maxCardWidth).toDouble();
        }

        return Stack(
          alignment: Alignment.center,
          children: [
            Transform.translate(
              offset: const Offset(16, 16),
              child: Transform.rotate(
                angle: 0.066,
                child: SizedBox(
                  width: cardWidth * 0.985,
                  height: cardHeight * 0.985,
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(22),
                      color: Theme.of(context).colorScheme.secondaryContainer.withAlpha(170),
                    ),
                  ),
                ),
              ),
            ),
            Transform.translate(
              offset: const Offset(-14, 28),
              child: Transform.rotate(
                angle: -0.073,
                child: SizedBox(
                  width: cardWidth * 0.955,
                  height: cardHeight * 0.955,
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(22),
                      color: Theme.of(context).colorScheme.tertiaryContainer.withAlpha(150),
                    ),
                  ),
                ),
              ),
            ),
            SizedBox(
              width: cardWidth,
              height: cardHeight,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(22),
                child: GlassSurface(
                  isDark: snapshot.isDark,
                  clarity: snapshot.settings.glassClarity,
                  blur: false,
                  radius: 22,
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      if (hasImage) EventImage(image: image!, detail: true),
                      if (hasImage)
                        const DecoratedBox(
                          decoration: BoxDecoration(
                            gradient: LinearGradient(
                              begin: Alignment.topCenter,
                              end: Alignment.bottomCenter,
                              colors: [Color(0x10000000), Colors.transparent, Color(0xB5000000)],
                            ),
                          ),
                        ),
                      Padding(
                        padding: const EdgeInsets.all(20),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisAlignment: MainAxisAlignment.end,
                          children: [
                            Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    event.title,
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                    style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                                          color: hasImage ? Colors.white : null,
                                        ),
                                  ),
                                ),
                                const SizedBox(width: 12),
                                DecoratedBox(
                                  decoration: BoxDecoration(
                                    borderRadius: BorderRadius.circular(99),
                                    color: hasImage
                                        ? Colors.white.withAlpha(46)
                                        : Theme.of(context).colorScheme.primary.withAlpha(30),
                                  ),
                                  child: Padding(
                                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                                    child: Text(
                                      dayDistanceLabel(event.signedDays),
                                      style: Theme.of(context).textTheme.labelLarge?.copyWith(
                                            color: hasImage ? Colors.white : Theme.of(context).colorScheme.primary,
                                          ),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            Text(
                              longDateText(event.effectiveDate),
                              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                                    color: hasImage
                                        ? Colors.white.withAlpha(210)
                                        : Theme.of(context).colorScheme.onSurfaceVariant,
                                  ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}

class _AlbumSelectionToolbar extends StatelessWidget {
  const _AlbumSelectionToolbar({
    required this.snapshot,
    required this.onDelete,
    required this.onEdit,
    required this.onDone,
  });

  final AppSnapshot snapshot;
  final VoidCallback onDelete;
  final VoidCallback onEdit;
  final VoidCallback onDone;

  @override
  Widget build(BuildContext context) => GlassSurface(
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
                  onPressed: onDelete,
                  style: FilledButton.styleFrom(
                    foregroundColor: Theme.of(context).colorScheme.onErrorContainer,
                    backgroundColor: Theme.of(context).colorScheme.errorContainer,
                  ),
                  child: const Icon(Icons.delete_rounded, semanticLabel: '移出纪念册'),
                ),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: SizedBox(
                height: 52,
                child: FilledButton(
                  onPressed: onEdit,
                  child: const Icon(Icons.edit_rounded, semanticLabel: '编辑日子'),
                ),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: SizedBox(
                height: 52,
                child: FilledButton(
                  onPressed: onDone,
                  child: const Icon(Icons.check_rounded, semanticLabel: '完成'),
                ),
              ),
            ),
          ],
        ),
      );
}
