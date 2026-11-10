package com.benlee.icalcs26

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

private val Ink = Color(0xFF000000)
private val Digit = Color(0xFF303033)
private val Function = Color(0xFF626266)
private val Scientific = Color(0xFF202023)
private val Orange = Color(0xFFFF9F0A)
private val Soft = Color(0xFF9A9A9E)

private enum class CalcMode { BASIC, SCIENTIFIC, NOTES }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Color(0xFF161616), primary = Orange)) {
                CalculatorRoot()
            }
        }
    }
}

private class CalcModel(private val store: HistoryStore) {
    var expression by mutableStateOf("0")
    var lastExpression by mutableStateOf("")
    var mode by mutableStateOf(CalcMode.BASIC)
    var convert by mutableStateOf(false)
    var degrees by mutableStateOf(true)
    var second by mutableStateOf(false)
    var justCalculated by mutableStateOf(false)
    var error by mutableStateOf("")
    var memory by mutableDoubleStateOf(0.0)
    var hasMemory by mutableStateOf(false)
    var lastRepeat by mutableStateOf<Pair<String, String>?>(null)
    val history = mutableStateListOf<CalcHistory>().apply { addAll(store.load()) }

    private fun rawResult(): Double = CalcEngine.evaluate(expression, degrees)
    private fun normalize(s: String): String = s.replace(",", "")
    private fun enter(text: String) {
        if (justCalculated) { expression = text; justCalculated = false; lastExpression = "" }
        else if (expression == "0") expression = text
        else expression += text
        error = ""
    }
    private fun appendOperation(op: String) {
        if (error.isNotBlank()) { expression = "0"; error = "" }
        justCalculated = false
        if (expression.lastOrNull() in listOf('+','−','×','÷','^')) expression = expression.dropLast(1) + op
        else expression += op
    }
    fun press(label: String) {
        try {
            when (label) {
                in "0".."9" -> enter(label)
                "." -> {
                    val token = expression.takeLastWhile { it.isDigit() || it == '.' }
                    if (!token.contains('.')) enter(if (token.isEmpty()) "0." else ".")
                }
                "+", "−", "×", "÷" -> appendOperation(label)
                "⌫" -> {
                    if (justCalculated) justCalculated = false
                    expression = expression.dropLast(1).ifEmpty { "0" }
                    error = ""
                }
                "C" -> {
                    if (justCalculated || expression == "0") clearAll()
                    else {
                        val stripped = expression.replace(Regex("[0-9.]+%?$"), "")
                        expression = stripped.ifEmpty { "0" }
                    }
                    error = ""
                }
                "AC" -> clearAll()
                "±" -> {
                    if (justCalculated) justCalculated = false
                    val match = Regex("([0-9]+(?:\\.[0-9]*)?)$").find(expression)
                    if (match != null) {
                        val begin = match.range.first
                        if (begin > 0 && expression[begin-1] == '−' &&
                            (begin-1 == 0 || expression[begin-2] in "(+−×÷^"))
                            expression = expression.removeRange(begin-1, begin)
                        else expression = expression.substring(0, begin) + "−" + expression.substring(begin)
                    } else expression = "−($expression)"
                }
                "%" -> { if (expression.lastOrNull()?.isDigit() == true) expression += "%" }
                "=" -> equalsPress()
                "(", ")" -> enter(label)
                "π" -> enter("π")
                "e" -> enter("e")
                "x²" -> appendOperation("^2")
                "x³" -> appendOperation("^3")
                "xʸ" -> appendOperation("^")
                "eˣ" -> enter("exp(")
                "10ˣ" -> enter("10^(")
                "1/x" -> { expression = "1÷($expression)"; justCalculated = false }
                "²√x" -> { expression = "sqrt($expression)"; justCalculated = false }
                "³√x" -> { expression = "cbrt($expression)"; justCalculated = false }
                "ʸ√x" -> enter("^(1÷")
                "ln", "log", "sin", "cos", "tan", "sinh", "cosh", "tanh" -> {
                    val f = if (second && label in listOf("sin", "cos", "tan")) "a$label" else label
                    enter("$f(")
                }
                "x!" -> enter("!")
                "EE" -> enter("E")
                "Rand" -> enter(Random.nextDouble().toString())
                "Deg", "Rad" -> degrees = !degrees
                "2nd" -> second = !second
                "mc" -> { memory = 0.0; hasMemory = false }
                "mr" -> enter(if (hasMemory) memory.toString() else "0")
                "m+" -> { memory += rawResult(); hasMemory = true }
                "m-" -> { memory -= rawResult(); hasMemory = true }
            }
        } catch (e: Exception) { error = e.message ?: "계산 오류" }
    }
    private fun clearAll() {
        expression = "0"; lastExpression = ""; justCalculated = false
        lastRepeat = null; error = ""
    }
    private fun equalsPress() {
        val before: String
        val value: Double
        if (justCalculated && lastRepeat != null) {
            val (op, operand) = lastRepeat!!
            before = expression + op + operand
            value = CalcEngine.evaluate(before, degrees)
        } else {
            before = expression
            value = rawResult()
            val matched = Regex("([+−×÷])([0-9]+(?:\\.[0-9]+)?%?)$").find(expression)
            lastRepeat = if (matched != null) matched.groupValues[1] to matched.groupValues[2] else null
        }
        if (!value.isFinite()) throw IllegalArgumentException("계산 범위 초과")
        lastExpression = before + " ="
        expression = value.toString()
        justCalculated = true
        error = ""
        history.add(0, CalcHistory(before, CalcEngine.format(value), System.currentTimeMillis()))
        while (history.size > 200) history.removeAt(history.lastIndex)
        store.save(history)
    }
    fun restore(item: CalcHistory) {
        expression = item.expression; lastExpression = ""; justCalculated = false; error = ""
    }
    fun removeHistory(index: Int) { history.removeAt(index); store.save(history) }
    fun clearHistory() { history.clear(); store.save(history) }
    fun copyResult(context: Context) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("계산 결과", if (justCalculated) expression else CalcEngine.format(runCatching { rawResult() }.getOrDefault(0.0))))
    }
    fun value(): Double? = runCatching { rawResult() }.getOrNull()
    fun visible(): String = if (error.isNotBlank()) "오류" else if (justCalculated) CalcEngine.format(expression.toDoubleOrNull() ?: Double.NaN)
        else if (expression == "0") "0" else expression
}

@Composable
private fun CalculatorRoot() {
    val context = LocalContext.current
    val model = remember { CalcModel(HistoryStore(context)) }
    val notesStore = remember { NotesStore(context) }
    var noteText by remember { mutableStateOf(notesStore.load()) }
    var showHistory by remember { mutableStateOf(false) }
    var showModes by remember { mutableStateOf(false) }
    var categoryIndex by remember { mutableIntStateOf(0) }
    var fromIndex by remember { mutableIntStateOf(0) }
    var toIndex by remember { mutableIntStateOf(1) }
    var rateSnapshot by remember { mutableStateOf<RateSnapshot?>(null) }
    var loadingRates by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    fun tap(key: String) { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); model.press(key) }
    fun openMode(mode: CalcMode) { model.mode = mode; showModes = false; showHistory = false }

    LaunchedEffect(model.convert) {
        if (model.convert && rateSnapshot == null) {
            loadingRates = true
            rateSnapshot = CurrencyRates.refresh(context)
            loadingRates = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Ink)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(45.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleTool(if (showHistory) "‹" else "◷") { showHistory = !showHistory }
            Box {
                CircleTool("▦") { showModes = true }
                DropdownMenu(expanded = showModes, onDismissRequest = { showModes = false },
                    modifier = Modifier.background(Color(0xFF242426))) {
                    DropdownMenuItem(text = { Text("✓  일반 계산기", color = Color.White) }, onClick = { openMode(CalcMode.BASIC) })
                    DropdownMenuItem(text = { Text("ƒ(x)  공학용 계산기", color = Color.White) }, onClick = { openMode(CalcMode.SCIENTIFIC) })
                    DropdownMenuItem(text = { Text("∑  수학 메모", color = Color.White) }, onClick = { openMode(CalcMode.NOTES) })
                    HorizontalDivider(color = Color.DarkGray)
                    DropdownMenuItem(text = { Text("⇄  단위·환율 변환  ${if (model.convert) "✓" else ""}", color = Color.White) },
                        onClick = { model.convert = !model.convert; if (model.mode == CalcMode.NOTES) model.mode = CalcMode.BASIC; showModes = false })
                }
            }
        }
        when {
            showHistory -> HistoryPage(model, onClose = { showHistory = false })
            model.mode == CalcMode.NOTES -> NotesPage(noteText, onChange = {
                noteText = it; notesStore.save(it)
            })
            else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val landscape = maxWidth > maxHeight
                val scientific = model.mode == CalcMode.SCIENTIFIC
                val rows = if (scientific && !landscape) 10 else 5
                val budget = (maxHeight - (if (model.convert) 170.dp else if (scientific) 96.dp else 148.dp))
                val keyHeight = minOf(
                    if (landscape) 56.dp else if (scientific) (maxWidth - 60.dp) / 6 else (maxWidth - 48.dp) / 4,
                    (budget / rows - 6.dp).coerceAtLeast(25.dp)
                )
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                    if (model.convert) {
                        ConversionPanel(model, categoryIndex, onCategory = { new ->
                            categoryIndex = new; fromIndex = 0; toIndex = 1
                        }, fromIndex, { fromIndex = it }, toIndex, { toIndex = it }, rateSnapshot, loadingRates,
                            onRefresh = {
                                scope.launch { loadingRates = true; rateSnapshot = CurrencyRates.refresh(context); loadingRates = false }
                            })
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(end = 5.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.End
                        ) {
                            if (model.lastExpression.isNotBlank()) {
                                Text(model.lastExpression, fontSize = 16.sp, color = Soft, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                            }
                            val digits = model.visible().length
                            val fontSize = if (scientific) {
                                when { digits > 18 -> 24.sp; digits > 12 -> 30.sp; else -> 45.sp }
                            } else when { digits > 19 -> 28.sp; digits > 13 -> 38.sp; digits > 9 -> 56.sp; else -> 78.sp }
                            Text(text = model.visible(), color = Color.White, fontSize = fontSize,
                                fontWeight = FontWeight.Light, maxLines = 2, textAlign = TextAlign.End,
                                lineHeight = fontSize * 1.10f, modifier = Modifier.fillMaxWidth()
                                    .pointerInput(model.expression) { detectTapGestures(onLongPress = { model.copyResult(context) }) })
                            if (model.error.isNotBlank()) Text(model.error, color = Orange, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    if (scientific && landscape) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column(modifier = Modifier.weight(1.28f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ScientificRows(model, keyHeight, ::tap)
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                BasicRows(model, keyHeight, ::tap)
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (scientific) ScientificRows(model, keyHeight, ::tap)
                            BasicRows(model, keyHeight, ::tap)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleTool(label: String, onClick: () -> Unit) {
    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Scientific).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Medium)
    }
}

private val basicRows = listOf(
    listOf("⌫", "C", "%", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf("±", "0", ".", "=")
)

private val scientificRows = listOf(
    listOf("(", ")", "mc", "m+", "m-", "mr"),
    listOf("2nd", "x²", "x³", "xʸ", "eˣ", "10ˣ"),
    listOf("1/x", "²√x", "³√x", "ʸ√x", "ln", "log"),
    listOf("x!", "sin", "cos", "tan", "e", "EE"),
    listOf("Rand", "sinh", "cosh", "tanh", "π", "Deg")
)

@Composable
private fun BasicRows(model: CalcModel, height: Dp, onKey: (String) -> Unit) {
    basicRows.forEach { row ->
        KeyRow(row, height, model, onKey, isScientific = false)
    }
}

@Composable
private fun ScientificRows(model: CalcModel, height: Dp, onKey: (String) -> Unit) {
    scientificRows.forEach { row -> KeyRow(row, height, model, onKey, isScientific = true) }
}

@Composable
private fun KeyRow(keys: List<String>, height: Dp, model: CalcModel, onKey: (String) -> Unit, isScientific: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { token ->
            val label = when {
                token == "Deg" -> if (model.degrees) "Deg" else "Rad"
                token == "C" && model.expression == "0" -> "AC"
                token == "sin" && model.second -> "sin⁻¹"
                token == "cos" && model.second -> "cos⁻¹"
                token == "tan" && model.second -> "tan⁻¹"
                else -> token
            }
            val isOp = token in listOf("÷", "×", "−", "+", "=")
            val isFunc = token in listOf("⌫", "C", "±", "%", "AC")
            val selected = isOp && token != "=" && model.expression.endsWith(token)
            val bg = when {
                selected -> Color.White
                isOp -> Orange
                isFunc -> Function
                isScientific -> Scientific
                else -> Digit
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                    .background(bg).clickable { onKey(if (token == "C" && model.expression == "0") "AC" else token) },
                contentAlignment = Alignment.Center) {
                Text(text = label, color = if (selected) Orange else Color.White,
                    fontSize = if (isScientific) (if (label.length > 3) 12.sp else 16.sp) else (if (label == "AC" || label == "⌫") 26.sp else 32.sp),
                    fontWeight = FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HistoryPage(model: CalcModel, onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 25.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("최근 계산", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("모두 삭제", color = Orange, modifier = Modifier.clickable { model.clearHistory() })
        }
        Spacer(Modifier.height(22.dp))
        if (model.history.isEmpty()) Text("계산 기록이 없습니다", color = Soft, modifier = Modifier.padding(top = 70.dp).fillMaxWidth(),
            textAlign = TextAlign.Center)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(model.history, key = { _, item -> item.timestamp }) { index, entry ->
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1C1C1E)).clickable { model.restore(entry); onClose() }
                    .padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(entry.timestamp)), color = Soft, fontSize = 12.sp)
                        Text(entry.expression, color = Soft, fontSize = 16.sp, maxLines = 2, textAlign = TextAlign.End)
                        Text(entry.result, color = Color.White, fontSize = 29.sp, maxLines = 1)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("×", color = Soft, fontSize = 25.sp,
                        modifier = Modifier.clip(CircleShape).clickable { model.removeHistory(index) }.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun UnitDropdown(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1D1D20)).clickable { show = true }.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(title, color = Soft, fontSize = 11.sp)
            Text((options.getOrNull(selected) ?: "선택") + "  ▾", color = Color.White, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = show, onDismissRequest = { show = false },
            modifier = Modifier.background(Color(0xFF2B2B2E)).heightIn(max = 360.dp)) {
            options.forEachIndexed { index, name ->
                DropdownMenuItem(text = { Text(name, color = Color.White) }, onClick = { onPick(index); show = false })
            }
        }
    }
}

@Composable
private fun ConversionPanel(
    model: CalcModel, categoryIndex: Int, onCategory: (Int) -> Unit,
    fromIndex: Int, onFrom: (Int) -> Unit, toIndex: Int, onTo: (Int) -> Unit,
    rates: RateSnapshot?, loading: Boolean, onRefresh: () -> Unit
) {
    val category = Converter.categories[categoryIndex]
    val from = category.units[fromIndex.coerceIn(category.units.indices)]
    val to = category.units[toIndex.coerceIn(category.units.indices)]
    val value = model.value()
    val output = value?.let { Converter.convert(it, category, from, to, rates?.rates ?: emptyMap()) }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            UnitDropdown("유형", Converter.categories.map { it.name }, categoryIndex, onCategory, Modifier.weight(0.8f))
            UnitDropdown("기준", category.units.map { it.symbol }, fromIndex, onFrom, Modifier.weight(1f))
            UnitDropdown("변환", category.units.map { it.symbol }, toIndex, onTo, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("${model.visible()} ${from.symbol}", color = Color.White, fontSize = 24.sp,
                maxLines = 1, modifier = Modifier.weight(1f))
            Text("⇄", color = Orange, fontSize = 24.sp,
                modifier = Modifier.clickable { onFrom(toIndex); onTo(fromIndex) }.padding(4.dp))
        }
        Text(if (output != null) "${CalcEngine.format(output)} ${to.symbol}" else "— ${to.symbol}",
            color = Orange, fontSize = 27.sp, fontWeight = FontWeight.Light, maxLines = 1)
        if (category.name == "환율") {
            Text(when {
                loading -> "유럽중앙은행 환율 확인 중…"
                rates == null -> "환율 조회 실패 · 인터넷 연결을 확인하세요"
                rates.isCached -> "ECB ${rates.date} 기준 저장 환율 · 최신값이 아닐 수 있음  ↻"
                else -> "ECB ${rates.date} 기준 환율  ↻"
            }, color = Soft, fontSize = 11.sp,
                modifier = Modifier.clickable(onClick = onRefresh))
        }
    }
}

@Composable
private fun NotesPage(text: String, onChange: (String) -> Unit) {
    val results = remember(text) { MathNotesEngine.resolve(text) }
    val graphExpr = remember(text) { Regex("(?m)^\\s*y\\s*=\\s*(.+)$").find(text)?.groupValues?.getOrNull(1) }
    Column(modifier = Modifier.fillMaxSize().padding(top = 22.dp).verticalScroll(rememberScrollState())) {
        Text("수학 메모", fontSize = 30.sp, color = Color.White, fontWeight = FontWeight.Bold)
        Text("계산식을 입력하면 즉시 계산합니다. x = 3 같은 변수도 사용할 수 있습니다.",
            fontSize = 13.sp, color = Soft, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
        BasicTextField(value = text, onValueChange = onChange,
            textStyle = TextStyle(color = Color.White, fontSize = 22.sp, lineHeight = 33.sp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1D1D1F)).padding(16.dp),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text("125 × 1.15 =\nx = 3\nx^2 + 4 =\ny = x^2", color = Soft, fontSize = 20.sp)
                    inner()
                }
            })
        Spacer(Modifier.height(20.dp))
        results.forEach { (expr, result) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(expr, color = Soft, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 2)
                Text(result, color = Orange, fontSize = 17.sp, maxLines = 1)
            }
        }
        if (graphExpr != null) {
            Spacer(Modifier.height(20.dp))
            Text("2D 그래프 · y = $graphExpr", color = Color.White, fontSize = 16.sp)
            Graph(graphExpr)
        }
        Spacer(Modifier.height(16.dp))
        Text("손글씨 인식·3D 그래프는 현재 미구현입니다.", color = Soft, fontSize = 12.sp)
    }
}

@Composable
private fun Graph(formula: String) {
    Canvas(modifier = Modifier.fillMaxWidth().height(245.dp).padding(vertical = 14.dp)
        .clip(RoundedCornerShape(16.dp)).background(Color(0xFF171719))) {
        val center = Offset(size.width * .5f, size.height * .5f)
        val scale = size.width / 14f
        drawLine(Color.DarkGray, Offset(0f, center.y), Offset(size.width, center.y), 1f)
        drawLine(Color.DarkGray, Offset(center.x, 0f), Offset(center.x, size.height), 1f)
        var prev: Offset? = null
        var pixel = 0
        while (pixel <= size.width.toInt()) {
            val x = (pixel - center.x) / scale
            val y = try { CalcEngine.evaluate(formula, variables = mapOf("x" to x.toDouble())) } catch (_: Exception) { Double.NaN }
            if (y.isFinite() && abs(y) < 100) {
                val point = Offset(pixel.toFloat(), center.y - y.toFloat() * scale)
                prev?.let { old -> if (abs(old.y - point.y) < size.height) drawLine(Orange, old, point, 2.5f) }
                prev = point
            } else prev = null
            pixel += 2
        }
    }
}
