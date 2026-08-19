import 'package:flutter/material.dart';

import '../models.dart';
import '../widgets/glass_surface.dart';

class ToolsScreen extends StatelessWidget {
  const ToolsScreen({
    super.key,
    required this.snapshot,
    required this.bottomInset,
    required this.onBack,
    required this.onOpenExport,
    required this.onOpenMilestones,
    required this.onOpenCalculator,
    required this.onOpenAlbums,
  });

  final AppSnapshot snapshot;
  final double bottomInset;
  final VoidCallback onBack;
  final VoidCallback onOpenExport;
  final VoidCallback onOpenMilestones;
  final VoidCallback onOpenCalculator;
  final VoidCallback onOpenAlbums;

  @override
  Widget build(BuildContext context) {
    return CustomScrollView(
      key: const PageStorageKey<String>('tools-scroll'),
      physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
      slivers: [
        SliverPadding(
          padding: EdgeInsets.fromLTRB(18, 10, 18, bottomInset),
          sliver: SliverList(
            delegate: SliverChildListDelegate.fixed([
              SafeArea(
                bottom: false,
                child: Row(
                  children: [
                    IconButton(
                      tooltip: '返回',
                      onPressed: onBack,
                      icon: const Icon(Icons.arrow_back_rounded),
                    ),
                    const SizedBox(width: 4),
                    Text('工具栏', style: Theme.of(context).textTheme.titleLarge),
                  ],
                ),
              ),
              const SizedBox(height: 18),
              _ToolCard(snapshot: snapshot, icon: Icons.ios_share_rounded, title: '导出', onTap: onOpenExport),
              const SizedBox(height: 10),
              _ToolCard(snapshot: snapshot, icon: Icons.flag_outlined, title: '纪念碑', onTap: onOpenMilestones),
              const SizedBox(height: 10),
              _ToolCard(snapshot: snapshot, icon: Icons.calculate_outlined, title: '计算器', onTap: onOpenCalculator),
              const SizedBox(height: 10),
              _ToolCard(snapshot: snapshot, icon: Icons.menu_book_outlined, title: '纪念册', onTap: onOpenAlbums),
            ]),
          ),
        ),
      ],
    );
  }
}

class _ToolCard extends StatelessWidget {
  const _ToolCard({required this.snapshot, required this.icon, required this.title, required this.onTap});
  final AppSnapshot snapshot;
  final IconData icon;
  final String title;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GlassSurface(
      isDark: snapshot.isDark,
      clarity: snapshot.settings.glassClarity,
      blur: false,
      radius: 22,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(22),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 20),
            child: Row(children: [
              Icon(icon, size: 28, color: Theme.of(context).colorScheme.primary),
              const SizedBox(width: 14),
              Expanded(child: Text(title, style: Theme.of(context).textTheme.titleMedium)),
            ]),
          ),
        ),
      ),
    );
  }
}
