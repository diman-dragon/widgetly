package app.clockweather

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*

class MainActivity : Activity() {
    private var cfg = Cfg()
    private var preset = ""
    private var isConfig = false
    private var dens = 1f
    private lateinit var status: TextView
    private lateinit var holder1: FrameLayout
    private lateinit var holder2: FrameLayout
    private lateinit var pvFrame: FrameLayout
    private var scroll1: ScrollView? = null
    private var scroll2: ScrollView? = null
    private var dirty1 = false
    private var pvCols = 5
    private var pvRows = 3
    private val chips = ArrayList<TextView>()

    private val BG = 0xFF121317.toInt()
    private val ACCENT = 0xFF4FC3F7.toInt()
    private val TXT = 0xFFFFFFFF.toInt()
    private val PAL = intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFC83D.toInt(), 0xFFFF5A5F.toInt(),
        0xFF4FC3F7.toInt(), 0xFF81C784.toInt(), 0xFFBA68C8.toInt(), 0xFFFF8A3D.toInt())
    private val SIZES = listOf(Triple("5×3", 5, 3), Triple("4×2", 4, 2), Triple("4×1", 4, 1), Triple("2×2", 2, 2))

    private fun dp(v: Int) = (v * dens).toInt()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        dens = resources.displayMetrics.density
        val wid = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0) ?: 0
        if (wid != 0) {
            isConfig = true
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid))
        }
        cfg = Cfg.load(this)
        preset = Store.p(this).getString("preset", PRESETS[0].first) ?: PRESETS[0].first
        RefreshJob.schedule(this)
        Refresh.async(this, false, null) // «при открытии»: обновит, только если сегодня ещё не обновляли

        val sp = Store.p(this)
        if (cfg.eventsOn && !sp.getBoolean("asked", false) && !calendarGranted()) {
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
        pager = Pager(this) { p ->
            for (i in 0..1) tabs[i].alpha = if (i == p) 1f else .45f
            if (p == 0 && dirty1) rebuildPresets()
        }
        tabs[1].alpha = .45f

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        val bar = LinearLayout(this)
        tabs.forEach { bar.addView(it, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) }
        root.addView(bar)

        val w = resources.displayMetrics.widthPixels
        val row = LinearLayout(this)
        holder1 = FrameLayout(this)
        row.addView(holder1, LinearLayout.LayoutParams(w, MATCH_PARENT))
        row.addView(settingsPage(), LinearLayout.LayoutParams(w, MATCH_PARENT))
        pager.addView(row, FrameLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT))
        root.addView(pager, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // нижняя панель: явный статус сохранения + «Готово»
        val bottom = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(8), dp(8)); setBackgroundColor(0xFF1C1E24.toInt())
        }
        status = tv("", 12f, 0xCCFFFFFF.toInt())
        bottom.addView(status, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bottom.addView(Button(this).apply {
            text = "Готово"
            setOnClickListener { ClockWidget.renderAll(this@MainActivity); finish() }
        })
        root.addView(bottom)
        setContentView(root)

        rebuildPresets()
        updateStatus(false)
    }

    override fun onStop() { super.onStop(); ClockWidget.renderAll(this) }

    override fun onRequestPermissionsResult(rc: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(rc, perms, res)
        if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) {
            Refresh.async(this, true, null); rebuildSettings()
        }
    }

    private fun calendarGranted() = checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    // ---------- сохранение и обратная связь ----------

    private fun commit() {
        cfg.save(this); ClockWidget.renderAll(this)
        dirty1 = true
        refreshPreview(); updateStatus(true)
    }

    private fun styleName(): String {
        val p = PRESETS.firstOrNull { it.first == preset } ?: return "Свой стиль"
        return if (p.second == cfg) p.first else "Свой стиль (на основе ${p.first})"
    }

    private fun updateStatus(saved: Boolean) {
        val n = AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this, ClockWidget::class.java)).size
        val onScreen = if (n > 0) "виджетов на экране: $n" else if (isConfig) "виджет добавится после «Готово»" else "виджет ещё не добавлен на рабочий стол"
        status.text = (if (saved) "✓ Сохранено и применено\n" else "Изменения сохраняются сразу\n") + styleName() + " · " + onScreen
    }

    // ---------- предпросмотр: тот же код, что рисует настоящий виджет ----------

    private fun wallpaper() = GradientDrawable(GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xFF614385.toInt(), 0xFF516395.toInt())).apply { cornerRadius = dp(20).toFloat() }

    private fun addPreview(frame: FrameLayout, c: Cfg, cols: Int, rows: Int, mode: Int) {
        frame.removeAllViews()
        val wd = cols * 70 - 30; val hd = rows * 70 - 30
        try {
            val v = ClockWidget.build(this, c, wd, hd, mode).apply(applicationContext, frame)
            frame.addView(v, FrameLayout.LayoutParams(dp(wd), dp(hd), Gravity.CENTER))
        } catch (e: Exception) {
            frame.addView(tv("Не удалось нарисовать предпросмотр", 12f, TXT))
        }
    }

    private fun refreshPreview() = addPreview(pvFrame, cfg, pvCols, pvRows, 1)

    // ---------- страница 1: пресеты (живые образцы) ----------

    private fun rebuildPresets() {
        val y = scroll1?.scrollY ?: 0
        dirty1 = false
        holder1.removeAllViews()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(24)) }
        col.addView(tv("Нажмите на стиль — он сразу сохранится и применится к виджету. Настройка деталей — следующая страница (свайп).",
            13f, 0xAAFFFFFF.toInt()))
        PRESETS.forEach { (n, c) ->
            col.addView(card(n, c), LinearLayout.LayoutParams(MATCH_PARENT, dp(196)).apply { topMargin = dp(12) })
        }
        val s = ScrollView(this).apply { addView(col) }
        scroll1 = s; holder1.addView(s)
        s.post { s.scrollY = y }
    }

    private fun card(name: String, c: Cfg): View {
        val sel = cfg == c
        val f = FrameLayout(this).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = wallpaper().apply { if (sel) setStroke(dp(3), ACCENT) }
        }
        val pv = FrameLayout(this)
        f.addView(pv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addPreview(pv, c, 5, 3, 2)
        f.addView(tv(if (sel) "✓ $name · выбран" else name, 11f, 0xFFFFFFFF.toInt()).apply {
            setPadding(dp(8), dp(2), dp(8), dp(3))
            background = GradientDrawable().apply { setColor(if (sel) 0xFF1B8FB8.toInt() else 0x99000000.toInt()); cornerRadius = dp(10).toFloat() }
        }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.START or Gravity.TOP))
        f.setOnClickListener {
            cfg = c; preset = name
            Store.p(this).edit().putString("preset", name).apply()
            commit(); rebuildPresets(); rebuildSettings()
            Toast.makeText(this, "Сохранено: $name", Toast.LENGTH_SHORT).show()
        }
        return f
    }

    // ---------- страница 2: настройки с предпросмотром сверху ----------

    private fun settingsPage(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pvFrame = FrameLayout(this).apply { setPadding(dp(8), dp(8), dp(8), dp(8)); background = wallpaper() }
        page.addView(pvFrame, LinearLayout.LayoutParams(MATCH_PARENT, dp(196)).apply { setMargins(dp(16), dp(8), dp(16), 0) })

        val chipRow = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(2)) }
        SIZES.forEachIndexed { i, (label, c, r) ->
            val t = tv(label, 13f, TXT).apply {
                gravity = Gravity.CENTER; setPadding(dp(14), dp(6), dp(14), dp(6))
                setOnClickListener { pvCols = c; pvRows = r; paintChips(); refreshPreview() }
            }
            chips.add(t); chipRow.addView(t)
        }
        page.addView(tv("Размер виджета на экране:", 11f, 0x88FFFFFF.toInt()).apply { gravity = Gravity.CENTER })
        page.addView(chipRow)
        paintChips()

        holder2 = FrameLayout(this)
        page.addView(holder2, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        rebuildSettings()
        refreshPreview()
        return page
    }

    private fun paintChips() {
        chips.forEachIndexed { i, t ->
            val on = SIZES[i].second == pvCols && SIZES[i].third == pvRows
            t.background = GradientDrawable().apply { setColor(if (on) 0xFF1B8FB8.toInt() else 0x22FFFFFF); cornerRadius = dp(14).toFloat() }
        }
    }

    private fun rebuildSettings() {
        val y = scroll2?.scrollY ?: 0
        holder2.removeAllViews()
        val s = buildSettings(); scroll2 = s; holder2.addView(s)
        s.post { s.scrollY = y }
    }

    private fun buildSettings(): ScrollView {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(32)) }

        col.title("РАСКЛАДКА И ФОН")
        col.spin(LAYOUT_NAMES, cfg.layout) { cfg = cfg.copy(layout = it); commit() }
        col.spin(SCENE_NAMES, cfg.scene) { cfg = cfg.copy(scene = it); commit() }
        col.seek("Прозрачность, %", 100 - cfg.bgAlpha, 0, 100) { cfg = cfg.copy(bgAlpha = 100 - it); commit() }
        col.colors(cfg.bgColor) { cfg = cfg.copy(bgColor = it); commit(); rebuildSettings() }

        col.title("ЧАСЫ")
        col.spin(FONT_NAMES, cfg.font) { cfg = cfg.copy(font = it); commit() }
        col.seek("Размер", cfg.size, 36, 80) { cfg = cfg.copy(size = it); commit() }
        col.colors(cfg.clockColor) { cfg = cfg.copy(clockColor = it); commit(); rebuildSettings() }
        col.sw("24-часовой формат", cfg.h24) { cfg = cfg.copy(h24 = it); commit() }

        col.title("ДАТА")
        col.sw("Показывать", cfg.dateOn) { cfg = cfg.copy(dateOn = it); commit() }
        col.spin(arrayOf("Короткая", "Полная", "Только число и месяц"), cfg.dateFmt) { cfg = cfg.copy(dateFmt = it); commit() }
        col.colors(cfg.dateColor) { cfg = cfg.copy(dateColor = it); commit(); rebuildSettings() }

        col.title("БУДИЛЬНИК")
        col.sw("Показывать следующий", cfg.alarmOn) { cfg = cfg.copy(alarmOn = it); commit() }
        col.colors(cfg.alarmColor) { cfg = cfg.copy(alarmColor = it); commit(); rebuildSettings() }

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
        col.sw("Прогноз на 6 дней (при достаточном размере)", cfg.forecastOn) { cfg = cfg.copy(forecastOn = it); commit() }
        col.sw("Градусы Фаренгейта", cfg.fahr) { cfg = cfg.copy(fahr = it); commit() }
        col.colors(cfg.weatherColor) { cfg = cfg.copy(weatherColor = it); commit(); rebuildSettings() }

        col.title("СОБЫТИЯ КАЛЕНДАРЯ")
        col.sw("Показывать ближайшие", cfg.eventsOn) {
            cfg = cfg.copy(eventsOn = it); commit()
            if (it && !calendarGranted()) requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 1)
        }
        if (!calendarGranted()) col.addView(Button(this).apply {
            text = "Разрешить доступ к календарю"
            setOnClickListener { requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 1) }
        })
        col.colors(cfg.eventsColor) { cfg = cfg.copy(eventsColor = it); commit(); rebuildSettings() }

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
            runOnUiThread {
                Toast.makeText(this, if (name != null) "Город: $name" else "Не найдено или нет сети", Toast.LENGTH_SHORT).show()
                refreshPreview(); updateStatus(name != null)
            }
        }.start()
    }

    private fun tv(t: String, sp: Float, col: Int) = TextView(this).apply { text = t; textSize = sp; setTextColor(col) }

    // ---------- мини-конструкторы контролов ----------

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
