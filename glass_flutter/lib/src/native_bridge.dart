import 'dart:convert';

import 'package:flutter/services.dart';

import 'models.dart';

class UpdateStatusModel {
  const UpdateStatusModel({
    required this.state,
    required this.versionName,
    required this.progressPercent,
    required this.wifiOnly,
    required this.extra,
  });

  final String state;
  final String? versionName;
  final int? progressPercent;
  final bool wifiOnly;
  final String? extra;

  factory UpdateStatusModel.decode(String raw) {
    final json = (jsonDecode(raw) as Map).cast<String, dynamic>();
    return UpdateStatusModel(
      state: json['state'] as String? ?? 'NONE',
      versionName: json['versionName'] as String?,
      progressPercent: (json['progressPercent'] as num?)?.round(),
      wifiOnly: json['wifiOnly'] as bool? ?? true,
      extra: json['extra'] as String?,
    );
  }
}

class NativeBridge {
  NativeBridge() {
    _channel.setMethodCallHandler(_handleNativeCall);
  }

  static const MethodChannel _channel = MethodChannel('io.github.thedayapp/glass');

  void Function(AppSnapshot snapshot)? onStateChanged;
  void Function(String eventId)? onOpenEvent;

  Future<dynamic> _handleNativeCall(MethodCall call) async {
    switch (call.method) {
      case 'stateChanged':
        final raw = call.arguments as String?;
        if (raw != null) onStateChanged?.call(AppSnapshot.decode(raw));
        return null;
      case 'openEvent':
        final eventId = call.arguments as String?;
        if (eventId != null && eventId.isNotEmpty) onOpenEvent?.call(eventId);
        return null;
      default:
        return null;
    }
  }

  Future<AppSnapshot> _snapshotCall(String method, [Object? arguments]) async {
    final raw = await _channel.invokeMethod<String>(method, arguments);
    if (raw == null) throw StateError('Native $method response was empty');
    return AppSnapshot.decode(raw);
  }

  Future<AppSnapshot> getSnapshot() => _snapshotCall('getSnapshot');

  Future<AppSnapshot> updateSettings(GlassSettings settings) =>
      _snapshotCall('updateSettings', jsonEncode(settings.toJson()));

  Future<AppSnapshot> saveEvent(Map<String, dynamic> event) =>
      _snapshotCall('saveEvent', jsonEncode(event));

  Future<AppSnapshot> saveNewEventDraft(NewEventDraftModel draft) =>
      _snapshotCall('saveNewEventDraft', jsonEncode(draft.toNativeJson()));

  Future<AppSnapshot> clearNewEventDraft() => _snapshotCall('clearNewEventDraft');

  Future<AppSnapshot> deleteEvent(String eventId) => _snapshotCall('deleteEvent', eventId);

  Future<AppSnapshot> togglePinned(String eventId) => _snapshotCall('togglePinned', eventId);

  Future<AppSnapshot> saveMilestone(Map<String, dynamic> milestone) =>
      _snapshotCall('saveMilestone', jsonEncode(milestone));

  Future<AppSnapshot> deleteMilestone(String id) => _snapshotCall('deleteMilestone', id);

  Future<AppSnapshot> moveMilestone(String id, int direction) => _snapshotCall(
        'moveMilestone',
        jsonEncode(<String, dynamic>{'id': id, 'direction': direction}),
      );

  Future<AppSnapshot> moveMilestoneToIndex(String id, int index) => _snapshotCall(
        'moveMilestoneToIndex',
        jsonEncode(<String, dynamic>{'id': id, 'index': index}),
      );

  Future<AppSnapshot> saveAlbum(Map<String, dynamic> album) =>
      _snapshotCall('saveAlbum', jsonEncode(album));

  Future<AppSnapshot> deleteAlbum(String id) => _snapshotCall('deleteAlbum', id);

  Future<AppSnapshot> updateCategoryCover(String category, EventImageModel? image) =>
      _snapshotCall(
        'updateCategoryCover',
        jsonEncode(<String, dynamic>{
          'category': category,
          'image': image?.toNativeJson(),
        }),
      );

  Future<AppSnapshot> deleteCategory(String category) =>
      _snapshotCall('deleteCategory', category);

  Future<AppSnapshot> clearAllEvents() => _snapshotCall('clearAllEvents');

  Future<bool> requestNotificationPermission() async =>
      await _channel.invokeMethod<bool>('requestNotificationPermission') ?? false;

  Future<EventImageModel?> pickImage() async {
    final raw = await _channel.invokeMethod<String>('pickImage');
    if (raw == null || raw.isEmpty) return null;
    return EventImageModel.fromJson((jsonDecode(raw) as Map).cast<String, dynamic>());
  }

  Future<EventImageModel?> recropImage(EventImageModel image) async {
    final raw = await _channel.invokeMethod<String>(
      'recropImage',
      jsonEncode(image.toNativeJson()),
    );
    if (raw == null || raw.isEmpty) return null;
    return EventImageModel.fromJson((jsonDecode(raw) as Map).cast<String, dynamic>());
  }

  Future<void> pinWidget(String eventId) async {
    await _channel.invokeMethod<bool>('pinWidget', eventId);
  }

  Future<void> shareText(String text) async {
    await _channel.invokeMethod<bool>('shareText', text);
  }

  Future<String> readDocument(String key) async =>
      await _channel.invokeMethod<String>('readDocument', key) ?? '';

  Future<void> openExternal(String url) async {
    await _channel.invokeMethod<bool>('openExternal', url);
  }

  Future<void> exportMilestones({
    required List<String> milestoneIds,
    required String action,
    required String template,
    String? title,
    required double backgroundPhase,
    required String backgroundMode,
    required String backgroundTexture,
  }) async {
    await _channel.invokeMethod<bool>(
      'exportMilestones',
      jsonEncode(<String, dynamic>{
        'milestoneIds': milestoneIds,
        'action': action,
        'template': template,
        'title': title ?? '',
        'backgroundPhase': backgroundPhase,
        'backgroundMode': backgroundMode,
        'backgroundTexture': backgroundTexture,
      }),
    );
  }

  Future<String> getMemoryImageTemplate() async =>
      await _channel.invokeMethod<String>('getMemoryImageTemplate') ?? 'MINIMAL';

  Future<String> renderEventImagePreview(
    String eventId,
    String template, {
    required double backgroundPhase,
    required String backgroundMode,
    required String backgroundTexture,
  }) async {
    final path = await _channel.invokeMethod<String>(
      'renderEventImagePreview',
      jsonEncode(<String, dynamic>{
        'eventId': eventId,
        'template': template,
        'backgroundPhase': backgroundPhase,
        'backgroundMode': backgroundMode,
        'backgroundTexture': backgroundTexture,
      }),
    );
    if (path == null || path.isEmpty) {
      throw StateError('Native preview path was empty');
    }
    return path;
  }

  Future<void> shareEventImage(
    String eventId,
    String action,
    String template, {
    required double backgroundPhase,
    required String backgroundMode,
    required String backgroundTexture,
  }) async {
    await _channel.invokeMethod<bool>(
      'shareEventImage',
      jsonEncode(<String, dynamic>{
        'eventId': eventId,
        'action': action,
        'template': template,
        'backgroundPhase': backgroundPhase,
        'backgroundMode': backgroundMode,
        'backgroundTexture': backgroundTexture,
      }),
    );
  }

  Future<UpdateStatusModel> getUpdateStatus() async {
    final raw = await _channel.invokeMethod<String>('getUpdateStatus');
    if (raw == null) throw StateError('Native update status was empty');
    return UpdateStatusModel.decode(raw);
  }

  Future<UpdateStatusModel> setUpdateWifiOnly(bool value) async {
    final raw = await _channel.invokeMethod<String>('setUpdateWifiOnly', value);
    if (raw == null) throw StateError('Native update status was empty');
    return UpdateStatusModel.decode(raw);
  }

  Future<UpdateStatusModel> checkForUpdate() async {
    final raw = await _channel.invokeMethod<String>('checkForUpdate');
    if (raw == null) throw StateError('Native update status was empty');
    return UpdateStatusModel.decode(raw);
  }

  Future<Map<String, dynamic>> requestInstallUpdate() async {
    final raw = await _channel.invokeMethod<String>('requestInstallUpdate');
    if (raw == null) return const <String, dynamic>{};
    return (jsonDecode(raw) as Map).cast<String, dynamic>();
  }

  Future<int> estimateExportPages({required List<String> eventIds, required String mode}) async {
    return await _channel.invokeMethod<int>(
          'estimateExportPages',
          jsonEncode(<String, dynamic>{'eventIds': eventIds, 'mode': mode}),
        ) ??
        0;
  }

  Future<void> exportEvents({
    required List<String> eventIds,
    required String mode,
    required String action,
    required String template,
    String? title,
    required double backgroundPhase,
    required String backgroundMode,
    required String backgroundTexture,
  }) async {
    await _channel.invokeMethod<bool>(
      'exportEvents',
      jsonEncode(<String, dynamic>{
        'eventIds': eventIds,
        'mode': mode,
        'action': action,
        'template': template,
        'title': title ?? '',
        'backgroundPhase': backgroundPhase,
        'backgroundMode': backgroundMode,
        'backgroundTexture': backgroundTexture,
      }),
    );
  }
}
