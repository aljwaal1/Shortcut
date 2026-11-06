package com.explapp.shortcut.tools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

class AccountingCalculatorsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AccountingCalculatorsScreen() } }
    }
}

private enum class AccountingMode {
    STRAIGHT_LINE, DECLINING_BALANCE, BREAK_EVEN_UNITS, BREAK_EVEN_SALES,
    SIMPLE_INTEREST, COMPOUND_AMOUNT, ROI, CONTRIBUTION_MARGIN,
    CURRENT_RATIO, QUICK_RATIO, GROSS_MARGIN, INVENTORY_TURNOVER,
    MARKUP, SAFETY_MARGIN, REORDER_POINT, WEIGHTED_AVERAGE_COST
}

private fun labels(mode: AccountingMode, ar: Boolean): List<String> = when (mode) {
    AccountingMode.STRAIGHT_LINE -> if (ar) listOf("تكلفة الأصل", "القيمة التخريدية", "العمر الإنتاجي بالسنوات") else listOf("Asset cost", "Salvage value", "Useful life in years")
    AccountingMode.DECLINING_BALANCE -> if (ar) listOf("القيمة الدفترية الحالية", "القيمة التخريدية", "معدل الإهلاك السنوي %") else listOf("Current book value", "Salvage value", "Annual depreciation rate %")
    AccountingMode.BREAK_EVEN_UNITS, AccountingMode.BREAK_EVEN_SALES -> if (ar) listOf("التكاليف الثابتة", "سعر بيع الوحدة", "التكلفة المتغيرة للوحدة") else listOf("Fixed costs", "Selling price per unit", "Variable cost per unit")
    AccountingMode.SIMPLE_INTEREST, AccountingMode.COMPOUND_AMOUNT -> if (ar) listOf("أصل المبلغ", "معدل الفائدة السنوي %", "المدة بالسنوات") else listOf("Principal", "Annual interest rate %", "Years")
    AccountingMode.ROI -> if (ar) listOf("المبلغ المسترد", "قيمة الاستثمار") else listOf("Returned amount", "Investment")
    AccountingMode.CONTRIBUTION_MARGIN -> if (ar) listOf("سعر بيع الوحدة", "التكلفة المتغيرة للوحدة") else listOf("Selling price per unit", "Variable cost per unit")
    AccountingMode.CURRENT_RATIO -> if (ar) listOf("الأصول المتداولة", "الالتزامات المتداولة") else listOf("Current assets", "Current liabilities")
    AccountingMode.QUICK_RATIO -> if (ar) listOf("الأصول المتداولة", "المخزون", "الالتزامات المتداولة") else listOf("Current assets", "Inventory", "Current liabilities")
    AccountingMode.GROSS_MARGIN -> if (ar) listOf("صافي المبيعات", "تكلفة البضاعة المباعة") else listOf("Net sales", "Cost of goods sold")
    AccountingMode.INVENTORY_TURNOVER -> if (ar) listOf("تكلفة البضاعة المباعة", "متوسط المخزون") else listOf("Cost of goods sold", "Average inventory")
    AccountingMode.MARKUP -> if (ar) listOf("تكلفة الصنف", "سعر بيعه") else listOf("Item cost", "Selling price")
    AccountingMode.SAFETY_MARGIN -> if (ar) listOf("المبيعات الفعلية", "مبيعات نقطة التعادل") else listOf("Actual sales", "Break-even sales")
    AccountingMode.REORDER_POINT -> if (ar) listOf("الطلب اليومي بالوحدات", "مهلة التوريد بالأيام", "مخزون الأمان") else listOf("Daily demand in units", "Lead time in days", "Safety stock")
    AccountingMode.WEIGHTED_AVERAGE_COST -> if (ar) listOf("كمية الدفعة الأولى", "تكلفة وحدة الدفعة الأولى", "كمية الدفعة الثانية", "تكلفة وحدة الدفعة الثانية") else listOf("First batch quantity", "First batch unit cost", "Second batch quantity", "Second batch unit cost")
}

private fun parseCalculatorNumber(raw: String): Double {
    val normalized = raw.trim().replace('٫', '.').replace('٬', ',').replace(',', '.')
        .map { char ->
            when (char) {
                in '٠'..'٩' -> ('0'.code + char.code - '٠'.code).toChar()
                in '۰'..'۹' -> ('0'.code + char.code - '۰'.code).toChar()
                else -> char
            }
        }.joinToString("")
    return normalized.toDoubleOrNull()?.takeIf(Double::isFinite)
        ?: throw IllegalArgumentException("Invalid finite number")
}

private fun calculate(mode: AccountingMode, values: List<String>): Double {
    val a = parseCalculatorNumber(values[0])
    val b = parseCalculatorNumber(values[1])
    val c = if (labels(mode, false).size == 3) parseCalculatorNumber(values[2]) else 0.0
    val third = if (labels(mode, false).size >= 3) parseCalculatorNumber(values[2]) else 0.0
    val fourth = if (labels(mode, false).size >= 4) parseCalculatorNumber(values[3]) else 0.0
    return when (mode) {
        AccountingMode.STRAIGHT_LINE -> AccountingCalculators.straightLineDepreciation(a, b, c.toInt().also { require(it.toDouble() == c) })
        AccountingMode.DECLINING_BALANCE -> AccountingCalculators.decliningBalanceDepreciation(a, b, c / 100.0)
        AccountingMode.BREAK_EVEN_UNITS -> AccountingCalculators.breakEvenUnits(a, b, c).toDouble()
        AccountingMode.BREAK_EVEN_SALES -> AccountingCalculators.breakEvenSales(a, b, c)
        AccountingMode.SIMPLE_INTEREST -> AccountingCalculators.simpleInterest(a, b / 100.0, c)
        AccountingMode.COMPOUND_AMOUNT -> AccountingCalculators.compoundAmount(a, b / 100.0, c.toInt().also { require(it.toDouble() == c) })
        AccountingMode.ROI -> AccountingCalculators.roiPercent(a, b)
        AccountingMode.CONTRIBUTION_MARGIN -> AccountingCalculators.contributionMarginPercent(a, b)
        AccountingMode.CURRENT_RATIO -> AccountingCalculators.currentRatio(a, b)
        AccountingMode.QUICK_RATIO -> AccountingCalculators.quickRatio(a, b, third)
        AccountingMode.GROSS_MARGIN -> AccountingCalculators.grossProfitMargin(a, b)
        AccountingMode.INVENTORY_TURNOVER -> AccountingCalculators.inventoryTurnover(a, b)
        AccountingMode.MARKUP -> AccountingCalculators.markupPercent(a, b)
        AccountingMode.SAFETY_MARGIN -> AccountingCalculators.marginOfSafetyPercent(a, b)
        AccountingMode.REORDER_POINT -> AccountingCalculators.reorderPoint(a, b, third)
        AccountingMode.WEIGHTED_AVERAGE_COST -> AccountingCalculators.weightedAverageCost(a, b, third, fourth)
    }
}

@Composable
private fun AccountingCalculatorsScreen() {
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    var mode by remember { mutableStateOf<AccountingMode?>(null) }
    var step by remember(mode) { mutableIntStateOf(0) }
    val values = remember(mode) { mutableStateListOf("", "", "", "") }
    var error by remember(mode) { mutableStateOf<String?>(null) }
    var answer by remember(mode) { mutableStateOf<Double?>(null) }
    val names = if (ar) listOf(
        "الإهلاك بالقسط الثابت", "إهلاك الرصيد المتناقص", "نقطة التعادل بالوحدات",
        "نقطة التعادل بالمبيعات", "الفوائد البسيطة", "الفوائد المركبة",
        "العائد على الاستثمار", "هامش المساهمة %",
        "نسبة التداول", "نسبة السيولة السريعة", "هامش مجمل الربح",
        "معدل دوران المخزون", "نسبة الزيادة على التكلفة", "هامش الأمان",
        "نقطة إعادة الطلب", "متوسط تكلفة المخزون المرجّح"
    ) else listOf(
        "Straight-line depreciation", "Declining-balance depreciation", "Break-even units",
        "Break-even sales", "Simple interest", "Compound interest",
        "Return on investment", "Contribution margin %",
        "Current ratio", "Quick ratio", "Gross profit margin",
        "Inventory turnover", "Markup %", "Margin of safety %",
        "Reorder point", "Weighted average inventory cost"
    )
    val current = mode
    val fields = current?.let { labels(it, ar) }.orEmpty()
    val formatter = remember { NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 2 } }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(if (ar) "الحاسبات المحاسبية" else "Accounting calculators",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (current == null) {
                Text(if (ar) "اختر الحاسبة للبدء بخطوات بسيطة. جميع الحسابات تتم على جهازك." else
                    "Choose a calculator to begin. All calculations stay on your device.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                AccountingMode.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        pair.forEach { item ->
                            val index = AccountingMode.entries.indexOf(item)
                            val bg = when (index % 3) {
                                0 -> MaterialTheme.colorScheme.primaryContainer
                                1 -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.tertiaryContainer
                            }
                            Card(
                                onClick = { mode = item },
                                modifier = Modifier.weight(1f).height(118.dp),
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = bg)
                            ) {
                                Column(modifier = Modifier.fillMaxSize().padding(16.dp),
                                    verticalArrangement = Arrangement.SpaceBetween) {
                                    Text(listOf("◈", "▣", "◉", "◆", "∑", "◇", "%", "▤", "÷", "◎", "▥", "⇄", "↗", "△", "▦", "∑")[index],
                                        style = MaterialTheme.typography.titleLarge)
                                    Text(names[index], style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            } else {
                val count = fields.size
                val selectedName = names[AccountingMode.entries.indexOf(current)]
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(selectedName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { mode = null }) {
                        Text(if (ar) "تغيير" else "Change")
                    }
                }
                if (step < count) {
                    Text(if (ar) "الخطوة ${step + 1} من $count" else "Step ${step + 1} of $count",
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = { (step + 1).toFloat() / count }, modifier = Modifier.fillMaxWidth())
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text(fields[step], style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold)
                            Text(if (ar) "أدخل القيمة ثم اضغط التالي" else "Enter the value, then tap Next",
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            OutlinedTextField(
                                value = values[step],
                                onValueChange = { values[step] = it; error = null },
                                label = { Text(fields[step]) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = error != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                shape = RoundedCornerShape(20.dp)
                            )
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { if (step > 0) step-- else mode = null; error = null },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (ar) "السابق" else "Back") }
                        Button(
                            onClick = {
                                try {
                                    parseCalculatorNumber(values[step])
                                    if (step == count - 1) {
                                        answer = calculate(current, values)
                                        step = count
                                    } else {
                                        step++
                                    }
                                    error = null
                                } catch (_: IllegalArgumentException) {
                                    error = if (ar) "أدخل رقمًا صحيحًا وتحقق من القيم." else "Enter a valid number and check your values."
                                } catch (_: ArithmeticException) {
                                    error = if (ar) "تعذر الحساب بهذه القيم." else "These values cannot be calculated."
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (step == count - 1) (if (ar) "احسب" else "Calculate") else (if (ar) "التالي" else "Next")) }
                    }
                } else {
                    Text(if (ar) "اكتمل الحساب" else "Calculation complete",
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(26.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (ar) "النتيجة" else "Result", style = MaterialTheme.typography.titleMedium)
                            val suffix = when (current) {
                                AccountingMode.BREAK_EVEN_UNITS -> if (ar) " وحدة" else " units"
                                AccountingMode.ROI, AccountingMode.CONTRIBUTION_MARGIN,
                                AccountingMode.GROSS_MARGIN, AccountingMode.MARKUP,
                                AccountingMode.SAFETY_MARGIN -> " %"
                                AccountingMode.REORDER_POINT -> if (ar) " وحدة" else " units"
                                else -> ""
                            }
                            Text(formatter.format(answer ?: 0.0) + suffix,
                                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            Text(if (ar) "يمكنك الرجوع لتعديل أي قيمة." else "You can go back to edit any value.")
                        }
                    }
                    OutlinedButton(onClick = { step = count - 1; answer = null }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (ar) "تعديل الأرقام" else "Edit values")
                    }
                    Button(onClick = { mode = null }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (ar) "حساب جديد" else "New calculation")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
