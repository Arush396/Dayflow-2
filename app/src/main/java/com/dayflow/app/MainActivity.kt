package com.dayflow.app

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val accents = listOf(0xFF7C8CFF, 0xFF2DD4BF, 0xFFFB7185, 0xFFF59E0B)
private val dayOrder = listOf(2, 3, 4, 5, 6, 7, 1)
private val dayNames = mapOf(1 to "Sun", 2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat")

private val Poppins = FontFamily(
    Font(R.font.poppins_light, FontWeight.Light), Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium), Font(R.font.poppins_bold, FontWeight.Bold))

private fun poppinsTypography(): Typography {
    val b = Typography(); val f = Poppins
    return Typography(
        displayLarge = b.displayLarge.copy(fontFamily = f), displayMedium = b.displayMedium.copy(fontFamily = f),
        displaySmall = b.displaySmall.copy(fontFamily = f), headlineLarge = b.headlineLarge.copy(fontFamily = f),
        headlineMedium = b.headlineMedium.copy(fontFamily = f), headlineSmall = b.headlineSmall.copy(fontFamily = f),
        titleLarge = b.titleLarge.copy(fontFamily = f), titleMedium = b.titleMedium.copy(fontFamily = f),
        titleSmall = b.titleSmall.copy(fontFamily = f), bodyLarge = b.bodyLarge.copy(fontFamily = f),
        bodyMedium = b.bodyMedium.copy(fontFamily = f), bodySmall = b.bodySmall.copy(fontFamily = f),
        labelLarge = b.labelLarge.copy(fontFamily = f), labelMedium = b.labelMedium.copy(fontFamily = f),
        labelSmall = b.labelSmall.copy(fontFamily = f))
}

class MainActivity : ComponentActivity() {
    private var openId by mutableIntStateOf(-1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openId = intent.getIntExtra("id", -1)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        val store = Store(this)
        setContent { DayFlowApp(store, openId) { openId = -1 } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); openId = intent.getIntExtra("id", -1) }
}

fun fmt(min: Int, is24: Boolean): String {
    val h = min / 60; val m = min % 60
    return if (is24) "%02d:%02d".format(h, m) else "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
}

fun daysLabel(d: Set<Int>) = when {
    d.size == 7 -> "Every day"
    d == setOf(2, 3, 4, 5, 6) -> "Weekdays"
    d == setOf(1, 7) -> "Weekends"
    else -> dayOrder.filter { it in d }.joinToString(" ") { dayNames[it]!! }
}

fun emojiFor(title: String): String {
    val s = title.lowercase()
    fun has(vararg k: String) = k.any { it in s }
    return when {
        has("wake") -> "⏰"
        has("sleep", "bed") -> "🌙"
        has("study", "read", "class", "homework") -> "📚"
        has("gym", "exercise", "workout", "run", "walk", "yoga") -> "💪"
        has("breakfast", "lunch", "dinner", "eat", "meal") -> "🍽️"
        has("water", "drink") -> "💧"
        has("work", "meeting", "office") -> "💼"
        has("break", "rest", "relax") -> "☕"
        else -> "🗓️"
    }
}

@Composable
fun DayFlowApp(store: Store, openId: Int, consume: () -> Unit) {
    val ctx = LocalContext.current
    var dark by remember { mutableStateOf(store.dark) }
    var is24 by remember { mutableStateOf(store.is24h) }
    var accent by remember { mutableIntStateOf(store.accent) }
    var entries by remember { mutableStateOf(store.loadOrSeed()) }
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        Scheduler.scheduleAll(ctx, entries)
        while (true) { delay(15_000); now = System.currentTimeMillis() }
    }
    LaunchedEffect(openId) {
        if (openId >= 0) { entries.firstOrNull { it.id == openId }?.let { editing = it; tab = 0 }; consume() }
    }

    fun commit(list: List<Entry>) {
        entries.filter { o -> list.none { it.id == o.id } }.forEach { Scheduler.cancel(ctx, it.id) }
        entries = list; store.save(list); Scheduler.scheduleAll(ctx, list)
    }

    val a = Color(accents[accent])
    val bg = if (dark) Color(0xFF0B0D12) else Color(0xFFF4F5F9)
    val card = if (dark) Color(0xFF161A23) else Color.White
    val ink = if (dark) Color(0xFFEDEFF5) else Color(0xFF12151C)
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = a, onPrimary = if (dark) Color(0xFF0B0D12) else Color.White,
        background = bg, surface = bg, onBackground = ink, onSurface = ink, surfaceVariant = card,
        onSurfaceVariant = if (dark) Color(0xFF9AA3B5) else Color(0xFF5B6475))

    MaterialTheme(colorScheme = scheme, typography = poppinsTypography()) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            Scaffold(
                containerColor = bg,
                bottomBar = {
                    NavigationBar(containerColor = card) {
                        val c = NavigationBarItemDefaults.colors(indicatorColor = a.copy(alpha = 0.18f), selectedIconColor = a, selectedTextColor = a)
                        NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Home, null) }, label = { Text("Today") }, colors = c)
                        NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.DateRange, null) }, label = { Text("All") }, colors = c)
                        NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") }, colors = c)
                    }
                },
                floatingActionButton = {
                    if (tab != 2) ExtendedFloatingActionButton(
                        text = { Text("Add", fontWeight = FontWeight.Medium) },
                        icon = { Icon(Icons.Default.Add, null) },
                        onClick = { editing = Entry(store.nextId(), "", 9 * 60, 60, (1..7).toSet()) },
                        containerColor = a, contentColor = scheme.onPrimary, shape = RoundedCornerShape(18.dp))
                }
            ) { pad ->
                Box(Modifier.padding(pad)) {
                    when (tab) {
                        0 -> TodayScreen(entries, now, is24, a, { e, key ->
                            commit(entries.map {
                                if (it.id == e.id) it.copy(doneDates = if (key in it.doneDates) it.doneDates - key else it.doneDates + key) else it
                            })
                        }) { editing = it }
                        1 -> AllScreen(entries, is24, { e, on -> commit(entries.map { if (it.id == e.id) it.copy(enabled = on) else it }) }) { editing = it }
                        else -> SettingsScreen(dark, is24, accent,
                            { dark = it; store.dark = it }, { is24 = it; store.is24h = it }, { accent = it; store.accent = it })
                    }
                }
            }
            editing?.let { e ->
                EntryDialog(e, entries.none { it.id == e.id }, is24,
                    onSave = { s ->
                        commit(if (entries.any { it.id == s.id }) entries.map { if (it.id == s.id) s else it } else entries + s)
                        editing = null; Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
                    },
                    onDuplicate = { s ->
                        commit(entries + s.copy(id = store.nextId(), title = s.title + " (copy)", doneDates = emptySet()))
                        editing = null; Toast.makeText(ctx, "Duplicated", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = { s -> commit(entries.filter { it.id != s.id }); editing = null; Toast.makeText(ctx, "Deleted", Toast.LENGTH_SHORT).show() },
                    onClose = { editing = null })
            }
        }
    }
}

@Composable
fun TodayScreen(entries: List<Entry>, now: Long, is24: Boolean, accent: Color, onToggle: (Entry, String) -> Unit, onEdit: (Entry) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val key = dateKey(cal)
    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val today = entries.filter { it.enabled && cal.get(Calendar.DAY_OF_WEEK) in it.days }.sortedBy { it.startMin }
    val done = today.count { key in it.doneDates }
    val next = today.firstOrNull { it.startMin > nowMin && key !in it.doneDates }
    val greeting = when (cal.get(Calendar.HOUR_OF_DAY)) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val cs = MaterialTheme.colorScheme
    val deep = lerp(accent, Color(0xFF1B1F3B), 0.55f)

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 110.dp)) {
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(accent, deep))).padding(22.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(greeting, color = Color.White.copy(alpha = 0.85f))
                            Text(fmt(nowMin, is24), fontSize = 38.sp, fontWeight = FontWeight.Light, color = Color.White, maxLines = 1)
                            Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(cal.time), fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(progress = { if (today.isEmpty()) 0f else done / today.size.toFloat() },
                                modifier = Modifier.fillMaxSize(), color = Color.White, trackColor = Color.White.copy(alpha = 0.25f), strokeWidth = 6.dp)
                            Text("$done/${today.size}", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(if (next != null) "Up next · ${emojiFor(next.title)} ${next.title} at ${fmt(next.startMin, is24)}" else "You're all caught up",
                        color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
        item { Text("Today's schedule", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        if (today.isEmpty()) item { Text("Nothing planned today. Tap Add to create your first activity.", color = cs.onSurfaceVariant) }
        items(today, key = { it.id }) { e ->
            val isDone = key in e.doneDates
            val current = nowMin in e.startMin until e.startMin + e.durationMin
            val fg = if (current) cs.onPrimary else cs.onSurface
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmt(e.startMin, is24), Modifier.width(62.dp), fontSize = 12.sp, color = cs.onSurfaceVariant)
                Row(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (current) cs.primary else cs.surfaceVariant)
                    .clickable { onEdit(e) }.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(if (current) Color.White.copy(alpha = 0.25f) else cs.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center) { Text(emojiFor(e.title), fontSize = 20.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).alpha(if (isDone) 0.5f else 1f)) {
                        Text(e.title, fontWeight = FontWeight.Medium, color = fg, textDecoration = if (isDone) TextDecoration.LineThrough else null)
                        Text(if (current) "Now · until ${fmt((e.startMin + e.durationMin) % 1440, is24)}" else "${e.durationMin} min",
                            fontSize = 12.sp, color = fg.copy(alpha = 0.7f))
                    }
                    Box(Modifier.size(48.dp).clip(CircleShape).clickable { onToggle(e, key) }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(26.dp).clip(CircleShape)
                            .background(if (isDone) (if (current) Color.White else cs.primary) else Color.Transparent)
                            .border(2.dp, fg.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                            if (isDone) Text("✓", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (current) cs.primary else cs.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AllScreen(entries: List<Entry>, is24: Boolean, onEnable: (Entry, Boolean) -> Unit, onEdit: (Entry) -> Unit) {
    val cs = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 110.dp)) {
        item { Text("All activities", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (entries.isEmpty()) item { Text("No activities yet. Tap Add to create one.", color = cs.onSurfaceVariant) }
        items(entries.sortedBy { it.startMin }, key = { it.id }) { e ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceVariant).clickable { onEdit(e) }
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(cs.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Text(emojiFor(e.title), fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, fontWeight = FontWeight.Medium)
                    Text("${fmt(e.startMin, is24)} · ${daysLabel(e.days)}", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                Switch(checked = e.enabled, onCheckedChange = { onEnable(e, it) })
            }
        }
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp), content = content)

@Composable
fun SettingsScreen(dark: Boolean, is24: Boolean, accent: Int, onDark: (Boolean) -> Unit, on24: (Boolean) -> Unit, onAccent: (Int) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val needExact = Build.VERSION.SDK_INT >= 31 && !ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Dark theme", Modifier.weight(1f)); Switch(dark, onDark) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("24-hour time", Modifier.weight(1f)); Switch(is24, on24) }
        }
        SettingsCard {
            Text("Accent color")
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                accents.forEachIndexed { i, c ->
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color(c)).clickable { onAccent(i) }, contentAlignment = Alignment.Center) {
                        if (i == accent) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        SettingsCard {
            Text("Alarms")
            Text("Activities with 'Ring like an alarm' play a loud alarm sound until you tap the notification.", fontSize = 12.sp, color = cs.onSurfaceVariant)
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    Toast.makeText(ctx, "Allow notifications for DayFlow in your phone settings first", Toast.LENGTH_LONG).show()
                else Notifier.show(ctx, Entry(9999, "Test alarm", 0, 1, emptySet()))
            }, shape = RoundedCornerShape(14.dp)) { Text("Test alarm now") }
            if (needExact) {
                Text("Exact alarms are off, so reminders may arrive a few minutes late.", fontSize = 12.sp)
                OutlinedButton(onClick = { ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) },
                    shape = RoundedCornerShape(14.dp)) { Text("Allow exact reminders") }
            }
        }
        Text("DayFlow 1.1 · All data stays on this device.", fontSize = 12.sp, color = cs.onSurfaceVariant)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.height(44.dp).clip(RoundedCornerShape(12.dp))
        .background(if (selected) cs.primary else cs.onSurface.copy(alpha = 0.08f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, maxLines = 1, color = if (selected) cs.onPrimary else cs.onSurface)
    }
}

@Composable
fun EntryDialog(initial: Entry, isNew: Boolean, is24: Boolean, onSave: (Entry) -> Unit, onDuplicate: (Entry) -> Unit,
                onDelete: (Entry) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var title by remember { mutableStateOf(initial.title) }
    var start by remember { mutableIntStateOf(initial.startMin) }
    var dur by remember { mutableIntStateOf(initial.durationMin) }
    var days by remember { mutableStateOf(initial.days) }
    var before by remember { mutableIntStateOf(initial.remindBefore) }
    var alarm by remember { mutableStateOf(initial.alarm) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun build() = initial.copy(title = title.trim(), startMin = start, durationMin = dur, days = days, remindBefore = before, alarm = alarm)
    val valid = title.isNotBlank() && days.isNotEmpty()
    val every = (1..7).toSet(); val weekdays = setOf(2, 3, 4, 5, 6); val weekend = setOf(1, 7)

    AlertDialog(
        onDismissRequest = onClose, containerColor = cs.surfaceVariant, shape = RoundedCornerShape(28.dp),
        title = { Text(if (isNew) "New activity" else "Edit activity", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("What are you doing?") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { TimePickerDialog(ctx, { _, h, m -> start = h * 60 + m }, start / 60, start % 60, is24).show() },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Starts at ${fmt(start, is24)}") }
                Text("How long", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30 to "30m", 60 to "1h", 90 to "1.5h", 120 to "2h").forEach { (v, l) -> Pill(l, dur == v, Modifier.weight(1f)) { dur = v } }
                }
                Text("Repeat", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Every day", days == every, Modifier.weight(1f)) { days = every }
                    Pill("Weekdays", days == weekdays, Modifier.weight(1f)) { days = weekdays }
                    Pill("Weekends", days == weekend, Modifier.weight(1f)) { days = weekend }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayOrder.forEach { d -> Pill(dayNames[d]!!.take(1), d in days, Modifier.weight(1f)) { days = if (d in days) days - d else days + d } }
                }
                Text("Remind me (minutes before)", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 5, 10, 15, 30).forEach { v -> Pill("$v", before == v, Modifier.weight(1f)) { before = v } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ring like an alarm")
                        Text("Loud sound until you tap it", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                    Switch(alarm, { alarm = it })
                }
                if (!isNew) Row {
                    TextButton(onClick = { onDuplicate(build()) }, enabled = valid) { Text("Duplicate") }
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = cs.error) }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(build()) }, enabled = valid, shape = RoundedCornerShape(14.dp)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = cs.surfaceVariant,
        title = { Text("Delete activity?") },
        text = { Text("“${initial.title}” will be removed and its reminders cancelled.") },
        confirmButton = { TextButton(onClick = { onDelete(initial) }) { Text("Delete", color = cs.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } }
    )
}
