package com.example.dreamland_reception.data.billing

import com.example.dreamland_reception.data.model.Bill
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.image.BufferedImage
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Date
import java.util.Locale

/**
 * Client for the **Humble Bill Engine** invoice-PDF service.
 *
 * Posts a [Bill] as GST tax-invoice data to the HBE Lambda, which renders a branded
 * PDF (branding/legal config lives server-side in Firestore per `appId`) and returns
 * a public S3 URL. The PDF is then downloaded and rasterised to page images via PDFBox
 * for the in-app viewer.
 *
 * Generation runs headless Chromium server-side (~3–15s), so a generous client timeout
 * is used. All public methods are `suspend` and run on [Dispatchers.IO].
 */
object HumbleBillEngine {

    private const val ENDPOINT = "https://ty7dvtg7bygzryorzmszp6ykjy0qlhsv.lambda-url.us-east-1.on.aws/"
    private const val APP_ID = "bookMyDreamland"

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    private val gson = Gson()
    private val apiDateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ── Public API ──────────────────────────────────────────────────────────────

    /** Generates the invoice PDF for [bill] and returns its public URL. */
    suspend fun generateInvoiceUrl(bill: Bill, guestPhone: String = ""): String =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(InvoiceRequest(appId = APP_ID, data = buildData(bill, guestPhone)))
            val request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(60)) // headless Chromium render is slow
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            val parsed = runCatching { gson.fromJson(response.body(), InvoiceResponse::class.java) }.getOrNull()
            val url = parsed?.url
            if (response.statusCode() !in 200..299 || url.isNullOrBlank()) {
                val reason = parsed?.details ?: parsed?.error ?: response.body()
                throw RuntimeException("Bill engine failed (${response.statusCode()}): $reason")
            }
            url
        }

    /** Downloads the PDF at [url] and rasterises each page to a [BufferedImage]. */
    suspend fun renderPdfPages(url: String, dpi: Float = 144f): List<BufferedImage> =
        withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() !in 200..299) {
                throw RuntimeException("Could not download invoice PDF (${response.statusCode()})")
            }
            PDDocument.load(response.body()).use { doc ->
                val renderer = PDFRenderer(doc)
                (0 until doc.numberOfPages).map { renderer.renderImageWithDPI(it, dpi) }
            }
        }

    // ── Bill → request mapping ───────────────────────────────────────────────────

    private fun buildData(bill: Bill, guestPhone: String): InvoiceData {
        val roomLabel = when {
            bill.roomNumbers.isNotEmpty() -> bill.roomNumbers.joinToString(", ")
            bill.roomNumber.isNotBlank() -> bill.roomNumber
            else -> ""
        }
        val payments = buildList {
            if (bill.advancePayment > 0) {
                add(PaymentEntry(bill.advancePayment, TxnInfo(apiDateFmt.format(bill.createdAt), "ADVANCE", null)))
            }
            bill.transactions.forEach {
                add(PaymentEntry(it.amount, TxnInfo(apiDateFmt.format(it.createdAt), "PAYMENT", it.id)))
            }
        }
        return InvoiceData(
            // STRICT: always the authoritative Humble Ledger invoice number (e.g.
            // INV-000044) so the printed bill reconciles 1:1 with the accounting ledger.
            // The caller (generateInvoicePdf) guarantees this is populated before
            // rendering, so we never fall back to the opaque Firestore bill id.
            invoiceNumber = bill.ledgerInvoiceNumber,
            issueDate = apiDateFmt.format(Date()),
            dueDate = bill.checkOutDate?.let { apiDateFmt.format(it) },
            description = if (roomLabel.isNotBlank()) "Stay — Room $roomLabel" else "Hotel Stay",
            status = mapStatus(bill.status),
            currency = "INR",
            subtotal = bill.subtotal,
            // Discount applied BEFORE tax: subtotal is the pre-discount base, `discount` is
            // deducted from it, and taxAmount/total are already computed on the discounted
            // base. Omitted (null) when there's no discount so non-discounted invoices are
            // unchanged. The engine renders: Subtotal → Discount → Taxable → GST → Total.
            discount = bill.discountAmount.takeIf { it > 0.0 },
            taxRate = if (bill.taxEnabled) bill.taxPercentage else 0.0,
            taxAmount = bill.taxAmount,
            total = bill.totalAmount,
            // Cap at the invoice total: an intentional overpayment (to clear prior dues or
            // leave credit) settles THIS invoice to zero — the surplus lives on the guest's
            // ledger account, not on this bill. So the invoice never shows a negative balance.
            // Under-payments are unaffected (a real outstanding balance still shows).
            amountPaid = minOf(bill.totalPaid + bill.advancePayment, bill.totalAmount),
            totalRefunded = 0.0,
            customer = Customer(
                name = bill.guestName.ifBlank { null },
                phone = guestPhone.ifBlank { null },
                gstin = bill.guestGstin.ifBlank { null },   // rendered in BILL TO when present
            ),
            payments = payments.ifEmpty { null },
            lineItems = bill.items.map {
                // The engine treats `rate` as the PRE-TAX base (it adds tax on top to reach
                // the total). When the bill is GST-inclusive, item.unitPrice is the GROSS
                // price, so we back the tax out here — otherwise the line items wouldn't sum
                // to the subtotal/tax we send. Exclusive bills keep unitPrice as-is.
                val baseRate = if (bill.taxInclusive && it.taxPercentage > 0)
                    it.unitPrice / (1.0 + it.taxPercentage / 100.0)
                else it.unitPrice
                LineItem(
                    name = it.name,
                    hsn = if (it.type == "ROOM") "996311" else null,
                    qty = it.quantity,
                    rate = baseRate,
                )
            }.ifEmpty { null },
        )
    }

    /** Maps the internal bill status to the HBE invoice status vocabulary. */
    private fun mapStatus(status: String): String = when (status) {
        "PAID" -> "PAID"
        "PARTIAL" -> "PARTIALLY_PAID"
        else -> "FINALIZED"
    }

    // ── Wire models (serialised by Gson; null fields are omitted) ─────────────────

    private data class InvoiceRequest(val appId: String, val data: InvoiceData)

    private data class InvoiceData(
        val invoiceNumber: String,
        val issueDate: String,
        val dueDate: String?,
        val description: String?,
        val status: String,
        val currency: String,
        val subtotal: Double,
        val discount: Double? = null,   // pre-tax discount; omitted when 0
        val taxRate: Double,
        val taxAmount: Double,
        val total: Double,
        val amountPaid: Double,
        val totalRefunded: Double,
        val customer: Customer?,
        val payments: List<PaymentEntry>?,
        val lineItems: List<LineItem>?,
    )

    private data class Customer(
        val name: String?,
        val email: String? = null,
        val phone: String? = null,
        val gstin: String? = null,
    )

    private data class PaymentEntry(val amount: Double, val transaction: TxnInfo)
    private data class TxnInfo(val date: String?, val postingType: String, val id: String?)
    private data class LineItem(val name: String, val hsn: String?, val qty: Int, val rate: Double)

    private data class InvoiceResponse(
        val message: String? = null,
        val url: String? = null,
        val error: String? = null,
        val details: String? = null,
    )
}
