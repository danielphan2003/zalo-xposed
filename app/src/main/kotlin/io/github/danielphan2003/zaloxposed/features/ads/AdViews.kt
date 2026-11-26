package io.github.danielphan2003.zaloxposed.features.ads

import android.view.View
import android.view.ViewGroup
import java.util.Collections
import java.util.WeakHashMap

/** Hides an ad view so it takes no space, and keeps it hidden when Zalo rebinds or re-lays it out. */
internal object AdViews {

    private val watched: MutableSet<View> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    fun hide(view: View) {
        collapse(view)
        if (!watched.add(view)) return
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = collapse(v)
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        view.addOnLayoutChangeListener { v, _, top, _, bottom, _, _, _, _ ->
            if (v.visibility != View.GONE || bottom - top > 0) v.post { collapse(v) }
        }
    }

    private fun collapse(view: View) {
        if (view.visibility != View.GONE) view.visibility = View.GONE
        view.minimumHeight = 0
        val lp = view.layoutParams ?: return
        var changed = false
        if (lp.height != 0) {
            lp.height = 0
            changed = true
        }
        if (lp is ViewGroup.MarginLayoutParams && (lp.topMargin != 0 || lp.bottomMargin != 0)) {
            lp.topMargin = 0
            lp.bottomMargin = 0
            changed = true
        }
        if (changed) view.layoutParams = lp
    }
}
