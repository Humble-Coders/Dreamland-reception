package com.example.dreamland_reception.grc

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.printing.PDFPageable
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.image.BufferedImage
import java.awt.print.PrinterJob
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.Base64
import javax.print.PrintServiceLookup

/** Field values for one guest's Guest Registration Card. Blank fields render as empty fill-in lines. */
data class GrcData(
    val hotelName: String = "",
    val hotelAddress: String = "",
    val hotelContact: String = "",
    val folioNo: String = "",
    val date: String = "",
    val guestName: String = "",
    val guestPhone: String = "",
    val gender: String = "",
    val dob: String = "",
    val idNumber: String = "",
    val address: String = "",
    val nationality: String = "",
    val roomNumber: String = "",
    val roomCategory: String = "",
    val checkIn: String = "",
    val checkOut: String = "",
    val nights: String = "",
    val adults: String = "",
    val children: String = "",
    val advance: String = "",
    val purpose: String = "",
    val idImageUrls: List<String> = emptyList(),
)

/**
 * Renders a Guest Registration Card from an editable HTML template to a PDF, then prints it via
 * the same PDFBox path used by the billing screen (`PrinterJob` + `PDFPageable`).
 *
 * The template uses `{{token}}` placeholders (see [DEFAULT_TEMPLATE]); `{{idImages}}` is replaced
 * with the captured ID document images, embedded as base64 data URIs so printing works offline.
 */
object GrcRenderer {

    /** Fills [templateHtml] (or [DEFAULT_TEMPLATE] when blank) with [data] and renders to PDF bytes. */
    suspend fun renderPdf(templateHtml: String, data: GrcData): ByteArray = withContext(Dispatchers.IO) {
        val html = fill(templateHtml.ifBlank { DEFAULT_TEMPLATE }, data)
        val os = ByteArrayOutputStream()
        PdfRendererBuilder()
            .useFastMode()
            .withHtmlContent(html, null)
            .toStream(os)
            .run()
        os.toByteArray()
    }

    /** Rasterises the PDF to page images for on-screen preview. */
    fun pagesOf(pdf: ByteArray, dpi: Float = 144f): List<BufferedImage> =
        PDDocument.load(pdf).use { doc ->
            val renderer = PDFRenderer(doc)
            (0 until doc.numberOfPages).map { renderer.renderImageWithDPI(it, dpi) }
        }

    /** Prints the PDF to the named printer (same mechanism as the invoice print on the billing screen). */
    fun print(pdf: ByteArray, printerName: String) {
        val service = PrintServiceLookup.lookupPrintServices(null, null)
            .firstOrNull { it.name == printerName }
            ?: throw IllegalStateException("Printer '$printerName' not found")
        PDDocument.load(pdf).use { doc ->
            val job = PrinterJob.getPrinterJob()
            job.setPrintService(service)
            job.setPageable(PDFPageable(doc))
            job.print()
        }
    }

    // ── Template filling ──────────────────────────────────────────────────────

    private fun fill(template: String, d: GrcData): String {
        val tokens = mapOf(
            "hotelName" to d.hotelName,
            "hotelAddress" to d.hotelAddress,
            "hotelContact" to d.hotelContact,
            "folioNo" to d.folioNo,
            "date" to d.date,
            "guestName" to d.guestName,
            "guestPhone" to d.guestPhone,
            "gender" to d.gender,
            "dob" to d.dob,
            "idNumber" to d.idNumber,
            "address" to d.address,
            "nationality" to d.nationality,
            "roomNumber" to d.roomNumber,
            "roomCategory" to d.roomCategory,
            "checkIn" to d.checkIn,
            "checkOut" to d.checkOut,
            "nights" to d.nights,
            "adults" to d.adults,
            "children" to d.children,
            "advance" to d.advance,
            "purpose" to d.purpose,
        )
        var out = template
        for ((k, v) in tokens) out = out.replace("{{$k}}", esc(v))
        // {{idImages}} carries pre-built markup, so substitute it last and un-escaped.
        out = out.replace("{{idImages}}", idImagesHtml(d.idImageUrls))
        return out
    }

    private fun idImagesHtml(urls: List<String>): String {
        if (urls.isEmpty()) return "<div class=\"id-empty\">No ID document captured</div>"
        val imgs = urls.mapNotNull { url ->
            fetchAsDataUri(url)?.let { "<div class=\"id-box\"><img src=\"$it\" /></div>" }
        }
        return if (imgs.isEmpty()) "<div class=\"id-empty\">ID image unavailable</div>" else imgs.joinToString("")
    }

    private fun fetchAsDataUri(url: String): String? = runCatching {
        val bytes = URL(url).readBytes()
        val mime = if (url.substringBefore('?').endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"
        "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
    }.getOrNull()

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    // ── Default template (valid XHTML for openhtmltopdf) ────────────────────────

    val DEFAULT_TEMPLATE: String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8" />
<style>
  @page { size: A4; margin: 1.4cm; }
  * { box-sizing: border-box; }
  body { font-family: Helvetica, Arial, sans-serif; color: #1a1a1a; font-size: 11px; }
  .header { text-align: center; border-bottom: 2px solid #0d1f17; padding-bottom: 8px; margin-bottom: 6px; }
  .hotel-name { font-size: 20px; font-weight: bold; letter-spacing: 1px; }
  .hotel-meta { font-size: 10px; color: #444; margin-top: 2px; }
  .title { text-align: center; font-size: 13px; font-weight: bold; letter-spacing: 3px;
           background: #0d1f17; color: #ffffff; padding: 5px; margin: 10px 0; }
  .meta-row { width: 100%; margin-bottom: 8px; }
  .meta-row td { font-size: 10px; }
  table.fields { width: 100%; border-collapse: collapse; margin-bottom: 10px; }
  table.fields td { padding: 5px 6px; vertical-align: bottom; }
  .label { color: #555; font-size: 9px; text-transform: uppercase; letter-spacing: 0.5px; }
  .value { border-bottom: 1px solid #999; min-height: 14px; font-size: 12px; padding-bottom: 2px; }
  .section { font-size: 11px; font-weight: bold; color: #0d1f17; border-bottom: 1px solid #0d1f17;
             padding-bottom: 3px; margin: 12px 0 4px 0; letter-spacing: 1px; }
  .id-row { width: 100%; }
  .id-box { display: inline-block; width: 46%; border: 1px solid #bbb; padding: 4px; margin: 1% 1% 0 0; text-align: center; }
  .id-box img { max-width: 100%; max-height: 160px; }
  .id-empty { color: #888; font-style: italic; padding: 8px 0; }
  .declaration { font-size: 9px; color: #444; margin-top: 14px; line-height: 1.4; text-align: justify; }
  .sign-row { width: 100%; margin-top: 28px; }
  .sign-row td { text-align: center; font-size: 10px; color: #555; }
  .sign-line { border-top: 1px solid #555; padding-top: 3px; }
  .footer { margin-top: 16px; border-top: 1px solid #ccc; padding-top: 4px;
            text-align: center; font-size: 8px; color: #999; }
</style>
</head>
<body>
  <div class="header">
    <div class="hotel-name">{{hotelName}}</div>
    <div class="hotel-meta">{{hotelAddress}}</div>
    <div class="hotel-meta">{{hotelContact}}</div>
  </div>

  <div class="title">GUEST REGISTRATION CARD</div>

  <table class="meta-row"><tr>
    <td>GRC No: <b>{{folioNo}}</b></td>
    <td style="text-align:right;">Date: <b>{{date}}</b></td>
  </tr></table>

  <div class="section">GUEST DETAILS</div>
  <table class="fields">
    <tr>
      <td width="60%"><div class="label">Full Name</div><div class="value">{{guestName}}</div></td>
      <td width="40%"><div class="label">Mobile</div><div class="value">{{guestPhone}}</div></td>
    </tr>
    <tr>
      <td><div class="label">Gender</div><div class="value">{{gender}}</div></td>
      <td><div class="label">Date of Birth</div><div class="value">{{dob}}</div></td>
    </tr>
    <tr>
      <td><div class="label">ID Number</div><div class="value">{{idNumber}}</div></td>
      <td><div class="label">Nationality</div><div class="value">{{nationality}}</div></td>
    </tr>
    <tr>
      <td colspan="2"><div class="label">Address</div><div class="value">{{address}}</div></td>
    </tr>
  </table>

  <div class="section">STAY DETAILS</div>
  <table class="fields">
    <tr>
      <td><div class="label">Room No.</div><div class="value">{{roomNumber}}</div></td>
      <td><div class="label">Room Type</div><div class="value">{{roomCategory}}</div></td>
      <td><div class="label">Nights</div><div class="value">{{nights}}</div></td>
    </tr>
    <tr>
      <td><div class="label">Check-In</div><div class="value">{{checkIn}}</div></td>
      <td><div class="label">Check-Out</div><div class="value">{{checkOut}}</div></td>
      <td><div class="label">Advance Paid (Rs.)</div><div class="value">{{advance}}</div></td>
    </tr>
    <tr>
      <td><div class="label">Adults</div><div class="value">{{adults}}</div></td>
      <td><div class="label">Children</div><div class="value">{{children}}</div></td>
      <td><div class="label">Purpose of Visit</div><div class="value">{{purpose}}</div></td>
    </tr>
  </table>

  <div class="section">IDENTITY DOCUMENT</div>
  <div class="id-row">{{idImages}}</div>

  <div class="declaration">
    I hereby declare that the above information is true and correct. I have read and agree to abide by the
    hotel's rules and regulations. I authorise the hotel to retain a copy of my identity document for the
    purpose of this stay as required by law. The hotel shall not be responsible for any loss of valuables
    not deposited at the reception.
  </div>

  <table class="sign-row"><tr>
    <td width="50%"><div class="sign-line">Guest Signature</div></td>
    <td width="50%"><div class="sign-line">Reception / Authorised Signatory</div></td>
  </tr></table>

  <div class="footer">This is a computer-generated Guest Registration Card. {{hotelName}} &#8226; {{hotelContact}}</div>
</body>
</html>
""".trimIndent()
}
