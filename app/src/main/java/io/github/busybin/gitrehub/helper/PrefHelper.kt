package io.github.busybin.gitrehub.helper

import android.content.Context
import com.tencent.mmkv.MMKV

/**
 * MMKV-backed key/value store. Keys are tagged with their type (`s@key`, `b@key`, ...) so that a
 * value read with the wrong type is never returned; the tagging predates the rewrite and is kept so
 * existing installs keep their settings.
 */
object PrefHelper {
    private var store: MMKV? = null

    @JvmStatic
    fun init(context: Context) {
        MMKV.initialize(context)
        store = MMKV.mmkvWithID("preferences")
    }

    @PublishedApi
    internal fun mmkv(): MMKV = store ?: error("PrefHelper.init() was not called")

    @PublishedApi
    internal inline fun <reified T> typed(key: String): String {
        val name = T::class.simpleName
        val prefix = when (name) {
            String::class.simpleName -> "s"
            Int::class.simpleName -> "i"
            Long::class.simpleName -> "l"
            Float::class.simpleName -> "f"
            Boolean::class.simpleName -> "b"
            Set::class.simpleName -> "ss"
            else -> "s"
        }
        return "$prefix@$key"
    }

    fun <T> putAny(key: String, value: T?) {
        if (key.isBlank() || value == null) return
        val text = value.toString()
        if (text.isBlank()) return
        when (value) {
            is String -> mmkv().putString(typed<String>(key), value)
            is Int -> mmkv().putInt(typed<Int>(key), value)
            is Long -> mmkv().putLong(typed<Long>(key), value)
            is Float -> mmkv().putFloat(typed<Float>(key), value)
            is Boolean -> mmkv().putBoolean(typed<Boolean>(key), value)
            is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
            else -> mmkv().putString(typed<String>(key), text)
        }
    }

    fun getString(key: String): String? = mmkv().getString(typed<String>(key), null)

    fun getBoolean(key: String): Boolean = mmkv().getBoolean(typed<Boolean>(key), false)

    fun getInt(key: String): Int = mmkv().getInt(typed<Int>(key), 0)

    fun getFloat(key: String): Float = mmkv().getFloat(typed<Float>(key), 0f)

    fun getStringSet(key: String): Set<String> =
        mmkv().getStringSet(typed<Set<String>>(key), emptySet()) ?: emptySet()

    fun putStringSet(key: String, values: Set<String>) {
        mmkv().putStringSet(typed<Set<String>>(key), values)
    }

    inline fun <reified T> clearKey(key: String) {
        mmkv().remove(typed<T>(key))
    }

    inline fun <reified T> isExist(key: String): Boolean = mmkv().contains(typed<T>(key))
}
