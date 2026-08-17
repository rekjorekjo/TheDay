import 'dart:io';
import 'dart:math' as math;

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

enum _BlockedEdge { left, right, top, bottom }

class _ImageTransformScreenState extends State<ImageTransformScreen> {
  late EventImageModel image;
  late final ValueNotifier<ImageTransformModel> _working;
  late final bool _hasReadableImage;
  late double _gestureStartZoom;

  final Set<int> _previewPointers = <int>{};
  bool _imageGestureActive = false;
  bool _blockedFeedbackSent = false;
  int _blockedFeedbackToken = 0;
  _BlockedEdge? _blockedEdge;

  @override
  void initState() {
    super.initState();
    image = widget.event.backgroundImage!;
    final initial = widget.detail ? image.detailTransform : image.homeTransform;
    _working = ValueNotifier<ImageTransformModel>(_normalized(initial));
    _gestureStartZoom = _working.value.zoom;
    _hasReadableImage = image.filePath?.isNotEmpty == true &&
        File(image.filePath!).existsSync();
  }

  @override
  void dispose() {
    _working.dispose();
    super.dispose();
  }

  ImageTransformModel _normalized(ImageTransformModel value) {
    return ImageTransformModel(
      focusX: value.focusX.clamp(0.0, 1.0).toDouble(),
      focusY: value.focusY.clamp(0.0, 1.0).toDouble(),
      zoom: value.zoom.clamp(1.0, 4.0).toDouble(),
    );
  }

  void _setWorking(ImageTransformModel next) {
    _working.value = _normalized(next);
  }

  EventImageModel _updatedImage(ImageTransformModel transform) => widget.detail
      ? image.copyWith(detailTransform: transform)
      : image.copyWith(homeTransform: transform);

  void _setImageGestureActive(bool active) {
    if (_imageGestureActive == active || !mounted) return;
    setState(() => _imageGestureActive = active);
  }

  void _pointerDown(PointerDownEvent event) {
    _previewPointers.add(event.pointer);
    _setImageGestureActive(true);
  }

  void _pointerFinished(PointerEvent event) {
    _previewPointers.remove(event.pointer);
    if (_previewPointers.isEmpty) _setImageGestureActive(false);
  }

  Offset _panOverflow(Size viewport, double zoom) {
    final width = viewport.width.clamp(1.0, double.infinity).toDouble();
    final height = viewport.height.clamp(1.0, double.infinity).toDouble();
    final sourceAspect = image.width > 0 && image.height > 0
        ? image.width / image.height
        : 1.0;
    final viewportAspect = width / height;

    late final double baseWidth;
    late final double baseHeight;
    if (sourceAspect > viewportAspect) {
      baseHeight = height;
      baseWidth = height * sourceAspect;
    } else {
      baseWidth = width;
      baseHeight = width / sourceAspect;
    }

    return Offset(
      math.max(0.0, (baseWidth * zoom) - width),
      math.max(0.0, (baseHeight * zoom) - height),
    );
  }

  void _showBlockedFeedback(_BlockedEdge edge) {
    if (_blockedFeedbackSent || !mounted) return;
    _blockedFeedbackSent = true;
    setState(() {
      _blockedEdge = edge;
      _blockedFeedbackToken++;
    });
  }

  @override
  Widget build(BuildContext context) {
    final event = widget.event;
    final imageAspect = image.width > 0 && image.height > 0
        ? image.width / image.height
        : 1.0;
    final previewAspect = widget.detail
        ? detailImagePreviewAspectRatio(image)
        : imageAspect.clamp(1.10, 1.65).toDouble();
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

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
            physics: _imageGestureActive
                ? const NeverScrollableScrollPhysics()
                : const BouncingScrollPhysics(
                    parent: AlwaysScrollableScrollPhysics(),
                  ),
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
                        style: theme.textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                Align(
                  alignment: Alignment.centerLeft,
                  child: Row(
                    children: [
                      Icon(Icons.touch_app_rounded, color: scheme.primary),
                      const SizedBox(width: 8),
                      Text(
                        '单指拖动，双指缩放',
                        style: theme.textTheme.titleMedium,
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                LayoutBuilder(
                  builder: (context, constraints) {
                    final width = constraints.maxWidth;
                    final height = width / previewAspect;
                    final viewport = Size(width, height);

                    final preview = Listener(
                      onPointerDown: _pointerDown,
                      onPointerUp: _pointerFinished,
                      onPointerCancel: _pointerFinished,
                      child: GestureDetector(
                        behavior: HitTestBehavior.opaque,
                        onScaleStart: (_) {
                          _gestureStartZoom = _working.value.zoom;
                          _blockedFeedbackSent = false;
                        },
                        onScaleUpdate: (details) {
                          final current = _working.value;
                          final nextZoom = (_gestureStartZoom * details.scale)
                              .clamp(1.0, 4.0)
                              .toDouble();
                          final overflow = _panOverflow(viewport, nextZoom);
                          final pan = details.focalPointDelta;

                          final canPanX = overflow.dx > 0.75;
                          final canPanY = overflow.dy > 0.75;
                          final nextFocusX = (canPanX
                                  ? current.focusX - (pan.dx / overflow.dx)
                                  : 0.5)
                              .clamp(0.0, 1.0)
                              .toDouble();
                          final nextFocusY = (canPanY
                                  ? current.focusY - (pan.dy / overflow.dy)
                                  : 0.5)
                              .clamp(0.0, 1.0)
                              .toDouble();

                          final horizontalAttempt = pan.dx.abs() > 1.2 &&
                              pan.dx.abs() >= pan.dy.abs();
                          final verticalAttempt = pan.dy.abs() > 1.2 &&
                              pan.dy.abs() > pan.dx.abs();
                          final movedX = canPanX &&
                              (nextFocusX - current.focusX).abs() > 0.0001;
                          final movedY = canPanY &&
                              (nextFocusY - current.focusY).abs() > 0.0001;
                          _BlockedEdge? blockedEdge;
                          if (horizontalAttempt && !movedX) {
                            blockedEdge = pan.dx > 0
                                ? _BlockedEdge.left
                                : _BlockedEdge.right;
                          } else if (verticalAttempt && !movedY) {
                            blockedEdge = pan.dy > 0
                                ? _BlockedEdge.top
                                : _BlockedEdge.bottom;
                          }
                          final zoomChanging =
                              (nextZoom - current.zoom).abs() > 0.002;
                          if (blockedEdge != null && !zoomChanging) {
                            _showBlockedFeedback(blockedEdge);
                          }

                          _setWorking(
                            current.copyWith(
                              focusX: nextFocusX,
                              focusY: nextFocusY,
                              zoom: nextZoom,
                            ),
                          );
                        },
                        onScaleEnd: (_) {
                          _gestureStartZoom = _working.value.zoom;
                        },
                        child: !_hasReadableImage
                            ? Center(
                                child: Text(
                                  '图片暂时无法读取',
                                  style: theme.textTheme.bodyLarge?.copyWith(
                                    color: scheme.onSurfaceVariant,
                                  ),
                                ),
                              )
                            : Stack(
                                fit: StackFit.expand,
                                children: [
                                  ValueListenableBuilder<ImageTransformModel>(
                                    valueListenable: _working,
                                    builder: (context, transform, _) => EventImage(
                                      image: _updatedImage(transform),
                                      detail: widget.detail,
                                      filterQuality: _imageGestureActive
                                          ? FilterQuality.low
                                          : FilterQuality.medium,
                                    ),
                                  ),
                                  const RepaintBoundary(
                                    child: DecoratedBox(
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
                                  ),
                                  RepaintBoundary(
                                    child: _PreviewText(
                                      event: event,
                                      detail: widget.detail,
                                    ),
                                  ),
                                ],
                              ),
                      ),
                    );

                    return SizedBox(
                      width: width,
                      height: height,
                      child: TweenAnimationBuilder<double>(
                        key: ValueKey(_blockedFeedbackToken),
                        tween: Tween<double>(begin: 0, end: 1),
                        duration: _blockedFeedbackToken == 0
                            ? Duration.zero
                            : const Duration(milliseconds: 760),
                        curve: Curves.easeOut,
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(24),
                          child: preview,
                        ),
                        builder: (context, value, child) {
                          final pulse = math.sin(value * math.pi).abs();
                          return Stack(
                            fit: StackFit.expand,
                            clipBehavior: Clip.none,
                            children: [
                              child!,
                              IgnorePointer(
                                child: CustomPaint(
                                  painter: _BlockedEdgePainter(
                                    edge: _blockedEdge,
                                    color: scheme.primary,
                                    pulse: pulse,
                                  ),
                                ),
                              ),
                            ],
                          );
                        },
                      ),
                    );
                  },
                ),
                const SizedBox(height: 12),
                ValueListenableBuilder<ImageTransformModel>(
                  valueListenable: _working,
                  builder: (context, transform, _) => Text(
                    '缩放 ${transform.zoom.toStringAsFixed(2)}×',
                    style: theme.textTheme.labelLarge?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: () => _setWorking(
                          const ImageTransformModel(
                            focusX: 0.5,
                            focusY: 0.5,
                            zoom: 1.0,
                          ),
                        ),
                        icon: const Icon(Icons.restore_rounded),
                        label: const Text('重置'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: FilledButton.icon(
                        onPressed: () => Navigator.of(context).pop(
                          _updatedImage(_working.value),
                        ),
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


class _BlockedEdgePainter extends CustomPainter {
  const _BlockedEdgePainter({
    required this.edge,
    required this.color,
    required this.pulse,
  });

  final _BlockedEdge? edge;
  final Color color;
  final double pulse;

  @override
  void paint(Canvas canvas, Size size) {
    if (edge == null || pulse <= 0.001 || size.isEmpty) return;

    final inset = 12.0;
    const position = 1.35;
    late final Offset start;
    late final Offset end;
    switch (edge!) {
      case _BlockedEdge.left:
        start = Offset(position, inset);
        end = Offset(position, size.height - inset);
        break;
      case _BlockedEdge.right:
        start = Offset(size.width - position, inset);
        end = Offset(size.width - position, size.height - inset);
        break;
      case _BlockedEdge.top:
        start = Offset(inset, position);
        end = Offset(size.width - inset, position);
        break;
      case _BlockedEdge.bottom:
        start = Offset(inset, size.height - position);
        end = Offset(size.width - inset, size.height - position);
        break;
    }

    // A wider translucent stroke creates a light Glass glow without a broad blur.
    canvas.drawLine(
      start,
      end,
      Paint()
        ..color = color.withValues(alpha: 0.24 * pulse)
        ..strokeWidth = 8.0
        ..strokeCap = StrokeCap.round,
    );
    canvas.drawLine(
      start,
      end,
      Paint()
        ..color = color.withValues(alpha: 0.98 * pulse)
        ..strokeWidth = 2.7
        ..strokeCap = StrokeCap.round,
    );
  }

  @override
  bool shouldRepaint(covariant _BlockedEdgePainter oldDelegate) =>
      oldDelegate.edge != edge ||
      oldDelegate.color != color ||
      oldDelegate.pulse != pulse;
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
