package io.github.busybin.gitrehub.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.view.View
import io.github.busybin.gitrehub.R
import io.github.busybin.gitrehub.compose.common.LanguagePrefs
import io.github.busybin.gitrehub.helper.PrefHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * In-app language persistence. The bug: pick Japanese, switch back to Auto, and after a restart it
 * is still Japanese. Root cause is `PrefHelper.putAny`, which returns early for an empty string, and
 * "follow system" is exactly the empty tag — only the in-memory flow changed while `ja` stayed on
 * disk. This pins "select auto = really delete the key".
 */
@RunWith(AndroidJUnit4::class)
class LanguagePrefsTest {

    private val original = LanguagePrefs.tag.value

    @After
    fun tearDown() {
        // Don't leave the emulator in Japanese (empty value = follow system; set("") removes the key).
        LanguagePrefs.set(original)
    }

    @Test
    fun explicitLanguageIsPersisted() {
        LanguagePrefs.set("ja")
        assertEquals("ja", LanguagePrefs.tag.value)
        assertEquals("ja", PrefHelper.getString("m3_language"))
        assertEquals(Locale.forLanguageTag("ja"), LanguagePrefs.locale())
    }

    @Test
    fun systemChoiceRemovesTheStoredTag() {
        LanguagePrefs.set("ja")
        assertEquals("ja", PrefHelper.getString("m3_language"))

        // Switch back to "follow system".
        LanguagePrefs.set("")
        assertEquals("", LanguagePrefs.tag.value)
        assertNull(LanguagePrefs.locale())
        // Key assertion: the disk must not keep "ja", or a cold start reads it back.
        assertNull(PrefHelper.getString("m3_language"))
    }

    /**
     * Every option must resolve to its own `values-xx` resources, never silently falling back to
     * English. The easy ones to break are `zh` (dir is `values-zh-rCN`) and `pt-BR`
     * (`values-pt-rBR`); two different strings distinguish es / pt, both labelled "Idioma".
     */
    @Test
    fun everyOptionResolvesToItsOwnResources() {
        val expected = mapOf(
            "en" to ("Language" to "No data yet"),
            "zh" to ("语言 / Language" to "暂无数据"),
            "es" to ("Idioma" to "Aún no hay datos"),
            "fr" to ("Langue" to "Pas encore de données"),
            "ar" to ("اللغة" to "لا توجد بيانات بعد"),
            "pt-BR" to ("Idioma" to "Ainda sem dados"),
            "ru" to ("Язык / Language" to "Пока нет данных"),
            "ja" to ("言語" to "データがまだありません"),
        )
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        expected.forEach { (tag, strings) ->
            LanguagePrefs.set(tag)
            val context = LanguagePrefs.wrap(base)
            assertEquals("language label for $tag", strings.first, context.getString(R.string.m3_language))
            assertEquals("empty label for $tag", strings.second, context.getString(R.string.m3_insights_empty))
        }
        // Arabic must actually be RTL (the result of setLayoutDirection in attachBaseContext).
        LanguagePrefs.set("ar")
        val arabic = LanguagePrefs.wrap(base)
        assertEquals(
            View.LAYOUT_DIRECTION_RTL,
            arabic.resources.configuration.layoutDirection,
        )
    }
}
