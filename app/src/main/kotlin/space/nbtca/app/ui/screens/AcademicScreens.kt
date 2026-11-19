package space.nbtca.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.nbtca.app.core.academic.AcademicSnapshot
import space.nbtca.app.core.academic.AcademicStore
import space.nbtca.app.core.academic.TimetableCourse
import space.nbtca.app.core.academic.TimetablePeriod
import space.nbtca.app.core.academic.timetableCurrentWeek
import space.nbtca.app.core.academic.timetableWeekOne
import space.nbtca.app.core.academic.toTimetableDate
import space.nbtca.app.core.parse.SchoolWeek
import space.nbtca.app.ui.components.ContentCard
import space.nbtca.app.ui.theme.AppThemeColors
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AcademicScaffold(
    title: String,
    store: AcademicStore,
    onBack: () -> Unit,
    preferredTerm: (List<AcademicSnapshot>) -> AcademicSnapshot? = { it.firstOrNull() },
    content: @Composable (AcademicSnapshot) -> Unit
) {
    var snapshots by remember(store) { mutableStateOf(store.loadSnapshots()) }
    var selectedTermKey by rememberSaveable { mutableStateOf<String?>(null) }
    var termMenu by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val snapshot = snapshots.firstOrNull { it.termKey == selectedTermKey } ?: preferredTerm(snapshots)

    // 只做提示，不自动拉取：是否同步由用户点「同步」决定
    fun requestSync() {
        // 同步页本身带登录能力：没会话时就在那里登录，不再拦截
        error = null
        showSync = true
    }

    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { requestSync() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "同步教务数据")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppThemeColors.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (snapshot != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        TextButton(onClick = { termMenu = true }) {
                            Text(snapshot.displayTerm, modifier = Modifier.weight(1f, fill = false))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "切换学期")
                        }
                        DropdownMenu(expanded = termMenu, onDismissRequest = { termMenu = false }) {
                            snapshots.sortedByDescending { it.displayTerm }.forEach { term ->
                                DropdownMenuItem(
                                    text = { Text(term.displayTerm) },
                                    onClick = {
                                        selectedTermKey = term.termKey
                                        termMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = "更新于 ${formatSyncTime(snapshot.updatedAt)}",
                        modifier = Modifier.padding(end = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppThemeColors.textSecondary
                    )
                }
            }
            error?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (snapshot == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有本地教务数据", color = AppThemeColors.textSecondary)
                        Button(
                            onClick = { requestSync() },
                            modifier = Modifier.padding(top = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("立即同步") }
                    }
                }
            } else {
                key(snapshot.termKey) { content(snapshot) }
            }
        }
    }

    if (showSync) {
        AcademicSyncScreen(
            store = store,
            onBack = {
                snapshots = store.loadSnapshots()
                showSync = false
            },
            onSynced = { warning ->
                snapshots = store.loadSnapshots()
                error = warning
                showSync = false
            }
        )
    }
}

private val AcademicSnapshot.displayTerm: String
    get() = termLabel.ifBlank { "已同步学期" }

private fun AcademicSnapshot.calendarMonday(weekInfo: SchoolWeek?): LocalDate? =
    weekInfo?.firstMonday(termLabel, term?.academicYear, term?.semester)

private fun AcademicSnapshot.weekCount(weekInfo: SchoolWeek?): Int = maxOf(
    1,
    if (calendarMonday(weekInfo) != null) weekInfo?.total ?: 0 else 0,
    timetableDays.maxOfOrNull { it.week } ?: 0,
    timetable.maxOfOrNull { it.weeks.maxOrNull() ?: 0 } ?: 0
)

private fun AcademicSnapshot.currentWeek(today: LocalDate, weekInfo: SchoolWeek?): Int? {
    val calendar = calendarMonday(weekInfo)
    val firstMonday = timetableWeekOne(timetableDays, calendar) ?: calendar
    return timetableDays.firstOrNull { it.date == today.toString() }?.week
        ?: timetableCurrentWeek(today, firstMonday, weekCount(weekInfo))
}

private fun formatSyncTime(epoch: Long): String {
    if (epoch <= 0L) return "-"
    val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
    return fmt.format(java.util.Date(epoch))
}

@Composable
fun TimetableScreen(
    apiClient: space.nbtca.app.core.network.ApiClient,
    store: AcademicStore,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var weekInfo by remember { mutableStateOf<SchoolWeek?>(null) }
    LaunchedEffect(Unit) { weekInfo = apiClient.getSchoolWeek() }
    val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))

    AcademicScaffold(
        title = "课表",
        store = store,
        onBack = onBack,
        preferredTerm = { snapshots ->
            snapshots.firstOrNull { it.currentWeek(today, weekInfo) != null } ?: snapshots.firstOrNull()
        }
    ) { snapshot ->
        val courses = snapshot.timetable
        val firstMonday = remember(snapshot, weekInfo) {
            timetableWeekOne(snapshot.timetableDays, snapshot.calendarMonday(weekInfo))
        }
        val dates = remember(snapshot) { snapshot.timetableDays.associateBy { it.week to it.weekday } }
        val totalWeeks = snapshot.weekCount(weekInfo)
        val currentWeek = snapshot.currentWeek(today, weekInfo)
        val periodCount = maxOf(
            10,
            snapshot.timetablePeriods.keys.maxOrNull() ?: 0,
            courses.maxOfOrNull { it.endPeriod } ?: 0
        ).coerceAtMost(99)

        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = (currentWeek ?: 1) - 1,
            pageCount = { totalWeeks }
        )
        // 校历是异步取回的；拿到当前周后跳到对应页
        LaunchedEffect(currentWeek) {
            if (currentWeek != null && pagerState.currentPage == 0 && !pagerState.isScrollInProgress) {
                pagerState.scrollToPage(currentWeek - 1)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // 周次切换条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                    }
                }, enabled = pagerState.currentPage > 0) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "上一周")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "第 ${pagerState.currentPage + 1} 周 / 共 $totalWeeks 周",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (currentWeek != null) {
                        TextButton(
                            enabled = pagerState.currentPage != currentWeek - 1,
                            onClick = { scope.launch { pagerState.animateScrollToPage(currentWeek - 1) } }
                        ) { Text("当前第 $currentWeek 周") }
                    } else {
                        val weekOne = firstMonday ?: snapshot.calendarMonday(weekInfo)
                        Text(
                            text = when {
                                weekOne == null -> "当前周次暂不可用"
                                today.isBefore(weekOne) -> "本学期尚未开始"
                                else -> "本学期已结束"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage + 1).coerceAtMost(totalWeeks - 1)
                        )
                    }
                }, enabled = pagerState.currentPage < totalWeeks - 1) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "下一周")
                }
            }

            if (courses.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("本学期暂无课表数据", color = AppThemeColors.textSecondary)
                }
                return@Column
            }

            if (snapshot.timetablePeriods.isEmpty()) {
                Text(
                    "暂无节次时间，可尝试同步",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val week = page + 1
                val weekCourses = courses.filter { course ->
                    course.weeks.isEmpty() || course.weeks.contains(week)
                }
                val weekDates = WEEKDAY_LABELS.indices.map { index ->
                    dates[week to (index + 1)]?.date?.toTimetableDate()
                        ?: firstMonday?.plusWeeks(page.toLong())?.plusDays(index.toLong())
                }
                TimetableGrid(weekCourses, snapshot.timetablePeriods, periodCount, weekDates, today)
            }
        }
    }
}

private val timetableDateFormat = DateTimeFormatter.ofPattern("MM/dd")

@Composable
private fun TimetableGrid(
    courses: List<TimetableCourse>,
    periods: Map<Int, TimetablePeriod>,
    periodCount: Int,
    dates: List<LocalDate?>,
    today: LocalDate
) {
    val colors = MaterialTheme.colorScheme
    val types = MaterialTheme.typography
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val periodWidth = 42.dp * fontScale.coerceIn(1f, 1.5f)
    val headerHeight = 52.dp * fontScale.coerceAtLeast(1f)
    val line = colors.outlineVariant.copy(alpha = 0.55f)
    val headerText = types.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum")
    var selected by remember { mutableStateOf<List<TimetableCourse>?>(null) }
    val byDay = remember(courses) { courses.groupBy { it.weekday } }

    BoxWithConstraints(
        Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerLow)
    ) {
        val rowHeight = with(density) {
            ((maxHeight - headerHeight) / periodCount)
                .coerceAtLeast(62.dp * fontScale.coerceAtLeast(1f)).roundToPx().toDp()
        }
        val gridHeight = rowHeight * periodCount

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(headerHeight).background(colors.surfaceContainer)) {
                Box(Modifier.width(periodWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text("节次", style = types.labelMedium, color = colors.onSurfaceVariant)
                }
                WEEKDAY_LABELS.forEachIndexed { index, weekday ->
                    val date = dates[index]
                    val isToday = date == today
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .background(if (isToday) colors.primaryContainer else colors.surfaceContainer)
                            .drawBehind { drawLine(line, Offset.Zero, Offset(0f, size.height), 1.dp.toPx()) },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            weekday, style = types.labelMedium, maxLines = 1,
                            color = if (isToday) colors.onPrimaryContainer else colors.onSurface
                        )
                        Text(
                            date?.format(timetableDateFormat) ?: "—", style = headerText, maxLines = 1,
                            color = if (isToday) colors.onPrimaryContainer else colors.onSurfaceVariant
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(gridHeight)) {
                    Canvas(Modifier.matchParentSize()) {
                        for (period in 0 until periodCount) {
                            val y = rowHeight.toPx() * period
                            drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        }
                    }
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.width(periodWidth)) {
                            for (period in 1..periodCount) {
                                Column(
                                    Modifier.fillMaxWidth().height(rowHeight),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(period.toString(), style = types.labelLarge, color = colors.onSurface)
                                    periods[period]?.let { time ->
                                        Text(time.start, style = headerText, color = colors.onSurfaceVariant, maxLines = 1)
                                        Text(time.end, style = headerText, color = colors.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                            }
                        }
                        for (day in 1..WEEKDAY_LABELS.size) {
                            Box(
                                Modifier.weight(1f).height(gridHeight)
                                    .drawBehind { drawLine(line, Offset.Zero, Offset(0f, size.height), 1.dp.toPx()) }
                            ) {
                                val blocks = remember(byDay, day, periodCount) {
                                    courseBlocks(byDay[day].orEmpty(), periodCount)
                                }
                                blocks.forEach { block ->
                                    CourseBlock(
                                        block.courses,
                                        roomLines = if (block.start == block.end) 2 else 5,
                                        modifier = Modifier.fillMaxWidth()
                                            .offset(y = rowHeight * (block.start - 1))
                                            .height(rowHeight * (block.end - block.start + 1))
                                            .padding(2.dp)
                                    ) { selected = block.courses }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { entries ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(entries.singleOrNull()?.name ?: "同一时段的课程") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    entries.forEach { course ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (entries.size > 1) Text(course.name, style = types.titleSmall)
                            Text("教室：${course.location.ifBlank { "待定" }}", color = colors.onSurface)
                            val date = dates.getOrNull(course.weekday - 1)?.format(timetableDateFormat)
                            val day = WEEKDAY_LABELS.getOrNull(course.weekday - 1)
                            val start = periods[course.startPeriod]?.start
                            val end = periods[course.endPeriod]?.end
                            val hours = if (start != null && end != null) "$start–$end" else null
                            Text(listOfNotNull(date, day, course.periodText, hours).joinToString(" · "))
                            if (course.teacher.isNotBlank()) Text("教师：${course.teacher}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("关闭") } }
        )
    }
}

private data class TimetableBlock(val start: Int, var end: Int, val courses: MutableList<TimetableCourse>)

private fun courseBlocks(courses: List<TimetableCourse>, periodCount: Int): List<TimetableBlock> {
    val blocks = mutableListOf<TimetableBlock>()
    courses.filter { it.startPeriod in 1..periodCount && it.endPeriod >= it.startPeriod }
        .sortedBy { it.startPeriod }
        .forEach { course ->
            val end = course.endPeriod.coerceAtMost(periodCount)
            val previous = blocks.lastOrNull()
            if (previous != null && course.startPeriod <= previous.end) {
                previous.end = maxOf(previous.end, end)
                previous.courses.add(course)
            } else {
                blocks.add(TimetableBlock(course.startPeriod, end, mutableListOf(course)))
            }
        }
    return blocks
}

@Composable
private fun CourseBlock(courses: List<TimetableCourse>, roomLines: Int, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, ink, accent) = when (Math.floorMod(courses.first().name.hashCode(), 3)) {
        0 -> Triple(colors.primaryContainer, colors.onPrimaryContainer, colors.primary)
        1 -> Triple(colors.secondaryContainer, colors.onSecondaryContainer, colors.secondary)
        else -> Triple(colors.tertiaryContainer, colors.onTertiaryContainer, colors.tertiary)
    }
    val title = courses.singleOrNull()?.name ?: "${courses.size} 门课程"
    val rooms = courses.map { it.location.ifBlank { "待定" } }.distinct().joinToString("\n")

    Box(modifier.clip(RoundedCornerShape(8.dp)).background(container).clickable(role = Role.Button, onClick = onClick)) {
        Box(
            Modifier.align(Alignment.CenterStart).padding(vertical = 5.dp).width(2.dp)
                .fillMaxHeight().background(accent, RoundedCornerShape(4.dp))
        )
        Column(
            Modifier.fillMaxSize().padding(start = 4.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Unweighted room text measures first. The course title uses the height left over.
            Text(
                title, modifier = Modifier.weight(1f, fill = false), fontSize = 12.sp,
                lineHeight = 15.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold,
                overflow = TextOverflow.Ellipsis, color = ink
            )
            Text(
                rooms, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.sp,
                maxLines = roomLines, overflow = TextOverflow.Ellipsis, color = ink
            )
        }
    }
}

/** [1,2,3,5,6] → "1-3,5-6" */
private fun compressWeeks(weeks: List<Int>): String {
    if (weeks.isEmpty()) return ""
    val sorted = weeks.distinct().sorted()
    val parts = mutableListOf<String>()
    var start = sorted.first()
    var prev = start
    for (index in 1 until sorted.size) {
        val week = sorted[index]
        if (week == prev + 1) {
            prev = week
            continue
        }
        parts.add(if (start == prev) "$start" else "$start-$prev")
        start = week
        prev = week
    }
    parts.add(if (start == prev) "$start" else "$start-$prev")
    return parts.joinToString(", ")
}

@Composable
fun ExamsScreen(store: AcademicStore, onBack: () -> Unit) {
    AcademicScaffold(title = "考试查询", store = store, onBack = onBack) { snapshot ->
        val exams = snapshot.exams
        if (exams.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("本学期的考试安排为空", color = AppThemeColors.textSecondary)
            }
            return@AcademicScaffold
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(exams) { exam ->
                ContentCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = exam.courseName,
                            modifier = Modifier.weight(1f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppThemeColors.textPrimary
                        )
                        if (exam.examName.isNotEmpty()) {
                            Text(exam.examName, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (exam.timeText.isNotEmpty()) {
                        Text(exam.timeText, modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp, color = AppThemeColors.textSecondary)
                    }
                    val extra = listOf(
                        if (exam.location.isNotEmpty()) "地点 ${exam.location}" else "",
                        if (exam.seat.isNotEmpty()) "座位 ${exam.seat}" else "",
                        if (exam.assessment.isNotEmpty()) exam.assessment else ""
                    ).filter { it.isNotEmpty() }
                    if (extra.isNotEmpty()) {
                        Text(extra.joinToString(" · "), modifier = Modifier.padding(top = 2.dp), fontSize = 11.sp, color = AppThemeColors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun GradesScreen(store: AcademicStore, onBack: () -> Unit) {
    AcademicScaffold(title = "成绩查询", store = store, onBack = onBack) { snapshot ->
        val grades = snapshot.grades
        if (grades.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("本学期暂无成绩记录", color = AppThemeColors.textSecondary)
            }
            return@AcademicScaffold
        }
        val points = grades.mapNotNull { it.gradePoint.toDoubleOrNull() }
        val credits = grades.mapNotNull { it.credits.toDoubleOrNull() }
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                ContentCard {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("课程数", fontSize = 11.sp, color = AppThemeColors.textSecondary)
                            Text(grades.size.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppThemeColors.textPrimary)
                        }
                        Column {
                            Text("总学分", fontSize = 11.sp, color = AppThemeColors.textSecondary)
                            Text(
                                text = if (credits.isEmpty()) "-" else String.format("%.1f", credits.sum()),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppThemeColors.textPrimary
                            )
                        }
                        Column {
                            Text("平均绩点", fontSize = 11.sp, color = AppThemeColors.textSecondary)
                            Text(
                                text = if (points.isEmpty()) "-" else String.format("%.2f", points.average()),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            items(grades) { grade ->
                ContentCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = grade.courseName,
                            modifier = Modifier.weight(1f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppThemeColors.textPrimary
                        )
                        Text(
                            text = grade.grade.ifEmpty { "-" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (grade.voided) AppThemeColors.textSecondary else MaterialTheme.colorScheme.primary
                        )
                    }
                    val meta = listOf(
                        if (grade.credits.isNotEmpty()) "${grade.credits} 学分" else "",
                        if (grade.gradePoint.isNotEmpty()) "绩点 ${grade.gradePoint}" else "",
                        if (grade.nature.isNotEmpty()) grade.nature else "",
                        if (grade.voided) "已作废" else ""
                    ).filter { it.isNotEmpty() }
                    if (meta.isNotEmpty()) {
                        Text(meta.joinToString(" · "), modifier = Modifier.padding(top = 4.dp), fontSize = 11.sp, color = AppThemeColors.textSecondary)
                    }
                }
            }
        }
    }
}
