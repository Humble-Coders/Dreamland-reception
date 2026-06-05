package com.example.dreamland_reception.data.model

import java.util.Date

data class GuestRecord(
    val name: String = "",
    val phone: String = "",
    val idProofVerified: Boolean = false,
    val gender: String = "",
    val idType: String = "",                         // e.g. Aadhaar | PAN | Passport | Driving Licence | Voter ID
    val govIdNumber: String = "",
    val govIdPictures: List<String> = emptyList(),   // URLs of the scanned ID images
    val purpose: String = "",                        // purpose of visit
    val address: String = "",
    val dob: String = "",
    val age: Int = 0,
    val grcNumber: String = "",                      // GRC serial issued for this guest (blank if not printed)
)

data class Stay(
    val id: String = "",
    val hotelId: String = "",
    val bookingId: String = "",
    val userId: String = "",
    val userName: String = "",
    val guestName: String = "",
    val guestPhone: String = "",
    val roomInstanceId: String = "",
    val roomNumber: String = "",
    val roomCategoryId: String = "",
    val roomCategoryName: String = "",
    val checkInActual: Date = Date(),            // normalized check-in date (date-only, for night math)
    val trueCheckIn: Date? = null,               // actual system time when "Check-in Guest" was clicked
    val expectedCheckOut: Date = Date(),
    val checkOutActual: Date? = null,
    val status: String = "ACTIVE",   // ACTIVE | COMPLETED
    val adults: Int = 1,
    val children: Int = 0,
    val breakfast: Boolean = false,
    val extraBed: Boolean = false,
    val earlyCheckIn: Boolean = false,
    val lateCheckOut: Boolean = false,
    val earlyCheckInCharge: Double = 0.0,
    val lateCheckOutCharge: Double = 0.0,
    val advancePaidAmount: Double = 0.0,
    val advancePaymentMethod: String = "CASH",   // CASH | BANK
    // Agreed room rate per night for THIS stay (the price the receptionist confirmed
    // at check-in: offline rate for walk-ins, or an adjusted rate). The bill uses this
    // when > 0; 0 means "use the category's current price" (the original behaviour, and
    // the default for un-adjusted booking check-ins).
    val agreedPricePerNight: Double = 0.0,
    // Reception manager on duty when the room was checked in / checked out. Recorded
    // separately so each shift change is attributable. Blank for legacy stays or when
    // no manager was set. (The same name is also stamped onto ledger entries.)
    val checkInManager: String = "",
    val checkOutManager: String = "",
    // True once the advance was posted to Humble Ledger at check-in (DR Cash/Bank /
    // CR Advance Liability) so live cash/bank reflects it immediately. Checkout
    // reverses this posting and re-posts the authoritative advance, so it must know
    // whether an early posting exists. False for stays checked in before this existed.
    val ledgerAdvancePostedAtCheckIn: Boolean = false,
    val totalBilled: Double = 0.0,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val guests: List<GuestRecord> = emptyList(),
    val groupStayId: String = "",
)
