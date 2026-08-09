import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';

class GlassEdgeEffects extends ChangeNotifier {
  bool _enabled = true;
  double _tiltX = 0;
  double _tiltY = 0;
  double _glint = -1;
  Timer? _glintTrigger;
  Timer? _glintFrames;

  bool get enabled => _enabled;
  double get tiltX => _enabled ? _tiltX : 0;
  double get tiltY => _enabled ? _tiltY : 0;
  double get glint => _enabled ? _glint : -1;

  void setEnabled(bool value) {
    if (_enabled == value) return;
    _enabled = value;
    if (value) {
      _scheduleGlint(initialDelay: const Duration(seconds: 4));
    } else {
      _glintTrigger?.cancel();
      _glintFrames?.cancel();
      _glint = -1;
      _tiltX = 0;
      _tiltY = 0;
    }
    notifyListeners();
  }

  void updateTilt(double x, double y) {
    if (!_enabled || !x.isFinite || !y.isFinite) return;
    final nextX = x.clamp(-1.0, 1.0).toDouble();
    final nextY = y.clamp(-1.0, 1.0).toDouble();
    // A small Flutter-side low-pass keeps tiny sensor noise out of the rim.
    final oldX = _tiltX;
    final oldY = _tiltY;
    _tiltX += (nextX - _tiltX) * 0.22;
    _tiltY += (nextY - _tiltY) * 0.22;
    if ((_tiltX - oldX).abs() < 0.0015 && (_tiltY - oldY).abs() < 0.0015) return;
    notifyListeners();
  }

  void start() {
    if (_enabled && _glintTrigger == null) {
      _scheduleGlint(initialDelay: const Duration(seconds: 4));
    }
  }

  void _scheduleGlint({required Duration initialDelay}) {
    _glintTrigger?.cancel();
    _glintTrigger = Timer(initialDelay, () {
      _runGlint();
      _glintTrigger = Timer.periodic(const Duration(seconds: 10), (_) => _runGlint());
    });
  }

  void _runGlint() {
    if (!_enabled || _glintFrames != null) return;
    const durationMs = 1050;
    const frameMs = 50;
    var elapsed = 0;
    _glint = 0;
    notifyListeners();
    _glintFrames = Timer.periodic(const Duration(milliseconds: frameMs), (timer) {
      elapsed += frameMs;
      final t = (elapsed / durationMs).clamp(0.0, 1.0).toDouble();
      // Smooth ends so the highlight arrives/leaves without a hard flash.
      _glint = 0.5 - (math.cos(t * math.pi) * 0.5);
      notifyListeners();
      if (elapsed >= durationMs) {
        timer.cancel();
        _glintFrames = null;
        _glint = -1;
        notifyListeners();
      }
    });
  }
}

final glassEdgeEffects = GlassEdgeEffects();
