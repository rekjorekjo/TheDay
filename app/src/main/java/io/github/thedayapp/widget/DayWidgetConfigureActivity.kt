package io.github.thedayapp.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.DayRepository
import io.github.thedayapp.domain.DayMath
import io.github.thedayapp.domain.EventOrdering
import io.github.thedayapp.domain.normalizedCategoryName
import io.github.thedayapp.ui.theme.TheDayTheme
import io.github.thedayapp.util.DateFormatting
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

class DayWidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(Activity.RESULT_CANCELED)
        appWidgetId = intent
            ?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repository = DayRepository(this)
        val settings = repository.loadSettings()
        val today = LocalDate.now()
        val events = EventOrdering.sort(
            events = repository.loadEvents(),
            mode = settings.sortMode,
            direction = settings.sortDirection,
            today = today,
        )

        setContent {
            TheDayTheme(settings = settings) {
                DayWidgetConfigureScreen(
                    events = events,
                    today = today,
                    onBack = ::finish,
                    onEventSelected = ::completeConfiguration,
                )
            }
        }
    }

    private fun completeConfiguration(eventId: String) {
        DayWidgetPreferences.setEventId(
            context = this,
            appWidgetId = appWidgetId,
            eventId = eventId,
        )
        DayWidgetProvider.requestUpdate(
            context = this,
            appWidgetId = appWidgetId,
        )

        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                appWidgetId,
            ),
        )
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayWidgetConfigureScreen(
    events: List<DayEvent>,
    today: LocalDate,
    onBack: () -> Unit,
    onEventSelected: (String) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Text("选择特殊日子")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "取消",
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        if (events.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "还没有可选择的日子",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "请先打开 The Day 创建一个日子，再从桌面添加小组件。",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentPadding = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = events,
                    key = DayEvent::id,
                ) { event ->
                    DayWidgetEventItem(
                        event = event,
                        today = today,
                        onClick = {
                            onEventSelected(event.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayWidgetEventItem(
    event: DayEvent,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val locale = Locale.getDefault()
    val delta = DayMath.signedDays(event, today)
    val relationText = when {
        delta > 0L -> "还有 ${abs(delta)} 天"
        delta < 0L -> "已经 ${abs(delta)} 天"
        else -> "今天"
    }
    val dateText = DateFormatting.longDate(
        DayMath.effectiveDate(event, today),
        locale,
    )
    val categoryColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 18.dp,
                vertical = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    append(event.title)
                    withStyle(
                        SpanStyle(
                            color = categoryColor,
                        ),
                    ) {
                        append(" · ")
                        append(normalizedCategoryName(event.category))
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = relationText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
