package com.example.dreamland_reception.data.model

import java.util.Date
import java.util.UUID

data class BillItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: String = "CUSTOM",   // ROOM | ORDER | SERVICE | CUSTOM
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val total: Double = unitPrice * quantity,
    val taxPercentage: Double = 0.0,
    // When true, this line's price already INCLUDES its GST (tax backed out of the price);
    // when false, GST is added on top. Per-item — toggled on the billing screen. Default false.
    val taxInclusive: Boolean = false,
    val refId: String = "",
    val notes: String = "",
)

data class PaymentTransaction(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double = 0.0,
    val method: String = "CASH",   // CASH | UPI | CARD
    val status: String = "PAID",
    val createdAt: Date = Date(),
)

data class Bill(
    val id: String = "",
    val hotelId: String = "",
    val stayId: String = "",             // primary stay ID (used for single and group bills)
    val stayIds: List<String> = emptyList(), // all stay IDs for group bills
    // Guest's Humble Ledger account UID (= stay.userId). The authoritative identity used
    // at settle so a returning guest who gave a NEW phone still maps to the SAME ledger
    // customer (balance never splits). Falls back to phone resolution only when blank.
    val userId: String = "",
    val guestName: String = "",
    val guestPhone: String = "",
    val guestGstin: String = "",          // optional customer GSTIN for B2B tax invoices
    val roomNumber: String = "",         // "22" or "22, 23, 24" for group
    val roomNumbers: List<String> = emptyList(), // ["22", "23", "24"] for group bills
    val checkInDate: Date? = null,
    val checkOutDate: Date? = null,
    val items: List<BillItem> = emptyList(),
    val taxEnabled: Boolean = false,
    val taxPercentage: Double = 18.0,
    // When true, item prices already INCLUDE GST (tax is backed out of the price);
    // when false, GST is added ON TOP of the prices. Default false = added on top.
    val taxInclusive: Boolean = false,
    val discountType: String = "FLAT",   // FLAT | PERCENT
    val discountValue: Double = 0.0,
    val subtotal: Double = 0.0,
    val taxAmount: Double = 0.0,
    val discountAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val transactions: List<PaymentTransaction> = emptyList(),
    val totalPaid: Double = 0.0,
    val advancePayment: Double = 0.0,
    val advancePaymentMethod: String = "CASH",   // CASH | BANK
    val pendingAmount: Double = 0.0,
    val status: String = "PENDING",   // PENDING | PARTIAL | PAID
    val invoiceUrl: String = "",
    // ── Humble Ledger sync state (durable, survives app restarts) ──────────────
    // ledgerSynced flips to true only after the full double-entry settlement
    // succeeds. Unsynced finalized bills are retried on load. ledgerInvoiceNumber
    // is the authoritative accounting invoice number printed on the PDF.
    val ledgerSynced: Boolean = false,
    val ledgerSyncError: String = "",
    val ledgerInvoiceId: String = "",
    val ledgerInvoiceNumber: String = "",
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
)
