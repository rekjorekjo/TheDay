double _glassBackgroundPhase = 0.18;

double get currentGlassBackgroundPhase => _glassBackgroundPhase;

void updateGlassBackgroundPhase(double value) {
  if (!value.isFinite) return;
  _glassBackgroundPhase = value % 1.0;
  if (_glassBackgroundPhase < 0) _glassBackgroundPhase += 1.0;
}
