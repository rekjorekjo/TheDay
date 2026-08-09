import 'package:flutter/material.dart';

import '../app_controller.dart';
import '../glass_theme.dart';
import '../models.dart';
import '../ui_utils.dart';
import '../widgets/event_widgets.dart';
import '../widgets/glass_surface.dart';

class CategoryScreen extends StatelessWidget {
  const CategoryScreen({
    super.key,
    required this.controller,
    required this.onOpenCategory,
    required this.bottomInset,
  });

  final AppController controller;
  final ValueChanged<String> onOpenCategory;
  final double bottomInset;

  @override
  Widget build(BuildContext context) {
    final snapshot = controller.snapshot!;
    final grouped = <String, List<DayEventModel>>{};
    for (final event in snapshot.events) {
      final key = normalizedCategory(event.category);
      grouped.putIfAbsent(key, () => <DayEventModel>[]).add(event);
    }
    final categories = grouped.keys.toList()
      ..sort((a, b) {
        if (a == b) return 0;
        if (a == unclassifiedCategoryName) return 1;
        if (b == unclassifiedCategoryName) return -1;
        return a.toLowerCase().compareTo(b.toLowerCase());
      });

    return CustomScrollView(
      key: const PageStorageKey<String>('category-scroll'),
      physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
      slivers: [
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(18, 10, 18, 0),
          sliver: SliverToBoxAdapter(
            child: SafeArea(
              bottom: false,
              child: Padding(
                padding: const EdgeInsets.only(bottom: 14),
                child: Text(
                  '分类',
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                ),
              ),
            ),
          ),
        ),
        if (categories.isEmpty)
          SliverFillRemaining(
            hasScrollBody: false,
            child: Center(
              child: Padding(
                padding: EdgeInsets.only(bottom: bottomInset),
                child: Text(
                  '暂无分类',
                  style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                ),
              ),
            ),
          )
        else
          SliverPadding(
            padding: EdgeInsets.fromLTRB(18, 0, 18, bottomInset),
            sliver: SliverGrid(
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                crossAxisSpacing: 12,
                mainAxisSpacing: 14,
                childAspectRatio: 0.72,
              ),
              delegate: SliverChildBuilderDelegate(
                (context, index) {
                  final name = categories[index];
                  return _CategoryBookCard(
                    name: name,
                    eventCount: grouped[name]!.length,
                    cover: snapshot.categoryCovers[name],
                    snapshot: snapshot,
                    onTap: () => onOpenCategory(name),
                  );
                },
                childCount: categories.length,
              ),
            ),
          ),
      ],
    );
  }
}

class _CategoryBookCard extends StatelessWidget {
  const _CategoryBookCard({
    required this.name,
    required this.eventCount,
    required this.cover,
    required this.snapshot,
    required this.onTap,
  });

  final String name;
  final int eventCount;
  final EventImageModel? cover;
  final AppSnapshot snapshot;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final hasCover = cover?.filePath?.isNotEmpty == true;
    final ambience = ambienceFor(snapshot.settings.paletteStyle);
    final shape = const BorderRadius.only(
      topLeft: Radius.circular(4),
      topRight: Radius.circular(16),
      bottomRight: Radius.circular(16),
      bottomLeft: Radius.circular(4),
    );

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: shape,
        child: ClipRRect(
          borderRadius: shape,
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (hasCover)
                EventImage(image: cover!, detail: true)
              else
                DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [
                        withOpacitySafe(ambience.primary, snapshot.isDark ? 0.56 : 0.36),
                        withOpacitySafe(ambience.secondary, snapshot.isDark ? 0.42 : 0.30),
                      ],
                    ),
                  ),
                ),
              if (hasCover)
                const DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [Color(0x28000000), Color(0x4C000000), Color(0xB8000000)],
                    ),
                  ),
                ),
              Positioned(
                left: 0,
                top: 0,
                bottom: 0,
                width: 7,
                child: ColoredBox(
                  color: hasCover
                      ? const Color(0x52000000)
                      : withOpacitySafe(ambience.accent, 0.76),
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Icon(
                      Icons.menu_book_rounded,
                      color: hasCover ? Colors.white.withAlpha(220) : Theme.of(context).colorScheme.onSurface,
                    ),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          name,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                                color: hasCover ? Colors.white : null,
                                fontWeight: FontWeight.w600,
                              ),
                        ),
                        const SizedBox(height: 3),
                        Text(
                          '$eventCount 个日子',
                          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                color: hasCover
                                    ? Colors.white.withAlpha(200)
                                    : Theme.of(context).colorScheme.onSurfaceVariant,
                              ),
                        ),
                      ],
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
