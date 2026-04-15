package nodomain.freeyourgadget.gadgetbridge.service.devices.ugreen

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.serial.GBDeviceProtocol
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.*

/**
 * Protocol handler for UGREEN Studio Pro headphones.
 *
 * Uses RFCOMM (Classic BT SPP) with custom frame format:
 *   Command (Host→Device):  [AA BB CC] [subcmd] [params...] [CRC16-MODBUS LE]
 *   Response (Device→Host): [DD EE FF] [subcmd] [data...]   [CRC16-CCITT-FALSE LE]
 *   Notification:           [85 86 87] [data...]
 *
 * Based on reverse engineering of the UGREEN Connect app for HiTune Max5c.
 */
class UgreenStudioProProtocol(device: GBDevice) : GBDeviceProtocol(device) {

    companion object {
        private val LOG = LoggerFactory.getLogger(UgreenStudioProProtocol::class.java)

        // Frame headers
        private val CMD_HEADER = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        private val RSP_HEADER = byteArrayOf(0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte())
        private val NOTIFY_HEADER = byteArrayOf(0x85.toByte(), 0x86.toByte(), 0x87.toByte())

        // Subcommands
        const val SUBCMD_EQ: Byte = 0x05
        const val SUBCMD_GAME_MODE: Byte = 0x06
        const val SUBCMD_FIND: Byte = 0x07
        const val SUBCMD_WIND_NOISE: Byte = 0x08
        const val SUBCMD_ANC: Byte = 0x09
        const val SUBCMD_DUAL_CONN: Byte = 0x0B
        const val SUBCMD_HD_CODEC: Byte = 0x0F
        const val SUBCMD_SPATIAL_AUDIO: Byte = 0x12

        // ANC mode byte values
        const val ANC_OFF: Byte = 0xA0.toByte()
        const val ANC_DEEP: Byte = 0xA1.toByte()
        const val ANC_MODERATE: Byte = 0xB1.toByte()
        const val ANC_MILD: Byte = 0xC1.toByte()
        const val ANC_AUTO: Byte = 0xD1.toByte()
        const val ANC_TRANSPARENT: Byte = 0xA2.toByte()

        // EQ preset values
        const val EQ_CLASSIC: Byte = 0x00
        const val EQ_JAZZ: Byte = 0x01
        const val EQ_ELECTRONIC: Byte = 0x02
        const val EQ_POP: Byte = 0x03
        const val EQ_CLASSICAL: Byte = 0x04
        const val EQ_ROCK: Byte = 0x05
        const val EQ_BASS_BOOST: Byte = 0x06
        const val EQ_TREBLE_BOOST: Byte = 0x07

        // Minimum frame size: 3 header + 1 subcmd + 2 CRC = 6 bytes
        private const val MIN_FRAME_SIZE = 6
        private const val HEADER_SIZE = 3
    }

    // ===== CRC Algorithms =====

    /**
     * CRC16-MODBUS for outgoing command frames.
     * Polynomial: 0xA001 (reflected 0x8005), Init: 0xFFFF, Reflected, No output XOR.
     */
    fun crc16Modbus(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            for (j in 0 until 8) {
                crc = if (crc and 1 != 0) {
                    (crc ushr 1) xor 0xA001
                } else {
                    crc ushr 1
                }
            }
        }
        return crc and 0xFFFF
    }

    /**
     * CRC16-CCITT-FALSE for validating response frames.
     * Polynomial: 0x1021, Init: 0xFFFF, Non-reflected, No output XOR.
     */
    fun crc16CcittFalse(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            val b = data[i].toInt() and 0xFF
            for (bit in 7 downTo 0) {
                val msb = (crc shr 15) and 1
                crc = (crc shl 1) and 0xFFFF
                if (((b shr bit) and 1) != msb) {
                    crc = crc xor 0x1021
                }
            }
        }
        return crc and 0xFFFF
    }

    // ===== Frame Building =====

    /**
     * Build a command frame: AA BB CC <subcmd> <params...> <CRC16-MODBUS LE>
     */
    fun buildCommand(subcmd: Byte, params: ByteArray = byteArrayOf()): ByteArray {
        val payload = byteArrayOf(subcmd) + params
        val crc = crc16Modbus(payload)
        val frame = ByteArray(CMD_HEADER.size + payload.size + 2)
        System.arraycopy(CMD_HEADER, 0, frame, 0, CMD_HEADER.size)
        System.arraycopy(payload, 0, frame, CMD_HEADER.size, payload.size)
        frame[frame.size - 2] = (crc and 0xFF).toByte()
        frame[frame.size - 1] = ((crc shr 8) and 0xFF).toByte()
        return frame
    }

    // ===== Command Encoding =====

    fun encodeSetAncMode(mode: Byte): ByteArray {
        return buildCommand(SUBCMD_ANC, byteArrayOf(0x01, mode))
    }

    fun encodeSetEqPreset(preset: Byte): ByteArray {
        return buildCommand(SUBCMD_EQ, byteArrayOf(0x01, preset))
    }

    fun encodeSetGameMode(enabled: Boolean): ByteArray {
        return buildCommand(SUBCMD_GAME_MODE, byteArrayOf(0x01, if (enabled) 0x01 else 0x00))
    }

    fun encodeSetWindNoise(enabled: Boolean): ByteArray {
        return buildCommand(SUBCMD_WIND_NOISE, byteArrayOf(0x01, if (enabled) 0x01 else 0x00))
    }

    fun encodeSetSpatialAudio(enabled: Boolean): ByteArray {
        return buildCommand(SUBCMD_SPATIAL_AUDIO, byteArrayOf(0x01, if (enabled) 0x01 else 0x00))
    }

    fun encodeSetDualConnection(enabled: Boolean): ByteArray {
        return buildCommand(SUBCMD_DUAL_CONN, byteArrayOf(0x01, if (enabled) 0x01 else 0x00))
    }

    fun encodeSetHdCodec(codec: Byte): ByteArray {
        return buildCommand(SUBCMD_HD_CODEC, byteArrayOf(0x01, codec))
    }

    // ===== Response Decoding =====

    override fun decodeResponse(data: ByteArray): Array<GBDeviceEvent> {
        val events = mutableListOf<GBDeviceEvent>()
        val buf = ByteBuffer.wrap(data)

        while (buf.remaining() >= MIN_FRAME_SIZE) {
            buf.mark()

            // Try to find a response header (DD EE FF) or notification header (85 86 87)
            val b1 = buf.get().toInt() and 0xFF
            val b2 = buf.get().toInt() and 0xFF
            val b3 = buf.get().toInt() and 0xFF

            val isResponse = b1 == 0xDD && b2 == 0xEE && b3 == 0xFF
            val isNotification = b1 == 0x85 && b2 == 0x86 && b3 == 0x87

            if (!isResponse && !isNotification) {
                buf.reset()
                // Skip one byte and try again
                if (buf.remaining() > 0) buf.get()
                continue
            }

            if (isResponse && buf.remaining() >= MIN_FRAME_SIZE - HEADER_SIZE) {
                val subcmd = buf.get()
                val remaining = buf.remaining()
                if (remaining < 2) break

                // Data is everything before the last 2 CRC bytes
                val dataLen = remaining - 2
                val payloadData = ByteArray(dataLen)
                if (dataLen > 0) buf.get(payloadData)

                val crcReceived = (buf.get().toInt() and 0xFF) or
                        ((buf.get().toInt() and 0xFF) shl 8)

                // Verify CRC16-CCITT-FALSE over subcmd + data
                val verifyPayload = byteArrayOf(subcmd) + payloadData
                val crcExpected = crc16CcittFalse(verifyPayload)

                if (crcReceived != crcExpected) {
                    LOG.warn(
                        "CRC mismatch for subcmd 0x{}: got 0x{}, expected 0x{}",
                        String.format("%02X", subcmd),
                        String.format("%04X", crcReceived),
                        String.format("%04X", crcExpected)
                    )
                    continue
                }

                val event = handleResponse(subcmd, payloadData)
                if (event != null) events.add(event)
            } else if (isNotification) {
                // Notification frames: 85 86 87 <data...>
                // Read remaining data in buffer as notification payload
                val notifData = ByteArray(buf.remaining())
                buf.get(notifData)
                val event = handleNotification(notifData)
                if (event != null) events.add(event)
            }
        }

        return events.toTypedArray()
    }

    private fun handleResponse(subcmd: Byte, data: ByteArray): GBDeviceEvent? {
        LOG.debug("Response subcmd=0x{} data={}", String.format("%02X", subcmd), data.toHexString())

        // Most responses echo the subcmd with the same data format as the command
        // We update prefs based on what we received
        return when (subcmd) {
            SUBCMD_ANC -> {
                if (data.size >= 1) {
                    GBDeviceEventUpdatePreferences(PREF_UGREEN_ANC_MODE, ancModeToPreference(data[0]))
                } else null
            }
            SUBCMD_EQ -> {
                if (data.size >= 1) {
                    GBDeviceEventUpdatePreferences(PREF_UGREEN_EQUALIZER_PRESET, eqPresetToPreference(data[0]))
                } else null
            }
            SUBCMD_GAME_MODE -> {
                if (data.size >= 1) {
                    GBDeviceEventUpdatePreferences(PREF_UGREEN_GAME_MODE, if (data[0].toInt() != 0) "true" else "false")
                } else null
            }
            SUBCMD_WIND_NOISE -> {
                if (data.size >= 1) {
                    GBDeviceEventUpdatePreferences(PREF_UGREEN_WIND_NOISE, if (data[0].toInt() != 0) "true" else "false")
                } else null
            }
            SUBCMD_SPATIAL_AUDIO -> {
                if (data.size >= 1) {
                    GBDeviceEventUpdatePreferences(PREF_UGREEN_SPATIAL_AUDIO, if (data[0].toInt() != 0) "true" else "false")
                } else null
            }
            SUBCMD_DUAL_CONN -> {
                if (data.size >= 1) {
                    GBDeviceEventUpdatePreferences(PREF_UGREEN_DUAL_CONNECTION, if (data[0].toInt() != 0) "true" else "false")
                } else null
            }
            else -> {
                LOG.debug("Unhandled response subcmd: 0x{}", String.format("%02X", subcmd))
                null
            }
        }
    }

    private fun handleNotification(data: ByteArray): GBDeviceEvent? {
        LOG.debug("Notification data={}", data.toHexString())
        // Notifications from Ugreen are unsolicited status updates
        // Format may vary - log for now, handle specific ones as discovered
        return null
    }

    // ===== Config Sending =====

    override fun encodeSendConfiguration(config: String): ByteArray? {
        return when (config) {
            PREF_UGREEN_ANC_MODE -> {
                val modeStr = devicePrefs.getString(PREF_UGREEN_ANC_MODE, "off") ?: "off"
                encodeSetAncMode(preferenceToAncMode(modeStr))
            }
            PREF_UGREEN_EQUALIZER_PRESET -> {
                val presetStr = devicePrefs.getString(PREF_UGREEN_EQUALIZER_PRESET, "classic") ?: "classic"
                encodeSetEqPreset(preferenceToEqPreset(presetStr))
            }
            PREF_UGREEN_GAME_MODE -> {
                val enabled = devicePrefs.getBoolean(PREF_UGREEN_GAME_MODE, false)
                encodeSetGameMode(enabled)
            }
            PREF_UGREEN_WIND_NOISE -> {
                val enabled = devicePrefs.getBoolean(PREF_UGREEN_WIND_NOISE, false)
                encodeSetWindNoise(enabled)
            }
            PREF_UGREEN_SPATIAL_AUDIO -> {
                val enabled = devicePrefs.getBoolean(PREF_UGREEN_SPATIAL_AUDIO, false)
                encodeSetSpatialAudio(enabled)
            }
            PREF_UGREEN_DUAL_CONNECTION -> {
                val enabled = devicePrefs.getBoolean(PREF_UGREEN_DUAL_CONNECTION, false)
                encodeSetDualConnection(enabled)
            }
            else -> super.encodeSendConfiguration(config)
        }
    }

    // ===== Preference Mappings =====

    private fun ancModeToPreference(mode: Byte): String {
        return when (mode) {
            ANC_OFF -> "off"
            ANC_DEEP -> "deep"
            ANC_MODERATE -> "moderate"
            ANC_MILD -> "mild"
            ANC_AUTO -> "auto"
            ANC_TRANSPARENT -> "transparent"
            else -> "off"
        }
    }

    private fun preferenceToAncMode(pref: String): Byte {
        return when (pref) {
            "deep" -> ANC_DEEP
            "moderate" -> ANC_MODERATE
            "mild" -> ANC_MILD
            "auto" -> ANC_AUTO
            "transparent" -> ANC_TRANSPARENT
            else -> ANC_OFF
        }
    }

    private fun eqPresetToPreference(preset: Byte): String {
        return when (preset) {
            EQ_CLASSIC -> "classic"
            EQ_JAZZ -> "jazz"
            EQ_ELECTRONIC -> "electronic"
            EQ_POP -> "pop"
            EQ_CLASSICAL -> "classical"
            EQ_ROCK -> "rock"
            EQ_BASS_BOOST -> "bass_boost"
            EQ_TREBLE_BOOST -> "treble_boost"
            else -> "classic"
        }
    }

    private fun preferenceToEqPreset(pref: String): Byte {
        return when (pref) {
            "jazz" -> EQ_JAZZ
            "electronic" -> EQ_ELECTRONIC
            "pop" -> EQ_POP
            "classical" -> EQ_CLASSICAL
            "rock" -> EQ_ROCK
            "bass_boost" -> EQ_BASS_BOOST
            "treble_boost" -> EQ_TREBLE_BOOST
            else -> EQ_CLASSIC
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(" ") { String.format("%02X", it.toInt() and 0xFF) }
    }
}
