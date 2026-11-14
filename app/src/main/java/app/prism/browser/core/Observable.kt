package app.prism.browser.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Minimal observable state for the View-based UI (replaces Compose snapshot state).
 * Any change schedules one coalesced UI refresh on the main thread.
 */
object Changes {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val scheduled = AtomicBoolean(false)

    fun add(listener: () -> Unit) { listeners += listener }
    fun remove(listener: () -> Unit) { listeners -= listener }

    fun notifyChanged() {
        if (scheduled.compareAndSet(false, true)) {
            main.post {
                scheduled.set(false)
                listeners.forEach { it() }
            }
        }
    }
}

/** Observable property: `var x by Obs(initial)`. */
class Obs<T>(private var value: T) : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (this.value != value) {
            this.value = value
            Changes.notifyChanged()
        }
    }
}

/** Observable list: every mutation notifies. */
class ObsList<T>(initial: Collection<T> = emptyList()) : MutableList<T> {
    private val inner = ArrayList<T>(initial)

    override val size: Int get() = inner.size
    override fun contains(element: T): Boolean = inner.contains(element)
    override fun containsAll(elements: Collection<T>): Boolean = inner.containsAll(elements)
    override fun get(index: Int): T = inner[index]
    override fun indexOf(element: T): Int = inner.indexOf(element)
    override fun isEmpty(): Boolean = inner.isEmpty()
    override fun iterator(): MutableIterator<T> = inner.iterator()
    override fun lastIndexOf(element: T): Int = inner.lastIndexOf(element)
    override fun listIterator(): MutableListIterator<T> = inner.listIterator()
    override fun listIterator(index: Int): MutableListIterator<T> = inner.listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> = inner.subList(fromIndex, toIndex)

    override fun add(element: T): Boolean = inner.add(element).also { Changes.notifyChanged() }
    override fun add(index: Int, element: T) { inner.add(index, element); Changes.notifyChanged() }
    override fun addAll(elements: Collection<T>): Boolean = inner.addAll(elements).also { Changes.notifyChanged() }
    override fun addAll(index: Int, elements: Collection<T>): Boolean = inner.addAll(index, elements).also { Changes.notifyChanged() }
    override fun clear() { inner.clear(); Changes.notifyChanged() }
    override fun remove(element: T): Boolean = inner.remove(element).also { Changes.notifyChanged() }
    override fun removeAll(elements: Collection<T>): Boolean = inner.removeAll(elements.toSet()).also { Changes.notifyChanged() }
    override fun removeAt(index: Int): T = inner.removeAt(index).also { Changes.notifyChanged() }
    override fun retainAll(elements: Collection<T>): Boolean = inner.retainAll(elements.toSet()).also { Changes.notifyChanged() }
    override fun set(index: Int, element: T): T = inner.set(index, element).also { Changes.notifyChanged() }

    /** Extension-style bulk removals bypass the overrides above; call this after them. */
    fun changed() = Changes.notifyChanged()
}
