import 'package:flutter/material.dart';

import '../app_controller.dart';
import '../models.dart';
import '../ui_utils.dart';
import '../widgets/glass_surface.dart';

class DateCalculatorScreen extends StatefulWidget {
  const DateCalculatorScreen({super.key, required this.controller});
  final AppController controller;

  @override
  State<DateCalculatorScreen> createState() => _DateCalculatorScreenState();
}

class _DateCalculatorScreenState extends State<DateCalculatorScreen> {
  late DateTime offsetStart;
  late DateTime intervalStart;
  late DateTime intervalEnd;
  final amountController = TextEditingController();
  String unit = '天';
  bool after = true;
  bool includeStart = false;

  @override
  void initState() {
    super.initState();
    final today = widget.controller.snapshot!.today;
    offsetStart = today;
    intervalStart = today;
    intervalEnd = today;
  }

  @override
  void dispose() {
    amountController.dispose();
    super.dispose();
  }

  Future<DateTime?> _pick(DateTime value) => showGlassCalendarDatePicker(
        context: context,
        initialDate: value,
        firstDate: DateTime(1900),
        lastDate: DateTime(2200),
      );

  DateTime get resultDate {
    final amount = (int.tryParse(amountController.text) ?? 0).clamp(0, 999999).toInt();
    final delta = after ? amount : -amount;
    if (unit == '天') return offsetStart.add(Duration(days: delta));
    if (unit == '周') return offsetStart.add(Duration(days: delta * 7));
    if (unit == '月') return _shiftMonths(offsetStart, delta);
    return _shiftMonths(offsetStart, delta * 12);
  }

  int get intervalDays {
    final a = DateTime(intervalStart.year, intervalStart.month, intervalStart.day);
    final b = DateTime(intervalEnd.year, intervalEnd.month, intervalEnd.day);
    final raw = b.difference(a).inDays;
    if (!includeStart) return raw;
    return raw >= 0 ? raw + 1 : raw - 1;
  }

  @override
  Widget build(BuildContext context) {
    final snapshot = widget.controller.snapshot!;
    final scheme = Theme.of(context).colorScheme;
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
                        onPressed: () => Navigator.of(context).pop(),
                        icon: const Icon(Icons.arrow_back_rounded),
                      ),
                      const SizedBox(width: 4),
                      Text('日期计算器', style: Theme.of(context).textTheme.titleLarge),
                    ],
                  ),
                  const SizedBox(height: 14),
                  _SectionTitle('计算日期'),
                  const SizedBox(height: 10),
                  _CalculatorCard(
                    snapshot: snapshot,
                    child: Column(
                      children: [
                        _DateSentence(
                          prefix: '从',
                          date: offsetStart,
                          suffix: '开始',
                          onTap: () async {
                            final value = await _pick(offsetStart);
                            if (value != null && mounted) setState(() => offsetStart = value);
                          },
                        ),
                        const SizedBox(height: 12),
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Expanded(
                              child: TextField(
                                controller: amountController,
                                keyboardType: TextInputType.number,
                                maxLength: 6,
                                decoration: const InputDecoration(labelText: '数字', counterText: ''),
                                onChanged: (_) => setState(() {}),
                              ),
                            ),
                            const SizedBox(width: 10),
                            _DropdownBox<String>(
                              value: unit,
                              width: 76,
                              values: const ['天', '周', '月', '年'],
                              label: (value) => value,
                              onChanged: (value) => setState(() => unit = value),
                              isDark: snapshot.isDark,
                              clarity: snapshot.settings.glassClarity,
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        Align(
                          alignment: Alignment.centerLeft,
                          child: _DropdownBox<bool>(
                            value: after,
                            width: 120,
                            values: const [false, true],
                            label: (value) => value ? '之后' : '之前',
                            onChanged: (value) => setState(() => after = value),
                            isDark: snapshot.isDark,
                            clarity: snapshot.settings.glassClarity,
                          ),
                        ),
                        const SizedBox(height: 14),
                        Text(
                          longDateText(resultDate),
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                                color: scheme.primary,
                                fontWeight: FontWeight.bold,
                              ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 14),
                  _SectionTitle('计算日期间隔'),
                  const SizedBox(height: 10),
                  _CalculatorCard(
                    snapshot: snapshot,
                    child: Column(
                      children: [
                        _DateSentence(
                          prefix: '从',
                          date: intervalStart,
                          suffix: '开始，',
                          onTap: () async {
                            final value = await _pick(intervalStart);
                            if (value != null && mounted) setState(() => intervalStart = value);
                          },
                        ),
                        const SizedBox(height: 10),
                        _DateSentence(
                          prefix: '至',
                          date: intervalEnd,
                          suffix: '结束',
                          onTap: () async {
                            final value = await _pick(intervalEnd);
                            if (value != null && mounted) setState(() => intervalEnd = value);
                          },
                        ),
                        const SizedBox(height: 18),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Text('共 ', style: Theme.of(context).textTheme.titleLarge),
                            Text(
                              intervalDays.abs().toString(),
                              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                                    color: scheme.primary,
                                    fontWeight: FontWeight.bold,
                                  ),
                            ),
                            Text(' Days', style: Theme.of(context).textTheme.titleLarge),
                          ],
                        ),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Checkbox(
                              value: includeStart,
                              onChanged: (value) => setState(() => includeStart = value ?? false),
                            ),
                            const Text('包含起始日'),
                          ],
                        ),
                      ],
                    ),
                  ),
                ]),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Text(
        text,
        style: Theme.of(context).textTheme.titleLarge?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
              fontWeight: FontWeight.bold,
            ),
      );
}

class _CalculatorCard extends StatelessWidget {
  const _CalculatorCard({required this.snapshot, required this.child});
  final AppSnapshot snapshot;
  final Widget child;

  @override
  Widget build(BuildContext context) => GlassSurface(
        isDark: snapshot.isDark,
        clarity: snapshot.settings.glassClarity,
        blur: false,
        radius: 18,
        padding: const EdgeInsets.all(18),
        child: child,
      );
}

class _DateSentence extends StatelessWidget {
  const _DateSentence({
    required this.prefix,
    required this.date,
    required this.suffix,
    required this.onTap,
  });

  final String prefix;
  final DateTime date;
  final String suffix;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(prefix, style: Theme.of(context).textTheme.titleLarge?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
              Text(
                ' ${compactDateText(date)} ',
                style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      color: Theme.of(context).colorScheme.primary,
                      fontWeight: FontWeight.bold,
                    ),
              ),
              Text(suffix, style: Theme.of(context).textTheme.titleLarge?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
            ],
          ),
        ),
      );
}

class _DropdownBox<T> extends StatefulWidget {
  const _DropdownBox({
    required this.value,
    required this.values,
    required this.label,
    required this.onChanged,
    required this.width,
    required this.isDark,
    required this.clarity,
  });

  final T value;
  final List<T> values;
  final String Function(T value) label;
  final ValueChanged<T> onChanged;
  final double width;
  final bool isDark;
  final int clarity;

  @override
  State<_DropdownBox<T>> createState() => _DropdownBoxState<T>();
}

class _DropdownBoxState<T> extends State<_DropdownBox<T>> {
  final LayerLink _link = LayerLink();
  OverlayEntry? _overlayEntry;
  LocalHistoryEntry? _historyEntry;

  bool get _expanded => _overlayEntry != null;

  @override
  void didUpdateWidget(covariant _DropdownBox<T> oldWidget) {
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
            offset: const Offset(0, 7),
            child: Material(
              type: MaterialType.transparency,
              child: SizedBox(
                width: widget.width,
                child: GlassSurface(
                  isDark: widget.isDark,
                  clarity: widget.clarity,
                  blur: true,
                  radius: 16,
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: widget.values.map((item) {
                      final selected = item == widget.value;
                      return InkWell(
                        onTap: () {
                          widget.onChanged(item);
                          _hideMenu();
                        },
                        child: SizedBox(
                          height: 44,
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 12),
                            child: Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    widget.label(item),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                                if (selected) const Icon(Icons.check_rounded, size: 17),
                              ],
                            ),
                          ),
                        ),
                      );
                    }).toList(growable: false),
                  ),
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
  Widget build(BuildContext context) => SizedBox(
        width: widget.width,
        child: CompositedTransformTarget(
          link: _link,
          child: OutlinedButton(
            onPressed: _toggleMenu,
            style: OutlinedButton.styleFrom(
              minimumSize: const Size(0, 50),
              padding: const EdgeInsets.symmetric(horizontal: 12),
            ),
            child: Row(
              children: [
                Expanded(child: Text(widget.label(widget.value), maxLines: 1)),
                Icon(_expanded ? Icons.expand_less_rounded : Icons.expand_more_rounded, size: 18),
              ],
            ),
          ),
        ),
      );
}

DateTime _shiftMonths(DateTime date, int delta) {
  final zero = date.year * 12 + date.month - 1 + delta;
  final year = zero ~/ 12;
  final month = zero % 12 + 1;
  final lastDay = DateTime(year, month + 1, 0).day;
  return DateTime(year, month, date.day.clamp(1, lastDay).toInt());
}
