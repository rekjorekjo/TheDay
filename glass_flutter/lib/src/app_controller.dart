import 'package:flutter/widgets.dart';

import 'glass_motion.dart';
import 'models.dart';
import 'native_bridge.dart';

class AppController extends ChangeNotifier with WidgetsBindingObserver {
  AppController({NativeBridge? bridge}) : bridge = bridge ?? NativeBridge() {
    this.bridge.onStateChanged = _receiveNativeSnapshot;
    this.bridge.onOpenEvent = (eventId) => onOpenEvent?.call(eventId);
    WidgetsBinding.instance.addObserver(this);
  }

  final NativeBridge bridge;

  AppSnapshot? snapshot;
  Object? lastError;
  bool loading = false;
  ValueChanged<String>? onOpenEvent;

  Future<void> initialize() async {
    if (loading) return;
    loading = true;
    notifyListeners();
    try {
      snapshot = await bridge.getSnapshot();
      lastError = null;
      final requested = snapshot?.requestedEventId;
      if (requested != null && requested.isNotEmpty) {
        WidgetsBinding.instance.addPostFrameCallback((_) => onOpenEvent?.call(requested));
      }
    } catch (error) {
      lastError = error;
    } finally {
      loading = false;
      notifyListeners();
    }
  }

  void _receiveNativeSnapshot(AppSnapshot next) {
    snapshot = next;
    lastError = null;
    notifyListeners();
  }

  Future<AppSnapshot> _commit(Future<AppSnapshot> operation) async {
    try {
      final next = await operation;
      snapshot = next;
      lastError = null;
      notifyListeners();
      return next;
    } catch (error) {
      lastError = error;
      notifyListeners();
      rethrow;
    }
  }

  Future<void> updateSettings(GlassSettings settings) async {
    await _commit(bridge.updateSettings(settings));
  }

  Future<DayEventModel> saveEvent(Map<String, dynamic> event) async {
    final next = await _commit(bridge.saveEvent(event));
    final explicitId = event['id'] as String?;
    final savedId = next.savedEventId ??
        (explicitId != null && explicitId.isNotEmpty ? explicitId : null);
    final saved = next.eventById(savedId);
    if (saved == null) {
      throw StateError('Native save completed without the saved event in the returned snapshot');
    }
    return saved;
  }

  Future<void> saveNewEventDraft(NewEventDraftModel draft) async {
    await _commit(bridge.saveNewEventDraft(draft));
  }

  Future<void> clearNewEventDraft() async {
    await _commit(bridge.clearNewEventDraft());
  }

  Future<void> deleteEvent(String eventId) async {
    await _commit(bridge.deleteEvent(eventId));
  }

  Future<void> togglePinned(String eventId) async {
    await _commit(bridge.togglePinned(eventId));
  }

  Future<void> saveMilestone(Map<String, dynamic> milestone) async {
    await _commit(bridge.saveMilestone(milestone));
  }

  Future<void> deleteMilestone(String id) async {
    await _commit(bridge.deleteMilestone(id));
  }

  Future<void> moveMilestone(String id, int direction) async {
    await _commit(bridge.moveMilestone(id, direction));
  }

  Future<void> moveMilestoneToIndex(String id, int index) async {
    await _commit(bridge.moveMilestoneToIndex(id, index));
  }

  Future<void> saveAlbum(Map<String, dynamic> album) async {
    await _commit(bridge.saveAlbum(album));
  }

  Future<void> deleteAlbum(String id) async {
    await _commit(bridge.deleteAlbum(id));
  }

  Future<void> updateCategoryCover(String category, EventImageModel? image) async {
    await _commit(bridge.updateCategoryCover(category, image));
  }

  Future<void> deleteCategory(String category) async {
    await _commit(bridge.deleteCategory(category));
  }

  Future<void> clearAllEvents() async {
    await _commit(bridge.clearAllEvents());
  }

  Future<bool> requestNotificationPermission() async {
    final granted = await bridge.requestNotificationPermission();
    await initialize();
    return granted;
  }

  Future<EventImageModel?> pickImage() => bridge.pickImage();
  Future<EventImageModel?> recropImage(EventImageModel image) => bridge.recropImage(image);
  Future<void> pinWidget(String eventId) => bridge.pinWidget(eventId);
  Future<void> shareText(String text) => bridge.shareText(text);
  Future<String> readDocument(String key) => bridge.readDocument(key);
  Future<void> openExternal(String url) => bridge.openExternal(url);

  Future<void> exportMilestones({
    required List<String> milestoneIds,
    required String action,
    required String template,
    String? title,
  }) =>
      bridge.exportMilestones(
        milestoneIds: milestoneIds,
        action: action,
        template: template,
        title: title,
        backgroundPhase: currentGlassBackgroundPhase,
        backgroundMode: snapshot?.settings.backgroundMotionMode ?? 'FLOW',
        backgroundTexture: snapshot?.settings.backgroundTexture ?? 'DIAGONAL',
      );

  Future<String> getMemoryImageTemplate() => bridge.getMemoryImageTemplate();
  Future<String> renderEventImagePreview(String eventId, String template) =>
      bridge.renderEventImagePreview(
        eventId,
        template,
        backgroundPhase: currentGlassBackgroundPhase,
        backgroundMode: snapshot?.settings.backgroundMotionMode ?? 'FLOW',
        backgroundTexture: snapshot?.settings.backgroundTexture ?? 'DIAGONAL',
      );

  Future<void> shareEventImage(String eventId, String action, String template) =>
      bridge.shareEventImage(
        eventId,
        action,
        template,
        backgroundPhase: currentGlassBackgroundPhase,
        backgroundMode: snapshot?.settings.backgroundMotionMode ?? 'FLOW',
        backgroundTexture: snapshot?.settings.backgroundTexture ?? 'DIAGONAL',
      );

  Future<UpdateStatusModel> getUpdateStatus() => bridge.getUpdateStatus();
  Future<UpdateStatusModel> setUpdateWifiOnly(bool value) => bridge.setUpdateWifiOnly(value);
  Future<UpdateStatusModel> checkForUpdate() => bridge.checkForUpdate();
  Future<Map<String, dynamic>> requestInstallUpdate() => bridge.requestInstallUpdate();

  Future<int> estimateExportPages({required List<String> eventIds, required String mode}) =>
      bridge.estimateExportPages(eventIds: eventIds, mode: mode);

  Future<void> exportEvents({
    required List<String> eventIds,
    required String mode,
    required String action,
    required String template,
    String? title,
  }) =>
      bridge.exportEvents(
        eventIds: eventIds,
        mode: mode,
        action: action,
        template: template,
        title: title,
        backgroundPhase: currentGlassBackgroundPhase,
        backgroundMode: snapshot?.settings.backgroundMotionMode ?? 'FLOW',
        backgroundTexture: snapshot?.settings.backgroundTexture ?? 'DIAGONAL',
      );

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) initialize();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    bridge.onStateChanged = null;
    bridge.onOpenEvent = null;
    super.dispose();
  }
}
