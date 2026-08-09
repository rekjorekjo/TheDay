package io.github.thedayapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import io.github.thedayapp.ui.currentJavaLocale
import io.github.thedayapp.util.DateFormatting
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private enum class CalculatorUnit(val label: String) {
    DAYS("天"),
    WEEKS("周"),
    MONTHS("月"),
    YEARS("年"),
}

private enum class CalculatorDirection(val label: String) {
    BEFORE("之前"),
    AFTER("之后"),
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
    val locale = currentJavaLocale()
    var offsetStart by remember { mutableStateOf(today) }
    var amountText by remember { mutableStateOf("") }
    var amountUnit by remember { mutableStateOf(CalculatorUnit.DAYS) }
    var direction by remember { mutableStateOf(CalculatorDirection.AFTER) }
    var intervalStart by remember { mutableStateOf(today) }
    var intervalEnd by remember { mutableStateOf(today) }
    var includeStart by remember { mutableStateOf(false) }
    var dateTarget by remember { mutableStateOf<CalculatorDateTarget?>(null) }

    val amount = amountText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    val resultDate = offsetStart.shifted(amount, amountUnit, direction)
    val rawInterval = ChronoUnit.DAYS.between(intervalStart, intervalEnd)
    val intervalDays = rawInterval + if (includeStart && rawInterval >= 0L) 1L else if (includeStart) -1L else 0L

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("日期计算器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
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
                SectionTitle("计算日期")
                Spacer(Modifier.height(10.dp))
                CalculatorCard {
                    DateRow(
                        prefix = "从",
                        date = offsetStart,
                        suffix = "开始",
                        locale = locale,
                        onClick = { dateTarget = CalculatorDateTarget.OFFSET_START },
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { input -> amountText = input.filter(Char::isDigit).take(6) },
                            modifier = Modifier.weight(1f),
                            label = { Text("数字") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        UnitDropdownButton(
                            selected = amountUnit,
                            onSelect = { amountUnit = it },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DirectionDropdownButton(
                            selected = direction,
                            onSelect = { direction = it },
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    ResultText(resultDate, locale)
                }
            }
            item {
                SectionTitle("计算日期间隔")
                Spacer(Modifier.height(10.dp))
                CalculatorCard {
                    DateRow(
                        prefix = "从",
                        date = intervalStart,
                        suffix = "开始，",
                        locale = locale,
                        onClick = { dateTarget = CalculatorDateTarget.INTERVAL_START },
                    )
                    Spacer(Modifier.height(10.dp))
                    DateRow(
                        prefix = "至",
                        date = intervalEnd,
                        suffix = "结束",
                        locale = locale,
                        onClick = { dateTarget = CalculatorDateTarget.INTERVAL_END },
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("共 ", style = MaterialTheme.typography.headlineSmall)
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
                        Text("包含起始日")
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
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { dateTarget = null }) { Text("取消") }
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
private fun UnitDropdownButton(
    selected: CalculatorUnit,
    onSelect: (CalculatorUnit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.width(76.dp),
        ) {
            Text(selected.label, maxLines = 1)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            CalculatorUnit.entries.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit.label) },
                    onClick = {
                        onSelect(unit)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DirectionDropdownButton(
    selected: CalculatorDirection,
    onSelect: (CalculatorDirection) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.width(120.dp),
        ) {
            Text(selected.label, maxLines = 1)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            CalculatorDirection.entries.forEach { direction ->
                DropdownMenuItem(
                    text = { Text(direction.label) },
                    onClick = {
                        onSelect(direction)
                        expanded = false
                    },
                )
            }
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
