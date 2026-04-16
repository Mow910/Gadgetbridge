package nodomain.freeyourgadget.gadgetbridge.devices.ugreen

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettings
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLClassicDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.ugreen.UgreenStudioProDeviceSupport
import java.util.regex.Pattern

/**
 * Coordinator for UGREEN Studio Pro headphones.
 *
 * Matches Bluetooth devices with name "UGREEN Studio Pro" (case-insensitive).
 */
class UgreenStudioProCoordinator : AbstractBLClassicDeviceCoordinator() {

    override fun getManufacturer(): String {
        return "UGREEN"
    }

    override fun getSupportedDeviceName(): Pattern? {
        // Match "UGREEN Studio Pro" case-insensitively
        return Pattern.compile("UGREEN Studio Pro", Pattern.CASE_INSENSITIVE)
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_ugreen_studio_pro
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_headphones
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport?> {
        return UgreenStudioProDeviceSupport::class.java
    }

    override fun suggestUnbindBeforePair(): Boolean {
        return false
    }

    override fun supportsOSBatteryLevel(device: GBDevice): Boolean {
        // Battery may be available via standard BLE Battery Service (0x180F)
        // or via RFCOMM notification frames. Enable the OS battery display.
        return true
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.HEADPHONES
    }

    override fun getDeviceSpecificSettings(device: GBDevice): DeviceSpecificSettings {
        val settings = DeviceSpecificSettings()

        // ANC mode selection
        settings.addRootScreen(R.xml.devicesettings_ugreen_anc)

        // EQ preset selection
        settings.addRootScreen(R.xml.devicesettings_ugreen_eq)

        // Calls and notifications
        settings.addRootScreen(DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS)
        settings.addSubScreen(
            DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS,
            R.xml.devicesettings_headphones
        )

        // CRITICAL: tell Gadgetbridge which preferences trigger onSendConfiguration()
        settings.addConnectedPreferences(
            DeviceSettingsPreferenceConst.PREF_UGREEN_ANC_MODE,
            DeviceSettingsPreferenceConst.PREF_UGREEN_EQUALIZER_PRESET,
        )

        return settings
    }
}
