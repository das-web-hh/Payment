package com.example.paymenttracker

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.paymenttracker.databinding.ActivityMainBinding
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("payments", Context.MODE_PRIVATE)
        loadSaved()

        binding.btnCalculate.setOnClickListener { calculate(save = true) }
        binding.btnPaid.setOnClickListener { confirmPaid() }
        binding.btnReset.setOnClickListener { confirmReset() }
    }

    private fun loadSaved() {
        binding.etTotal.setText(prefs.getString("total", ""))
        binding.etRemaining.setText(prefs.getString("remaining", ""))
        binding.etMonthly.setText(prefs.getString("monthly", ""))
        binding.etPayDay.setText(prefs.getString("day", ""))
        if (!binding.etTotal.text.isNullOrBlank()) calculate(save = false)
    }

    private fun parseNumber(text: String?): Double? =
        text?.trim()?.replace(" ", "")?.replace(",", ".")?.toDoubleOrNull()

    private fun money(value: Double): String =
        String.format(Locale("ru"), "%,.2f", value)

    private fun calculate(save: Boolean) {
        val total = parseNumber(binding.etTotal.text?.toString())
        val monthly = parseNumber(binding.etMonthly.text?.toString())
        val day = binding.etPayDay.text?.toString()?.trim()?.toIntOrNull()
        val remainingInput = parseNumber(binding.etRemaining.text?.toString())

        if (total == null || total <= 0) return error("Введите общую сумму")
        if (monthly == null || monthly <= 0) return error("Введите платёж в месяц")
        if (day == null || day !in 1..31) return error("День оплаты должен быть от 1 до 31")

        val remaining = min(max(remainingInput ?: total, 0.0), total)
        val paid = total - remaining
        val percent = (paid / total * 100).coerceIn(0.0, 100.0)

        if (save) {
            prefs.edit()
                .putString("total", binding.etTotal.text.toString())
                .putString("remaining", binding.etRemaining.text.toString())
                .putString("monthly", binding.etMonthly.text.toString())
                .putString("day", day.toString())
                .apply()
        }

        binding.progress.setProgressCompat(percent.toInt(), true)

        if (remaining <= 0.0) {
            binding.tvResult.text = "✅ Всё выплачено!\n\nОбщая сумма: ${money(total)}\nОплачено: ${money(paid)}"
            return
        }

        val monthsLeft = ceil(remaining / monthly).toInt()
        val lastPayment = remaining - (monthly * (monthsLeft - 1))

        val today = LocalDate.now()
        var nextDate = dateWithDay(YearMonth.from(today), day)
        if (nextDate.isBefore(today)) {
            nextDate = dateWithDay(YearMonth.from(today).plusMonths(1), day)
        }
        val endDate = dateWithDay(YearMonth.from(nextDate).plusMonths((monthsLeft - 1).toLong()), day)
        val daysUntil = ChronoUnit.DAYS.between(today, nextDate)

        val nextPaymentAmount = min(monthly, remaining)

        binding.tvResult.text = buildString {
            appendLine("Общая сумма: ${money(total)}")
            appendLine("Оплачено: ${money(paid)} (${String.format(Locale("ru"), "%.1f", percent)}%)")
            appendLine("Осталось: ${money(remaining)}")
            appendLine()
            appendLine("Платёж в месяц: ${money(monthly)}")
            appendLine("Осталось месяцев: $monthsLeft")
            if (monthsLeft > 1 && lastPayment < monthly - 0.005) {
                appendLine("Последний платёж: ${money(lastPayment)}")
            }
            appendLine()
            appendLine("Следующий платёж: ${money(nextPaymentAmount)}")
            appendLine("Дата: ${nextDate.format(dateFormat)}")
            appendLine(
                when (daysUntil) {
                    0L -> "⚠️ Платить нужно сегодня!"
                    1L -> "Завтра"
                    else -> "Через $daysUntil дн."
                }
            )
            appendLine()
            append("Полностью закроете: ${endDate.format(dateFormat)}")
        }
    }

    private fun dateWithDay(month: YearMonth, day: Int): LocalDate =
        month.atDay(min(day, month.lengthOfMonth()))

    private fun error(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun confirmPaid() {
        val total = parseNumber(binding.etTotal.text?.toString())
        val monthly = parseNumber(binding.etMonthly.text?.toString())
        if (total == null || monthly == null || monthly <= 0) {
            return error("Сначала заполните сумму и платёж")
        }
        AlertDialog.Builder(this)
            .setTitle("Отметить оплату")
            .setMessage("Вычесть ${money(monthly)} из оставшейся суммы?")
            .setPositiveButton("Да") { _, _ ->
                val current = parseNumber(binding.etRemaining.text?.toString()) ?: total
                val newRemaining = max(current - monthly, 0.0)
                binding.etRemaining.setText(
                    String.format(Locale.US, "%.2f", newRemaining).removeSuffix(".00")
                )
                calculate(save = true)
            }
            .setNegativeButton("Нет", null)
            .show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Сбросить всё?")
            .setMessage("Все данные будут удалены.")
            .setPositiveButton("Сбросить") { _, _ ->
                prefs.edit().clear().apply()
                binding.etTotal.setText("")
                binding.etRemaining.setText("")
                binding.etMonthly.setText("")
                binding.etPayDay.setText("")
                binding.progress.setProgressCompat(0, false)
                binding.tvResult.setText(R.string.result_hint)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
