/*
 * This file contains the UI and logic for the App Usage screen.
 * Location: app/src/main/java/com/example/deviceinspector/ui/screens/UsageScreen.kt
 */
package com.example.deviceinspector.ui.screens

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Paint
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceinspector.data.AppUsageInfo
import com.example.deviceinspector.ui.components.GenericScreen
import com.example.deviceinspector.util.formatUsageTime
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// Enum to manage the chart view type. Made public to be accessible by AppUsageCard.
enum class ChartType { DAILY, HOURLY, TODAY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen() {
    val context = LocalContext.current
    var chartType by remember { mutableStateOf(ChartType.DAILY) }
    // Add "Today" to the list of options
    val chartTypes = listOf("7 Days", "24 Hours", "Today")

    val appUsageState = produceState<List<AppUsageInfo>?>(initialValue = null) {
        value = getAppUsageStats(context)
    }

    GenericScreen("App Usage") {
        // Segmented button to switch chart type
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            chartTypes.forEachIndexed { index, label ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = chartTypes.size),
                    onClick = {
                        chartType = when (index) {
                            0 -> ChartType.DAILY
                            1 -> ChartType.HOURLY
                            else -> ChartType.TODAY
                        }
                    },
                    selected = (chartType.ordinal == index)
                ) {
                    Text(label)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        when (val apps = appUsageState.value) {
            null -> { // Loading state
                CircularProgressIndicator()
                Text("Calculating usage...", modifier = Modifier.padding(top = 16.dp))
            }
            else -> {
                if (apps.isEmpty()) {
                    Text("No application usage data found.")
                } else {
                    // Re-sort the list based on the selected chart type.
                    val sortedApps = remember(apps, chartType) {
                        apps.sortedByDescending { app ->
                            when (chartType) {
                                ChartType.DAILY -> app.totalTimeInForeground
                                ChartType.HOURLY -> app.hourlyUsageEvents.sumOf { it.second - it.first }
                                ChartType.TODAY -> {
                                    val todayStart = getDayStart(System.currentTimeMillis())
                                    app.hourlyUsageEvents
                                        .filter { (start, end) -> end > todayStart && start < System.currentTimeMillis() }
                                        .sumOf { (start, end) -> end - max(todayStart, start) }
                                }
                            }
                        }
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(sortedApps) { app ->
                            AppUsageCard(app = app, chartType = chartType)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppUsageCard(app: AppUsageInfo, chartType: ChartType) {
    var isExpanded by remember { mutableStateOf(false) }

    // Dynamically calculate the usage to display based on the selected chart type.
    val displayedUsage = when (chartType) {
        ChartType.DAILY -> app.totalTimeInForeground // 7-day total
        ChartType.HOURLY -> app.hourlyUsageEvents.sumOf { it.second - it.first } // 24-hour total
        ChartType.TODAY -> { // Today's total (since midnight)
            val todayStart = getDayStart(System.currentTimeMillis())
            app.hourlyUsageEvents
                .filter { (start, end) -> end > todayStart && start < System.currentTimeMillis() }
                .sumOf { (start, end) ->
                    // Clamp session to today's boundaries
                    end - max(todayStart, start)
                }
        }
    }


    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { isExpanded = !isExpanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painter = rememberDrawablePainter(drawable = app.icon), contentDescription = "${app.appName} icon", modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = app.appName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                // Use the new dynamic usage value here
                Text(text = formatUsageTime(displayedUsage), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Icon(imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Toggle details", modifier = Modifier.padding(start = 8.dp))
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))
                    when (chartType) {
                        ChartType.DAILY -> {
                            Text("Daily Usage Chart (7d):", style = MaterialTheme.typography.labelMedium)
                            DailyUsageBarChart(dailyUsage = app.dailyUsage)
                        }
                        ChartType.HOURLY -> {
                            Text("Hourly Timeline (24h):", style = MaterialTheme.typography.labelMedium)
                            HourlyUsageTimelineChart(
                                usageEvents = app.hourlyUsageEvents,
                                timelineStart = System.currentTimeMillis() - 24 * 60 * 60 * 1000,
                                timelineEnd = System.currentTimeMillis()
                            )
                        }
                        ChartType.TODAY -> {
                            Text("Hourly Timeline (Today):", style = MaterialTheme.typography.labelMedium)
                            HourlyUsageTimelineChart(
                                usageEvents = app.hourlyUsageEvents,
                                timelineStart = getDayStart(System.currentTimeMillis()),
                                timelineEnd = System.currentTimeMillis(),
                                isFixedTimeline = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailyUsageBarChart(dailyUsage: Map<Long, Long>) {
    val maxUsage = max(1L, dailyUsage.values.maxOrNull() ?: 1L)
    val (roundedMax, yAxisValues) = calculateYAxisValues(maxUsage)

    val dayFormatter = SimpleDateFormat("E", Locale.getDefault())
    val density = LocalDensity.current
    val textPaint = remember {
        Paint().apply {
            color = Color.Gray.toArgb()
            textAlign = Paint.Align.RIGHT
            textSize = density.run { 10.sp.toPx() }
        }
    }
    val barColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .padding(top = 8.dp)
    ) {
        val yAxisWidth = 50.dp.toPx()
        val xAxisHeight = 20.dp.toPx()
        val chartAreaWidth = size.width - yAxisWidth
        val chartAreaHeight = size.height - xAxisHeight
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

        // Draw Y-Axis labels and grid lines
        yAxisValues.forEach { value ->
            val y = chartAreaHeight * (1 - (value.toFloat() / roundedMax))
            drawIntoCanvas {
                it.nativeCanvas.drawText(
                    formatUsageTime(value),
                    yAxisWidth - 8.dp.toPx(),
                    y + textPaint.fontMetrics.descent,
                    textPaint
                )
            }
            if (value > 0) { // Don't draw line at 0
                drawLine(
                    color = Color.LightGray,
                    start = Offset(yAxisWidth, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = pathEffect
                )
            }
        }

        // Draw Bars and X-Axis labels
        val calendar = Calendar.getInstance()
        val dayTimestamps = (0..6).map {
            calendar.timeInMillis.also { calendar.add(Calendar.DAY_OF_YEAR, -1) }
        }.reversed()
        val barWidth = (chartAreaWidth / 7) * 0.6f
        val barSpacing = (chartAreaWidth / 7) * 0.4f

        dayTimestamps.forEachIndexed { index, timestamp ->
            val dayStart = getDayStart(timestamp)
            val usage = dailyUsage[dayStart] ?: 0L
            val barHeight = chartAreaHeight * (usage.toFloat() / roundedMax)

            val barLeft = yAxisWidth + (barSpacing / 2) + index * (barWidth + barSpacing)

            // Draw Bar
            drawRect(
                color = barColor,
                topLeft = Offset(barLeft, chartAreaHeight - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
            )

            // Draw X-Axis Label
            drawIntoCanvas {
                val dayLabel = dayFormatter.format(Date(timestamp))
                val textBounds = android.graphics.Rect()
                val labelPaint = textPaint.apply { textAlign = Paint.Align.CENTER }
                labelPaint.getTextBounds(dayLabel, 0, dayLabel.length, textBounds)
                it.nativeCanvas.drawText(
                    dayLabel,
                    barLeft + barWidth / 2,
                    chartAreaHeight + xAxisHeight - textBounds.bottom,
                    labelPaint
                )
            }
        }

        // Draw Base Line for the chart
        drawLine(
            color = Color.Gray,
            start = Offset(yAxisWidth, chartAreaHeight),
            end = Offset(size.width, chartAreaHeight),
            strokeWidth = 1.dp.toPx()
        )
    }
}


private fun getDayStart(timestamp: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/**
 * Calculates a "nice" rounded maximum value and a list of step values for the Y-axis.
 * @return A Pair of (rounded max value, list of axis values).
 */
private fun calculateYAxisValues(maxUsage: Long): Pair<Long, List<Long>> {
    if (maxUsage == 0L) return 1L to listOf(0L)

    val hour = 3_600_000L
    val halfHour = 1_800_000L

    // Determine a "nice" step interval based on the max usage
    val step = when {
        maxUsage < hour -> halfHour / 2 // 15 min steps
        maxUsage < 3 * hour -> halfHour // 30 min steps
        maxUsage < 6 * hour -> hour // 1 hour steps
        else -> 2 * hour // 2 hour steps
    }

    val roundedMax = ceil(maxUsage.toDouble() / step).toLong() * step
    val stepsCount = (roundedMax / step).toInt().coerceAtLeast(1)
    val yAxisValues = (0..stepsCount).map { it * step }

    return roundedMax to yAxisValues
}


@Composable
fun HourlyUsageTimelineChart(
    usageEvents: List<Pair<Long, Long>>,
    timelineStart: Long,
    timelineEnd: Long,
    isFixedTimeline: Boolean = false
) {
    val density = LocalDensity.current
    val textPaint = remember {
        Paint().apply {
            color = Color.Gray.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = density.run { 9.sp.toPx() }
        }
    }
    val timelineColor = MaterialTheme.colorScheme.surfaceVariant
    val usageColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(top = 8.dp)
    ) {
        val timelineHeight = 30.dp.toPx()
        val tickHeight = 5.dp.toPx()

        // ** FIX: The scaling duration should always be a full 24 hours. **
        val scaleDuration = (24 * 60 * 60 * 1000).toFloat()

        // 1. Calculate responsive label step
        val minLabelSpacing = 35.dp.toPx()
        val maxLabels = (size.width / minLabelSpacing).toInt()
        val labelStep = when {
            maxLabels >= 24 -> 2 // e.g. 0, 2, 4...
            maxLabels >= 12 -> 4 // e.g. 0, 4, 8...
            maxLabels >= 8 -> 6  // e.g. 0, 6, 12...
            else -> 12           // e.g. 0, 12, 24
        }

        // 2. Draw timeline background
        drawRoundRect(
            color = timelineColor,
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(size.width, timelineHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
        )

        // 3. Draw usage blocks
        usageEvents.forEach { (start, end) ->
            // Check if the event overlaps with the current timeline window
            if (end > timelineStart && start < timelineEnd) {
                // Clamp the event to the timeline boundaries for drawing
                val clampedStart = max(start, timelineStart)
                val clampedEnd = min(end, timelineEnd)

                // The offset is relative to the start of the timeline window (e.g. midnight for "Today")
                val startOffset = size.width * ((clampedStart - timelineStart) / scaleDuration)
                val endOffset = size.width * ((clampedEnd - timelineStart) / scaleDuration)

                if (endOffset > startOffset) {
                    drawRect(
                        color = usageColor,
                        topLeft = Offset(startOffset, 0f),
                        size = androidx.compose.ui.geometry.Size(endOffset - startOffset, timelineHeight)
                    )
                }
            }
        }

        // 4. Draw labels and tick marks
        val endHour = Calendar.getInstance().apply { timeInMillis = timelineEnd }.get(Calendar.HOUR_OF_DAY)

        drawIntoCanvas { canvas ->
            for (hour in 0..24 step labelStep) {
                val xPos = size.width * (hour / 24f)

                // Draw tick mark
                drawLine(
                    color = Color.Gray,
                    start = Offset(xPos, timelineHeight),
                    end = Offset(xPos, timelineHeight + tickHeight),
                    strokeWidth = 1.dp.toPx()
                )

                // Draw label
                val labelHour = if (isFixedTimeline) {
                    hour
                } else {
                    // For the rolling 24h view, calculate the hour relative to the end time
                    ((endHour - 24 + hour) + 24) % 24
                }
                canvas.nativeCanvas.drawText(
                    labelHour.toString(),
                    xPos,
                    timelineHeight + tickHeight + textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent,
                    textPaint
                )
            }
        }
    }
}


/**
 * Queries the UsageStatsManager to get app usage for the last 7 days AND granular events for the last 24 hours.
 */
private fun getAppUsageStats(context: Context): List<AppUsageInfo> {
    val pm: PackageManager = context.packageManager
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    // --- 1. Get 7-Day Daily Stats ---
    val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
    val dailyStatsList: List<UsageStats> = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, calendar.timeInMillis, System.currentTimeMillis())
    val aggregatedDailyStats = mutableMapOf<String, MutableMap<Long, Long>>()
    dailyStatsList.filter { it.totalTimeInForeground > 0 }.forEach { stat ->
        val dailyMap = aggregatedDailyStats.getOrPut(stat.packageName) { mutableMapOf() }
        dailyMap[getDayStart(stat.firstTimeStamp)] = stat.totalTimeInForeground
    }

    // --- 2. Get 24-Hour Hourly Events ---
    val endTime = System.currentTimeMillis()
    val startTime = endTime - (24 * 60 * 60 * 1000)
    val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
    val hourlyAppSessions = mutableMapOf<String, MutableList<Pair<Long, Long>>>()
    val currentSessions = mutableMapOf<String, Long>()
    val event = UsageEvents.Event()
    while(usageEvents.hasNextEvent()) {
        usageEvents.getNextEvent(event)
        if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            currentSessions[event.packageName] = event.timeStamp
        } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
            currentSessions.remove(event.packageName)?.let { sessionStart ->
                hourlyAppSessions.getOrPut(event.packageName) { mutableListOf() }.add(sessionStart to event.timeStamp)
            }
        }
    }
    currentSessions.forEach { (pkg, sessionStart) ->
        hourlyAppSessions.getOrPut(pkg) { mutableListOf() }.add(sessionStart to endTime)
    }

    // --- 3. Combine Data into Final List ---
    val allPackageNames = aggregatedDailyStats.keys + hourlyAppSessions.keys
    val appUsageList = mutableListOf<AppUsageInfo>()
    for (packageName in allPackageNames) {
        try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }

            if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                val dailyUsage = aggregatedDailyStats[packageName] ?: emptyMap()
                val hourlyEvents = hourlyAppSessions[packageName] ?: emptyList()
                val totalUsage = dailyUsage.values.sum()

                if (totalUsage > 0 || hourlyEvents.isNotEmpty()) {
                    appUsageList.add(AppUsageInfo(
                        appName = appInfo.loadLabel(pm).toString(),
                        packageName = packageName,
                        totalTimeInForeground = totalUsage,
                        icon = appInfo.loadIcon(pm),
                        dailyUsage = dailyUsage,
                        hourlyUsageEvents = hourlyEvents
                    ))
                }
            }
        } catch (e: PackageManager.NameNotFoundException) { /* Ignore */ }
    }

    // The initial sort is based on the 7-day total. UI will re-sort when view changes.
    return appUsageList.sortedByDescending { it.totalTimeInForeground }
}
