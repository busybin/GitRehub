package io.github.busybin.gitrehub.helper

/** Small string helpers shared by the rendering code. */
object InputHelper {
    @JvmStatic
    fun isEmpty(text: CharSequence?): Boolean {
        if (text == null) return true
        if (text.isBlank()) return true
        return text.toString().equals("null", ignoreCase = true)
    }

    @JvmStatic
    fun toString(text: CharSequence?): String = text?.toString().orEmpty()

    /** Notification ids are 64-bit; Android's notify() takes an int. */
    @JvmStatic
    fun getSafeIntId(id: Long): Int = if (id > Int.MAX_VALUE) (id - Int.MAX_VALUE).toInt() else id.toInt()
}
