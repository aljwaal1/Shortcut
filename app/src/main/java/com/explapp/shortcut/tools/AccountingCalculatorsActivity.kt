package com.explapp.shortcut.tools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

class AccountingCalculatorsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { AccountingCalculatorsScreen() }
        }
    }
}

private enum class AccountingMode {
    STRAIGHT_LINE, DECLINING_BALANCE, BREAK_EVEN_UNITS, BREAK_EVEN_SALES,
    SIMPLE_INTEREST, COMPOUND_AMOUNT, ROI, CONTRIBUTION_MARGIN
}

@Composable
private fun AccountingCalculatorsScreen() {
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    var mode by remember { mutableStateOf(AccountingMode.STRAIGHT_LINE) }
    var first by remember(mode) { mutableStateOf("") }
    var second by remember(mode) { mutableStateOf("") }
    var third by remember(mode) { mutableStateOf("") }
    var result by remember(mode) { mutableStateOf<String?>(null) }
    val numberFormatter = remember { NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 2 } }
    val names = if (ar) listOf("الإهلاك بالقسط الثابت", "إهلاك الرصيد المتناقص", "نقطة التعادل بالوحدات", "نقطة التعادل بالمبيعات", "الفوائد البسيطة", "الفوائد المركبة", "العائد على الاستثمار", "هامش المساهمة %") else
        listOf("Straight-line depreciation", "Declining-balance depreciation", "Break-even units", "Break-even sales", "Simple interest", "Compound interest", "Return on investment", "Contribution margin %")
    val currentIndex = AccountingMode.entries.indexOf(mode)
    val currency = if (ar) "المبلغ (بعملتك)" else "Amount (your currency)"
    val fields = when(mode) {
        AccountingMode.STRAIGHT_LINE -> if(ar) listOf("تكلفة الأصل", "القيمة التخريدية", "العمر الإنتاجي بالسنوات") else listOf("Asset cost", "Salvage value", "Useful life in years")
        AccountingMode.DECLINING_BALANCE -> if(ar) listOf("القيمة الدفترية الحالية", "القيمة التخريدية", "معدل الإهلاك السنوي %") else listOf("Current book value", "Salvage value", "Annual depreciation rate %")
        AccountingMode.BREAK_EVEN_UNITS, AccountingMode.BREAK_EVEN_SALES -> if(ar) listOf("التكاليف الثابتة", "سعر بيع الوحدة", "التكلفة المتغيرة للوحدة") else listOf("Fixed costs", "Selling price per unit", "Variable cost per unit")
        AccountingMode.SIMPLE_INTEREST, AccountingMode.COMPOUND_AMOUNT -> if(ar) listOf("أصل المبلغ", "معدل الفائدة السنوي %", "المدة بالسنوات") else listOf("Principal", "Annual interest rate %", "Years")
        AccountingMode.ROI -> if(ar) listOf("المبلغ المسترد", "قيمة الاستثمار", "غير مطلوب") else listOf("Returned amount", "Investment", "Not needed")
        AccountingMode.CONTRIBUTION_MARGIN -> if(ar) listOf("سعر بيع الوحدة", "التكلفة المتغيرة", "غير مطلوب") else listOf("Selling price per unit", "Variable cost", "Not needed")
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if(ar) "الحاسبات المحاسبية" else "Accounting calculators", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(if(ar) "اختر الحاسبة ثم أدخل القيم. لا تُرسل البيانات إلى الإنترنت." else "Select a calculator and enter amounts. Data stays on your device.")
            AccountingMode.entries.forEachIndexed { index, item ->
                Card(onClick = { mode = item }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
                    Text(
                        text = (if(mode == item) "●  " else "○  ") + names[index],
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = if(mode == item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Text(names[currentIndex], fontWeight = FontWeight.Bold)
            OutlinedTextField(value = first, onValueChange = { first = it; result = null }, label = { Text(fields[0]) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = second, onValueChange = { second = it; result = null }, label = { Text(fields[1]) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (mode != AccountingMode.ROI && mode != AccountingMode.CONTRIBUTION_MARGIN) {
                OutlinedTextField(value = third, onValueChange = { third = it; result = null }, label = { Text(fields[2]) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Button(
                onClick = {
                    result = runCatching {
                        fun parse(value: String): Double = value.trim().replace(',', '.').toDouble()
                        val a = parse(first)
                        val b = parse(second)
                        val c = if (mode == AccountingMode.ROI || mode == AccountingMode.CONTRIBUTION_MARGIN) 0.0 else parse(third)
                        val answer = when(mode) {
                            AccountingMode.STRAIGHT_LINE -> AccountingCalculators.straightLineDepreciation(a, b, c.toInt().also { require(it.toDouble() == c) })
                            AccountingMode.DECLINING_BALANCE -> AccountingCalculators.decliningBalanceDepreciation(a, b, c / 100.0)
                            AccountingMode.BREAK_EVEN_UNITS -> AccountingCalculators.breakEvenUnits(a, b, c).toDouble()
                            AccountingMode.BREAK_EVEN_SALES -> AccountingCalculators.breakEvenSales(a, b, c)
                            AccountingMode.SIMPLE_INTEREST -> AccountingCalculators.simpleInterest(a, b / 100.0, c)
                            AccountingMode.COMPOUND_AMOUNT -> AccountingCalculators.compoundAmount(a, b / 100.0, c.toInt().also { require(it.toDouble() == c) })
                            AccountingMode.ROI -> AccountingCalculators.roiPercent(a, b)
                            AccountingMode.CONTRIBUTION_MARGIN -> AccountingCalculators.contributionMarginPercent(a, b)
                        }
                        (if(ar) "النتيجة: " else "Result: ") + numberFormatter.format(answer) +
                            (when(mode) {
                                AccountingMode.BREAK_EVEN_UNITS -> if(ar) " وحدة" else " units"
                                AccountingMode.ROI, AccountingMode.CONTRIBUTION_MARGIN -> " %"
                                else -> ""
                            })
                    }.getOrElse {
                        if(ar) "راجع الأرقام: القيم يجب أن تكون صحيحة، وسعر البيع أكبر من التكلفة المتغيرة." else
                            "Check values: enter valid numbers and make sure selling price exceeds variable cost."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if(ar) "احسب الآن" else "Calculate") }
            result?.let { message ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Text(message, modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(currency, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
