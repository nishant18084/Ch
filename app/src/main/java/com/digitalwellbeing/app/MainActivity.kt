package com.digitalwellbeing.app

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class AppUsage(val name: String, val packageName: String, val timeMs: Long)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WellbeingApp() }
    }
}

@Composable
private fun WellbeingApp() {
    val context = LocalContext.current
    var darkMode by remember { mutableStateOf(false) }
    var hasAccess by remember { mutableStateOf(hasUsageAccess(context)) }
    var usages by remember { mutableStateOf(emptyList<AppUsage>()) }

    fun refresh() {
        hasAccess = hasUsageAccess(context)
        usages = if (hasAccess) readTodayUsage(context) else emptyList()
    }

    LaunchedEffect(Unit) { refresh() }

    MaterialTheme(colorScheme = if (darkMode) darkColorScheme() else lightColorScheme()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                    NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.BarChart, null) }, label = { Text("Reports") })
                    NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.CenterFocusStrong, null) }, label = { Text("Focus") })
                    NavigationBarItem(selected = false, onClick = { darkMode = !darkMode }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Theme") })
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 22.dp, bottom = 24.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Good day 👋", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Digital Wellbeing", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { darkMode = !darkMode }) {
                            Icon(if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle theme")
                        }
                    }
                }
                item {
                    ScreenTimeCard(totalMs = usages.sumOf { it.timeMs }, hasAccess = hasAccess,
                        onGrant = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
                }
                item {
                    Text("Today's activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    UsageChart()
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Most used apps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${usages.size} apps", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!hasAccess) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Usage access is needed", fontWeight = FontWeight.SemiBold)
                                Text("App usage data stays on this phone. Allow Usage Access in Android settings to show real statistics.")
                                Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                                    Text("Grant Usage Access")
                                }
                            }
                        }
                    }
                } else if (usages.isEmpty()) {
                    item { Text("No usage data yet. Open a few apps and return here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(usages.take(12), key = { it.packageName }) { usage ->
                        AppUsageRow(usage)
                    }
                }
                item {
                    OutlinedButton(onClick = { refresh() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Refresh usage")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenTimeCard(totalMs: Long, hasAccess: Boolean, onGrant: () -> Unit) {
    val hours = TimeUnit.MILLISECONDS.toHours(totalMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(totalMs) % 60
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timelapse, null)
                Spacer(Modifier.width(8.dp))
                Text("DAILY SCREEN TIME", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Text(if (hasAccess) "${hours}h ${minutes}m" else "--h --m", fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Text(if (hasAccess) "Today's recorded device app usage" else "Allow Usage Access to see your screen time")
            if (!hasAccess) Button(onClick = onGrant) { Text("Set up access") }
        }
    }
}

@Composable
private fun UsageChart() {
    val values = listOf(0.25f, 0.55f, 0.35f, 0.72f, 0.48f, 0.88f, 0.62f)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Weekly overview", fontWeight = FontWeight.SemiBold)
            Text("Sample preview · weekly history will be added next", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(105.dp)) {
                val gap = size.width / (values.size * 2 - 1)
                values.forEachIndexed { index, value ->
                    val x = gap / 2 + index * gap * 2
                    drawLine(
                        color = androidx.compose.ui.graphics.Color(0xFF4776E6),
                        start = Offset(x, size.height),
                        end = Offset(x, size.height * (1f - value)),
                        strokeWidth = 12f,
                        cap = StrokeCap.Round
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun AppUsageRow(usage: AppUsage) {
    val mins = TimeUnit.MILLISECONDS.toMinutes(usage.timeMs)
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Apps, null)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(usage.name, fontWeight = FontWeight.SemiBold)
            Text(usage.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m", fontWeight = FontWeight.Bold)
    }
}

private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun readTodayUsage(context: Context): List<AppUsage> {
    val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val end = System.currentTimeMillis()
    return manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        .orEmpty()
        .filter { it.totalTimeInForeground > 0L && it.packageName != context.packageName }
        .map { stat ->
            val appName = try {
                context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(stat.packageName, 0)).toString()
            } catch (_: Exception) { stat.packageName }
            AppUsage(appName, stat.packageName, stat.totalTimeInForeground)
        }
        .sortedByDescending { it.timeMs }
}
