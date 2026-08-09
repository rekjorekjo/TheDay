import 'dart:io';

import 'package:flutter/material.dart';

import '../models.dart';
import '../ui_utils.dart';
import '../widgets/event_widgets.dart';

class ImageTransformScreen extends StatefulWidget {
  const ImageTransformScreen({
    super.key,
    required this.snapshot,
    required this.event,
    required this.detail,
    required this.title,
  });

  final AppSnapshot snapshot;
  final DayEventModel event;
  final bool detail;
  final String title;

  @override
  State<ImageTransformScreen> createState() => _ImageTransformScreenState();
}

class _ImageTransformScreenState extends State<ImageTransformScreen> {
  late EventImageModel image;
  late ImageTransformModel working;
  late ImageTransformModel gestureStart;

  @override
  void initState() {
    super.initState();
    image = widget.event.backgroundImage!;
    working = widget.detail ? image.detailTransform : image.homeTransform;
    gestureStart = working;
  }

  void _setWorking(ImageTransformModel next) {
    setState(() {
      working = ImageTransformModel(
        focusX: next.focusX.clamp(0.0, 1.0).toDouble(),
        focusY: next.focusY.clamp(0.0, 1.0).toDouble(),
        zoom: next.zoom.clamp(1.0, 4.0).toDouble(),
      );
    });
  }

  EventImageModel get updatedImage => widget.detail
      ? image.copyWith(detailTransform: working)
      : image.copyWith(homeTransform: working);

  @override
  Widget build(BuildContext context) {
    final snapshot = widget.snapshot;
    final event = widget.event;
    final imageAspect = image.width > 0 && image.height > 0
        ? image.width / image.height
        : 1.0;
    final previewAspect = widget.detail
        ? detailImagePreviewAspectRatio(image)
        : imageAspect.clamp(1.10, 1.65).toDouble();
    final hasReadableImage = image.filePath?.isNotEmpty == true &&
        File(image.filePath!).existsSync();

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SafeArea(
        bottom: false,
        child: Padding(
          padding: EdgeInsets.fromLTRB(
            18,
            10,
            18,
            18 + MediaQuery.paddingOf(context).bottom,
          ),
          child: SingleChildScrollView(
            physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
            child: Column(
              children: [
                Row(
                  children: [
                    IconButton(
                      tooltip: '返回',
                      onPressed: () => Navigator.of(context).pop(),
                      icon: const Icon(Icons.arrow_back_rounded),
                    ),
                    const SizedBox(width: 4),
                    Expanded(
                      child: Text(
                        widget.title,
                        style: Theme.of(context).textTheme.titleLarge?.copyWith(
                              fontWeight: FontWeight.w600,
                            ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                Align(
                  alignment: Alignment.centerLeft,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(
                            Icons.touch_app_rounded,
                            color: Theme.of(context).colorScheme.primary,
                          ),
                          const SizedBox(width: 8),
                          Text(
                            '单指拖动，双指缩放',
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                LayoutBuilder(
                  builder: (context, constraints) {
                    final width = constraints.maxWidth;
                    final height = width / previewAspect;
                    return SizedBox(
                      width: width,
                      height: height,
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(24),
                        child: GestureDetector(
                          behavior: HitTestBehavior.opaque,
                          onScaleStart: (_) => gestureStart = working,
                          onScaleUpdate: (details) {
                            final nextZoom = (gestureStart.zoom * details.scale)
                                .clamp(1.0, 4.0)
                                .toDouble();
                            final dx = details.focalPointDelta.dx /
                                width.clamp(1.0, double.infinity);
                            final dy = details.focalPointDelta.dy /
                                height.clamp(1.0, double.infinity);
                            _setWorking(
                              working.copyWith(
                                focusX: working.focusX - dx / nextZoom,
                                focusY: working.focusY - dy / nextZoom,
                                zoom: nextZoom,
                              ),
                            );
                            gestureStart = gestureStart.copyWith(zoom: nextZoom);
                          },
                          child: Stack(
                            fit: StackFit.expand,
                            children: [
                              if (hasReadableImage) ...[
                                EventImage(image: updatedImage, detail: widget.detail),
                                const DecoratedBox(
                                  decoration: BoxDecoration(
                                    gradient: LinearGradient(
                                      begin: Alignment.topCenter,
                                      end: Alignment.bottomCenter,
                                      colors: [
                                        Color(0x24000000),
                                        Color(0x52000000),
                                        Color(0xA8000000),
                                      ],
                                    ),
                                  ),
                                ),
                                _PreviewText(event: event, detail: widget.detail),
                              ] else
                                Center(
                                  child: Text(
                                    '图片暂时无法读取',
                                    style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                                        ),
                                  ),
                                ),
                            ],
                          ),
                        ),
                      ),
                    );
                  },
                ),
                const SizedBox(height: 12),
                Text(
                  '缩放 ${working.zoom.toStringAsFixed(2)}×',
                  style: Theme.of(context).textTheme.labelLarge?.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: () => _setWorking(
                          const ImageTransformModel(focusX: 0.5, focusY: 0.5, zoom: 1.0),
                        ),
                        icon: const Icon(Icons.restore_rounded),
                        label: const Text('重置'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: FilledButton.icon(
                        onPressed: () => Navigator.of(context).pop(updatedImage),
                        icon: const Icon(Icons.check_rounded),
                        label: const Text('保存'),
                      ),
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

class _PreviewText extends StatelessWidget {
  const _PreviewText({required this.event, required this.detail});
  final DayEventModel event;
  final bool detail;

  @override
  Widget build(BuildContext context) {
    final delta = event.signedDays;
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 22),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Flexible(
                  child: Text(
                    event.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    textAlign: TextAlign.center,
                    style: (detail
                            ? Theme.of(context).textTheme.headlineMedium
                            : Theme.of(context).textTheme.titleLarge)
                        ?.copyWith(color: Colors.white),
                  ),
                ),
                if (delta != 0) ...[
                  const SizedBox(width: 6),
                  Text(
                    delta > 0 ? '还有' : '已经',
                    style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                          color: Colors.white.withAlpha(214),
                        ),
                  ),
                ],
              ],
            ),
            const SizedBox(height: 18),
            Text(
              delta == 0 ? '今天' : delta.abs().toString(),
              style: Theme.of(context).textTheme.displayLarge?.copyWith(
                    color: Colors.white,
                  ),
            ),
            if (delta != 0)
              Text(
                '天',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: Colors.white.withAlpha(214),
                    ),
              ),
            const SizedBox(height: 10),
            Text(
              longDateText(event.effectiveDate),
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Colors.white.withAlpha(214),
                  ),
            ),
          ],
        ),
      ),
    );
  }
}
