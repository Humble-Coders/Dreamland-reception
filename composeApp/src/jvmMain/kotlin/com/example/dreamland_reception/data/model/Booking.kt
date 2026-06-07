package com.example.dreamland_reception.data.model

import java.util.Date

data class GuestDetail(
    val name: String = "",
    val phone: String = "",
    val idProofVerified: Boolean = false,
    val gender: String = "",
    val govIdNumber: String = "",
    val address: String = "",
    val dob: String = "",
    val age: Int = 0,
)

data class Booking(
    val id: String = "",
    val hotelId: String = "",
    val hotelName: String = "",
    val userId: String = "",
    val userName: String = "",
    // Guest info (primary stored in Firestore guestDetails map; all guests in allGuests list)
    val guestName: String = "",
    val guestPhone: String = "",
    val allGuestDetails: List<GuestDetail> = emptyList(),
    // Room
    val roomCategoryId: String = "",
    val roomCategoryName: String = "",
    val roomInstanceId: String = "",
    val roomNumber: String = "",
    // Dates (stored in Firestore as checkInDate / checkOutDate)
    val checkIn: Date = Date(),
    val checkOut: Date = Date(),
    // Guests (stored in Firestore as guests map)
    val adults: Int = 1,
    val children: Int = 0,
    // Options (stored in Firestore as options map)
    val breakfastIncluded: Boolean = false,
    val breakfastPricePerDay: Double = 0.0,
    val earlyCheckInEnabled: Boolean = false,
    val earlyCheckInCharge: Double = 0.0,
    val lateCheckOutEnabled: Boolean = false,
    val lateCheckOutCharge: Double = 0.0,
    val status: String = "CONFIRMED",       // CONFIRMED | PENDING_PAYMENT | COMPLETED | CANCELLED | NO_SHOW
    val source: String = "APP",             // APP | WALK_IN | from bookingSources.name
    val sourceId: String = "",              // bookingSources document id (empty for legacy records)
    val totalAmount: Double = 0.0,
    val advancePaidAmount: Double = 0.0,
    val advancePaymentMethod: String = "CASH",   // CASH | BANK
    val notes: String = "",
    val groupBookingId: String = "",
    val createdAt: Date = Date(),
    // No-show audit fields (set only when status = NO_SHOW)
    val noShowMarkedAt: Date? = null,
    val noShowRefundStatus: String = "",   // "" | "PENDING" | "REFUNDED" | "FORFEITED" | "PARTIAL"
    val noShowRefundNote: String = "",
    // Razorpay refund flow (set by Cloud Function on app-originated bookings)
    val paymentOrderId: String = "",                 // payments/{paymentOrderId}; "" for walk-ins
    val cancellationLockedAt: Date? = null,          // 10-min guest cancel grace window
    val advancePaidAmountPaise: Long = 0L,           // server-of-truth in paise
    // Cancellation audit (populated by cancelBookingByReceptionHttp)
    val cancellationSource: String = "",             // "" | "USER" | "RECEPTION" | "PAYMENT_FAILURE" | "ADMIN"
    val cancellationReason: String = "",
    val cancelledByReceptionUserId: String = "",
    val cancellationRefundId: String = "",           // Razorpay refund_id
    val cancellationRefundAmountPaise: Long = 0L,
    val cancellationRefundStatus: String = "",       // "" | "INITIATED" | "COMPLETED" | "FAILED"
    val cancellationRefundMode: String = "",         // "" | "POLICY" | "FULL" | "FIXED"
)
