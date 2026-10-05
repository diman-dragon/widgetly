package com.widgetly.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView

/** Выбор дизайна при добавлении виджета вручную или при его перенастройке. */
class ConfigActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val store = Store(this)

        // Виджет закреплён из редактора: дизайн уже выбран.
        val pending = store.pendingDesignId
        if (pending != null && store.getDesign(pending) != null) {
            finishWith(store, pending)
            return
        }

        val designs = store.listDesigns()
        if (designs.length() == 0) {
            finishWith(store, null)
            return
        }

        val ids = ArrayList<String?>()
        val names = ArrayList<String>()
        ids.add(null)
        names.add(getString(R.string.config_default))
        for (i in 0 until designs.length()) {
            val d = designs.optJSONObject(i) ?: continue
            ids.add(d.optString("id"))
            names.add(d.optString("name", "—"))
        }

        title = getString(R.string.config_title)
        val list = ListView(this)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        list.setOnItemClickListener { _, _, position, _ -> finishWith(store, ids[position]) }
        setContentView(list)
    }

    private fun finishWith(store: Store, designId: String?) {
        if (designId == null) store.clearAssignment(widgetId) else store.setAssignment(widgetId, designId)
        if (designId != null && designId == store.pendingDesignId) store.pendingDesignId = null
        val mgr = AppWidgetManager.getInstance(this)
        WidgetUpdater.updateWidgets(this, mgr, intArrayOf(widgetId))
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
