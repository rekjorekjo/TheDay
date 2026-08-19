import 'package:flutter/material.dart';

import 'models.dart';

const String unclassifiedCategoryName = '未分类';

String normalizedCategory(String raw) {
  final normalized = raw.trim();
  return normalized.isEmpty ? unclassifiedCategoryName : normalized;
}

String compactDateText(DateTime date) => '${date.year}年${date.month}月${date.day}日';
String longDateText(DateTime date) => '${date.year}年${date.month}月${date.day}日';

double detailImagePreviewAspectRatio(EventImageModel image) {
  if (image.width <= 0 || image.height <= 0) return 1.50;
  final ratio = image.width / image.height;
  if (!ratio.isFinite || ratio <= 0) return 1.50;
  return ratio.clamp(0.60, 1.50).toDouble();
}

Future<DateTime?> showGlassCalendarDatePicker({
  required BuildContext context,
  required DateTime initialDate,
  DateTime? firstDate,
  DateTime? lastDate,
  String title = '选择日期',
}) {
  final first = DateUtils.dateOnly(firstDate ?? DateTime(1900));
  final last = DateUtils.dateOnly(lastDate ?? DateTime(2200));
  final initial = DateUtils.dateOnly(initialDate).isBefore(first)
      ? first
      : DateUtils.dateOnly(initialDate).isAfter(last)
          ? last
          : DateUtils.dateOnly(initialDate);

  return showDialog<DateTime>(
    context: context,
    builder: (dialogContext) {
      var selected = initial;
      return StatefulBuilder(
        builder: (context, setDialogState) => Dialog(
          clipBehavior: Clip.antiAlias,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 400),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(24, 22, 24, 4),
                    child: Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        title,
                        style: Theme.of(context).textTheme.titleLarge?.copyWith(
                              fontWeight: FontWeight.w600,
                            ),
                      ),
                    ),
                  ),
                  CalendarDatePicker(
                    initialDate: initial,
                    firstDate: first,
                    lastDate: last,
                    currentDate: DateUtils.dateOnly(DateTime.now()),
                    onDateChanged: (value) => setDialogState(
                      () => selected = DateUtils.dateOnly(value),
                    ),
                  ),
                  const Divider(height: 1),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(12, 8, 12, 10),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        TextButton(
                          onPressed: () => Navigator.of(dialogContext).pop(),
                          child: const Text('取消'),
                        ),
                        const SizedBox(width: 4),
                        TextButton(
                          onPressed: () => Navigator.of(dialogContext).pop(selected),
                          child: const Text('确定'),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      );
    },
  );
}
String weekdayText(DateTime date) {
  const values = <String>['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];
  return values[date.weekday - 1];
}

String dayDistanceLabel(int delta) {
  if (delta > 0) return '还有 ${delta.abs()} 天';
  if (delta < 0) return '已经 ${delta.abs()} 天';
  return '今天';
}

String reminderText(int? daysBefore) {
  if (daysBefore == null) return '不提醒';
  switch (daysBefore) {
    case 0:
      return '当天';
    case 1:
      return '提前 1 天';
    case 3:
      return '提前 3 天';
    case 7:
      return '提前 7 天';
    default:
      return '提前 $daysBefore 天';
  }
}

List<DayEventModel> sortEvents(
  Iterable<DayEventModel> input,
  GlassSettings settings,
) {
  final values = input.toList(growable: false);
  int direction(int value) => settings.sortDirection == 'DESCENDING' ? -value : value;

  int contentCompare(DayEventModel a, DayEventModel b) {
    switch (settings.sortMode) {
      case 'DATE':
        return direction(a.effectiveDate.compareTo(b.effectiveDate));
      case 'TITLE':
        return direction(a.title.toLowerCase().compareTo(b.title.toLowerCase()));
      case 'CREATED':
        return direction(a.createdAtEpochMillis.compareTo(b.createdAtEpochMillis));
      case 'SMART':
      default:
        final groupA = a.signedDays >= 0 ? 0 : 1;
        final groupB = b.signedDays >= 0 ? 0 : 1;
        if (groupA != groupB) return groupA.compareTo(groupB);
        return direction(a.signedDays.abs().compareTo(b.signedDays.abs()));
    }
  }

  values.sort((a, b) {
    final pinned = (a.isPinned ? 0 : 1).compareTo(b.isPinned ? 0 : 1);
    if (pinned != 0) return pinned;
    final content = contentCompare(a, b);
    if (content != 0) return content;
    final created = a.createdAtEpochMillis.compareTo(b.createdAtEpochMillis);
    if (created != 0) return created;
    return a.id.compareTo(b.id);
  });
  return values;
}

class GlassPageTitleBar extends StatelessWidget {
  const GlassPageTitleBar({
    super.key,
    required this.title,
    required this.isDark,
    required this.clarity,
    this.onBack,
    this.actions = const <Widget>[],
  });

  final String title;
  final bool isDark;
  final int clarity;
  final VoidCallback? onBack;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        if (onBack != null) ...[
          SizedBox.square(
            dimension: 44,
            child: IconButton(
              tooltip: '返回',
              onPressed: onBack,
              icon: const Icon(Icons.arrow_back_rounded),
            ),
          ),
          const SizedBox(width: 4),
        ],
        Expanded(
          child: Text(
            title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.w600,
                ),
          ),
        ),
        ...actions,
      ],
    );
  }
}
