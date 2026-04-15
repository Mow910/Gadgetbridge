package nodomain.freeyourgadget.gadgetbridge.service.devices.ugreen

import android.bluetooth.BluetoothAdapter
import android.content.Context
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

    private lateinit var protocol: UgreenStudioProProtocol

    init {
        addSupportedService(SPP_UUID)
    }

    override fun useAutoConnect(): Boolean {
        return true
    }

    override fun setContext(gbDevice: GBDevice, btAdapter: BluetoothAdapter, context: Context) {
        super.setContext(gbDevice, btAdapter, context)
        protocol = UgreenStudioProProtocol(gbDevice)
        LOG.info("UGREEN Studio Pro support initialized for device: {}", gbDevice.name)
    }

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        builder.setDeviceState(GBDevice.State.INITIALIZING)
        LOG.info("Initializing UGREEN Studio Pro")

        // Query current ANC mode
        val ancQuery = protocol.buildCommand(UgreenStudioProProtocol.SUBCMD_ANC)
        builder.write(*ancQuery)

        // Query current EQ preset
        val eqQuery = protocol.buildCommand(UgreenStudioProProtocol.SUBCMD_EQ)
        builder.write(*eqQuery)

        builder.setDeviceState(GBDevice.State.INITIALIZED)
        return builder
    }

    override fun onSocketRead(data: ByteArray) {
        if (LOG.isDebugEnabled) {
            LOG.debug("Received {} bytes: {}", data.size, data.toHexString())
        }

        val events = protocol.decodeResponse(data)
        for (event in events) {
            evaluateGBDeviceEvent(event)
        }
    }

    /**
     * Send a command to the headphones.
     */
    fun sendCommand(command: ByteArray) {
        if (!isConnected) {
            LOG.warn("Cannot send command: device not connected")
            return
        }
        LOG.debug("Sending command: {}", command.toHexString())
        val builder = createTransactionBuilder("ugreen_command")
        builder.write(*command)
        builder.queue()
    }

    /**
     * Set ANC mode.
     */
    fun setAncMode(mode: Byte) {
        sendCommand(protocol.encodeSetAncMode(mode))
    }

    /**
     * Set EQ preset.
     */
    fun setEqPreset(preset: Byte) {
        sendCommand(protocol.encodeSetEqPreset(preset))
    }

    /**
     * Set game mode.
     */
    fun setGameMode(enabled: Boolean) {
        sendCommand(protocol.encodeSetGameMode(enabled))
    }

    /**
     * Set wind noise reduction.
     */
    fun setWindNoise(enabled: Boolean) {
        sendCommand(protocol.encodeSetWindNoise(enabled))
    }

    /**
     * Set spatial audio.
     */
    fun setSpatialAudio(enabled: Boolean) {
        sendCommand(protocol.encodeSetSpatialAudio(enabled))
    }

    override fun dispose() {
        synchronized(ConnectionMonitor) {
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
