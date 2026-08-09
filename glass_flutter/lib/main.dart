import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'src/app_controller.dart';
import 'src/glass_theme.dart';
import 'src/shell.dart';
import 'src/widgets/glass_surface.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  runApp(const TheDayGlassApp());
}

class TheDayGlassApp extends StatefulWidget {
  const TheDayGlassApp({super.key});

  @override
  State<TheDayGlassApp> createState() => _TheDayGlassAppState();
}

class _TheDayGlassAppState extends State<TheDayGlassApp> {
  late final AppController controller;

  @override
  void initState() {
    super.initState();
    controller = AppController();
    controller.initialize();
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        final snapshot = controller.snapshot;
        final isDark = snapshot?.isDark ?? true;
        final palette = snapshot?.settings.paletteStyle ?? 'MIDNIGHT';
        final ambience = ambienceFor(palette);
        final clarity = snapshot?.settings.glassClarity ?? 62;
        final backgroundMotionMode = snapshot?.settings.backgroundMotionMode ?? 'FLOW';
        final backgroundTexture = snapshot?.settings.backgroundTexture ?? 'DIAGONAL';
        return MaterialApp(
          debugShowCheckedModeBanner: false,
          localizationsDelegates: GlobalMaterialLocalizations.delegates,
          supportedLocales: const [Locale('zh', 'CN'), Locale('en')],
          theme: buildGlassTheme(isDark, ambience, clarity: clarity),
          builder: (context, child) {
            return AnnotatedRegion<SystemUiOverlayStyle>(
              value: overlayStyleFor(isDark),
              child: GlassBackdrop(
                isDark: isDark,
                paletteStyle: palette,
                backgroundMode: backgroundMotionMode,
                textureStyle: backgroundTexture,
                child: child ?? const SizedBox.shrink(),
              ),
            );
          },
          home: snapshot == null
              ? _LaunchSurface(
                  isDark: isDark,
                  loading: controller.loading,
                  onRetry: controller.initialize,
                )
              : GlassShell(controller: controller),
        );
      },
    );
  }
}

class _LaunchSurface extends StatelessWidget {
  const _LaunchSurface({
    required this.isDark,
    required this.loading,
    required this.onRetry,
  });

  final bool isDark;
  final bool loading;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Center(
        child: SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                'THE DAY',
                style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      letterSpacing: 3.0,
                      fontWeight: FontWeight.w700,
                    ),
              ),
              const SizedBox(height: 18),
              if (loading)
                const SizedBox.square(
                  dimension: 22,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              else
                IconButton(
                  onPressed: onRetry,
                  icon: const Icon(Icons.refresh_rounded),
                  tooltip: '重试',
                ),
            ],
          ),
        ),
      ),
    );
  }
}