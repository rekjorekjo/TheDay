package io.github.thedayapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.thedayapp.util.DateFormatting
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private enum class CalculatorUnit(val label: String) {
    DAYS("\u5929"),
    WEEKS("\u5468"),
    MONTHS("\u6708"),
    YEARS("\u5e74"),
}

private enum class CalculatorDirection(val label: String) {
    BEFORE("\u4e4b\u524d"),
    AFTER("\u4e4b\u540e"),
}

private enum class CalculatorDateTarget {
    OFFSET_START,
    INTERVAL_START,
    INTERVAL_END,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateCalculatorScreen(
    today: LocalDate,
    onBack: () -> Unit,
) {
    val locale = Locale.getDefault()
    var offsetStart by remember { mutableStateOf(today) }
    var amountText by remember { mutableStateOf("") }
    var amountUnit by remember { mutableStateOf(CalculatorUnit.DAYS) }
    var beforeDirection by remember { mutableStateOf(CalculatorDirection.BEFORE) }
    var afterDirection by remember { mutableStateOf(CalculatorDirection.AFTER) }
    var intervalStart by remember { mutableStateOf(today) }
    var intervalEnd by remember { mutableStateOf(today) }
    var includeStart by remember { mutableStateOf(false) }
    var dateTarget by remember { mutableStateOf<CalculatorDateTarget?>(null) }

    val amount = amountText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    val beforeResult = offsetStart.shifted(amount, amountUnit, beforeDirection)
    val afterResult = offsetStart.shifted(amount, amountUnit, afterDirection)
    val rawInterval = ChronoUnit.DAYS.between(intervalStart, intervalEnd)
    val intervalDays = rawInterval + if (includeStart && rawInterval >= 0L) 1L else if (includeStart) -1L else 0L

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("\u65e5\u671f\u8ba1\u7b97\u5668") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "\u8fd4\u56de")
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                SectionTitle("\u8ba1\u7b97\u65e5\u671f")
                Spacer(Modifier.height(10.dp))
                CalculatorCard {
                    DateRow(
                        prefix = "\u4ece",
                        date = offsetStart,
                        suffix = "\u5f00\u59cb",
                        locale = locale,
                        onClick = { dateTarget = CalculatorDateTarget.OFFSET_START },
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { input -> amountText = input.filter(Char::isDigit).take(6) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("\u8f93\u5165\u6570\u5b57\u8ba1\u7b97") },
                        suffix = { Text(amountUnit.label) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.height(10.dp))
                    ChipRow(CalculatorUnit.entries, amountUnit) { amountUnit = it }
                    Spacer(Modifier.height(10.dp))
                    ChipRow(CalculatorDirection.entries, beforeDirection) { beforeDirection = it }
                    ResultText(beforeResult, locale)
                    Spacer(Modifier.height(12.dp))
                    ChipRow(CalculatorDirection.entries, afterDirection) { afterDirection = it }
                    ResultText(afterResult, locale)
                }
            }
            item {
                SectionTitle("\u8ba1\u7b97\u65e5\u671f\u95f4\u9694")
                Spacer(Modifier.height(10.dp))
                CalculatorCard {
                    DateRow(
                        prefix = "\u4ece",
                        date = intervalStart,
                        suffix = "\u5f00\u59cb\uff0c",
                        locale = locale,
                        onClick = { dateTarget = CalculatorDateTarget.INTERVAL_START },
                    )
                    Spacer(Modifier.height(10.dp))
                    DateRow(
                        prefix = "\u81f3",
                        date = intervalEnd,
                        suffix = "\u7ed3\u675f",
                        locale = locale,
                        onClick = { dateTarget = CalculatorDateTarget.INTERVAL_END },
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("\u5171 ", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = abs(intervalDays).toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(" Days", style = MaterialTheme.typography.titleLarge)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = includeStart,
                            onCheckedChange = { includeStart = it },
                        )
                        Text("\u5305\u542b\u8d77\u59cb\u65e5")
                    }
                }
            }
        }
    }

    dateTarget?.let { target ->
        val initialDate = when (target) {
            CalculatorDateTarget.OFFSET_START -> offsetStart
            CalculatorDateTarget.INTERVAL_START -> intervalStart
            CalculatorDateTarget.INTERVAL_END -> intervalEnd
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { dateTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            when (target) {
                                CalculatorDateTarget.OFFSET_START -> offsetStart = picked
                                CalculatorDateTarget.INTERVAL_START -> intervalStart = picked
                                CalculatorDateTarget.INTERVAL_END -> intervalEnd = picked
                            }
                        }
                        dateTarget = null
                    },
                ) { Text("\u786e\u5b9a") }
            },
            dismissButton = {
                TextButton(onClick = { dateTarget = null }) { Text("\u53d6\u6d88") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun CalculatorCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content,
        )
    }
}

@Composable
private fun DateRow(
    prefix: String,
    date: LocalDate,
    suffix: String,
    locale: Locale,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(prefix, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = " ${DateFormatting.compactDate(date, locale)} ",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(suffix, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> ChipRow(
    values: Iterable<T>,
    selected: T,
    onSelect: (T) -> Unit,
) where T : Enum<T> {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            val label = when (value) {
                is CalculatorUnit -> value.label
                is CalculatorDirection -> value.label
                else -> value.name
            }
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun ResultText(date: LocalDate, locale: Locale) {
    Text(
        text = DateFormatting.longDate(date, locale),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
}

private fun LocalDate.shifted(
    amount: Long,
    unit: CalculatorUnit,
    direction: CalculatorDirection,
): LocalDate {
    val signedAmount = if (direction == CalculatorDirection.BEFORE) -amount else amount
    return when (unit) {
        CalculatorUnit.DAYS -> plusDays(signedAmount)
        CalculatorUnit.WEEKS -> plusWeeks(signedAmount)
        CalculatorUnit.MONTHS -> plusMonths(signedAmount)
        CalculatorUnit.YEARS -> plusYears(signedAmount)
    }
}