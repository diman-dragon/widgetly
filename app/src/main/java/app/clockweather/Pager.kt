package app.clockweather

import android.content.Context
import android.view.MotionEvent
import android.widget.HorizontalScrollView

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

