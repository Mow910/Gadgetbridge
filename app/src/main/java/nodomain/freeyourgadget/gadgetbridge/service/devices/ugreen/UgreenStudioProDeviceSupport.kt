package nodomain.freeyourgadget.gadgetbridge.service.devices.ugreen

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.AbstractHeadphoneBTBRDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btbr.TransactionBuilder
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Device support for UGREEN Studio Pro headphones.
 *
 * Uses RFCOMM (Classic BT SPP) with the standard Serial Port Profile UUID.
 * Communication is via custom Ugreen protocol frames over RFCOMM channel 1.
 */
class UgreenStudioProDeviceSupport : AbstractHeadphoneBTBRDeviceSupport(LOG, MAX_MTU) {

    private var protocol: UgreenStudioProProtocol? = null

    init {
        addSupportedService(SPP_UUID)
    }

    override fun useAutoConnect(): Boolean {
        return true
    }

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        builder.setDeviceState(GBDevice.State.INITIALIZING)

        // Request current ANC mode
        val ancGetCmd = byteArrayOf(
            0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(),
            0x09, 0x00 // subcmd 0x09 with no params = query
        )
        // Actually, looking at the protocol, GET queries may use a different pattern.
        // For now, set device as initialized and let user configure.
        // TODO: Discover GET/query commands when sniffing the app

        builder.setDeviceState(GBDevice.State.INITIALIZED)
        return builder
    }

    override fun onSocketRead(data: ByteArray) {
        if (LOG.isTraceEnabled) {
            LOG.trace("Received {} bytes: {}", data.size, data.toHexString())
        }

        val proto = protocol ?: return
        val events = proto.decodeResponse(data)

        for (event in events) {
            evaluateGBDeviceEvent(event)
        }
    }

    /**
     * Send a command to the headphones.
     */
    fun sendCommand(command: ByteArray) {
        val builder = createTransactionBuilder("ugreen_command")
        builder.write(command)
        builder.queue()
    }

    /**
     * Set ANC mode.
     */
    fun setAncMode(mode: Byte) {
        val proto = protocol ?: return
        sendCommand(proto.encodeSetAncMode(mode))
    }

    /**
     * Set EQ preset.
     */
    fun setEqPreset(preset: Byte) {
        val proto = protocol ?: return
        sendCommand(proto.encodeSetEqPreset(preset))
    }

    /**
     * Set game mode.
     */
    fun setGameMode(enabled: Boolean) {
        val proto = protocol ?: return
        sendCommand(proto.encodeSetGameMode(enabled))
    }

    /**
     * Set wind noise reduction.
     */
    fun setWindNoise(enabled: Boolean) {
        val proto = protocol ?: return
        sendCommand(proto.encodeSetWindNoise(enabled))
    }

    /**
     * Set spatial audio.
     */
    fun setSpatialAudio(enabled: Boolean) {
        val proto = protocol ?: return
        sendCommand(proto.encodeSetSpatialAudio(enabled))
    }

    override fun dispose() {
        synchronized(ConnectionMonitor) {
            protocol = null
            super.dispose()
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(UgreenStudioProDeviceSupport::class.java)
        private const val MAX_MTU = 1024

        // Standard Serial Port Profile UUID for RFCOMM
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private fun ByteArray.toHexString(): String {
            return joinToString(" ") { String.format("%02X", it.toInt() and 0xFF) }
        }
    }
}
