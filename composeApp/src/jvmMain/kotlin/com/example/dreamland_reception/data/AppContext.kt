package com.example.dreamland_reception.data

import com.example.dreamland_reception.data.config.AppConfig

/**
 * App-wide active hotel context.
 *
 * Initialised from [AppConfig] (which must be loaded before any ViewModel starts).
 * Writes go back through [AppConfig] so the config file stays in sync.
 */
object AppContext {

    @Volatile
    var hotelId: String = AppConfig.selectedHotelId
        private set

    @Volatile
    var hotelName: String = AppConfig.selectedHotelName
        private set

    /** Persist a hotel selection to the config file and update in-memory state. */
    fun setHotel(id: String, name: String) {
        hotelId = id
        hotelName = name
        AppConfig.saveHotelSelection(id, name)
    }

    /** Clear the selection (hotel deleted, etc.). */
    fun clearHotel() {
        hotelId = ""
        hotelName = ""
        AppConfig.clearHotelSelection()
    }

    /**
     * Re-sync from [AppConfig] after a fresh [AppConfig.load] call.
     * Called once from [DreamlandAppInitializer] after config is loaded.
     */
    internal fun syncFromConfig() {
        hotelId = AppConfig.selectedHotelId
        hotelName = AppConfig.selectedHotelName
    }
}
