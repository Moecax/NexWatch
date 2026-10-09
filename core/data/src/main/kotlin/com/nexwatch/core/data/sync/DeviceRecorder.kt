package com.nexwatch.core.data.sync

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.DeviceDao
import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.database.DeviceEventEntity
import com.nexwatch.core.model.DeviceEventType
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal const val FITCLOUD_SDK_VERSION = "3.0.2.4" // third_party/maven's vendored version (README.md)

/**
 * Keeps the §5.3 `device` row for the bound watch. The normaliser attributes every record to the most
 * recently bound device and processes nothing without one, so this has to run before each normalisation.
 */
class DeviceRecorder @Inject constructor(
    private val deviceDao: DeviceDao,
    private val dispatchers: CoroutineDispatchers,
) {
    suspend fun record(address: String, firmwareVersion: String?): Unit = withContext(dispatchers.io) {
        val now = System.currentTimeMillis()
        val existing = deviceDao.findByAddress(address)
        when {
            existing == null -> {
                deviceDao.upsert(DeviceEntity(address, model = null, firmwareVersion, FITCLOUD_SDK_VERSION,
                    capabilitiesJson = null, boundAtMs = now))
                deviceDao.insertEvent(DeviceEventEntity(deviceAddress = address, type = DeviceEventType.BOUND.name,
                    details = null, at = now))
            }
            firmwareVersion == null || firmwareVersion == existing.firmwareVersion -> Unit
            // Capabilities may not be read yet on the first record, so a null version is "unknown", not an update.
            existing.firmwareVersion == null -> deviceDao.upsert(existing.copy(firmwareVersion = firmwareVersion))
            else -> {
                deviceDao.upsert(existing.copy(firmwareVersion = firmwareVersion))
                deviceDao.insertEvent(DeviceEventEntity(deviceAddress = address,
                    type = DeviceEventType.FIRMWARE_UPDATED.name,
                    details = "${existing.firmwareVersion} -> $firmwareVersion", at = now))
            }
        }
    }
}
