package com.explapp.shortcut.automation

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class BatterySetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showSetup()
    }

    private fun showSetup() {
        val store = BatteryAutomationStore(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, 0)
        }
        container.addView(TextView(this).apply {
            text = local("Battery percentage", "نسبة البطارية")
        })
        val threshold = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(store.threshold.toString())
        }
        container.addView(threshold, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val below = RadioButton(this).apply {
            text = local("When battery falls to / below the percentage", "عندما تنخفض البطارية إلى / تحت النسبة")
            id = android.R.id.button1
        }
        val above = RadioButton(this).apply {
            text = local("When battery rises to / above the percentage", "عندما ترتفع البطارية إلى / فوق النسبة")
            id = android.R.id.button2
        }
        group.addView(below)
        group.addView(above)
        group.check(if (store.direction == BatteryDirection.BELOW) below.id else above.id)
        container.addView(group)

        val charger = CheckBox(this).apply {
            text = local("Notify when charger connects or disconnects", "تنبيه عند توصيل الشاحن أو فصله")
            isChecked = store.chargerNotifications
        }
        container.addView(charger)

        AlertDialog.Builder(this)
            .setTitle(local("Battery automation", "أتمتة البطارية"))
            .setView(container)
            .setPositiveButton(local("Enable", "تفعيل")) { _, _ ->
                val value = threshold.text.toString().toIntOrNull()?.coerceIn(1, 100) ?: 20
                store.threshold = value
                store.direction = if (group.checkedRadioButtonId == above.id) BatteryDirection.ABOVE else BatteryDirection.BELOW
                store.chargerNotifications = charger.isChecked
                store.previousLevel = currentBatteryLevel(this)
                store.enabled = true
                BatteryAutomationScheduler.schedule(this)
                Toast.makeText(this, local("Battery automation enabled", "تم تفعيل أتمتة البطارية"), Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNeutralButton(local("Disable", "إيقاف")) { _, _ ->
                store.enabled = false
                BatteryAutomationScheduler.cancel(this)
                Toast.makeText(this, local("Battery automation disabled", "تم إيقاف أتمتة البطارية"), Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun local(en: String, ar: String): String =
        if (resources.configuration.locales[0].language == "ar") ar else en
}
