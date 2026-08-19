import 'package:flutter/material.dart';

import '../app_controller.dart';
import '../models.dart';
import '../ui_utils.dart';
import '../widgets/event_widgets.dart';

class CategoryDetailScreen extends StatefulWidget {
  const CategoryDetailScreen({
    super.key,
    required this.controller,
    required this.category,
    required this.onOpenEvent,
  });

  final AppController controller;
  final String category;
  final ValueChanged<DayEventModel> onOpenEvent;

  @override
  State<CategoryDetailScreen> createState() => _CategoryDetailScreenState();
}

class _CategoryDetailScreenState extends State<CategoryDetailScreen> {
  bool coverBusy = false;
  bool deleteBusy = false;

  Future<void> _coverAction(String action, EventImageModel? cover) async {
    if (coverBusy) return;
    setState(() => coverBusy = true);
    try {
      if (action == 'remove') {
        await widget.controller.updateCategoryCover(widget.category, null);
      } else if (action == 'crop' && cover != null) {
        final image = await widget.controller.recropImage(cover);
        if (image != null) {
          await widget.controller.updateCategoryCover(widget.category, image);
        }
      } else {
        final image = await widget.controller.pickImage();
        if (image != null) {
          await widget.controller.updateCategoryCover(widget.category, image);
        }
      }
    } catch (error) {
      debugPrint('分类封面处理失败: $error');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('封面处理失败')),
        );
      }
    } finally {
      if (mounted) setState(() => coverBusy = false);
    }
  }

  Future<void> _deleteCategory() async {
    if (deleteBusy || widget.category == '未分类') return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('删除分类'),
        content: Text('删除“${widget.category}”分类？分类中的日子不会被删除，只会变为未分类。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('删除'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() => deleteBusy = true);
    try {
      await widget.controller.deleteCategory(widget.category);
      if (mounted) Navigator.of(context).pop();
    } catch (error) {
      debugPrint('删除分类失败: $error');
      if (mounted) {
        setState(() => deleteBusy = false);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('分类删除失败')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.controller,
      builder: (context, _) {
        final snapshot = widget.controller.snapshot!;
        final categoryEvents = snapshot.events.where((event) {
          return normalizedCategory(event.category) == widget.category;
        });
        final events = sortEvents(categoryEvents, snapshot.settings);
        final cover = snapshot.categoryCovers[widget.category];

        return Scaffold(
          backgroundColor: Colors.transparent,
          body: SafeArea(
            bottom: false,
            child: CustomScrollView(
              physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
              slivers: [
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(18, 10, 18, 0),
                  sliver: SliverToBoxAdapter(
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
                            widget.category,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.titleLarge?.copyWith(
                                  fontWeight: FontWeight.w600,
                                ),
                          ),
                        ),
                        if (widget.category != '未分类')
                          IconButton(
                            tooltip: '删除分类',
                            onPressed: deleteBusy || coverBusy ? null : _deleteCategory,
                            icon: Icon(
                              Icons.delete_outline_rounded,
                              color: Theme.of(context).colorScheme.error,
                            ),
                          ),
                        if (deleteBusy)
                          const Padding(
                            padding: EdgeInsets.all(12),
                            child: SizedBox.square(
                              dimension: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            ),
                          )
                        else if (coverBusy)
                          const Padding(
                            padding: EdgeInsets.all(12),
                            child: SizedBox.square(
                              dimension: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            ),
                          )
                        else
                          PopupMenuButton<String>(
                            tooltip: '设置封面',
                            icon: Icon(
                              Icons.image_rounded,
                              color: cover != null
                                  ? Theme.of(context).colorScheme.primary
                                  : Theme.of(context).colorScheme.onSurfaceVariant,
                            ),
                            onSelected: (value) => _coverAction(value, cover),
                            itemBuilder: (_) => [
                              PopupMenuItem<String>(
                                value: 'pick',
                                child: Text(cover == null ? '选择封面' : '更换封面'),
                              ),
                              if (cover != null)
                                const PopupMenuItem<String>(
                                  value: 'crop',
                                  child: Text('重新裁剪'),
                                ),
                              if (cover != null)
                                PopupMenuItem<String>(
                                  value: 'remove',
                                  child: Text(
                                    '移除封面',
                                    style: TextStyle(color: Theme.of(context).colorScheme.error),
                                  ),
                                ),
                            ],
                          ),
                      ],
                    ),
                  ),
                ),
                if (events.isEmpty)
                  SliverFillRemaining(
                    hasScrollBody: false,
                    child: Center(
                      child: Text(
                        '暂无事件',
                        style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                              color: Theme.of(context).colorScheme.onSurfaceVariant,
                            ),
                      ),
                    ),
                  )
                else ...[
                  SliverPadding(
                    padding: const EdgeInsets.fromLTRB(18, 14, 18, 10),
                    sliver: SliverToBoxAdapter(
                      child: Text(
                        '${events.length} 个日子',
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                              color: Theme.of(context).colorScheme.onSurfaceVariant,
                            ),
                      ),
                    ),
                  ),
                  SliverPadding(
                    padding: EdgeInsets.fromLTRB(
                      18,
                      0,
                      18,
                      24 + MediaQuery.paddingOf(context).bottom,
                    ),
                    sliver: SliverList(
                      delegate: SliverChildBuilderDelegate(
                        (context, index) {
                          final event = events[index];
                          return Padding(
                            padding: EdgeInsets.only(
                              bottom: index == events.length - 1 ? 0 : 12,
                            ),
                            child: EventListCard(
                              event: event,
                              isDark: snapshot.isDark,
                              clarity: snapshot.settings.glassClarity,
                              accent: Theme.of(context).colorScheme.primary,
                              onTap: () => widget.onOpenEvent(event),
                            ),
                          );
                        },
                        childCount: events.length,
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        );
      },
    );
  }
}
