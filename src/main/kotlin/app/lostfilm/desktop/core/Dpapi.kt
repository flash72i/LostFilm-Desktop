package app.lostfilm.desktop.core

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString

@Structure.FieldOrder("cbData", "pbData")
open class DataBlob : Structure() {
    @JvmField
    var cbData: Int = 0

    @JvmField
    var pbData: Pointer? = null
}

private interface Crypt32 : Library {
    fun CryptProtectData(
        pDataIn: DataBlob,
        szDataDescr: WString?,
        pOptionalEntropy: DataBlob?,
        pvReserved: Pointer?,
        pPromptStruct: Pointer?,
        dwFlags: Int,
        pDataOut: DataBlob,
    ): Boolean

    fun CryptUnprotectData(
        pDataIn: DataBlob,
        ppszDataDescr: Pointer?,
        pOptionalEntropy: DataBlob?,
        pvReserved: Pointer?,
        pPromptStruct: Pointer?,
        dwFlags: Int,
        pDataOut: DataBlob,
    ): Boolean
}

private interface LocalAllocator : Library {
    fun LocalFree(hMem: Pointer?): Pointer?
}

object Dpapi {
    private const val UI_FORBIDDEN = 0x1

    private val crypt32: Crypt32 by lazy { Native.load("crypt32", Crypt32::class.java) }
    private val kernel32: LocalAllocator by lazy { Native.load("kernel32", LocalAllocator::class.java) }

    private val available: Boolean by lazy {
        System.getProperty("os.name").orEmpty().startsWith("Windows", true) &&
            runCatching { crypt32; kernel32; true }.getOrDefault(false)
    }

    fun protect(data: ByteArray): ByteArray? {
        if (data.isEmpty() || !available) return null
        return runCatching {
            val input = blob(data)
            val output = DataBlob()
            if (!crypt32.CryptProtectData(input, null, null, null, null, UI_FORBIDDEN, output)) {
                return@runCatching null
            }
            output.read()
            val result = output.pbData?.getByteArray(0, output.cbData)
            output.pbData?.let { kernel32.LocalFree(it) }
            result
        }.getOrNull()
    }

    fun unprotect(data: ByteArray): ByteArray? {
        if (data.isEmpty() || !available) return null
        return runCatching {
            val input = blob(data)
            val output = DataBlob()
            if (!crypt32.CryptUnprotectData(input, null, null, null, null, UI_FORBIDDEN, output)) {
                return@runCatching null
            }
            output.read()
            val result = output.pbData?.getByteArray(0, output.cbData)
            output.pbData?.let { kernel32.LocalFree(it) }
            result
        }.getOrNull()
    }

    private fun blob(data: ByteArray): DataBlob {
        val memory = Memory(data.size.toLong())
        memory.write(0, data, 0, data.size)
        return DataBlob().apply {
            cbData = data.size
            pbData = memory
            write()
        }
    }
}
