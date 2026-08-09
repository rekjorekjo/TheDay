import 'package:flutter/material.dart';

import '../app_controller.dart';
import '../glass_route.dart';
import '../glass_theme.dart';
import '../models.dart';
import '../ui_utils.dart';
import '../widgets/event_widgets.dart';
import '../widgets/glass_surface.dart';
import 'image_transform_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({
    super.key,
    required this.controller,
    required this.onOpenEvent,
    required this.onOpenTools,
    required this.bottomInset,
  });

  final AppController controller;
  final ValueChanged<DayEventModel> onOpenEvent;
  final VoidCallback onOpenTools;
  final double bottomInset;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  String timeFilter = 'ALL';
  final Set<String> selectedCategories = <String>{};

  @override
  Widget build(BuildContext context) {
    final snapshot = widget.controller.snapshot!;
    final visibleEvents = snapshot.events.where((event) {
      return snapshot.settings.showPastEvents || event.signedDays >= 0;
    }).toList(growable: false);
    final categories = visibleEvents
        .map((event) => event.category.trim())
        .where((value) => value.isNotEmpty)
        .toSet()
        .toList()
      ..sort((a, b) => a.toLowerCase().compareTo(b.toLowerCase()));
    selectedCategories.removeWhere((value) => !categories.contains(value));
    if (!snapshot.settings.showPastEvents && timeFilter == 'PAST') {
      timeFilter = 'ALL';
    }

    final filtered = visibleEvents.where((event) {
      final timeMatches = switch (timeFilter) {
        'UPCOMING' => event.signedDays >= 0,
        'PAST' => event.signedDays < 0,
        _ => true,
      };
      final categoryMatches = selectedCategories.isEmpty ||
          selectedCategories.contains(event.category.trim());
      return timeMatches && categoryMatches;
    });
    final events = sortEvents(filtered, snapshot.settings);
    final hero = snapshot.heroEvent;
    final ambience = ambienceFor(snapshot.settings.paletteStyle);
    final daysInMonth = DateTime(snapshot.today.year, snapshot.today.month + 1, 0).day;
    final progress = snapshot.today.day / daysInMonth;
    final hiddenPastOnly = snapshot.events.isNotEmpty && visibleEvents.isEmpty;

    return CustomScrollView(
      key: const PageStorageKey<String>('home-scroll'),
      physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
      slivers: [
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(18, 10, 18, 0),
          sliver: SliverList(
            delegate: SliverChildListDelegate.fixed([
              _HomeHeader(
                today: snapshot.today,
                progress: progress,
                accent: ambience.accent,
                isDark: snapshot.isDark,
                clarity: snapshot.settings.glassClarity,
                onOpenTools: widget.onOpenTools,
                onOpenCalendar: () => _showCalendar(snapshot),
              ),
              const SizedBox(height: 18),
              HeroEventCard(
                event: hero,
                isDark: snapshot.isDark,
                clarity: snapshot.settings.glassClarity,
                emptyTitle: hiddenPastOnly ? '暂无可显示事件' : '暂无事件',
                onTap: hero == null ? null : () => widget.onOpenEvent(hero),
                onAdjustImage: hero?.backgroundImage?.filePath?.isNotEmpty == true
                    ? () => _openImageAdjust(hero!, detail: false)
                    : null,
              ),
              if (visibleEvents.isNotEmpty) ...[
                const SizedBox(height: 14),
                _HomeFilterMenuButton(
                  label: _filterSummary(
                    total: visibleEvents.length,
                    upcoming: visibleEvents.where((e) => e.signedDays >= 0).length,
                    past: visibleEvents.where((e) => e.signedDays < 0).length,
                  ),
                  snapshot: snapshot,
                  categories: categories,
                  timeFilter: timeFilter,
                  selectedCategories: selectedCategories,
                  totalCount: visibleEvents.length,
                  upcomingCount: visibleEvents.where((e) => e.signedDays >= 0).length,
                  pastCount: visibleEvents.where((e) => e.signedDays < 0).length,
                  onTimeFilterChanged: (value) => setState(() => timeFilter = value),
                  onCategoryToggle: (category) => setState(() {
                    if (!selectedCategories.add(category)) {
                      selectedCategories.remove(category);
                    }
                  }),
                  onClearCategories: () => setState(selectedCategories.clear),
                ),
                const SizedBox(height: 12),
              ],
            ]),
          ),
        ),
        if (visibleEvents.isNotEmpty && events.isEmpty)
          SliverPadding(
            padding: EdgeInsets.fromLTRB(18, 18, 18, widget.bottomInset),
            sliver: SliverToBoxAdapter(
              child: Text(
                '当前筛选下没有事件',
                style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
              ),
            ),
          )
        else if (events.isNotEmpty)
          SliverPadding(
            padding: EdgeInsets.fromLTRB(18, 0, 18, widget.bottomInset),
            sliver: SliverList(
              delegate: SliverChildBuilderDelegate(
                (context, index) {
                  final event = events[index];
                  return Padding(
                    padding: EdgeInsets.only(
                      bottom: index == events.length - 1 ? 0 : 10,
                    ),
                    child: EventListCard(
                      event: event,
                      isDark: snapshot.isDark,
                      clarity: snapshot.settings.glassClarity,
                      accent: ambience.accent,
                      onTap: () => widget.onOpenEvent(event),
                    ),
                  );
                },
                childCount: events.length,
              ),
            ),
          )
        else
          SliverToBoxAdapter(child: SizedBox(height: widget.bottomInset)),
      ],
    );
  }

  String _filterSummary({
    required int total,
    required int upcoming,
    required int past,
  }) {
    final label = switch (timeFilter) {
      'UPCOMING' => '倒数 $upcoming',
      'PAST' => '正数 $past',
      _ => '全部 $total',
    };
    final category = selectedCategories.isEmpty
        ? '全部分类'
        : selectedCategories.length == 1
            ? selectedCategories.first
            : '${selectedCategories.length} 个分类';
    return '筛选：$label · $category';
  }

  Future<void> _openImageAdjust(DayEventModel event, {required bool detail}) async {
    final image = event.backgroundImage;
    if (image == null) return;
    final updated = await Navigator.of(context).push<EventImageModel>(
      glassRoute<EventImageModel>(
        controller: widget.controller,
        builder: (_) => ImageTransformScreen(
          snapshot: widget.controller.snapshot!,
          event: event,
          detail: detail,
          title: detail ? '调整详情图片' : '调整首页图片',
        ),
      ),
    );
    if (updated == null) return;
    await widget.controller.saveEvent(event.toNativeJson(imageOverride: updated));
  }

  Future<void> _showCalendar(AppSnapshot snapshot) async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) => _EventCalendarSheet(
        snapshot: snapshot,
      ),
    );
  }

}

class _HomeHeader extends StatelessWidget {
  const _HomeHeader({
    required this.today,
    required this.progress,
    required this.accent,
    required this.isDark,
    required this.clarity,
    required this.onOpenTools,
    required this.onOpenCalendar,
  });

  final DateTime today;
  final double progress;
  final Color accent;
  final bool isDark;
  final int clarity;
  final VoidCallback onOpenTools;
  final VoidCallback onOpenCalendar;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final percent = (progress * 100).round();
    return SafeArea(
      bottom: false,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          _TodayCalendarMark(today: today, accent: accent, onTap: onOpenCalendar),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'THE DAY',
                  style: theme.textTheme.titleMedium?.copyWith(
                    letterSpacing: 2.2,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    SizedBox(
                      width: 124,
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(99),
                        child: SizedBox(
                          height: 3,
                          child: LinearProgressIndicator(
                            value: progress,
                            color: accent,
                            backgroundColor: withOpacitySafe(theme.colorScheme.onSurface, 0.10),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text('$percent%', style: theme.textTheme.bodySmall),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(width: 10),
          GlassRoundButton(
            icon: Icons.build_rounded,
            tooltip: '工具栏',
            isDark: isDark,
            clarity: clarity,
            onPressed: onOpenTools,
          ),
        ],
      ),
    );
  }
}

class _TodayCalendarMark extends StatelessWidget {
  const _TodayCalendarMark({required this.today, required this.accent, required this.onTap});
  final DateTime today;
  final Color accent;
  final VoidCallback onTap;
  static const _weekdays = <String>['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(10),
        child: Container(
          width: 42,
          height: 46,
          clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            color: withOpacitySafe(theme.colorScheme.onSurface, 0.055),
            border: Border.all(color: withOpacitySafe(Colors.white, 0.16), width: 0.8),
          ),
          child: Column(
            children: [
              Container(
                height: 15,
                alignment: Alignment.center,
                color: accent,
                child: Text(
                  _weekdays[today.weekday - 1],
                  style: theme.textTheme.labelMedium?.copyWith(
                    color: isDarkColor(accent) ? Colors.white : const Color(0xFF101418),
                    fontSize: 7,
                    height: 1,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 0.45,
                  ),
                ),
              ),
              Expanded(
                child: Center(
                  child: Text(
                    '${today.day}',
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontSize: 18,
                      height: 1,
                      fontWeight: FontWeight.w700,
                    ),
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

class _HomeFilterMenuButton extends StatefulWidget {
  const _HomeFilterMenuButton({
    required this.label,
    required this.snapshot,
    required this.categories,
    required this.timeFilter,
    required this.selectedCategories,
    required this.totalCount,
    required this.upcomingCount,
    required this.pastCount,
    required this.onTimeFilterChanged,
    required this.onCategoryToggle,
    required this.onClearCategories,
  });

  final String label;
  final AppSnapshot snapshot;
  final List<String> categories;
  final String timeFilter;
  final Set<String> selectedCategories;
  final int totalCount;
  final int upcomingCount;
  final int pastCount;
  final ValueChanged<String> onTimeFilterChanged;
  final ValueChanged<String> onCategoryToggle;
  final VoidCallback onClearCategories;

  @override
  State<_HomeFilterMenuButton> createState() => _HomeFilterMenuButtonState();
}

class _HomeFilterMenuButtonState extends State<_HomeFilterMenuButton> {
  final LayerLink _link = LayerLink();
  OverlayEntry? _overlayEntry;
  LocalHistoryEntry? _historyEntry;

  bool get _expanded => _overlayEntry != null;

  @override
  void didUpdateWidget(covariant _HomeFilterMenuButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    _overlayEntry?.markNeedsBuild();
  }

  @override
  void dispose() {
    _hideMenu(notify: false);
    super.dispose();
  }

  void _toggleMenu() => _expanded ? _hideMenu() : _showMenu();

  void _showMenu() {
    final renderBox = context.findRenderObject() as RenderBox?;
    if (renderBox == null || !renderBox.hasSize) return;
    final anchorWidth = renderBox.size.width;
    final overlay = Overlay.of(context, rootOverlay: true);

    _overlayEntry = OverlayEntry(
      builder: (overlayContext) => Stack(
        children: [
          Positioned.fill(
            child: GestureDetector(
              behavior: HitTestBehavior.translucent,
              onTap: () => _hideMenu(),
              child: const SizedBox.expand(),
            ),
          ),
          CompositedTransformFollower(
            link: _link,
            showWhenUnlinked: false,
            targetAnchor: Alignment.bottomLeft,
            followerAnchor: Alignment.topLeft,
            offset: const Offset(0, 8),
            child: Material(
              type: MaterialType.transparency,
              child: SizedBox(
                width: anchorWidth,
                child: _HomeFilterForm(
                  snapshot: widget.snapshot,
                  categories: widget.categories,
                  timeFilter: widget.timeFilter,
                  selectedCategories: widget.selectedCategories,
                  totalCount: widget.totalCount,
                  upcomingCount: widget.upcomingCount,
                  pastCount: widget.pastCount,
                  onTimeFilterChanged: widget.onTimeFilterChanged,
                  onCategoryToggle: widget.onCategoryToggle,
                  onClearCategories: widget.onClearCategories,
                  onDone: () => _hideMenu(),
                ),
              ),
            ),
          ),
        ],
      ),
    );
    overlay.insert(_overlayEntry!);
    _historyEntry = LocalHistoryEntry(
      onRemove: () {
        _historyEntry = null;
        final entry = _overlayEntry;
        if (entry == null) return;
        _overlayEntry = null;
        entry.remove();
        if (mounted) setState(() {});
      },
    );
    ModalRoute.of(context)?.addLocalHistoryEntry(_historyEntry!);
    setState(() {});
  }

  void _hideMenu({bool notify = true}) {
    final entry = _overlayEntry;
    if (entry == null) return;
    _overlayEntry = null;
    entry.remove();
    final historyEntry = _historyEntry;
    _historyEntry = null;
    historyEntry?.remove();
    if (notify && mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) => CompositedTransformTarget(
        link: _link,
        child: OutlinedButton(
          onPressed: _toggleMenu,
          style: OutlinedButton.styleFrom(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
          ),
          child: Row(
            children: [
              Expanded(
                child: Text(widget.label, maxLines: 1, overflow: TextOverflow.ellipsis),
              ),
              Icon(_expanded ? Icons.expand_less_rounded : Icons.expand_more_rounded),
            ],
          ),
        ),
      );
}

class _HomeFilterForm extends StatelessWidget {
  const _HomeFilterForm({
    required this.snapshot,
    required this.categories,
    required this.timeFilter,
    required this.selectedCategories,
    required this.totalCount,
    required this.upcomingCount,
    required this.pastCount,
    required this.onTimeFilterChanged,
    required this.onCategoryToggle,
    required this.onClearCategories,
    required this.onDone,
  });

  final AppSnapshot snapshot;
  final List<String> categories;
  final String timeFilter;
  final Set<String> selectedCategories;
  final int totalCount;
  final int upcomingCount;
  final int pastCount;
  final ValueChanged<String> onTimeFilterChanged;
  final ValueChanged<String> onCategoryToggle;
  final VoidCallback onClearCategories;
  final VoidCallback onDone;

  @override
  Widget build(BuildContext context) => GlassSurface(
        isDark: snapshot.isDark,
        clarity: snapshot.settings.glassClarity,
        blur: true,
        radius: 24,
        padding: const EdgeInsets.all(16),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxHeight: 360),
          child: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    FilterChip(
                      selected: timeFilter == 'ALL',
                      showCheckmark: false,
                      onSelected: (_) => onTimeFilterChanged('ALL'),
                      label: Text('全部 $totalCount'),
                    ),
                    FilterChip(
                      selected: timeFilter == 'UPCOMING',
                      showCheckmark: false,
                      onSelected: (_) => onTimeFilterChanged('UPCOMING'),
                      label: Text('倒数 $upcomingCount'),
                    ),
                    if (snapshot.settings.showPastEvents)
                      FilterChip(
                        selected: timeFilter == 'PAST',
                        showCheckmark: false,
                        onSelected: (_) => onTimeFilterChanged('PAST'),
                        label: Text('正数 $pastCount'),
                      ),
                  ],
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    FilterChip(
                      selected: selectedCategories.isEmpty,
                      showCheckmark: false,
                      onSelected: (_) => onClearCategories(),
                      label: const Text('全部分类'),
                    ),
                    for (final category in categories)
                      FilterChip(
                        selected: selectedCategories.contains(category),
                        showCheckmark: false,
                        onSelected: (_) => onCategoryToggle(category),
                        label: Text(category, maxLines: 1, overflow: TextOverflow.ellipsis),
                      ),
                  ],
                ),
                const SizedBox(height: 14),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: selectedCategories.isEmpty ? null : onClearCategories,
                        child: const Text('清空选择'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: FilledButton(
                        onPressed: onDone,
                        child: const Text('完成'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      );
}

class _EventCalendarSheet extends StatefulWidget {
  const _EventCalendarSheet({required this.snapshot});
  final AppSnapshot snapshot;

  @override
  State<_EventCalendarSheet> createState() => _EventCalendarSheetState();
}

class _EventCalendarSheetState extends State<_EventCalendarSheet> {
  late int year;
  late int month;

  @override
  void initState() {
    super.initState();
    year = widget.snapshot.today.year;
    month = widget.snapshot.today.month;
  }

  void _shift(int delta) {
    final value = DateTime(year, month + delta, 1);
    setState(() {
      year = value.year;
      month = value.month;
    });
  }

  Set<int> get eventDays {
    final result = <int>{};
    for (final event in widget.snapshot.events) {
      if (event.repeatMode == 'YEARLY') {
        // Match Classic/DayMath: yearly events do not exist before their
        // original year, and Feb 29 falls back to Feb 28 in non-leap years.
        if (year < event.date.year || event.date.month != month) continue;
        final maxDay = DateTime(year, event.date.month + 1, 0).day;
        final annualDay = event.date.day > maxDay ? maxDay : event.date.day;
        result.add(annualDay);
      } else if (event.date.year == year && event.date.month == month) {
        result.add(event.date.day);
      }
    }
    return result;
  }

  @override
  Widget build(BuildContext context) {
    final snapshot = widget.snapshot;
    final today = snapshot.today;
    final firstWeekday = DateTime(year, month, 1).weekday;
    final length = DateTime(year, month + 1, 0).day;
    final marked = eventDays;
    final bottom = MediaQuery.viewPaddingOf(context).bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(14, 0, 14, 14 + bottom),
      child: GlassSurface(
        isDark: snapshot.isDark,
        clarity: snapshot.settings.glassClarity,
        blur: true,
        radius: 28,
        padding: const EdgeInsets.fromLTRB(14, 8, 14, 18),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                IconButton(onPressed: () => _shift(-1), icon: const Icon(Icons.chevron_left_rounded)),
                Expanded(
                  child: Text(
                    '$year年$month月',
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700),
                  ),
                ),
                IconButton(onPressed: () => _shift(1), icon: const Icon(Icons.chevron_right_rounded)),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: const [
                _Weekday('一'), _Weekday('二'), _Weekday('三'), _Weekday('四'),
                _Weekday('五'), _Weekday('六'), _Weekday('日'),
              ],
            ),
            const SizedBox(height: 8),
            for (var week = 0; week < 6; week++)
              Row(
                children: [
                  for (var dayOfWeek = 1; dayOfWeek <= 7; dayOfWeek++)
                    Expanded(
                      child: SizedBox(
                        height: 40,
                        child: Builder(
                          builder: (context) {
                            final position = week * 7 + dayOfWeek;
                            final day = position - firstWeekday + 1;
                            if (day < 1 || day > length) return const SizedBox.shrink();
                            final isToday = today.year == year && today.month == month && today.day == day;
                            final hasEvent = marked.contains(day);
                            final primary = Theme.of(context).colorScheme.primary;
                            return Center(
                              child: Container(
                                width: 36,
                                height: 36,
                                alignment: Alignment.center,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: isToday && hasEvent
                                      ? primary
                                      : hasEvent
                                          ? withOpacitySafe(primary, 0.20)
                                          : Colors.transparent,
                                  border: isToday && !hasEvent ? Border.all(color: primary) : null,
                                ),
                                child: Text(
                                  '$day',
                                  style: TextStyle(
                                    fontWeight: isToday ? FontWeight.w700 : FontWeight.w400,
                                    color: isToday && hasEvent ? Theme.of(context).colorScheme.onPrimary : null,
                                  ),
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                    ),
                ],
              ),
          ],
        ),
      ),
    );
  }
}

class _Weekday extends StatelessWidget {
  const _Weekday(this.text);
  final String text;
  @override
  Widget build(BuildContext context) => Expanded(
        child: Text(
          text,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.labelMedium?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
        ),
      );
}

bool isDarkColor(Color color) => color.computeLuminance() < 0.48;
