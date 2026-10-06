package app.clockweather

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Две страницы со снапом: 0 = пресеты, 1 = тонкая настройка. */
class Pager(c: Context, private val onPage: (Int) -> Unit) : HorizontalScrollView(c) {
    private var flung = false
    init { isHorizontalScrollBarEnabled = false; overScrollMode = OVER_SCROLL_NEVER; isFillViewport = true }

    private fun near() = if (width > 0 && scrollX > width / 2) 1 else 0
    fun go(p: Int) = smoothScrollTo(p * width, 0)

    override fun fling(v: Int) { flung = true; go(if (v > 500) 1 else if (v < -500) 0 else near()) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) flung = false
        val r = super.onTouchEvent(e)
        if ((e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) && !flung) go(near())
        return r
    }

    override fun onScrollChanged(l: Int, t: Int, ol: Int, ot: Int) {
        super.onScrollChanged(l, t, ol, ot); onPage(near())
    }
}

class MainActivity : Activity() {
    private var cfg = Cfg()
    private var dens = 1f
    private lateinit var holder: FrameLayout
    private var scroll: ScrollView? = null

    private val BG = 0xFF121317.toInt()
    private val ACCENT = 0xFF4FC3F7.toInt()
    private val TXT = 0xFFFFFFFF.toInt()
    private val PAL = intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFC83D.toInt(), 0xFFFF5A5F.toInt(),
        0xFF4FC3F7.toInt(), 0xFF81C784.toInt(), 0xFFBA68C8.toInt(), 0xFFFF8A3D.toInt())

    private fun dp(v: Int) = (v * dens).toInt()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        dens = resources.displayMetrics.density
        // запуск как экран настройки при добавлении виджета
        val wid = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0) ?: 0
        if (wid != 0) setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid))

        cfg = Cfg.load(this)
        RefreshJob.schedule(this)
        Refresh.async(this, false, null) // «при открытии»: обновит, только если данные старше ~20 ч

        val sp = Store.p(this)
        if (cfg.eventsOn && !sp.getBoolean("asked", false) &&
            checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            sp.edit().putBoolean("asked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 1)
        }

        val tabs = ArrayList<TextView>()
        lateinit var pager: Pager
        listOf("Пресеты", "Настройки").forEachIndexed { i, name ->
            tabs.add(tv(name, 16f, TXT).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(14)); typeface = Typeface.DEFAULT_BOLD
                setOnClickListener { pager.go(i) }
            })
        }
        pager = Pager(this) { p -> for (i in 0..1) tabs[i].alpha = if (i == p) 1f else .45f }
        tabs[1].alpha = .45f

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        val bar = LinearLayout(this)
        tabs.forEach { bar.addView(it, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) }
        root.addView(bar)

        val w = resources.displayMetrics.widthPixels
        val row = LinearLayout(this)
        row.addView(presetsPage(), LinearLayout.LayoutParams(w, MATCH_PARENT))
        holder = FrameLayout(this)
        buildSettings().also { scroll = it; holder.addView(it) }
        row.addView(holder, LinearLayout.LayoutParams(w, MATCH_PARENT))
        pager.addView(row, FrameLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT))
        root.addView(pager, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onStop() { super.onStop(); ClockWidget.renderAll(this) }

    override fun onRequestPermissionsResult(rc: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(rc, perms, res)
        if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) Refresh.async(this, true, null)
    }

    private fun commit() { cfg.save(this); ClockWidget.renderAll(this) }

    private fun rebuild() {
        val y = scroll?.scrollY ?: 0
        holder.removeAllViews()
        val s = buildSettings(); scroll = s; holder.addView(s)
        s.post { s.scrollY = y }
    }

    private fun tv(t: String, sp: Float, col: Int) = TextView(this).apply { text = t; textSize = sp; setTextColor(col) }

    // ---------------- Страница 1: пресеты ----------------

    private fun presetsPage(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(24)) }
        col.addView(tv("Выберите стиль. Тонкая настройка — свайпом на соседнюю страницу.", 14f, 0xAAFFFFFF.toInt()))
        PRESETS.forEach { (n, c) ->
            col.addView(card(n, c), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })
        }
        return ScrollView(this).apply { addView(col) }
    }

    private fun card(name: String, c: Cfg): View {
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(12))
            background = GradientDrawable().apply { setColor(c.bgColor); alpha = c.bgAlpha * 255 / 100; cornerRadius = dp(20).toFloat() }
        }
        inner.addView(tv("10:09", c.size * 0.55f, c.clockColor).apply {
            typeface = Typeface.create(FONT_FAMILY[c.font], Typeface.NORMAL)
        })
        val line = LinearLayout(this)
        if (c.dateOn) line.addView(tv(SimpleDateFormat(DATE_FMT[c.dateFmt], Locale.getDefault()).format(Date()), 14f, c.dateColor))
        if (c.alarmOn) line.addView(tv("   ⏰ 07:30", 13f, c.alarmColor))
        if (c.weatherOn) line.addView(tv("   ⛅ 18°", 14f, c.weatherColor))
        inner.addView(line)
        if (c.eventsOn) inner.addView(tv("10:00 · Встреча", 12f, c.eventsColor))

        val f = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF614385.toInt(), 0xFF516395.toInt())).apply { cornerRadius = dp(20).toFloat() }
        }
        f.addView(inner, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        f.addView(tv(name, 11f, 0xCCFFFFFF.toInt()).apply { setPadding(dp(10), dp(4), dp(10), dp(6)) },
            FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.END or Gravity.BOTTOM))
        f.setOnClickListener {
            cfg = c; commit(); rebuild()
            Toast.makeText(this, name, Toast.LENGTH_SHORT).show()
        }
        return f
    }

    // ---------------- Страница 2: настройки ----------------

    private fun buildSettings(): ScrollView {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(32)) }

        col.title("ЧАСЫ")
        col.spin(FONT_NAMES, cfg.font) { cfg = cfg.copy(font = it); commit() }
        col.seek("Размер", cfg.size, 36, 80) { cfg = cfg.copy(size = it); commit() }
        col.colors(cfg.clockColor) { cfg = cfg.copy(clockColor = it); commit(); rebuild() }
        col.sw("24-часовой формат", cfg.h24) { cfg = cfg.copy(h24 = it); commit() }

        col.title("ДАТА")
        col.sw("Показывать", cfg.dateOn) { cfg = cfg.copy(dateOn = it); commit() }
        col.spin(arrayOf("Вт, 6 окт", "Вторник, 6 октября", "6 октября"), cfg.dateFmt) { cfg = cfg.copy(dateFmt = it); commit() }
        col.colors(cfg.dateColor) { cfg = cfg.copy(dateColor = it); commit(); rebuild() }

        col.title("БУДИЛЬНИК")
        col.sw("Показывать следующий", cfg.alarmOn) { cfg = cfg.copy(alarmOn = it); commit() }
        col.colors(cfg.alarmColor) { cfg = cfg.copy(alarmColor = it); commit(); rebuild() }

        col.title("ПОГОДА И ГОРОД")
        col.sw("Показывать", cfg.weatherOn) { cfg = cfg.copy(weatherOn = it); commit() }
        val et = EditText(this).apply {
            hint = "Город"; setSingleLine(); setTextColor(TXT); setHintTextColor(0x88FFFFFF.toInt())
            setText(Store.p(this@MainActivity).getString("city", ""))
        }
        val btn = Button(this).apply { text = "Найти"; setOnClickListener { findCity(et.text.toString().trim()) } }
        val cityRow = LinearLayout(this)
        cityRow.addView(et, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); cityRow.addView(btn)
        col.addView(cityRow)
        col.sw("Градусы Фаренгейта", cfg.fahr) { cfg = cfg.copy(fahr = it); commit() }
        col.colors(cfg.weatherColor) { cfg = cfg.copy(weatherColor = it); commit(); rebuild() }

        col.title("СОБЫТИЯ КАЛЕНДАРЯ")
        col.sw("Показывать ближайшие", cfg.eventsOn) {
            cfg = cfg.copy(eventsOn = it); commit()
            if (it && checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 1)
        }
        col.colors(cfg.eventsColor) { cfg = cfg.copy(eventsColor = it); commit(); rebuild() }

        col.title("ФОН")
        col.seek("Прозрачность, %", 100 - cfg.bgAlpha, 0, 100) { cfg = cfg.copy(bgAlpha = 100 - it); commit() }
        col.colors(cfg.bgColor) { cfg = cfg.copy(bgColor = it); commit(); rebuild() }

        col.addView(tv("Погода и календарь обновляются раз в сутки. Тап по погоде на виджете — обновить вручную.",
            12f, 0x88FFFFFF.toInt()).apply { setPadding(0, dp(24), 0, 0) })
        return ScrollView(this).apply { addView(col) }
    }

    private fun findCity(q: String) {
        if (q.isEmpty()) return
        val a = applicationContext
        Thread {
            val name = Refresh.geocode(a, q)
            if (name != null) Refresh.run(a, true)
            runOnUiThread { Toast.makeText(this, if (name != null) "Город: $name" else "Не найдено или нет сети", Toast.LENGTH_SHORT).show() }
        }.start()
    }

    // ---------------- Мини-конструкторы контролов ----------------

    private fun LinearLayout.title(t: String) = addView(tv(t, 13f, ACCENT).apply {
        typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(22), 0, dp(6))
    })

    private fun LinearLayout.sw(label: String, on: Boolean, f: (Boolean) -> Unit) = addView(Switch(context).apply {
        text = label; isChecked = on; setTextColor(TXT)
        setOnCheckedChangeListener { _, v -> f(v) }
    })

    private fun LinearLayout.seek(label: String, v: Int, lo: Int, hi: Int, f: (Int) -> Unit) {
        val t = tv("$label: $v", 14f, TXT)
        val sb = SeekBar(context).apply {
            max = hi - lo; progress = v - lo
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) { t.text = "$label: ${p + lo}" }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) { f(s.progress + lo) }
            })
        }
        addView(t); addView(sb)
    }

    private fun LinearLayout.spin(items: Array<String>, sel: Int, f: (Int) -> Unit) = addView(Spinner(context).apply {
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(sel)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var first = true
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (first) { first = false; return }
                f(pos)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    })

    private fun LinearLayout.colors(cur: Int, f: (Int) -> Unit) {
        val row = LinearLayout(context)
        PAL.forEach { col ->
            val cell = FrameLayout(context)
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(col)
                    setStroke(dp(if (col == cur) 3 else 1), if (col == cur) ACCENT else 0x55FFFFFF.toInt())
                }
            }
            cell.addView(dot, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
            cell.setOnClickListener { f(col) }
            row.addView(cell, LinearLayout.LayoutParams(0, dp(40), 1f))
        }
        addView(row)
    }
}
