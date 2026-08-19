import 'package:flutter/material.dart';

import 'app_controller.dart';
import 'glass_route.dart';
import 'glass_theme.dart';
import 'models.dart';
import 'screens/about_screen.dart';
import 'screens/album_screen.dart';
import 'screens/category_detail_screen.dart';
import 'screens/category_screen.dart';
import 'screens/date_calculator_screen.dart';
import 'screens/event_detail_screen.dart';
import 'screens/event_editor_screen.dart';
import 'screens/export_screen.dart';
import 'screens/home_screen.dart';
import 'screens/milestone_screen.dart';
import 'screens/settings_screen.dart';
import 'screens/tools_screen.dart';
import 'widgets/glass_surface.dart';

class GlassShell extends StatefulWidget {
  const GlassShell({super.key, required this.controller});

  final AppController controller;

  @override
  State<GlassShell> createState() => _GlassShellState();
}

class _GlassShellState extends State<GlassShell> {
  int selectedIndex = 0;
  int editorGeneration = 0;

  @override
  void initState() {
    super.initState();
    widget.controller.onOpenEvent = _openEventById;
  }

  @override
  void didUpdateWidget(covariant GlassShell oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.onOpenEvent = null;
      widget.controller.onOpenEvent = _openEventById;
    }
  }

  @override
  void dispose() {
    widget.controller.onOpenEvent = null;
    super.dispose();
  }

  void _openEventById(String eventId) {
    final event = widget.controller.snapshot?.eventById(eventId);
    if (event != null && mounted) _openEvent(event);
  }

  void _openEvent(DayEventModel event) {
    Navigator.of(context).push(
      glassRoute<void>(
        controller: widget.controller,
        builder: (_) => EventDetailScreen(
          controller: widget.controller,
          eventId: event.id,
        ),
      ),
    );
  }

  void _selectTab(int index) {
    if (selectedIndex == index) return;
    setState(() => selectedIndex = index);
  }

  void _openTools() {
    setState(() => selectedIndex = 4);
  }

  void _handleEventSaved(DayEventModel? event) {
    setState(() {
      selectedIndex = 0;
      editorGeneration += 1;
    });

    if (event != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        _openEvent(event);
      });
    }
  }

  void _openPage(WidgetBuilder builder) {
    Navigator.of(context).push(
      glassRoute<void>(
        controller: widget.controller,
        builder: builder,
      ),
    );
  }

  void _openCategory(String category) {
    _openPage(
      (_) => CategoryDetailScreen(
        controller: widget.controller,
        category: category,
        onOpenEvent: _openEvent,
      ),
    );
  }

  void _openAbout() => _openPage((_) => AboutScreen(controller: widget.controller));
  void _openExport() => _openPage((_) => ExportScreen(controller: widget.controller, onOpenEvent: _openEvent));
  void _openMilestones() => _openPage((_) => MilestoneScreen(controller: widget.controller));
  void _openCalculator() => _openPage((_) => DateCalculatorScreen(controller: widget.controller));
  void _openAlbums() => _openPage(
        (_) => AlbumListScreen(
          controller: widget.controller,
          onOpenEvent: _openEvent,
        ),
      );

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.controller,
      builder: (context, _) {
        final snapshot = widget.controller.snapshot;
        if (snapshot == null) return const SizedBox.shrink();
        final safeBottom = MediaQuery.paddingOf(context).bottom;
        final contentBottomInset = 104 + safeBottom;

        return PopScope<Object?>(
          canPop: selectedIndex == 0,
          onPopInvokedWithResult: (didPop, result) {
            if (!didPop && selectedIndex != 0 && mounted) {
              setState(() => selectedIndex = 0);
            }
          },
          child: Scaffold(
            backgroundColor: Colors.transparent,
            extendBody: true,
            body: Stack(
            fit: StackFit.expand,
            children: [
              IndexedStack(
                index: selectedIndex,
                children: [
                  HomeScreen(
                    controller: widget.controller,
                    onOpenEvent: _openEvent,
                    onOpenTools: _openTools,
                    bottomInset: contentBottomInset,
                  ),
                  CategoryScreen(
                    controller: widget.controller,
                    onOpenCategory: _openCategory,
                    bottomInset: contentBottomInset,
                  ),
                  EventEditorScreen(
                    key: ValueKey<int>(editorGeneration),
                    controller: widget.controller,
                    bottomInset: contentBottomInset,
                    onSaved: _handleEventSaved,
                  ),
                  SettingsScreen(
                    controller: widget.controller,
                    bottomInset: contentBottomInset,
                    onOpenAbout: _openAbout,
                  ),
                  ToolsScreen(
                    snapshot: snapshot,
                    bottomInset: contentBottomInset,
                    onBack: () => _selectTab(0),
                    onOpenExport: _openExport,
                    onOpenMilestones: _openMilestones,
                    onOpenCalculator: _openCalculator,
                    onOpenAlbums: _openAlbums,
                  ),
                ],
              ),
              Positioned(
                left: 16,
                right: 16,
                bottom: 10 + safeBottom,
                child: Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 520),
                    child: _GlassBottomNavigation(
                      snapshot: snapshot,
                      selectedIndex: selectedIndex == 4 ? 0 : selectedIndex,
                      onSelect: _selectTab,
                    ),
                  ),
                ),
              ),
              ],
            ),
          ),
        );
      },
    );
  }

}


class _GlassBottomNavigation extends StatefulWidget {
  const _GlassBottomNavigation({
    required this.snapshot,
    required this.selectedIndex,
    required this.onSelect,
  });

  final AppSnapshot snapshot;
  final int selectedIndex;
  final ValueChanged<int> onSelect;

  @override
  State<_GlassBottomNavigation> createState() => _GlassBottomNavigationState();
}

class _GlassBottomNavigationState extends State<_GlassBottomNavigation>
    with SingleTickerProviderStateMixin {
  late final AnimationController _selectionMotion;
  late double _fromPosition;
  late double _toPosition;

  @override
  void initState() {
    super.initState();
    _fromPosition = widget.selectedIndex.toDouble();
    _toPosition = _fromPosition;
    _selectionMotion = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 480),
      value: 1,
    );
  }

  double _currentPosition() {
    final eased = Curves.easeInOutCubic.transform(_selectionMotion.value);
    return _fromPosition + ((_toPosition - _fromPosition) * eased);
  }

  @override
  void didUpdateWidget(covariant _GlassBottomNavigation oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.selectedIndex == widget.selectedIndex) return;
    _fromPosition = _currentPosition();
    _toPosition = widget.selectedIndex.toDouble();
    _selectionMotion.forward(from: 0);
  }

  @override
  void dispose() {
    _selectionMotion.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final snapshot = widget.snapshot;
    final ambience = ambienceFor(snapshot.settings.paletteStyle);
    return GlassSurface(
      isDark: snapshot.isDark,
      clarity: snapshot.settings.glassClarity,
      blur: true,
      radius: 34,
      padding: const EdgeInsets.all(6),
      child: SizedBox(
        height: 64,
        child: LayoutBuilder(
          builder: (context, constraints) {
            final itemWidth = constraints.maxWidth / 4;
            return AnimatedBuilder(
              animation: _selectionMotion,
              builder: (context, _) {
                final position = _currentPosition();
                double selectionFor(int index) =>
                    (1.0 - (position - index).abs()).clamp(0.0, 1.0).toDouble();
                return Stack(
                  fit: StackFit.expand,
                  children: [
                    Positioned(
                      left: (position * itemWidth) + 2,
                      top: 4,
                      width: itemWidth - 4,
                      height: 56,
                      child: IgnorePointer(
                        child: _LiquidNavSelection(
                          accent: ambience.accent,
                          clarity: snapshot.settings.glassClarity,
                        ),
                      ),
                    ),
                    Row(
                      children: [
                        _NavItem(
                          icon: Icons.calendar_today_outlined,
                          label: '日子',
                          selection: selectionFor(0),
                          accent: ambience.accent,
                          onTap: () => widget.onSelect(0),
                        ),
                        _NavItem(
                          icon: Icons.grid_view_rounded,
                          label: '分类',
                          selection: selectionFor(1),
                          accent: ambience.accent,
                          onTap: () => widget.onSelect(1),
                        ),
                        _NavItem(
                          icon: Icons.add_rounded,
                          label: '新增',
                          selection: selectionFor(2),
                          accent: ambience.accent,
                          onTap: () => widget.onSelect(2),
                        ),
                        _NavItem(
                          icon: Icons.settings_outlined,
                          label: '设置',
                          selection: selectionFor(3),
                          accent: ambience.accent,
                          onTap: () => widget.onSelect(3),
                        ),
                      ],
                    ),
                  ],
                );
              },
            );
          },
        ),
      ),
    );
  }
}

class _LiquidNavSelection extends StatelessWidget {
  const _LiquidNavSelection({
    required this.accent,
    required this.clarity,
  });

  final Color accent;
  final int clarity;

  @override
  Widget build(BuildContext context) {
    final t = clarity.clamp(0, 100) / 100.0;
    final fade = 1.0 - t;
    final radius = BorderRadius.circular(28);
    final fill = 0.055 + (0.075 * fade);
    final rim = 0.11 + (0.10 * fade);

    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: radius,
        boxShadow: [
          BoxShadow(
            color: withOpacitySafe(Colors.black, 0.13),
            blurRadius: 12,
            spreadRadius: -7,
            offset: const Offset(0, 5),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: radius,
        child: Stack(
          fit: StackFit.expand,
          children: [
            DecoratedBox(
              decoration: BoxDecoration(
                borderRadius: radius,
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [
                    withOpacitySafe(Colors.white, fill * 1.22),
                    withOpacitySafe(accent, fill * 0.72),
                    withOpacitySafe(Colors.white, fill * 0.46),
                  ],
                  stops: const [0.0, 0.55, 1.0],
                ),
              ),
            ),
            DecoratedBox(
              decoration: BoxDecoration(
                borderRadius: radius,
                border: Border.all(
                  width: 1.05,
                  color: withOpacitySafe(Colors.white, rim),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(1.45),
              child: DecoratedBox(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(26.5),
                  border: Border.all(
                    width: 0.65,
                    color: withOpacitySafe(Colors.white, 0.075 + (0.035 * fade)),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _NavItem extends StatelessWidget {
  const _NavItem({
    required this.icon,
    required this.label,
    required this.selection,
    required this.accent,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final double selection;
  final Color accent;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final foreground = Theme.of(context).colorScheme.onSurface;
    final idle = withOpacitySafe(foreground, 0.78);
    final color = Color.lerp(idle, accent, selection) ?? idle;
    return Expanded(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 2),
        child: Semantics(
          button: true,
          selected: selection > 0.5,
          label: label,
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: onTap,
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                SizedBox(
                  width: 25,
                  height: 25,
                  child: Center(
                    child: Icon(icon, size: 22, color: color),
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  label,
                  maxLines: 1,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: color,
                        fontWeight: FontWeight.w500,
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
