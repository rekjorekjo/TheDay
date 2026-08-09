import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

Color withOpacitySafe(Color color, double opacity) {
  final alpha = (opacity.clamp(0.0, 1.0) * 255).round();
  return color.withAlpha(alpha);
}

class PaletteAmbience {
  const PaletteAmbience({
    required this.primary,
    required this.secondary,
    required this.tertiary,
    required this.accent,
  });

  final Color primary;
  final Color secondary;
  final Color tertiary;
  final Color accent;
}

const Map<String, PaletteAmbience> paletteAmbiences = <String, PaletteAmbience>{
  'MIDNIGHT': PaletteAmbience(primary: Color(0xFF3987E8), secondary: Color(0xFF7656D8), tertiary: Color(0xFF22BFA1), accent: Color(0xFF7EC6FF)),
  'CINNABAR': PaletteAmbience(primary: Color(0xFFF05F68), secondary: Color(0xFFE58C59), tertiary: Color(0xFF9B5ACB), accent: Color(0xFFFF9992)),
  'PINE': PaletteAmbience(primary: Color(0xFF2FB58F), secondary: Color(0xFF6DA56E), tertiary: Color(0xFF3F7EB9), accent: Color(0xFF69DCB7)),
  'ANTIQUE_GOLD': PaletteAmbience(primary: Color(0xFFD6A43E), secondary: Color(0xFFB86D4E), tertiary: Color(0xFF6B78A9), accent: Color(0xFFFFD37D)),
  'BLOOM_PETAL': PaletteAmbience(primary: Color(0xFFE76F9D), secondary: Color(0xFFA06FDC), tertiary: Color(0xFF668DD5), accent: Color(0xFFFFA4C4)),
  'BLOOM_MIST': PaletteAmbience(primary: Color(0xFF61A5D7), secondary: Color(0xFF6E84D5), tertiary: Color(0xFF4FC0B5), accent: Color(0xFF9FD7FF)),
  'BLOOM_VERDANT': PaletteAmbience(primary: Color(0xFF70B85C), secondary: Color(0xFF3CA17F), tertiary: Color(0xFF6B85BE), accent: Color(0xFFA4E48C)),
  'BLOOM_STONE': PaletteAmbience(primary: Color(0xFF9C8A80), secondary: Color(0xFF89768F), tertiary: Color(0xFF6C8D98), accent: Color(0xFFCDBEB2)),
  'BLOOM_WHEAT': PaletteAmbience(primary: Color(0xFFD9A63D), secondary: Color(0xFFBF7751), tertiary: Color(0xFF718D78), accent: Color(0xFFFFCB73)),
  'BLOOM_INK': PaletteAmbience(primary: Color(0xFF526F91), secondary: Color(0xFF766B95), tertiary: Color(0xFF3D929A), accent: Color(0xFF9AB8DC)),
  'BLOOM_AMBER': PaletteAmbience(primary: Color(0xFFE38A32), secondary: Color(0xFFAE6257), tertiary: Color(0xFF7E9250), accent: Color(0xFFFFB25B)),
  'BLOOM_LAPIS': PaletteAmbience(primary: Color(0xFF4D77D4), secondary: Color(0xFF6C62C5), tertiary: Color(0xFF389CAC), accent: Color(0xFF9BB8FF)),
  'BLOOM_RIPPLE': PaletteAmbience(primary: Color(0xFF34AAA5), secondary: Color(0xFF477FB2), tertiary: Color(0xFF7068C8), accent: Color(0xFF74E0DC)),
  'BLOOM_CINNABAR': PaletteAmbience(primary: Color(0xFFE84F5C), secondary: Color(0xFFB86576), tertiary: Color(0xFF8E8153), accent: Color(0xFFFF7E87)),
  'BLOOM_SAGE': PaletteAmbience(primary: Color(0xFF42B69A), secondary: Color(0xFF4A8EAF), tertiary: Color(0xFF7272B7), accent: Color(0xFF81E3C5)),
  'BLOOM_SPRING': PaletteAmbience(primary: Color(0xFF9C6DE0), secondary: Color(0xFFD45B9B), tertiary: Color(0xFF4D96C2), accent: Color(0xFFD3A4FF)),
};

PaletteAmbience ambienceFor(String palette) => paletteAmbiences[palette] ?? paletteAmbiences['MIDNIGHT']!;


class GlassPredictiveBackPageTransitionsBuilder
    extends PredictiveBackPageTransitionsBuilder {
  const GlassPredictiveBackPageTransitionsBuilder();

  @override
  Duration get transitionDuration => const Duration(milliseconds: 170);

  @override
  Duration get reverseTransitionDuration => const Duration(milliseconds: 150);

  @override
  Widget buildTransitions<T>(
    PageRoute<T> route,
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    final platformTransition = super.buildTransitions<T>(
      route,
      context,
      animation,
      secondaryAnimation,
      child,
    );

    // 150 ms is long enough to read as a deliberate transition instead of a
    // one-frame ghost, while still feeling materially faster than the forward
    // route. On pop the old page stays fully opaque for the first few frames,
    // then exits cleanly before the final settle so the prepared snapshot below
    // is never perceived as a second copy of the same page.
    final opacity = CurvedAnimation(
      parent: animation,
      curve: const Interval(0.0, 1.0, curve: Curves.easeOutCubic),
      reverseCurve: const Interval(0.08, 0.92, curve: Curves.easeInCubic),
    );
    final scale = Tween<double>(begin: 0.992, end: 1.0).animate(
      CurvedAnimation(
        parent: animation,
        curve: Curves.easeOutCubic,
        reverseCurve: Curves.easeInOutCubic,
      ),
    );

    return FadeTransition(
      opacity: opacity,
      child: ScaleTransition(
        scale: scale,
        alignment: Alignment.center,
        child: platformTransition,
      ),
    );
  }
}

ThemeData buildGlassTheme(bool isDark, PaletteAmbience ambience, {required int clarity}) {
  final brightness = isDark ? Brightness.dark : Brightness.light;
  final foreground = isDark ? const Color(0xFFF5F7FA) : const Color(0xFF17202B);
  final muted = isDark ? const Color(0xFFBCC6D2) : const Color(0xFF56616F);
  final glassFade = 1.0 - (clarity.clamp(0, 100) / 100.0);
  final scheme = ColorScheme.fromSeed(seedColor: ambience.accent, brightness: brightness).copyWith(
    primary: ambience.accent,
    surface: Colors.transparent,
  );

  return ThemeData(
    useMaterial3: true,
    brightness: brightness,
    scaffoldBackgroundColor: Colors.transparent,
    canvasColor: Colors.transparent,
    splashFactory: InkRipple.splashFactory,
    colorScheme: scheme,
    pageTransitionsTheme: const PageTransitionsTheme(
      builders: <TargetPlatform, PageTransitionsBuilder>{
        TargetPlatform.android: GlassPredictiveBackPageTransitionsBuilder(),
      },
    ),
    textTheme: TextTheme(
      displayLarge: TextStyle(color: foreground, fontSize: 58, height: 0.95, fontWeight: FontWeight.w600, letterSpacing: -2.2),
      headlineLarge: TextStyle(color: foreground, fontSize: 30, height: 1.12, fontWeight: FontWeight.w600, letterSpacing: -0.8),
      headlineMedium: TextStyle(color: foreground, fontSize: 24, height: 1.15, fontWeight: FontWeight.w600, letterSpacing: -0.4),
      titleLarge: TextStyle(color: foreground, fontSize: 20, height: 1.2, fontWeight: FontWeight.w600),
      titleMedium: TextStyle(color: foreground, fontSize: 16, height: 1.25, fontWeight: FontWeight.w600),
      bodyLarge: TextStyle(color: foreground, fontSize: 16, height: 1.45),
      bodyMedium: TextStyle(color: foreground, fontSize: 14, height: 1.42),
      bodySmall: TextStyle(color: muted, fontSize: 12, height: 1.35),
      labelLarge: TextStyle(color: foreground, fontSize: 14, fontWeight: FontWeight.w600),
      labelMedium: TextStyle(color: muted, fontSize: 12, fontWeight: FontWeight.w500),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: withOpacitySafe(
        isDark ? Colors.white : Colors.black,
        (isDark ? 0.07 : 0.045) * glassFade * glassFade,
      ),
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(18), borderSide: BorderSide.none),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(18),
        borderSide: BorderSide(color: withOpacitySafe(isDark ? Colors.white : Colors.black, 0.10)),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(18),
        borderSide: BorderSide(color: ambience.accent, width: 1.2),
      ),
      contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
    ),
    sliderTheme: SliderThemeData(
      trackHeight: 3,
      activeTrackColor: ambience.accent,
      inactiveTrackColor: withOpacitySafe(foreground, 0.15),
      thumbColor: foreground,
      overlayColor: withOpacitySafe(ambience.accent, 0.12),
      thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 8),
      overlayShape: const RoundSliderOverlayShape(overlayRadius: 18),
    ),
    switchTheme: SwitchThemeData(
      thumbColor: WidgetStateProperty.resolveWith((states) => states.contains(WidgetState.selected) ? foreground : muted),
      trackColor: WidgetStateProperty.resolveWith((states) => states.contains(WidgetState.selected)
          ? withOpacitySafe(ambience.accent, 0.70)
          : withOpacitySafe(foreground, 0.14)),
    ),
  );
}

SystemUiOverlayStyle overlayStyleFor(bool isDark) => SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      systemNavigationBarColor: Colors.transparent,
      systemNavigationBarDividerColor: Colors.transparent,
      statusBarIconBrightness: isDark ? Brightness.light : Brightness.dark,
      statusBarBrightness: isDark ? Brightness.dark : Brightness.light,
      systemNavigationBarIconBrightness: isDark ? Brightness.light : Brightness.dark,
    );
