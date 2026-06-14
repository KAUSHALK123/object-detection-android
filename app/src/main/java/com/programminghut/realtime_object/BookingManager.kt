package com.programminghut.realtime_object

import kotlinx.coroutines.delay

/**
 * Handles the automated booking of rides.
 * In a production environment, this would interface with the Uber/Lyft API.
 */
class BookingManager {

    data class RideDetails(
        val driverName: String,
        val carModel: String,
        val licensePlate: String,
        val etaMinutes: Int,
        val price: String
    )

    suspend fun bookRide(destination: String): RideDetails {
        // Simulate API latency
        delay(3000)
        
        // Mock data for a successful booking
        return RideDetails(
            driverName = "John",
            carModel = "White Toyota Camry",
            licensePlate = "7X B 4 9 2",
            etaMinutes = 4,
            price = "$12.50"
        )
    }
}
