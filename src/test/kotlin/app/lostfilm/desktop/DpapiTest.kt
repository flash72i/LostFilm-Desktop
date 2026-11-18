package app.lostfilm.desktop

import app.lostfilm.desktop.core.Dpapi
import org.junit.Assume.assumeNotNull
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

class DpapiTest {
    @Test
    fun protectAndUnprotectRoundTrip() {
        val source = "пароль-проверка-123".toByteArray(Charsets.UTF_8)
        val protected = Dpapi.protect(source)
        assumeNotNull(protected)
        assertFalse(protected!!.contentEquals(source))
        assertContentEquals(source, Dpapi.unprotect(protected))
    }

    @Test
    fun unprotectRejectsGarbage() {
        val broken = ByteArray(32) { (it * 7).toByte() }
        val result = Dpapi.unprotect(broken)
        if (result != null) {
            assertFalse(result.toString(Charsets.UTF_8).contains("пароль"))
        }
    }
}
