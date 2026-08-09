import 'dart:convert';


String _backgroundMotionModeFromJson(Map<String, dynamic> json) {
  final raw = json['backgroundMotionMode'] as String?;
  if (raw == 'STATIC' || raw == 'FLOW' || raw == 'AURORA') return raw!;
  return (json['dynamicBackground'] as bool? ?? true) ? 'FLOW' : 'STATIC';
}

class GlassSettings {
  const GlassSettings({
    required this.themeMode,
    required this.paletteStyle,
    required this.glassClarity,
    required this.backgroundMotionMode,
    required this.backgroundTexture,
    required this.sortMode,
    required this.sortDirection,
    required this.showPastEvents,
    required this.reminderHour,
    required this.reminderMinute,
  });

  final String themeMode;
  final String paletteStyle;
  final int glassClarity;
  final String backgroundMotionMode;
  final String backgroundTexture;
  final String sortMode;
  final String sortDirection;
  final bool showPastEvents;
  final int reminderHour;
  final int reminderMinute;

  factory GlassSettings.fromJson(Map<String, dynamic> json) {
    return GlassSettings(
      themeMode: json['themeMode'] as String? ?? 'SYSTEM',
      paletteStyle: json['paletteStyle'] as String? ?? 'MIDNIGHT',
      glassClarity: (json['glassClarity'] as num? ?? 62).round().clamp(0, 100).toInt(),
      backgroundMotionMode: _backgroundMotionModeFromJson(json),
      backgroundTexture: json['backgroundTexture'] as String? ?? 'DIAGONAL',
      sortMode: json['sortMode'] as String? ?? 'SMART',
      sortDirection: json['sortDirection'] as String? ?? 'ASCENDING',
      showPastEvents: json['showPastEvents'] as bool? ?? true,
      reminderHour: (json['reminderHour'] as num? ?? 9).round().clamp(0, 23).toInt(),
      reminderMinute: (json['reminderMinute'] as num? ?? 0).round().clamp(0, 59).toInt(),
    );
  }

  GlassSettings copyWith({
    String? themeMode,
    String? paletteStyle,
    int? glassClarity,
    String? backgroundMotionMode,
    String? backgroundTexture,
    String? sortMode,
    String? sortDirection,
    bool? showPastEvents,
    int? reminderHour,
    int? reminderMinute,
  }) {
    return GlassSettings(
      themeMode: themeMode ?? this.themeMode,
      paletteStyle: paletteStyle ?? this.paletteStyle,
      glassClarity: glassClarity ?? this.glassClarity,
      backgroundMotionMode: backgroundMotionMode ?? this.backgroundMotionMode,
      backgroundTexture: backgroundTexture ?? this.backgroundTexture,
      sortMode: sortMode ?? this.sortMode,
      sortDirection: sortDirection ?? this.sortDirection,
      showPastEvents: showPastEvents ?? this.showPastEvents,
      reminderHour: reminderHour ?? this.reminderHour,
      reminderMinute: reminderMinute ?? this.reminderMinute,
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'themeMode': themeMode,
        'paletteStyle': paletteStyle,
        'glassClarity': glassClarity,
        'backgroundMotionMode': backgroundMotionMode,
        'dynamicBackground': backgroundMotionMode != 'STATIC',
        'backgroundTexture': backgroundTexture,
        'sortMode': sortMode,
        'sortDirection': sortDirection,
        'showPastEvents': showPastEvents,
        'reminderHour': reminderHour,
        'reminderMinute': reminderMinute,
      };
}

class ImageTransformModel {
  const ImageTransformModel({
    required this.focusX,
    required this.focusY,
    required this.zoom,
  });

  final double focusX;
  final double focusY;
  final double zoom;

  factory ImageTransformModel.fromJson(Map<String, dynamic>? json) {
    return ImageTransformModel(
      focusX: (json?['focusX'] as num? ?? 0.5).toDouble().clamp(0.0, 1.0).toDouble(),
      focusY: (json?['focusY'] as num? ?? 0.5).toDouble().clamp(0.0, 1.0).toDouble(),
      zoom: (json?['zoom'] as num? ?? 1.0).toDouble().clamp(1.0, 4.0).toDouble(),
    );
  }

  ImageTransformModel copyWith({double? focusX, double? focusY, double? zoom}) {
    return ImageTransformModel(
      focusX: focusX ?? this.focusX,
      focusY: focusY ?? this.focusY,
      zoom: zoom ?? this.zoom,
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'focusX': focusX,
        'focusY': focusY,
        'zoom': zoom,
      };
}

class EventImageModel {
  const EventImageModel({
    required this.fileName,
    required this.filePath,
    required this.originalFileName,
    required this.width,
    required this.height,
    required this.homeTransform,
    required this.detailTransform,
  });

  final String fileName;
  final String? filePath;
  final String? originalFileName;
  final int width;
  final int height;
  final ImageTransformModel homeTransform;
  final ImageTransformModel detailTransform;

  factory EventImageModel.fromJson(Map<String, dynamic> json) {
    return EventImageModel(
      fileName: json['fileName'] as String? ?? '',
      filePath: json['filePath'] as String?,
      originalFileName: json['originalFileName'] as String?,
      width: (json['width'] as num? ?? 1).round(),
      height: (json['height'] as num? ?? 1).round(),
      homeTransform: ImageTransformModel.fromJson(
        (json['homeTransform'] as Map?)?.cast<String, dynamic>(),
      ),
      detailTransform: ImageTransformModel.fromJson(
        (json['detailTransform'] as Map?)?.cast<String, dynamic>(),
      ),
    );
  }

  EventImageModel copyWith({
    String? fileName,
    String? filePath,
    String? originalFileName,
    int? width,
    int? height,
    ImageTransformModel? homeTransform,
    ImageTransformModel? detailTransform,
  }) {
    return EventImageModel(
      fileName: fileName ?? this.fileName,
      filePath: filePath ?? this.filePath,
      originalFileName: originalFileName ?? this.originalFileName,
      width: width ?? this.width,
      height: height ?? this.height,
      homeTransform: homeTransform ?? this.homeTransform,
      detailTransform: detailTransform ?? this.detailTransform,
    );
  }

  Map<String, dynamic> toNativeJson() => <String, dynamic>{
        'fileName': fileName,
        'width': width,
        'height': height,
        'originalFileName': originalFileName,
        'homeTransform': homeTransform.toJson(),
        'detailTransform': detailTransform.toJson(),
      };
}

class DayEventModel {
  const DayEventModel({
    required this.id,
    required this.title,
    required this.date,
    required this.effectiveDate,
    required this.signedDays,
    required this.repeatMode,
    required this.category,
    required this.note,
    required this.isPinned,
    required this.reminderDaysBefore,
    required this.backgroundImage,
    required this.createdAtEpochMillis,
  });

  final String id;
  final String title;
  final DateTime date;
  final DateTime effectiveDate;
  final int signedDays;
  final String repeatMode;
  final String category;
  final String note;
  final bool isPinned;
  final int? reminderDaysBefore;
  final EventImageModel? backgroundImage;
  final int createdAtEpochMillis;

  factory DayEventModel.fromJson(Map<String, dynamic> json) {
    final image = json['backgroundImage'];
    return DayEventModel(
      id: json['id'] as String? ?? '',
      title: json['title'] as String? ?? '',
      date: DateTime.tryParse(json['date'] as String? ?? '') ?? DateTime.now(),
      effectiveDate: DateTime.tryParse(json['effectiveDate'] as String? ?? '') ??
          DateTime.tryParse(json['date'] as String? ?? '') ??
          DateTime.now(),
      signedDays: (json['signedDays'] as num? ?? 0).round(),
      repeatMode: json['repeatMode'] as String? ?? 'NONE',
      category: json['category'] as String? ?? '',
      note: json['note'] as String? ?? '',
      isPinned: json['isPinned'] as bool? ?? false,
      reminderDaysBefore: (json['reminderDaysBefore'] as num?)?.round(),
      backgroundImage: image is Map
          ? EventImageModel.fromJson(image.cast<String, dynamic>())
          : null,
      createdAtEpochMillis: (json['createdAtEpochMillis'] as num? ?? 0).round(),
    );
  }

  Map<String, dynamic> toNativeJson({EventImageModel? imageOverride, bool removeImage = false}) {
    return <String, dynamic>{
      'id': id,
      'title': title,
      'date': _isoDate(date),
      'dateYear': date.year,
      'dateMonth': date.month,
      'dateDay': date.day,
      'category': category,
      'note': note,
      'repeatMode': repeatMode,
      'isPinned': isPinned,
      'reminderDaysBefore': reminderDaysBefore,
      'backgroundImage': removeImage ? null : (imageOverride ?? backgroundImage)?.toNativeJson(),
      'createdAtEpochMillis': createdAtEpochMillis,
    };
  }
}

class NewEventDraftModel {
  const NewEventDraftModel({
    required this.title,
    required this.date,
    required this.category,
    required this.note,
    required this.repeatMode,
    required this.isPinned,
    required this.reminderDaysBefore,
    required this.backgroundImage,
  });

  final String title;
  final DateTime date;
  final String category;
  final String note;
  final String repeatMode;
  final bool isPinned;
  final int? reminderDaysBefore;
  final EventImageModel? backgroundImage;

  factory NewEventDraftModel.fromJson(Map<String, dynamic> json) {
    final image = json['backgroundImage'];
    return NewEventDraftModel(
      title: json['title'] as String? ?? '',
      date: DateTime.tryParse(json['date'] as String? ?? '') ?? DateTime.now(),
      category: json['category'] as String? ?? '',
      note: json['note'] as String? ?? '',
      repeatMode: json['repeatMode'] as String? ?? 'NONE',
      isPinned: json['isPinned'] as bool? ?? false,
      reminderDaysBefore: (json['reminderDaysBefore'] as num?)?.round(),
      backgroundImage: image is Map
          ? EventImageModel.fromJson(image.cast<String, dynamic>())
          : null,
    );
  }

  Map<String, dynamic> toNativeJson() => <String, dynamic>{
        'title': title,
        'date': _isoDate(date),
        'dateYear': date.year,
        'dateMonth': date.month,
        'dateDay': date.day,
        'category': category,
        'note': note,
        'repeatMode': repeatMode,
        'isPinned': isPinned,
        'reminderDaysBefore': reminderDaysBefore,
        'backgroundImage': backgroundImage?.toNativeJson(),
      };
}

class DayMilestoneModel {
  const DayMilestoneModel({
    required this.id,
    required this.title,
    required this.date,
    required this.note,
    required this.createdAtEpochMillis,
  });

  final String id;
  final String title;
  final DateTime date;
  final String note;
  final int createdAtEpochMillis;

  factory DayMilestoneModel.fromJson(Map<String, dynamic> json) => DayMilestoneModel(
        id: json['id'] as String? ?? '',
        title: json['title'] as String? ?? '',
        date: DateTime.tryParse(json['date'] as String? ?? '') ?? DateTime.now(),
        note: json['note'] as String? ?? '',
        createdAtEpochMillis: (json['createdAtEpochMillis'] as num? ?? 0).round(),
      );
}

class DayAlbumModel {
  const DayAlbumModel({
    required this.id,
    required this.title,
    required this.eventIds,
    required this.coverEventId,
    required this.createdAtEpochMillis,
    required this.updatedAtEpochMillis,
  });

  final String id;
  final String title;
  final List<String> eventIds;
  final String? coverEventId;
  final int createdAtEpochMillis;
  final int updatedAtEpochMillis;

  factory DayAlbumModel.fromJson(Map<String, dynamic> json) => DayAlbumModel(
        id: json['id'] as String? ?? '',
        title: json['title'] as String? ?? '',
        eventIds: (json['eventIds'] as List? ?? const <dynamic>[]).whereType<String>().toList(),
        coverEventId: json['coverEventId'] as String?,
        createdAtEpochMillis: (json['createdAtEpochMillis'] as num? ?? 0).round(),
        updatedAtEpochMillis: (json['updatedAtEpochMillis'] as num? ?? 0).round(),
      );
}

class AppSnapshot {
  const AppSnapshot({
    required this.today,
    required this.isDark,
    required this.notificationGranted,
    required this.canPinWidget,
    required this.versionName,
    required this.edition,
    required this.requestedEventId,
    required this.savedEventId,
    required this.heroEventId,
    required this.orderedEventIds,
    required this.settings,
    required this.newEventDraft,
    required this.events,
    required this.milestones,
    required this.albums,
    required this.categoryCovers,
  });

  final DateTime today;
  final bool isDark;
  final bool notificationGranted;
  final bool canPinWidget;
  final String versionName;
  final String edition;
  final String? requestedEventId;
  final String? savedEventId;
  final String? heroEventId;
  final List<String> orderedEventIds;
  final GlassSettings settings;
  final NewEventDraftModel? newEventDraft;
  final List<DayEventModel> events;
  final List<DayMilestoneModel> milestones;
  final List<DayAlbumModel> albums;
  final Map<String, EventImageModel> categoryCovers;

  factory AppSnapshot.decode(String raw) {
    final json = (jsonDecode(raw) as Map).cast<String, dynamic>();
    final eventJson = (json['events'] as List? ?? const <dynamic>[]);
    final milestoneJson = (json['milestones'] as List? ?? const <dynamic>[]);
    final albumJson = (json['albums'] as List? ?? const <dynamic>[]);
    final coverJson = (json['categoryCovers'] as Map? ?? const <String, dynamic>{});
    final draftJson = json['newEventDraft'];
    return AppSnapshot(
      today: DateTime.tryParse(json['today'] as String? ?? '') ?? DateTime.now(),
      isDark: json['isDark'] as bool? ?? true,
      notificationGranted: json['notificationGranted'] as bool? ?? true,
      canPinWidget: json['canPinWidget'] as bool? ?? true,
      versionName: json['versionName'] as String? ?? '',
      edition: json['edition'] as String? ?? 'glass',
      requestedEventId: json['requestedEventId'] as String?,
      savedEventId: json['savedEventId'] as String?,
      heroEventId: json['heroEventId'] as String?,
      orderedEventIds: (json['orderedEventIds'] as List? ?? const <dynamic>[])
          .whereType<String>()
          .toList(growable: false),
      settings: GlassSettings.fromJson(
        (json['settings'] as Map? ?? const <String, dynamic>{}).cast<String, dynamic>(),
      ),
      newEventDraft: draftJson is Map
          ? NewEventDraftModel.fromJson(draftJson.cast<String, dynamic>())
          : null,
      events: eventJson
          .whereType<Map>()
          .map((item) => DayEventModel.fromJson(item.cast<String, dynamic>()))
          .toList(growable: false),
      milestones: milestoneJson
          .whereType<Map>()
          .map((item) => DayMilestoneModel.fromJson(item.cast<String, dynamic>()))
          .toList(growable: false),
      albums: albumJson
          .whereType<Map>()
          .map((item) => DayAlbumModel.fromJson(item.cast<String, dynamic>()))
          .toList(growable: false),
      categoryCovers: <String, EventImageModel>{
        for (final entry in coverJson.entries)
          if (entry.value is Map)
            entry.key.toString(): EventImageModel.fromJson((entry.value as Map).cast<String, dynamic>()),
      },
    );
  }

  DayEventModel? eventById(String? id) {
    if (id == null || id.isEmpty) return null;
    for (final event in events) {
      if (event.id == id) return event;
    }
    return null;
  }

  DayMilestoneModel? milestoneById(String? id) {
    if (id == null) return null;
    for (final item in milestones) {
      if (item.id == id) return item;
    }
    return null;
  }

  DayAlbumModel? albumById(String? id) {
    if (id == null) return null;
    for (final item in albums) {
      if (item.id == id) return item;
    }
    return null;
  }

  List<DayEventModel> get orderedEvents {
    final byId = <String, DayEventModel>{for (final event in events) event.id: event};
    return orderedEventIds
        .map((id) => byId[id])
        .whereType<DayEventModel>()
        .toList(growable: false);
  }

  DayEventModel? get heroEvent => eventById(heroEventId);

  List<String> get categories {
    final values = orderedEvents
        .map((event) => event.category.trim())
        .where((category) => category.isNotEmpty)
        .toSet()
        .toList();
    values.sort((a, b) => a.toLowerCase().compareTo(b.toLowerCase()));
    return values;
  }
}

String _isoDate(DateTime date) => '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
