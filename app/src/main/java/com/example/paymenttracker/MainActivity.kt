package com.example.paymenttracker

import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.paymenttracker.databinding.ActivityMainBinding
import com.example.paymenttracker.databinding.DialogAddPaymentBinding
import com.example.paymenttracker.databinding.DialogSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class Payment(val amount: Double, val epochDay: Long)

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private val ru = Locale("ru")
    private val dateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", ru)
    private val shortFmt = DateTimeFormatter.ofPattern("d MMM yyyy", ru)

    private val currencies = listOf(
        "UZS" to "сум", "USD" to "$", "EUR" to "€", "RUB" to "₽",
        "KZT" to "₸", "TRY" to "₺", "GBP" to "£"
    )

    private var total = 0.0
    private var initial = 0.0
    private var planned = 0.0
    private var payDay = 10
    private var currencyCode = "UZS"
    private val payments = mutableListOf<Payment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(dp(16), bars.top + dp(8), dp(16), bars.bottom + dp(16))
            b.building.bottomReserve = dp(104).toFloat() + bars.bottom
            insets
        }

        prefs = getSharedPreferences("payments", Context.MODE_PRIVATE)
        load()

        b.btnSettings.setOnClickListener { showSettings() }
        b.btnAdd.setOnClickListener { showAddPayment() }
        b.btnHistory.setOnClickListener { showHistory() }

        refresh(animate = true)
        if (total <= 0.0) b.root.post { showSettings() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------- данные ----------
    private fun load() {
        total = prefs.getString("total", null)?.toDoubleOrNull() ?: 0.0
        initial = prefs.getString("initial", null)?.toDoubleOrNull() ?: 0.0
        planned = prefs.getString("planned", null)?.toDoubleOrNull() ?: 0.0
        payDay = prefs.getInt("day", 10)
        currencyCode = prefs.getString("currency", "UZS") ?: "UZS"
        payments.clear()
        val raw = prefs.getString("payments", null)
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    payments.add(Payment(o.getDouble("a"), o.getLong("d")))
                }
            } catch (e: Exception) {
                // повреждённые данные игнорируем
            }
        }
    }

    private fun save() {
        val arr = JSONArray()
        payments.forEach { arr.put(JSONObject().put("a", it.amount).put("d", it.epochDay)) }
        prefs.edit()
            .putString("total", total.toString())
            .putString("initial", initial.toString())
            .putString("planned", planned.toString())
            .putInt("day", payDay)
            .putString("currency", currencyCode)
            .putString("payments", arr.toString())
            .apply()
    }

    private fun symbol(): String =
        currencies.firstOrNull { it.first == currencyCode }?.second ?: currencyCode

    private fun fmt(v: Double): String {
        val nf = NumberFormat.getNumberInstance(ru)
        nf.maximumFractionDigits = 2
        nf.minimumFractionDigits = 0
        return nf.format(v) + " " + symbol()
    }

    private fun plain(v: Double): String =
        if (v == floor(v)) v.toLong().toString() else String.format(Locale.US, "%.2f", v)

    private fun parse(s: CharSequence?): Double? =
        s?.toString()
            ?.replace(Regex("[\\s\\u00A0\\u202F]"), "")
            ?.replace(',', '.')
            ?.toDoubleOrNull()

    private fun paidTotal(): Double = initial + payments.sumOf { it.amount }

    private fun dateWithDay(ym: YearMonth, day: Int): LocalDate =
        ym.atDay(min(day, ym.lengthOfMonth()))

    private fun hasPaymentIn(ym: YearMonth): Boolean =
        payments.any { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == ym }

    // ---------- главный экран ----------
    private fun refresh(animate: Boolean = true) {
        if (total <= 0.0) {
            b.tvRemaining.text = "—"
            b.tvPercent.text = "Нажмите ⚙ и введите данные"
            b.tvPaid.text = ""
            b.tvTotal.text = ""
            b.progress.setProgressCompat(0, false)
            b.tvNext.text = "Платёж ещё не настроен"
            b.tvEstimate.text = ""
            b.building.setProgress(0f, false)
            return
        }

        val paid = paidTotal()
        val remaining = max(total - paid, 0.0)
        val percent = (paid / total * 100.0).coerceIn(0.0, 100.0)
        val pInt = floor(percent).toInt()

        b.building.setProgress((percent / 100.0).toFloat(), animate)
        b.tvRemaining.text = fmt(remaining)
        b.progress.setProgressCompat(pInt, true)
        b.tvPercent.text = "Дом построен на $pInt%"
        b.tvPaid.text = "Оплачено\n${fmt(paid)}"
        b.tvTotal.text = "Всего\n${fmt(total)}"

        if (remaining <= 0.0) {
            b.tvNext.text = "🎉 Всё выплачено — дом сдан!"
            b.tvEstimate.text = ""
            return
        }

        val today = LocalDate.now()
        val thisMonth = YearMonth.from(today)
        var next = dateWithDay(thisMonth, payDay)
        if (next.isBefore(today) || hasPaymentIn(YearMonth.from(next))) {
            next = dateWithDay(thisMonth.plusMonths(1), payDay)
        }
        val days = ChronoUnit.DAYS.between(today, next)
        val whenTxt = when (days) {
            0L -> "сегодня!"
            1L -> "завтра"
            else -> "через $days дн."
        }
        b.tvNext.text = "Следующий платёж: ${next.format(dateFmt)} ($whenTxt)"

        val avg = when {
            planned > 0.0 -> planned
            payments.isNotEmpty() -> payments.sumOf { it.amount } / payments.size
            else -> 0.0
        }
        if (avg > 0.0) {
            val months = ceil(remaining / avg).toInt()
            val end = dateWithDay(YearMonth.from(next).plusMonths((months - 1).toLong()), payDay)
            val label = if (planned > 0.0) "при платеже" else "при среднем платеже"
            b.tvEstimate.text =
                "≈ $months мес. до конца $label ${fmt(avg)}\nПримерно закроете: ${end.format(dateFmt)}"
        } else {
            b.tvEstimate.text = "Добавьте платежи — покажу прогноз срока"
        }
    }

    // ---------- настройки ----------
    private fun showSettings() {
        val d = DialogSettingsBinding.inflate(layoutInflater)
        if (total > 0.0) d.etTotal.setText(plain(total))
        if (initial > 0.0) d.etInitial.setText(plain(initial))
        if (planned > 0.0) d.etPlanned.setText(plain(planned))
        d.etPayDay.setText(payDay.toString())

        val items = currencies.map { "${it.first} (${it.second})" }
        var selected = currencyCode
        d.dropCurrency.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        )
        val idx = max(currencies.indexOfFirst { it.first == currencyCode }, 0)
        d.dropCurrency.setText(items[idx], false)
        d.dropCurrency.setOnItemClickListener { _, _, pos, _ ->
            selected = currencies[pos].first
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Настройки")
            .setView(d.root)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Сбросить всё") { _, _ -> confirmReset() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val t = parse(d.etTotal.text)
                val ini = parse(d.etInitial.text) ?: 0.0
                val pl = parse(d.etPlanned.text) ?: 0.0
                val day = d.etPayDay.text?.toString()?.trim()?.toIntOrNull()
                when {
                    t == null || t <= 0.0 -> d.etTotal.error = "Введите общую сумму"
                    ini < 0.0 -> d.etInitial.error = "Не может быть меньше нуля"
                    day == null || day !in 1..31 -> d.etPayDay.error = "От 1 до 31"
                    else -> {
                        total = t
                        initial = ini
                        planned = pl
                        payDay = day
                        currencyCode = selected
                        save()
                        refresh(animate = true)
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Сбросить всё?")
            .setMessage("Все настройки и платежи будут удалены.")
            .setPositiveButton("Сбросить") { _, _ ->
                prefs.edit().clear().apply()
                load()
                refresh(animate = true)
                showSettings()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------- платежи ----------
    private fun showAddPayment() {
        if (total <= 0.0) {
            Toast.makeText(this, "Сначала заполните настройки", Toast.LENGTH_SHORT).show()
            showSettings()
            return
        }
        val d = DialogAddPaymentBinding.inflate(layoutInflater)
        var date = LocalDate.now()
        d.tilAmount.suffixText = symbol()
        d.btnDate.text = "Дата: ${date.format(dateFmt)}"
        d.tvHint.text = "Осталось выплатить: ${fmt(max(total - paidTotal(), 0.0))}"
        d.btnDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, day ->
                    date = LocalDate.of(y, m + 1, day)
                    d.btnDate.text = "Дата: ${date.format(dateFmt)}"
                },
                date.year, date.monthValue - 1, date.dayOfMonth
            ).show()
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Новый платёж")
            .setView(d.root)
            .setPositiveButton("Добавить", null)
            .setNegativeButton("Отмена", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val amount = parse(d.etAmount.text)
                if (amount == null || amount <= 0.0) {
                    d.tilAmount.error = "Введите сумму"
                } else {
                    payments.add(Payment(amount, date.toEpochDay()))
                    save()
                    refresh(animate = true)
                    dialog.dismiss()
                    if (total - paidTotal() <= 0.0) {
                        Toast.makeText(this, "Поздравляю! Дом полностью оплачен 🎉", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showHistory() {
        if (payments.isEmpty()) {
            val extra = if (initial > 0.0) "Первый взнос: ${fmt(initial)}\n\n" else ""
            MaterialAlertDialogBuilder(this)
                .setTitle("История платежей")
                .setMessage(extra + "Платежей пока нет.")
                .setPositiveButton("Ок", null)
                .show()
            return
        }
        val sorted = payments.sortedByDescending { it.epochDay }
        val items = sorted.map {
            "${LocalDate.ofEpochDay(it.epochDay).format(shortFmt)}  —  ${fmt(it.amount)}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("История (нажмите — удалить)")
            .setItems(items) { _, i -> confirmDelete(sorted[i]) }
            .setPositiveButton("Закрыть", null)
            .show()
    }

    private fun confirmDelete(p: Payment) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Удалить платёж?")
            .setMessage("${LocalDate.ofEpochDay(p.epochDay).format(shortFmt)} — ${fmt(p.amount)}")
            .setPositiveButton("Удалить") { _, _ ->
                payments.remove(p)
                save()
                refresh(animate = true)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
