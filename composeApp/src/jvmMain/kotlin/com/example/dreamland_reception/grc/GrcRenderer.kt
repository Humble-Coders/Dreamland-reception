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
    val logoUrl: String = "",
    val hotelName: String = "",
    val hotelAddress: String = "",
    val hotelPhone: String = "",
    val hotelEmail: String = "",
    val folioNo: String = "",
    val date: String = "",
    val guestName: String = "",
    val guestPhone: String = "",
    val gender: String = "",
    val dob: String = "",
    val idType: String = "",
    val idNumber: String = "",
    val nationality: String = "",
    val address: String = "",
    val roomNumber: String = "",
    val roomCategory: String = "",
    val checkIn: String = "",
    val checkOut: String = "",
    val nights: String = "",
    val advance: String = "",
    val purpose: String = "",
    val idImageUrls: List<String> = emptyList(),
)

/**
 * Renders a Guest Registration Card from an editable HTML template to a PDF, then prints it via
 * the same PDFBox path used by the billing screen (`PrinterJob` + `PDFPageable`).
 *
 * The template uses `{{token}}` placeholders (see [DEFAULT_TEMPLATE]). `{{logoImg}}` and
 * `{{idImages}}` are replaced with images embedded as base64 data URIs (downloaded from their URLs)
 * so printing works offline. The default template mirrors the company invoice theme (brand green
 * header, rounded boxes, uppercase section labels) using table-based layout — openhtmltopdf does
 * not support CSS grid/flex or remote Google Fonts, so we fall back to a clean sans-serif.
 */
object GrcRenderer {

    /** Default company logo used in the GRC header when the hotel has no `grcLogoUrl` configured. */
    const val DEFAULT_LOGO_URL =
        "https://firebasestorage.googleapis.com/v0/b/humble-bill-engine.firebasestorage.app/o/assets%2FbookMyDreamland%2FlogoUrl-1780295509985.png?alt=media&token=45ce6c9e-31ff-455d-b62e-beb83e22c544"

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
            "hotelPhone" to d.hotelPhone,
            "hotelEmail" to d.hotelEmail,
            "folioNo" to d.folioNo,
            "date" to d.date,
            "guestName" to d.guestName,
            "guestPhone" to d.guestPhone,
            "gender" to d.gender,
            "dob" to d.dob,
            "idType" to d.idType,
            "idNumber" to d.idNumber,
            "nationality" to d.nationality,
            "address" to d.address,
            "roomNumber" to d.roomNumber,
            "roomCategory" to d.roomCategory,
            "checkIn" to d.checkIn,
            "checkOut" to d.checkOut,
            "nights" to d.nights,
            "advance" to d.advance,
            "purpose" to d.purpose,
        )
        var out = template
        for ((k, v) in tokens) out = out.replace("{{$k}}", esc(v))
        // Image tokens carry pre-built markup, so substitute them last and un-escaped.
        out = out.replace("{{logoImg}}", logoImgHtml(d.logoUrl))
        out = out.replace("{{idImages}}", idImagesHtml(d.idImageUrls))
        return out
    }

    private fun logoImgHtml(url: String): String {
        if (url.isBlank()) return ""
        val dataUri = fetchAsDataUri(url) ?: return ""
        return "<img class=\"logo\" src=\"$dataUri\" />"
    }

    private fun idImagesHtml(urls: List<String>): String {
        if (urls.isEmpty()) return "<div class=\"id-empty\">No ID document captured</div>"
        val imgs = urls.mapNotNull { url ->
            fetchAsDataUri(url)?.let { "<div class=\"idbox\"><img src=\"$it\" /></div>" }
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

    // ── Default template (valid XHTML for openhtmltopdf; mirrors the invoice theme) ──

    val DEFAULT_TEMPLATE: String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8" />
<style>
  @page { size: A4; margin: 12mm 12mm; }
  * { box-sizing: border-box; }
  body { font-family: 'DM Sans', Helvetica, Arial, sans-serif; color: #0f172a; font-size: 10px; line-height: 1.4; }
  table { border-collapse: collapse; }

  .head { width: 100%; border-bottom: 2px solid #15603a; padding-bottom: 10px; }
  .head td { vertical-align: middle; }
  .logo { max-width: 170px; max-height: 72px; }
  .cname { font-size: 20px; font-weight: 700; color: #15603a; text-align: center; letter-spacing: 0.2px; }
  .cmeta { font-size: 9px; color: #334155; text-align: center; margin-top: 3px; line-height: 1.5; }
  .cright { font-size: 9px; color: #334155; text-align: right; line-height: 1.6; }

  .docband { width: 100%; margin: 13px 0 4px; }
  .title { font-size: 14px; font-weight: 700; letter-spacing: 2.5px; text-transform: uppercase; color: #1e293b; }
  .grcno { text-align: right; font-size: 9.5px; color: #475569; line-height: 1.6; }

  .seclabel { font-size: 8px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; color: #64748b; margin: 12px 0 5px; }

  .box { border: 1px solid #e2e8f0; border-radius: 7px; background: #f8fafc; padding: 6px 8px; }
  .flds { width: 100%; }
  .flds td { padding: 5px 6px; vertical-align: top; }
  .k { font-size: 8px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; color: #64748b; }
  .v { font-size: 11px; color: #0f172a; border-bottom: 1px solid #cbd5e1; padding-bottom: 2px; min-height: 15px; }

  .idbox { display: inline-block; width: 48%; border: 1px solid #e2e8f0; border-radius: 7px; padding: 5px; margin: 4px 1% 0 0; text-align: center; }
  .idbox img { max-width: 100%; max-height: 175px; }
  .id-empty { color: #94a3b8; font-style: italic; padding: 8px 0; }

  .decl { font-size: 8.5px; color: #475569; line-height: 1.5; text-align: justify; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 7px; padding: 8px 10px; margin-top: 5px; }
  .terms { margin: 5px 0 0 0; padding-left: 15px; }
  .terms li { margin-bottom: 2px; }
  .sign { margin-top: 34px; }
  .signline { border-top: 1px solid #64748b; padding-top: 3px; font-size: 9px; color: #64748b; width: 240px; }
  /* Pinned to the bottom of the page so it always sits at the very end. */
  .footer { position: fixed; bottom: 0; left: 0; right: 0; border-top: 1px dashed #e2e8f0; padding-top: 4px; text-align: center; font-size: 8px; color: #94a3b8; }
</style>
</head>
<body>

  <table class="head"><tr>
    <td style="width:34%">{{logoImg}}</td>
    <td style="width:34%">
      <div class="cname">{{hotelName}}</div>
      <div class="cmeta">{{hotelAddress}}</div>
    </td>
    <td style="width:32%" class="cright">
      <div>Phone: {{hotelPhone}}</div>
      <div>{{hotelEmail}}</div>
    </td>
  </tr></table>

  <table class="docband"><tr>
    <td class="title">Guest Registration Card</td>
    <td class="grcno">GRC No: <b>{{folioNo}}</b><br />Date: <b>{{date}}</b></td>
  </tr></table>

  <div class="seclabel">Guest Details</div>
  <div class="box">
    <table class="flds">
      <tr>
        <td style="width:60%"><div class="k">Full Name</div><div class="v">{{guestName}}</div></td>
        <td style="width:40%"><div class="k">Mobile</div><div class="v">{{guestPhone}}</div></td>
      </tr>
      <tr>
        <td><div class="k">Gender</div><div class="v">{{gender}}</div></td>
        <td><div class="k">Date of Birth</div><div class="v">{{dob}}</div></td>
      </tr>
      <tr>
        <td><div class="k">ID Type</div><div class="v">{{idType}}</div></td>
        <td><div class="k">ID Number</div><div class="v">{{idNumber}}</div></td>
      </tr>
      <tr>
        <td colspan="2"><div class="k">Nationality</div><div class="v">{{nationality}}</div></td>
      </tr>
      <tr>
        <td colspan="2"><div class="k">Permanent Address</div><div class="v">{{address}}</div></td>
      </tr>
    </table>
  </div>

  <div class="seclabel">Stay Details</div>
  <div class="box">
    <table class="flds">
      <tr>
        <td style="width:34%"><div class="k">Room No.</div><div class="v">{{roomNumber}}</div></td>
        <td style="width:33%"><div class="k">Room Type</div><div class="v">{{roomCategory}}</div></td>
        <td style="width:33%"><div class="k">Nights</div><div class="v">{{nights}}</div></td>
      </tr>
      <tr>
        <td><div class="k">Check-In</div><div class="v">{{checkIn}}</div></td>
        <td><div class="k">Expected Check-Out</div><div class="v">{{checkOut}}</div></td>
        <td><div class="k">Advance Paid (Rs.)</div><div class="v">{{advance}}</div></td>
      </tr>
      <tr>
        <td colspan="3"><div class="k">Purpose of Visit</div><div class="v">{{purpose}}</div></td>
      </tr>
    </table>
  </div>

  <div class="seclabel">Identity Document</div>
  <div>{{idImages}}</div>

  <div class="seclabel">Declaration &amp; Terms</div>
  <div class="decl">
    I confirm that the information provided above is true and correct, and that the identity document
    attached is genuine and belongs to me. I agree to the following:
    <ul class="terms">
      <li>I will abide by the hotel's rules, policies and all applicable laws during my stay.</li>
      <li>Check-out is as per hotel policy; late check-out may attract additional charges.</li>
      <li>I am responsible for payment of all dues and for any loss or damage to hotel property.</li>
      <li>Only registered guests may occupy the room; visitors are not permitted to stay overnight.</li>
      <li>The hotel is not liable for cash or valuables not deposited at the reception.</li>
      <li>I authorise the hotel to retain a copy of my identity document for this stay as required by law.</li>
      <li>Smoking and any illegal or hazardous activity is prohibited as per hotel policy and law.</li>
    </ul>
  </div>

  <div class="sign"><div class="signline">Guest Signature &amp; Date</div></div>

  <div class="footer">Computer-generated Guest Registration Card &#8226; {{hotelName}} &#8226; {{hotelPhone}}</div>

</body>
</html>
""".trimIndent()
}
