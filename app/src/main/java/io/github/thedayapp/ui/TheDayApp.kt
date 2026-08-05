package io.github.thedayapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.thedayapp.data.ImagePlacementTarget
import io.github.thedayapp.data.TheDayState
import io.github.thedayapp.ui.components.TheDayBottomBar
import io.github.thedayapp.ui.components.TheDayTab
import io.github.thedayapp.ui.screens.AboutScreen
import io.github.thedayapp.ui.screens.CategoryDetailScreen
import io.github.thedayapp.ui.screens.CategoryScreen
import io.github.thedayapp.ui.screens.DateCalculatorScreen
import io.github.thedayapp.ui.screens.DocumentViewerScreen
import io.github.thedayapp.ui.screens.EventDetailScreen
import io.github.thedayapp.ui.screens.EventEditorScreen
import io.github.thedayapp.ui.screens.ExportScreen
import io.github.thedayapp.ui.screens.HomeScreen
import io.github.thedayapp.ui.screens.ImageTransformScreen
import io.github.thedayapp.ui.screens.MilestoneScreen
import io.github.thedayapp.ui.screens.SettingsScreen
import io.github.thedayapp.ui.screens.ToolsScreen
import io.github.thedayapp.ui.documents.AppDocument

private sealed interface EventReturnTarget {
    data object Home : EventReturnTarget
    data object Export : EventReturnTarget
    data class Category(val categoryName: String) : EventReturnTarget
}

private sealed interface Screen {
    data object Home : Screen
    data object Categories : Screen
    data class CategoryDetail(val categoryName: String) : Screen
    data object New : Screen
    data object Tools : Screen
    data object Export : Screen
    data object Milestones : Screen
    data object Calculator : Screen
    data object Settings : Screen
    data object About : Screen
    data class Document(val document: AppDocument) : Screen
    data class Detail(
        val eventId: String,
        val returnTarget: EventReturnTarget = EventReturnTarget.Home,
    ) : Screen
    data class Editor(
        val eventId: String,
        val returnTarget: EventReturnTarget = EventReturnTarget.Home,
    ) : Screen
    data class ImageAdjust(
        val eventId: String,
        val target: ImagePlacementTarget,
        val returnTarget: EventReturnTarget = EventReturnTarget.Home,
        val returnToDetail: Boolean,
    ) : Screen
}

private fun EventReturnTarget.toScreen(): Screen {
    return when (this) {
        EventReturnTarget.Home -> Screen.Home
        EventReturnTarget.Export -> Screen.Export
        is EventReturnTarget.Category -> Screen.CategoryDetail(categoryName)
    }
}

@Composable
fun TheDayApp(
    state: TheDayState,
    requestedEventId: String?,
    onRequestedEventConsumed: () -> Unit,
) {
    var screen: Screen by remember { mutableStateOf(Screen.Home) }

    LaunchedEffect(requestedEventId, state.events) {
        val eventId = requestedEventId ?: return@LaunchedEffect
        if (state.eventById(eventId) != null) {
            screen = Screen.Detail(
                eventId = eventId,
                returnTarget = EventReturnTarget.Home,
            )
        }
        onRequestedEventConsumed()
    }

    BackHandler(enabled = screen !is Screen.Home) {
        screen = when (screen) {
            is Screen.Editor -> {
                val editor = screen as Screen.Editor
                if (state.eventById(editor.eventId) != null) {
                    Screen.Detail(
                        eventId = editor.eventId,
                        returnTarget = editor.returnTarget,
                    )
                } else {
                    editor.returnTarget.toScreen()
                }
            }
            is Screen.ImageAdjust -> {
                val adjust = screen as Screen.ImageAdjust
                if (adjust.returnToDetail) {
                    Screen.Detail(
                        eventId = adjust.eventId,
                        returnTarget = adjust.returnTarget,
                    )
                } else {
                    adjust.returnTarget.toScreen()
                }
            }
            is Screen.Detail -> (screen as Screen.Detail).returnTarget.toScreen()
            is Screen.New -> Screen.Home
            is Screen.CategoryDetail -> Screen.Categories
            is Screen.Categories -> Screen.Home
            is Screen.Export -> Screen.Tools
            is Screen.Milestones -> Screen.Tools
            is Screen.Calculator -> Screen.Tools
            is Screen.Tools -> Screen.Home
            is Screen.Settings -> Screen.Home
            is Screen.About -> Screen.Settings
            is Screen.Document -> Screen.About
            else -> Screen.Home
        }
    }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            state = state,
            onOpenEvent = { eventId ->
                screen = Screen.Detail(
                    eventId = eventId,
                    returnTarget = EventReturnTarget.Home,
                )
            },
            onAdjustHeroImage = { eventId ->
                screen = Screen.ImageAdjust(
                    eventId = eventId,
                    target = ImagePlacementTarget.HOME,
                    returnTarget = EventReturnTarget.Home,
                    returnToDetail = false,
                )
            },
            onOpenTools = { screen = Screen.Tools },
            bottomBar = {
                TheDayBottomBar(
                    selectedTab = TheDayTab.DAYS,
                    onDaysClick = { screen = Screen.Home },
                    onCategoriesClick = { screen = Screen.Categories },
                    onNewClick = { screen = Screen.New },
                    onSettingsClick = { screen = Screen.Settings },
                )
            },
        )

        Screen.Tools -> ToolsScreen(
            bottomBar = {
                TheDayBottomBar(
                    selectedTab = TheDayTab.DAYS,
                    onDaysClick = { screen = Screen.Home },
                    onCategoriesClick = { screen = Screen.Categories },
                    onNewClick = { screen = Screen.New },
                    onSettingsClick = { screen = Screen.Settings },
                )
            },
            onBack = { screen = Screen.Home },
            onOpenExport = { screen = Screen.Export },
            onOpenMilestones = { screen = Screen.Milestones },
            onOpenCalculator = { screen = Screen.Calculator },
        )

        Screen.Milestones -> MilestoneScreen(
            state = state,
            onBack = { screen = Screen.Tools },
        )

        Screen.Calculator -> DateCalculatorScreen(
            today = state.today,
            onBack = { screen = Screen.Tools },
        )
        Screen.Categories -> CategoryScreen(
            events = state.events,
            categoryCovers = state.categoryCovers,
            onOpenCategory = { categoryName ->
                screen = Screen.CategoryDetail(categoryName)
            },
            bottomBar = {
                TheDayBottomBar(
                    selectedTab = TheDayTab.CATEGORIES,
                    onDaysClick = { screen = Screen.Home },
                    onCategoriesClick = { screen = Screen.Categories },
                    onNewClick = { screen = Screen.New },
                    onSettingsClick = { screen = Screen.Settings },
                )
            },
        )

        is Screen.CategoryDetail -> CategoryDetailScreen(
            categoryName = current.categoryName,
            state = state,
            onBack = { screen = Screen.Categories },
            onOpenEvent = { eventId ->
                screen = Screen.Detail(
                    eventId = eventId,
                    returnTarget = EventReturnTarget.Category(current.categoryName),
                )
            },
        )

        Screen.New -> EventEditorScreen(
            existing = null,
            onBack = null,
            onSave = { event ->
                state.upsertEvent(event)
                state.clearNewEventDraft()
                screen = Screen.Detail(
                    eventId = event.id,
                    returnTarget = EventReturnTarget.Home,
                )
            },
            bottomBar = {
                TheDayBottomBar(
                    selectedTab = TheDayTab.NEW,
                    onDaysClick = { screen = Screen.Home },
                    onCategoriesClick = { screen = Screen.Categories },
                    onNewClick = { screen = Screen.New },
                    onSettingsClick = { screen = Screen.Settings },
                )
            },
            initialDraft = state.newEventDraft,
            onDraftSave = state::saveNewEventDraft,
            onDraftClear = state::clearNewEventDraft,
            initialBackgroundImage = state.newEventDraft?.backgroundImage,
            onImportBackgroundImage = state::importLocalImage,
            onRecropBackgroundImage = state::recropLocalImage,
            onReleaseBackgroundImage = state::releaseLocalImageIfUnreferenced,
        )

        Screen.Export -> ExportScreen(
            state = state,
            bottomBar = {},
            onBack = { screen = Screen.Tools },
            onOpenEvent = { eventId ->
                screen = Screen.Detail(
                    eventId = eventId,
                    returnTarget = EventReturnTarget.Export,
                )
            },
        )

        Screen.Settings -> SettingsScreen(
            state = state,
            bottomBar = {
                TheDayBottomBar(
                    selectedTab = TheDayTab.SETTINGS,
                    onDaysClick = { screen = Screen.Home },
                    onCategoriesClick = { screen = Screen.Categories },
                    onNewClick = { screen = Screen.New },
                    onSettingsClick = { screen = Screen.Settings },
                )
            },
            onOpenAbout = { screen = Screen.About },
        )

        Screen.About -> AboutScreen(
            onBack = { screen = Screen.Settings },
            onOpenDocument = { document -> screen = Screen.Document(document) },
        )

        is Screen.Document -> DocumentViewerScreen(
            document = (screen as Screen.Document).document,
            onBack = { screen = Screen.About },
        )

        is Screen.Detail -> {
            val event = state.eventById(current.eventId)
            if (event == null) {
                LaunchedEffect(current.eventId) {
                    screen = current.returnTarget.toScreen()
                }
            } else {
                EventDetailScreen(
                    event = event,
                    today = state.today,
                    onBack = { screen = current.returnTarget.toScreen() },
                    onEdit = {
                        screen = Screen.Editor(
                            eventId = event.id,
                            returnTarget = current.returnTarget,
                        )
                    },
                    onDelete = {
                        state.deleteEvent(event.id)
                        screen = current.returnTarget.toScreen()
                    },
                    onTogglePinned = { state.togglePinned(event.id) },
                    onAdjustImage = {
                        screen = Screen.ImageAdjust(
                            eventId = event.id,
                            target = ImagePlacementTarget.DETAIL,
                            returnTarget = current.returnTarget,
                            returnToDetail = true,
                        )
                    },
                    onImageTransformChange = { transform ->
                        state.updateEventImageTransform(
                            eventId = event.id,
                            target = ImagePlacementTarget.DETAIL,
                            transform = transform,
                        )
                    },
                )
            }
        }

        is Screen.ImageAdjust -> {
            val event = state.eventById(current.eventId)
            if (event == null || event.backgroundImage == null) {
                LaunchedEffect(current.eventId) {
                    screen = if (current.returnToDetail && event != null) {
                        Screen.Detail(
                            eventId = current.eventId,
                            returnTarget = current.returnTarget,
                        )
                    } else {
                        current.returnTarget.toScreen()
                    }
                }
            } else {
                val returnScreen: () -> Unit = {
                    screen = if (current.returnToDetail) {
                        Screen.Detail(
                            eventId = current.eventId,
                            returnTarget = current.returnTarget,
                        )
                    } else {
                        current.returnTarget.toScreen()
                    }
                }
                ImageTransformScreen(
                    event = event,
                    today = state.today,
                    target = current.target,
                    onBack = returnScreen,
                    onSave = { transform ->
                        state.updateEventImageTransform(
                            eventId = event.id,
                            target = current.target,
                            transform = transform,
                        )
                        returnScreen()
                    },
                )
            }
        }

        is Screen.Editor -> {
            val event = state.eventById(current.eventId)
            if (event == null) {
                LaunchedEffect(current.eventId) {
                    screen = current.returnTarget.toScreen()
                }
            } else {
                EventEditorScreen(
                    existing = event,
                    onBack = {
                        screen = if (state.eventById(current.eventId) != null) {
                            Screen.Detail(
                                eventId = current.eventId,
                                returnTarget = current.returnTarget,
                            )
                        } else {
                            current.returnTarget.toScreen()
                        }
                    },
                    onSave = { updatedEvent ->
                        state.upsertEvent(updatedEvent)
                        screen = Screen.Detail(
                            eventId = updatedEvent.id,
                            returnTarget = current.returnTarget,
                        )
                    },
                    initialBackgroundImage = event.backgroundImage,
                    onImportBackgroundImage = state::importLocalImage,
                    onRecropBackgroundImage = state::recropLocalImage,
                    onReleaseBackgroundImage = state::releaseLocalImageIfUnreferenced,
                )
            }
        }
    }
}
