import 'package:flutter/material.dart';

import 'app_controller.dart';

Route<T> glassRoute<T>({
  required AppController controller,
  required WidgetBuilder builder,
}) {
  return MaterialPageRoute<T>(
    allowSnapshotting: true,
    builder: (context) {
      return AnimatedBuilder(
        animation: controller,
        builder: (context, _) {
          if (controller.snapshot == null) {
            return const ColoredBox(color: Color(0xFF0B111E));
          }
          return RepaintBoundary(
            child: Builder(builder: builder),
          );
        },
      );
    },
  );
}
