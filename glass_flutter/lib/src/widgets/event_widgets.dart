import 'dart:io';

import 'package:flutter/material.dart';

import '../glass_theme.dart';
import '../models.dart';
import 'glass_surface.dart';

String compactDate(DateTime date) => '${date.year}年${date.month}月${date.day}日';

String dayLead(int delta) {
  if (delta == 0) return '今天';
  return delta > 0 ? '还有' : '已经';
}

String dayMagnitude(int delta) => delta == 0 ? '今天' : delta.abs().toString();

class EventImage extends StatelessWidget {
  const EventImage({
    super.key,
    required this.image,
    required this.detail,
    this.filterQuality = FilterQuality.medium,
  });

  final EventImageModel image;
  final bool detail;
  final FilterQuality filterQuality;

  @override
  Widget build(BuildContext context) {
    final path = image.filePath;
    if (path == null || path.isEmpty) return const SizedBox.shrink();
    final file = File(path);
    final transform = detail ? image.detailTransform : image.homeTransform;
    final focusX = transform.focusX.clamp(0.0, 1.0).toDouble();
    final focusY = transform.focusY.clamp(0.0, 1.0).toDouble();
    final zoom = transform.zoom.clamp(1.0, 4.0).toDouble();
    final sourceAspect = image.width > 0 && image.height > 0
        ? image.width / image.height
        : 1.0;

    return LayoutBuilder(
      builder: (context, constraints) {
        final viewportWidth = constraints.maxWidth;
        final viewportHeight = constraints.maxHeight;
        if (!viewportWidth.isFinite ||
            !viewportHeight.isFinite ||
            viewportWidth <= 0 ||
            viewportHeight <= 0) {
          return Image.file(
            file,
            fit: BoxFit.cover,
            filterQuality: filterQuality,
            gaplessPlayback: true,
            errorBuilder: (_, __, ___) => const SizedBox.shrink(),
          );
        }

        final viewportAspect = viewportWidth / viewportHeight;
        late final double baseWidth;
        late final double baseHeight;
        if (sourceAspect > viewportAspect) {
          baseHeight = viewportHeight;
          baseWidth = viewportHeight * sourceAspect;
        } else {
          baseWidth = viewportWidth;
          baseHeight = viewportWidth / sourceAspect;
        }

        final renderedWidth = baseWidth * zoom;
        final renderedHeight = baseHeight * zoom;
        final overflowX = (renderedWidth - viewportWidth)
            .clamp(0.0, double.infinity)
            .toDouble();
        final overflowY = (renderedHeight - viewportHeight)
            .clamp(0.0, double.infinity)
            .toDouble();
        final offset = Offset(-overflowX * focusX, -overflowY * focusY);

        return ClipRect(
          child: Stack(
            fit: StackFit.expand,
            clipBehavior: Clip.hardEdge,
            children: [
              Positioned(
                left: 0,
                top: 0,
                width: baseWidth,
                height: baseHeight,
                child: Transform.translate(
                  offset: offset,
                  child: Transform.scale(
                    scale: zoom,
                    alignment: Alignment.topLeft,
                    filterQuality: filterQuality,
                    child: RepaintBoundary(
                      child: Image.file(
                        file,
                        fit: BoxFit.fill,
                        filterQuality: filterQuality,
                        gaplessPlayback: true,
                        errorBuilder: (_, __, ___) => const SizedBox.shrink(),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class HeroEventCard extends StatelessWidget {
  const HeroEventCard({
    super.key,
    required this.event,
    required this.isDark,
    required this.clarity,
    required this.onTap,
    this.onAdjustImage,
    this.emptyTitle = '暂无事件',
  });

  final DayEventModel? event;
  final bool isDark;
  final int clarity;
  final VoidCallback? onTap;
  final VoidCallback? onAdjustImage;
  final String emptyTitle;

  @override
  Widget build(BuildContext context) {
    final image = event?.backgroundImage;
    final hasImage = image?.filePath?.isNotEmpty == true;
    final theme = Theme.of(context);

    Widget content = SizedBox(
      height: 238,
      child: Stack(
        fit: StackFit.expand,
        children: [
          if (hasImage) EventImage(image: image!, detail: false),
          if (hasImage)
            DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    withOpacitySafe(Colors.black, 0.10),
                    withOpacitySafe(Colors.black, 0.62),
                  ],
                ),
              ),
            ),
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 24, 24, 22),
            child: event == null
                ? Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(Icons.event_note_rounded, size: 42, color: theme.colorScheme.primary),
                      const SizedBox(width: 16),
                      Padding(
                        padding: const EdgeInsets.only(top: 8),
                        child: Text(emptyTitle, style: theme.textTheme.titleLarge),
                      ),
                    ],
                  )
                : _HeroContent(event: event!, forceLightText: hasImage),
          ),
          if (hasImage && onAdjustImage != null)
            Positioned(
              top: 10,
              right: 10,
              child: Material(
                color: withOpacitySafe(Colors.black, 0.32),
                shape: const CircleBorder(),
                child: IconButton(
                  tooltip: '调整首页图片',
                  onPressed: onAdjustImage,
                  icon: const Icon(Icons.touch_app_rounded, color: Colors.white, size: 21),
                ),
              ),
            ),
        ],
      ),
    );

    if (!hasImage) {
      content = GlassSurface(
        isDark: isDark,
        clarity: clarity,
        radius: 30,
        blur: false,
        child: content,
      );
    } else {
      // Photo hero cards should share the same physical edge and elevation as
      // the rest of the glass system without adding blur or a translucent fill
      // on top of the image itself.
      content = GlassSurface(
        isDark: isDark,
        clarity: 100,
        radius: 30,
        blur: false,
        borderOpacityScale: 0.92,
        child: content,
      );
    }

    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(30),
        onTap: onTap,
        child: content,
      ),
    );
  }
}

class _HeroContent extends StatelessWidget {
  const _HeroContent({required this.event, required this.forceLightText});

  final DayEventModel event;
  final bool forceLightText;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final primary = forceLightText ? Colors.white : theme.colorScheme.onSurface;
    final secondary = forceLightText
        ? withOpacitySafe(Colors.white, 0.78)
        : withOpacitySafe(theme.colorScheme.onSurface, 0.62);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                event.title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.headlineMedium?.copyWith(color: primary),
              ),
            ),
            if (event.isPinned) ...[
              const SizedBox(width: 8),
              Icon(Icons.vertical_align_top_rounded, size: 18, color: secondary),
            ],
          ],
        ),
        const Spacer(),
        if (event.signedDays == 0)
          Text(
            '今天',
            style: theme.textTheme.displayLarge?.copyWith(color: primary),
          )
        else ...[
          Text(
            dayLead(event.signedDays),
            style: theme.textTheme.bodyMedium?.copyWith(color: secondary),
          ),
          const SizedBox(height: 2),
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                event.signedDays.abs().toString(),
                style: theme.textTheme.displayLarge?.copyWith(
                  color: primary,
                  shadows: glassDayCountGlow(
                    theme.colorScheme.primary,
                    fontSize: theme.textTheme.displayLarge?.fontSize ?? 58,
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.only(left: 7, bottom: 6),
                child: Text(
                  '天',
                  style: theme.textTheme.titleMedium?.copyWith(color: secondary),
                ),
              ),
            ],
          ),
        ],
        const SizedBox(height: 8),
        Row(
          children: [
            Text(
              compactDate(event.effectiveDate),
              style: theme.textTheme.bodyMedium?.copyWith(color: secondary),
            ),
            if (event.category.isNotEmpty) ...[
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: Text('·', style: theme.textTheme.bodyMedium?.copyWith(color: secondary)),
              ),
              Flexible(
                child: Text(
                  event.category,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodyMedium?.copyWith(color: secondary),
                ),
              ),
            ],
          ],
        ),
      ],
    );
  }
}

class EventListCard extends StatelessWidget {
  const EventListCard({
    super.key,
    required this.event,
    required this.isDark,
    required this.clarity,
    required this.accent,
    required this.onTap,
  });

  final DayEventModel event;
  final bool isDark;
  final int clarity;
  final Color accent;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return GlassSurface(
      isDark: isDark,
      clarity: clarity,
      radius: 22,
      blur: false,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(22),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 15),
            child: Row(
              children: [
                Container(
                  width: 3,
                  height: 58,
                  decoration: BoxDecoration(
                    color: accent,
                    borderRadius: BorderRadius.circular(99),
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              event.title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: theme.textTheme.titleMedium,
                            ),
                          ),
                          if (event.isPinned) ...[
                            const SizedBox(width: 6),
                            Icon(Icons.vertical_align_top_rounded, size: 15, color: accent),
                          ],
                        ],
                      ),
                      const SizedBox(height: 5),
                      Text(
                        compactDate(event.effectiveDate),
                        style: theme.textTheme.bodySmall,
                      ),
                      if (event.category.isNotEmpty || event.repeatMode == 'YEARLY') ...[
                        const SizedBox(height: 7),
                        Wrap(
                          spacing: 6,
                          children: [
                            if (event.category.isNotEmpty) _MiniTag(event.category),
                            if (event.repeatMode == 'YEARLY') const _MiniTag('每年'),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                if (event.signedDays == 0)
                  Text(
                    '今天',
                    style: theme.textTheme.titleMedium?.copyWith(color: accent),
                  )
                else
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        event.signedDays.abs().toString(),
                        style: theme.textTheme.headlineMedium?.copyWith(
                          color: accent,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.only(left: 3, bottom: 2),
                        child: Text('天', style: theme.textTheme.bodySmall),
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

class _MiniTag extends StatelessWidget {
  const _MiniTag(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    final foreground = Theme.of(context).colorScheme.onSurface;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: withOpacitySafe(foreground, 0.07),
        borderRadius: BorderRadius.circular(99),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        child: Text(text, style: Theme.of(context).textTheme.bodySmall),
      ),
    );
  }
}