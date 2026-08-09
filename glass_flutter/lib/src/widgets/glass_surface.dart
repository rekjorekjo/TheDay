import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../glass_motion.dart';
import '../glass_theme.dart';

class GlassBackdrop extends StatefulWidget {
  const GlassBackdrop({
    super.key,
    required this.isDark,
    required this.paletteStyle,
    required this.backgroundMode,
    required this.textureStyle,
    required this.child,
  });

  final bool isDark;
  final String paletteStyle;
  final String backgroundMode;
  final String textureStyle;
  final Widget child;

  @override
  State<GlassBackdrop> createState() => _GlassBackdropState();
}

class _GlassBackdropState extends State<GlassBackdrop>
    with SingleTickerProviderStateMixin {
  late final AnimationController _motion;

  bool get _motionEnabled => widget.backgroundMode != 'STATIC';

  Duration get _motionDuration => const Duration(seconds: 36);

  @override
  void initState() {
    super.initState();
    _motion = AnimationController(
      vsync: this,
      duration: _motionDuration,
    );
    _motion.addListener(_publishPhase);
    _syncMotion();
    _publishPhase();
  }

  @override
  void didUpdateWidget(covariant GlassBackdrop oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.backgroundMode != widget.backgroundMode) {
      _motion.stop();
      _motion.duration = _motionDuration;
      _syncMotion();
    }
  }

  void _publishPhase() => updateGlassBackgroundPhase(_motion.value);

  void _syncMotion() {
    if (_motionEnabled) {
      if (!_motion.isAnimating) _motion.repeat();
    } else {
      _motion.stop();
      _motion.value = 0.18;
    }
  }

  @override
  void dispose() {
    _motion.removeListener(_publishPhase);
    _motion.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final ambience = ambienceFor(widget.paletteStyle);
    final snowScene = widget.textureStyle == 'WAVE';
    final baseTop = snowScene
        ? (widget.isDark ? const Color(0xFF081321) : const Color(0xFFD7EBF5))
        : (widget.isDark ? const Color(0xFF121A2D) : const Color(0xFFEAF2F8));
    final baseBottom = snowScene
        ? (widget.isDark ? const Color(0xFF040A12) : const Color(0xFFC9D7D9))
        : (widget.isDark ? const Color(0xFF0B111E) : const Color(0xFFDCE7EE));

    return Stack(
      fit: StackFit.expand,
      children: [
        RepaintBoundary(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [baseTop, baseBottom],
              ),
            ),
          ),
        ),
        Positioned.fill(
          child: IgnorePointer(
            child: _OrbitingLightField(
              animation: _motion,
              ambience: ambience,
              isDark: widget.isDark,
              interior: widget.backgroundMode == 'AURORA',
            ),
          ),
        ),
        if (snowScene)
          Positioned.fill(
            child: IgnorePointer(
              child: _SnowSceneLayer(
                isDark: widget.isDark,
                motionEnabled: _motionEnabled,
              ),
            ),
          ),
        Positioned.fill(
          child: IgnorePointer(
            child: _TextureLayer(
              style: snowScene ? 'NONE' : widget.textureStyle,
              isDark: widget.isDark,
              motionEnabled: _motionEnabled,
            ),
          ),
        ),
        widget.child,
      ],
    );
  }
}

class _OrbitingLightField extends StatelessWidget {
  const _OrbitingLightField({
    required this.animation,
    required this.ambience,
    required this.isDark,
    required this.interior,
  });

  final Animation<double> animation;
  final PaletteAmbience ambience;
  final bool isDark;
  final bool interior;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final size = constraints.biggest;
        if (size.isEmpty) return const SizedBox.shrink();

        final orbSize = math.max(
          430.0,
          math.min(600.0, size.shortestSide * 1.28),
        );
        final radiusX = interior
            ? math.max(56.0, size.width * 0.30)
            : (size.width / 2) + (orbSize * 0.22);
        final radiusY = interior
            ? math.max(120.0, size.height * 0.30)
            : (size.height / 2) + (orbSize * 0.12);

        final primaryOrb = RepaintBoundary(
          child: _AmbientOrb(
            color: ambience.primary,
            opacity: isDark ? 0.50 : 0.33,
          ),
        );
        final secondaryOrb = RepaintBoundary(
          child: _AmbientOrb(
            color: ambience.secondary,
            opacity: isDark ? 0.43 : 0.29,
          ),
        );
        final tertiaryOrb = RepaintBoundary(
          child: _AmbientOrb(
            color: ambience.tertiary,
            opacity: isDark ? 0.46 : 0.27,
          ),
        );

        return AnimatedBuilder(
          animation: animation,
          builder: (context, _) {
            final baseAngle = animation.value * math.pi * 2;
            return Stack(
              clipBehavior: Clip.none,
              children: [
                _OrbitingAmbientOrb(
                  viewport: size,
                  orbSize: orbSize,
                  radiusX: radiusX,
                  radiusY: radiusY,
                  angle: baseAngle + 0.10,
                  breathing: 0.028,
                  organicShape: interior,
                  shapeX: 1.30,
                  shapeY: 0.82,
                  shapePhase: 0.35,
                  rotationBase: -0.30,
                  child: primaryOrb,
                ),
                _OrbitingAmbientOrb(
                  viewport: size,
                  orbSize: orbSize * 0.98,
                  radiusX: radiusX * 1.02,
                  radiusY: radiusY * 0.99,
                  angle: baseAngle + ((math.pi * 2) / 3) + 0.18,
                  breathing: 0.024,
                  organicShape: interior,
                  shapeX: 0.84,
                  shapeY: 1.26,
                  shapePhase: 2.10,
                  rotationBase: 0.42,
                  child: secondaryOrb,
                ),
                _OrbitingAmbientOrb(
                  viewport: size,
                  orbSize: orbSize * 1.07,
                  radiusX: radiusX * 0.98,
                  radiusY: radiusY * 1.02,
                  angle: baseAngle + ((math.pi * 4) / 3) - 0.10,
                  breathing: 0.032,
                  organicShape: interior,
                  shapeX: 1.38,
                  shapeY: 0.74,
                  shapePhase: 4.30,
                  rotationBase: -0.62,
                  child: tertiaryOrb,
                ),
              ],
            );
          },
        );
      },
    );
  }
}

class _OrbitingAmbientOrb extends StatelessWidget {
  const _OrbitingAmbientOrb({
    required this.viewport,
    required this.orbSize,
    required this.radiusX,
    required this.radiusY,
    required this.angle,
    required this.breathing,
    required this.organicShape,
    required this.shapeX,
    required this.shapeY,
    required this.shapePhase,
    required this.rotationBase,
    required this.child,
  });

  final Size viewport;
  final double orbSize;
  final double radiusX;
  final double radiusY;
  final double angle;
  final double breathing;
  final bool organicShape;
  final double shapeX;
  final double shapeY;
  final double shapePhase;
  final double rotationBase;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final centre = Offset(
      (viewport.width / 2) + (math.cos(angle) * radiusX),
      (viewport.height / 2) + (math.sin(angle) * radiusY),
    );
    final scale = 1.0 + math.sin((angle * 2) + 0.6) * breathing;
    final shapePulseX = organicShape
        ? 1.0 + math.sin((angle * 2) + shapePhase) * 0.075
        : 1.0;
    final shapePulseY = organicShape
        ? 1.0 + math.cos((angle * 3) + shapePhase) * 0.065
        : 1.0;
    final rotation = organicShape
        ? rotationBase + math.sin(angle + shapePhase) * 0.18
        : 0.0;

    return Positioned(
      left: centre.dx - (orbSize / 2),
      top: centre.dy - (orbSize / 2),
      width: orbSize,
      height: orbSize,
      child: Transform.rotate(
        angle: rotation,
        child: Transform.scale(
          scaleX: scale * shapeX * shapePulseX,
          scaleY: scale * shapeY * shapePulseY,
          child: child,
        ),
      ),
    );
  }
}

class _AmbientOrb extends StatelessWidget {
  const _AmbientOrb({required this.color, required this.opacity});

  final Color color;
  final double opacity;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: RadialGradient(
          colors: [
            withOpacitySafe(color, opacity),
            withOpacitySafe(color, opacity * 0.42),
            Colors.transparent,
          ],
          stops: const [0.0, 0.46, 1.0],
        ),
      ),
    );
  }
}


class _SnowSceneLayer extends StatefulWidget {
  const _SnowSceneLayer({
    required this.isDark,
    required this.motionEnabled,
  });

  final bool isDark;
  final bool motionEnabled;

  @override
  State<_SnowSceneLayer> createState() => _SnowSceneLayerState();
}

class _SnowSceneLayerState extends State<_SnowSceneLayer>
    with SingleTickerProviderStateMixin {
  late final AnimationController _cycle;

  @override
  void initState() {
    super.initState();
    _cycle = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 9200),
      value: 0.22,
    );
    _sync();
  }

  @override
  void didUpdateWidget(covariant _SnowSceneLayer oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.motionEnabled != widget.motionEnabled) _sync();
  }

  void _sync() {
    if (widget.motionEnabled) {
      if (!_cycle.isAnimating) _cycle.repeat();
    } else {
      _cycle.stop();
      _cycle.value = 0.22;
    }
  }

  @override
  void dispose() {
    _cycle.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox.expand(
      child: RepaintBoundary(
        child: AnimatedBuilder(
          animation: _cycle,
          builder: (context, _) => CustomPaint(
            painter: _SnowScenePainter(
              isDark: widget.isDark,
              progress: _cycle.value,
            ),
            child: const SizedBox.expand(),
          ),
        ),
      ),
    );
  }
}

class _SnowScenePainter extends CustomPainter {
  const _SnowScenePainter({required this.isDark, required this.progress});

  final bool isDark;
  final double progress;

  Color _c(Color color, double opacity) => withOpacitySafe(color, opacity);

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final w = size.width;
    final h = size.height;

    canvas.drawRect(
      Rect.fromLTWH(0, h * 0.70, w, h * 0.30),
      Paint()
        ..shader = ui.Gradient.linear(
          Offset(0, h * 0.70),
          Offset(0, h),
          <Color>[
            Colors.transparent,
            _c(const Color(0xFFEAF3FF), isDark ? 0.06 : 0.04),
            _c(const Color(0xFFF6FAFF), isDark ? 0.12 : 0.08),
          ],
          const <double>[0.0, 0.55, 1.0],
        ),
    );

    final rng = math.Random(0x5F21);
    final fall = h * 0.46 * progress;
    for (var i = 0; i < 70; i++) {
      final baseX = rng.nextDouble() * w;
      final baseY = rng.nextDouble() * h;
      final radius = 0.9 + rng.nextDouble() * 2.4;
      final speed = 0.40 + rng.nextDouble() * 0.85;
      final drift = math.sin((progress * math.pi * 2) + (i * 0.41)) *
          (2.5 + rng.nextDouble() * 4.0);
      final y = ((baseY + (fall * speed)) % (h + 26)) - 13;
      final x = baseX + drift;
      final opacity = (isDark ? 0.11 : 0.08) + (radius * 0.018);
      canvas.drawCircle(
        Offset(x, y),
        radius,
        Paint()..color = _c(const Color(0xFFF7FBFF), opacity),
      );
    }

    final nearRng = math.Random(0x7319);
    for (var i = 0; i < 18; i++) {
      final baseX = nearRng.nextDouble() * w;
      final baseY = nearRng.nextDouble() * h;
      final radius = 2.0 + nearRng.nextDouble() * 3.6;
      final speed = 0.28 + nearRng.nextDouble() * 0.42;
      final sway = math.cos((progress * math.pi * 2) + (i * 0.53)) *
          (4.0 + nearRng.nextDouble() * 6.0);
      final y = ((baseY + (h * 0.34 * progress * speed)) % (h + 32)) - 16;
      final x = baseX + sway;
      canvas.drawCircle(
        Offset(x, y),
        radius,
        Paint()..color = _c(const Color(0xFFFFFFFF), isDark ? 0.10 : 0.06),
      );
    }
  }

  @override
  bool shouldRepaint(covariant _SnowScenePainter oldDelegate) =>
      oldDelegate.isDark != isDark || oldDelegate.progress != progress;
}

class _TextureLayer extends StatefulWidget {
  const _TextureLayer({
    required this.style,
    required this.isDark,
    required this.motionEnabled,
  });

  final String style;
  final bool isDark;
  final bool motionEnabled;

  @override
  State<_TextureLayer> createState() => _TextureLayerState();
}

class _TextureLayerState extends State<_TextureLayer>
    with SingleTickerProviderStateMixin {
  late final AnimationController _stellarCycle;
  late int _stellarSeed;

  bool get _isStellar =>
      widget.style == 'STARS' || widget.style == 'CONSTELLATION';

  bool get _hasAnimatedTexture =>
      widget.style == 'DIAGONAL' || _isStellar;

  Duration get _textureDuration =>
      widget.style == 'DIAGONAL'
          ? const Duration(milliseconds: 4200)
          : const Duration(milliseconds: 8200);

  @override
  void initState() {
    super.initState();
    _stellarSeed = DateTime.now().microsecondsSinceEpoch & 0x7fffffff;
    _stellarCycle = AnimationController(
      vsync: this,
      duration: _textureDuration,
    )..addStatusListener(_onStellarStatus);
    _syncStellarMotion();
  }

  @override
  void didUpdateWidget(covariant _TextureLayer oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.motionEnabled != widget.motionEnabled ||
        oldWidget.style != widget.style) {
      _syncStellarMotion();
    }
  }

  void _onStellarStatus(AnimationStatus status) {
    if (status != AnimationStatus.completed ||
        !widget.motionEnabled ||
        !_hasAnimatedTexture) {
      return;
    }
    setState(() {
      _stellarSeed = ((_stellarSeed * 1103515245) + 12345) & 0x7fffffff;
    });
    _stellarCycle.forward(from: 0);
  }

  void _syncStellarMotion() {
    _stellarCycle.duration = _textureDuration;
    if (widget.motionEnabled && _hasAnimatedTexture) {
      if (!_stellarCycle.isAnimating) {
        _stellarCycle.forward(from: 0);
      }
    } else {
      _stellarCycle.stop();
    }
  }

  @override
  void dispose() {
    _stellarCycle.removeStatusListener(_onStellarStatus);
    _stellarCycle.dispose();
    super.dispose();
  }

  double _stellarOpacity(double progress) {
    if (progress < 0.15) return 0;
    if (progress < 0.32) {
      return Curves.easeOutCubic.transform((progress - 0.15) / 0.17);
    }
    if (progress < 0.63) return 1;
    if (progress < 0.82) {
      return 1 - Curves.easeInCubic.transform((progress - 0.63) / 0.19);
    }
    return 0;
  }

  @override
  Widget build(BuildContext context) {
    if (widget.style == 'NONE') return const SizedBox.shrink();

    final baseTexture = RepaintBoundary(
      child: CustomPaint(
        painter: _TexturePainter(isDark: widget.isDark, style: widget.style),
        size: Size.infinite,
      ),
    );

    if (!widget.motionEnabled || !_hasAnimatedTexture) return baseTexture;

    return Stack(
      fit: StackFit.expand,
      children: [
        baseTexture,
        RepaintBoundary(
          child: AnimatedBuilder(
            animation: _stellarCycle,
            builder: (context, _) {
              if (widget.style == 'DIAGONAL') {
                return CustomPaint(
                  painter: _TransientRainPainter(
                    isDark: widget.isDark,
                    progress: _stellarCycle.value,
                  ),
                  size: Size.infinite,
                );
              }

              final opacity = _stellarOpacity(_stellarCycle.value);
              if (opacity <= 0.001) return const SizedBox.shrink();

              if (widget.style == 'STARS') {
                return CustomPaint(
                  painter: _TransientMeteorPainter(
                    isDark: widget.isDark,
                    seed: _stellarSeed,
                    opacity: opacity,
                  ),
                  size: Size.infinite,
                );
              }

              return CustomPaint(
                painter: _TransientStellarPainter(
                  isDark: widget.isDark,
                  constellation: widget.style == 'CONSTELLATION',
                  seed: _stellarSeed,
                  opacity: opacity,
                ),
                size: Size.infinite,
              );
            },
          ),
        ),
      ],
    );
  }
}


class _TexturePainter extends CustomPainter {
  const _TexturePainter({required this.isDark, required this.style});

  final bool isDark;
  final String style;

  Color _tone(double opacity) => withOpacitySafe(
        isDark ? Colors.white : Colors.black,
        opacity,
      );

  Color _color(Color color, double opacity) => withOpacitySafe(color, opacity);

  @override
  void paint(Canvas canvas, Size size) {
    if (style == 'NONE' || size.isEmpty) return;

    switch (style) {
      case 'WAVE':
        break;
      case 'STARS':
        _paintMeteors(canvas, size);
        break;
      case 'CONSTELLATION':
        _paintStars(canvas, size, 0.0, constellation: true);
        break;
      case 'HEART':
        _paintHearts(canvas, size);
        break;
      case 'DIAGONAL':
      default:
        _paintRainBase(canvas, size);
        break;
    }
  }

  Paint _textureStroke(Size size, double top, double opacity, double width) {
    return Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = width
      ..strokeCap = StrokeCap.round
      ..shader = ui.Gradient.linear(
        Offset(0, top),
        Offset(0, size.height),
        <Color>[_tone(opacity * 0.94), _tone(opacity)],
        const <double>[0.0, 1.0],
      );
  }

  void _paintRainBase(Canvas canvas, Size size) {
    final rng = math.Random(0x4A21);
    final linePaint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round
      ..strokeWidth = 0.9
      ..color = _tone(isDark ? 0.08 : 0.05);
    for (var i = 0; i < 52; i++) {
      final start = Offset(
        rng.nextDouble() * (size.width + 40) - 20,
        rng.nextDouble() * (size.height + 30) - 15,
      );
      final length = size.shortestSide * (0.028 + rng.nextDouble() * 0.030);
      final dx = length * (0.14 + rng.nextDouble() * 0.08);
      final dy = length * (0.82 + rng.nextDouble() * 0.12);
      canvas.drawLine(start, Offset(start.dx - dx, start.dy + dy), linePaint);
    }

    final mistCenter = Offset(size.width * 0.22, size.height * 0.18);
    canvas.drawOval(
      Rect.fromCenter(
        center: mistCenter,
        width: size.width * 0.36,
        height: size.height * 0.16,
      ),
      Paint()
        ..shader = ui.Gradient.radial(
          mistCenter,
          size.width * 0.20,
          <Color>[
            _color(const Color(0xFFBFD9F4), isDark ? 0.045 : 0.030),
            Colors.transparent,
          ],
          const <double>[0.0, 1.0],
        ),
    );
  }

  void _paintMeteors(Canvas canvas, Size size) {
    final rng = math.Random(0x5A31);
    final starCount = 24;
    for (var i = 0; i < starCount; i++) {
      final x = rng.nextDouble() * size.width;
      final y = rng.nextDouble() * size.height * 0.84;
      final radius = 0.6 + rng.nextDouble() * 1.1;
      canvas.drawCircle(
        Offset(x, y),
        radius,
        Paint()..color = _tone(isDark ? 0.12 : 0.08),
      );
    }
    for (var i = 0; i < 5; i++) {
      final start = Offset(
        size.width * (0.05 + rng.nextDouble() * 0.80),
        size.height * (0.08 + rng.nextDouble() * 0.42),
      );
      final length = size.shortestSide * (0.08 + rng.nextDouble() * 0.08);
      final angle = 0.65 + (rng.nextDouble() * 0.12);
      final end = Offset(
        start.dx + math.cos(angle) * length,
        start.dy + math.sin(angle) * length,
      );
      final paint = Paint()
        ..color = _tone(isDark ? 0.10 : 0.065)
        ..strokeWidth = 0.95
        ..strokeCap = StrokeCap.round;
      canvas.drawLine(start, end, paint);
      canvas.drawCircle(end, 1.15, Paint()..color = _tone(isDark ? 0.16 : 0.10));
    }
  }

  void _paintStars(
    Canvas canvas,
    Size size,
    double fadeTop, {
    required bool constellation,
  }) {
    final rng = math.Random(constellation ? 0x51A7 : 0x57A2);
    final count = constellation ? 30 : 52;
    final points = <Offset>[];
    final strengths = <double>[];
    final span = size.height - fadeTop;

    for (var i = 0; i < count; i++) {
      final yRatio = math.pow(rng.nextDouble(), 0.72).toDouble();
      final point = Offset(
        rng.nextDouble() * size.width,
        fadeTop + (yRatio * span),
      );
      points.add(point);
      strengths.add(0.80 + ((i % 5) * 0.05));
    }

    if (constellation) {
      final linePaint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 0.55
        ..color = _tone(isDark ? 0.076 : 0.052);
      for (var i = 0; i + 1 < points.length; i += 3) {
        final a = points[i];
        final b = points[i + 1];
        if ((a - b).distance < 150) {
          canvas.drawLine(a, b, linePaint);
        }
        if (i + 2 < points.length) {
          final c = points[i + 2];
          if ((b - c).distance < 150) {
            canvas.drawLine(b, c, linePaint);
          }
        }
      }
    }

    for (var i = 0; i < points.length; i++) {
      final strength = strengths[i] * strengths[i];
      final radius = constellation
          ? (i % 7 == 0 ? 1.65 : 0.85 + (i % 3) * 0.18)
          : (i % 11 == 0 ? 1.45 : 0.65 + (i % 4) * 0.13);
      final paint = Paint()
        ..color = _tone((isDark ? 0.175 : 0.120) * strength);
      canvas.drawCircle(points[i], radius, paint);
      if (constellation && i % 9 == 0) {
        final cross = Paint()
          ..color = _tone((isDark ? 0.125 : 0.085) * strength)
          ..strokeWidth = 0.55;
        canvas.drawLine(
          points[i] - const Offset(3.5, 0),
          points[i] + const Offset(3.5, 0),
          cross,
        );
        canvas.drawLine(
          points[i] - const Offset(0, 3.5),
          points[i] + const Offset(0, 3.5),
          cross,
        );
      }
    }
  }

  void _paintHearts(Canvas canvas, Size size) {
    final rng = math.Random(0x7E41);
    for (var i = 0; i < 15; i++) {
      final center = Offset(
        size.width * (0.08 + rng.nextDouble() * 0.84),
        size.height * (0.10 + rng.nextDouble() * 0.80),
      );
      final heartSize = 6.0 + rng.nextDouble() * 7.0;
      final path = _heartPath(center, heartSize);
      final paint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 0.9
        ..color = _tone(isDark ? 0.11 : 0.07);
      canvas.drawPath(path, paint);
    }
  }

  Path _heartPath(Offset center, double size) {
    final x = center.dx;
    final y = center.dy;
    return Path()
      ..moveTo(x, y + size * 0.34)
      ..cubicTo(x - size * 0.80, y - size * 0.18, x - size * 0.82, y - size * 0.86, x, y - size * 0.28)
      ..cubicTo(x + size * 0.82, y - size * 0.86, x + size * 0.80, y - size * 0.18, x, y + size * 0.34)
      ..close();
  }

  @override
  bool shouldRepaint(covariant _TexturePainter oldDelegate) =>
      oldDelegate.isDark != isDark || oldDelegate.style != style;
}

class _TransientRainPainter extends CustomPainter {
  const _TransientRainPainter({required this.isDark, required this.progress});

  final bool isDark;
  final double progress;

  Color _tone(double opacity) =>
      withOpacitySafe(isDark ? Colors.white : Colors.black, opacity);

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final rng = math.Random(0x6D12);
    final travel = size.height * progress;
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;
    for (var i = 0; i < 28; i++) {
      final baseX = rng.nextDouble() * (size.width + 60) - 30;
      final baseY = rng.nextDouble() * size.height;
      final speed = 0.70 + rng.nextDouble() * 0.90;
      final y = ((baseY + (travel * speed)) % (size.height + 48)) - 24;
      final length = size.shortestSide * (0.042 + rng.nextDouble() * 0.032);
      final dx = length * (0.16 + rng.nextDouble() * 0.07);
      final dy = length * (0.86 + rng.nextDouble() * 0.08);
      paint
        ..strokeWidth = 1.0 + rng.nextDouble() * 0.55
        ..color = _tone(isDark ? 0.16 : 0.10);
      canvas.drawLine(
        Offset(baseX, y),
        Offset(baseX - dx, y + dy),
        paint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _TransientRainPainter oldDelegate) =>
      oldDelegate.isDark != isDark || oldDelegate.progress != progress;
}

class _TransientMeteorPainter extends CustomPainter {
  const _TransientMeteorPainter({required this.isDark, required this.seed, required this.opacity});

  final bool isDark;
  final int seed;
  final double opacity;

  Color _tone(double value) => withOpacitySafe(isDark ? Colors.white : Colors.black, value * opacity);

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty || opacity <= 0) return;
    final rng = math.Random(seed);
    final count = 4;
    for (var i = 0; i < count; i++) {
      final start = Offset(
        size.width * (0.06 + rng.nextDouble() * 0.78),
        size.height * (0.05 + rng.nextDouble() * 0.42),
      );
      final length = size.shortestSide * (0.13 + rng.nextDouble() * 0.10);
      final angle = 0.72 + (rng.nextDouble() * 0.16);
      final end = Offset(
        start.dx + math.cos(angle) * length,
        start.dy + math.sin(angle) * length,
      );
      final tail = Paint()
        ..color = _tone(isDark ? 0.18 : 0.12)
        ..strokeWidth = 1.2 + rng.nextDouble() * 0.4
        ..strokeCap = StrokeCap.round;
      canvas.drawLine(start, end, tail);
      canvas.drawCircle(end, 1.5 + rng.nextDouble() * 0.6, Paint()..color = _tone(isDark ? 0.28 : 0.17));
    }
  }

  @override
  bool shouldRepaint(covariant _TransientMeteorPainter oldDelegate) =>
      oldDelegate.isDark != isDark || oldDelegate.seed != seed || oldDelegate.opacity != opacity;
}

class _TransientStellarPainter extends CustomPainter {
  const _TransientStellarPainter({
    required this.isDark,
    required this.constellation,
    required this.seed,
    required this.opacity,
  });

  final bool isDark;
  final bool constellation;
  final int seed;
  final double opacity;

  Color _tone(double value) => withOpacitySafe(
        isDark ? Colors.white : Colors.black,
        value * opacity,
      );

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty || opacity <= 0) return;
    final rng = math.Random(seed);
    final count = constellation ? 9 : 13;
    final points = <Offset>[];
    for (var i = 0; i < count; i++) {
      points.add(Offset(
        (0.04 + (rng.nextDouble() * 0.92)) * size.width,
        (0.035 + (rng.nextDouble() * 0.93)) * size.height,
      ));
    }

    if (constellation) {
      final linePaint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 0.62
        ..color = _tone(isDark ? 0.13 : 0.09);
      for (var i = 0; i + 1 < points.length; i += 3) {
        final a = points[i];
        final b = points[i + 1];
        if ((a - b).distance < size.shortestSide * 0.34) {
          canvas.drawLine(a, b, linePaint);
        }
        if (i + 2 < points.length) {
          final c = points[i + 2];
          if ((b - c).distance < size.shortestSide * 0.34) {
            canvas.drawLine(b, c, linePaint);
          }
        }
      }
    }

    for (var i = 0; i < points.length; i++) {
      final emphasis = rng.nextDouble();
      final radius = constellation
          ? (0.95 + emphasis * 1.15)
          : (0.80 + emphasis * 1.25);
      canvas.drawCircle(
        points[i],
        radius,
        Paint()..color = _tone(isDark ? 0.30 : 0.20),
      );
      if (emphasis > 0.78) {
        final flare = Paint()
          ..color = _tone(isDark ? 0.22 : 0.15)
          ..strokeWidth = 0.62
          ..strokeCap = StrokeCap.round;
        final reach = 2.8 + (emphasis * 1.8);
        canvas.drawLine(
          points[i] - Offset(reach, 0),
          points[i] + Offset(reach, 0),
          flare,
        );
        canvas.drawLine(
          points[i] - Offset(0, reach),
          points[i] + Offset(0, reach),
          flare,
        );
      }
    }
  }

  @override
  bool shouldRepaint(covariant _TransientStellarPainter oldDelegate) =>
      oldDelegate.isDark != isDark ||
      oldDelegate.constellation != constellation ||
      oldDelegate.seed != seed ||
      oldDelegate.opacity != opacity;
}

class GlassSurface extends StatelessWidget {
  const GlassSurface({
    super.key,
    required this.child,
    required this.isDark,
    required this.clarity,
    this.blur = true,
    this.radius = 28,
    this.padding = EdgeInsets.zero,
    this.borderOpacityScale = 1.0,
  });

  final Widget child;
  final bool isDark;
  final int clarity;
  final bool blur;
  final double radius;
  final EdgeInsetsGeometry padding;
  final double borderOpacityScale;

  double _mix(double start, double end, double t) => start + ((end - start) * t);

  @override
  Widget build(BuildContext context) {
    final t = clarity.clamp(0, 100) / 100.0;
    final sigma = _mix(30, 0, t);
    final fade = 1.0 - t;
    final fillAlpha = (isDark ? 0.285 : 0.46) * fade * fade;
    final depthAlpha = _mix(0.20, 0.095, t);
    final tightShadowAlpha = _mix(0.16, 0.075, t);
    final borderRadius = BorderRadius.circular(radius);
    final accent = Theme.of(context).colorScheme.primary;

    Widget interior = Stack(
      fit: StackFit.passthrough,
      children: [
        if (blur && clarity < 100 && sigma > 0.1)
          Positioned.fill(
            child: BackdropFilter(
              filter: ui.ImageFilter.blur(sigmaX: sigma, sigmaY: sigma),
              child: const ColoredBox(color: Colors.transparent),
            ),
          ),
        Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: withOpacitySafe(Colors.white, fillAlpha),
              borderRadius: borderRadius,
            ),
          ),
        ),
        // Keep colour reflection at the rim. A directional gradient across the
        // full card made the entire control look illuminated rather than glassy.
        Padding(padding: padding, child: child),
        Positioned.fill(
          child: IgnorePointer(
            child: CustomPaint(
              painter: _GlassDepthPainter(
                radius: radius,
                isDark: isDark,
                accent: accent,
                borderOpacityScale: borderOpacityScale,
                clarity: clarity,
              ),
            ),
          ),
        ),
      ],
    );

    interior = ClipRRect(
      borderRadius: borderRadius,
      clipBehavior: Clip.antiAlias,
      child: interior,
    );

    // Keep the depth shadow outside the clip. In the old structure the entire
    // surface, including its shadow, was clipped by ClipRRect, which flattened
    // cards against the moving ambience behind them.
    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: borderRadius,
        boxShadow: [
          BoxShadow(
            color: withOpacitySafe(Colors.black, depthAlpha),
            blurRadius: 30,
            spreadRadius: -13,
            offset: const Offset(0, 16),
          ),
          BoxShadow(
            color: withOpacitySafe(Colors.black, tightShadowAlpha),
            blurRadius: 10,
            spreadRadius: -7,
            offset: const Offset(0, 5),
          ),
        ],
      ),
      child: interior,
    );
  }
}

class _GlassDepthPainter extends CustomPainter {
  const _GlassDepthPainter({
    required this.radius,
    required this.isDark,
    required this.accent,
    required this.borderOpacityScale,
    required this.clarity,
  });

  final double radius;
  final bool isDark;
  final Color accent;
  final double borderOpacityScale;
  final int clarity;

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;

    final t = clarity.clamp(0, 100) / 100.0;
    final rect = Offset.zero & size;
    final edgeAlpha = (_lerp(0.35, 0.275, t) * borderOpacityScale)
        .clamp(0.0, 1.0)
        .toDouble();

    // Liquid Glass is kept optically quiet in the centre. The refractive cue is
    // built from a few sub-2.5 px rings only, so cards retain the r15 depth
    // without turning into large illuminated buttons.
    final outer = RRect.fromRectAndRadius(
      rect.deflate(0.52),
      Radius.circular(math.max(0.0, radius - 0.52)),
    );
    final outerPaint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.04
      ..shader = ui.Gradient.linear(
        rect.topLeft,
        rect.bottomRight,
        <Color>[
          withOpacitySafe(Colors.white, edgeAlpha),
          withOpacitySafe(Colors.white, edgeAlpha * 0.72),
          withOpacitySafe(accent, edgeAlpha * 0.46),
          withOpacitySafe(Colors.white, edgeAlpha * 0.13),
        ],
        const <double>[0.0, 0.24, 0.58, 1.0],
      );
    canvas.drawRRect(outer, outerPaint);

    // A second narrow optical ring gives the edge a lens-like "swollen" feel.
    // It is static and intentionally never reaches the card fill.
    final refractRect = rect.deflate(1.28);
    if (refractRect.width > 0 && refractRect.height > 0) {
      final refract = RRect.fromRectAndRadius(
        refractRect,
        Radius.circular(math.max(0.0, radius - 1.28)),
      );
      final refractPaint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 0.72
        ..shader = SweepGradient(
          center: Alignment.center,
          colors: <Color>[
            withOpacitySafe(Colors.white, 0.045 * borderOpacityScale),
            withOpacitySafe(Colors.white, 0.19 * borderOpacityScale),
            withOpacitySafe(accent, 0.095 * borderOpacityScale),
            Colors.transparent,
            withOpacitySafe(Colors.black, 0.07 * borderOpacityScale),
            Colors.transparent,
            withOpacitySafe(Colors.white, 0.12 * borderOpacityScale),
            withOpacitySafe(Colors.white, 0.045 * borderOpacityScale),
          ],
          stops: const <double>[0.0, 0.10, 0.19, 0.34, 0.53, 0.69, 0.88, 1.0],
        ).createShader(refractRect);
      canvas.drawRRect(refract, refractPaint);
    }

    // Inner transmission rim: a tiny bright upper/left edge and a darker
    // lower/right edge make the glass read as thickness rather than a flat line.
    final innerRect = rect.deflate(1.88);
    if (innerRect.width > 0 && innerRect.height > 0) {
      final inner = RRect.fromRectAndRadius(
        innerRect,
        Radius.circular(math.max(0.0, radius - 1.88)),
      );
      final innerPaint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 0.56
        ..shader = ui.Gradient.linear(
          innerRect.topLeft,
          innerRect.bottomRight,
          <Color>[
            withOpacitySafe(
              Colors.white,
              (isDark ? 0.145 : 0.20) * borderOpacityScale,
            ),
            Colors.transparent,
            withOpacitySafe(
              Colors.black,
              (isDark ? 0.115 : 0.065) * borderOpacityScale,
            ),
          ],
          const <double>[0.0, 0.48, 1.0],
        );
      canvas.drawRRect(inner, innerPaint);
    }
  }

  double _lerp(double start, double end, double t) => start + ((end - start) * t);

  @override
  bool shouldRepaint(covariant _GlassDepthPainter oldDelegate) {
    return oldDelegate.radius != radius ||
        oldDelegate.isDark != isDark ||
        oldDelegate.accent != accent ||
        oldDelegate.borderOpacityScale != borderOpacityScale ||
        oldDelegate.clarity != clarity;
  }
}

class GlassRoundButton extends StatelessWidget {
  const GlassRoundButton({
    super.key,
    required this.icon,
    required this.onPressed,
    required this.isDark,
    required this.clarity,
    this.tooltip,
  });

  final IconData icon;
  final VoidCallback onPressed;
  final bool isDark;
  final int clarity;
  final String? tooltip;

  @override
  Widget build(BuildContext context) {
    final button = SizedBox.square(
      dimension: 44,
      child: GlassSurface(
        isDark: isDark,
        clarity: clarity,
        radius: 22,
        blur: false,
        padding: EdgeInsets.zero,
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            customBorder: const CircleBorder(),
            onTap: onPressed,
            child: Center(
              child: Icon(icon, size: 22),
            ),
          ),
        ),
      ),
    );

    if (tooltip == null) return button;
    return Tooltip(message: tooltip!, child: button);
  }
}

class GlassSectionLabel extends StatelessWidget {
  const GlassSectionLabel(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(6, 8, 6, 8),
      child: Text(
        text,
        style: Theme.of(context).textTheme.labelMedium?.copyWith(
              letterSpacing: 0.3,
              fontWeight: FontWeight.w600,
            ),
      ),
    );
  }
}