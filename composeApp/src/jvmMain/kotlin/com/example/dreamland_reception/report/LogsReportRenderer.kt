package com.example.dreamland_reception.report

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** One register line — a single guest within a stay (police/govt guest register format). */
data class LogsReportRow(
    val room: String,
    val guestName: String,
    val phone: String,
    val gender: String,
    val dobAge: String,
    val idType: String,
    val idNumber: String,
    val purpose: String,
    val address: String,
    val checkIn: String,
    val checkOut: String,
    // Reception managers on duty at check-in / check-out (blank when not recorded).
    val checkInBy: String = "",
    val checkOutBy: String = "",
    // Grouping: rows are emitted room-wise. `groupStart` marks the first guest of a
    // room (room number + check-in/out are shown once per room and the group gets a
    // divider); `guestPos` is the guest's position within the room, e.g. "1/2".
    val groupStart: Boolean = true,
    val guestPos: String = "",
    val guestCount: Int = 1,
)

/**
 * Renders a landscape A4 "Guest Register" PDF (hotel header + tabular logs) via openhtmltopdf —
 * the same HTML→PDF engine used for the GRC. Pages are previewed/printed with [com.example.dreamland_reception.grc.GrcRenderer].
 */
object LogsReportRenderer {

    suspend fun renderPdf(
        hotelName: String,
        hotelAddress: String,
        hotelPhone: String,
        rangeLabel: String,
        generatedAt: String,
        rows: List<LogsReportRow>,
    ): ByteArray = withContext(Dispatchers.IO) {
        val body = StringBuilder()
        if (rows.isEmpty()) {
            body.append("<tr><td colspan=\"11\" class=\"empty\">No stays in the selected period.</td></tr>")
        } else {
            rows.forEachIndexed { i, r ->
                // Divider above the first guest of each room (except the very first row).
                val trClass = if (r.groupStart && i != 0) " class=\"grp\"" else ""
                body.append("<tr").append(trClass).append(">")
                body.append("<td class=\"num\">").append(i + 1).append("</td>")
                // Room number (+ guest count) is room-level: shown once on the first guest.
                body.append("<td class=\"room\">")
                if (r.groupStart) {
                    body.append(esc(r.room))
                    body.append("<br /><span class=\"cnt\">")
                        .append(r.guestCount).append(if (r.guestCount == 1) " guest" else " guests")
                        .append("</span>")
                }
                body.append("</td>")
                body.append("<td>").append(esc(r.guestName))
                if (r.guestPos.isNotBlank()) body.append("<br /><span class=\"pos\">Guest ").append(esc(r.guestPos)).append("</span>")
                body.append("</td>")
                body.append("<td>").append(esc(r.phone)).append("</td>")
                body.append("<td>").append(esc(r.gender)).append("</td>")
                body.append("<td>").append(esc(r.dobAge)).append("</td>")
                body.append("<td>")
                if (r.idType.isNotBlank()) body.append("<b>").append(esc(r.idType)).append("</b><br />")
                body.append(esc(r.idNumber)).append("</td>")
                body.append("<td class=\"addr\">").append(esc(r.address)).append("</td>")
                // Check-in/out (with the manager on duty) repeated for every guest of the room.
                body.append("<td>").append(esc(r.checkIn))
                if (r.checkInBy.isNotBlank()) body.append("<br /><span class=\"by\">by ").append(esc(r.checkInBy)).append("</span>")
                body.append("</td>")
                body.append("<td>").append(esc(r.checkOut))
                if (r.checkOutBy.isNotBlank()) body.append("<br /><span class=\"by\">by ").append(esc(r.checkOutBy)).append("</span>")
                body.append("</td>")
                body.append("<td>").append(esc(r.purpose)).append("</td>")
                body.append("</tr>")
            }
        }

        val html = TEMPLATE
            .replace("{{hotelName}}", esc(hotelName))
            .replace("{{hotelAddress}}", esc(hotelAddress))
            .replace("{{hotelPhone}}", esc(hotelPhone))
            .replace("{{rangeLabel}}", esc(rangeLabel))
            .replace("{{generatedAt}}", esc(generatedAt))
            .replace("{{count}}", rows.size.toString())
            .replace("{{rows}}", body.toString())

        val os = ByteArrayOutputStream()
        PdfRendererBuilder()
            .useFastMode()
            .withHtmlContent(html, null)
            .toStream(os)
            .run()
        os.toByteArray()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private val TEMPLATE = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8" />
<style>
  @page { size: A4 landscape; margin: 10mm 10mm 12mm 10mm; }
  * { box-sizing: border-box; }
  body { font-family: Helvetica, Arial, sans-serif; color: #0f172a; font-size: 8px; }
  .head { width: 100%; border-bottom: 2px solid #15603a; padding-bottom: 6px; }
  .hotel { font-size: 16px; font-weight: 700; color: #15603a; text-align: center; }
  .meta { font-size: 8px; color: #334155; text-align: center; margin-top: 2px; }
  .title { text-align: center; font-size: 10px; font-weight: 700; letter-spacing: 2px; text-transform: uppercase;
           color: #ffffff; background: #14532d; padding: 4px; margin: 8px 0 4px; }
  .sub { width: 100%; font-size: 8px; color: #475569; margin-bottom: 6px; }
  .sub td { padding: 1px 0; }
  .sub td.r { text-align: right; }
  table.logs { width: 100%; border-collapse: collapse; }
  /* Repeat the column header at the top of every page when the table spans pages. */
  table.logs thead { display: table-header-group; }
  table.logs thead th { background: #14532d; color: #fff; font-size: 7.5px; text-transform: uppercase;
                        letter-spacing: 0.2px; padding: 5px 4px; text-align: left; border: 1px solid #14532d; }
  table.logs tbody td { padding: 4px 4px; border: 1px solid #e2e8f0; vertical-align: top; }
  table.logs tbody tr:nth-child(even) { background: #f1f5f9; }
  table.logs tbody tr { page-break-inside: avoid; }
  /* Thicker rule above the first guest of each room, so rooms read as blocks. */
  table.logs tbody tr.grp td { border-top: 2px solid #15603a; }
  td.room { font-weight: 700; color: #15603a; }
  .cnt { font-weight: 400; color: #64748b; font-size: 7px; }
  .pos { color: #64748b; font-size: 7px; }
  .by { color: #15603a; font-size: 7px; font-style: italic; }
  td.num { text-align: center; color: #64748b; }
  td.addr { font-size: 7.5px; }
  td.empty { text-align: center; color: #94a3b8; font-style: italic; padding: 14px; }
  .foot { margin-top: 8px; text-align: center; font-size: 7px; color: #94a3b8; }
</style>
</head>
<body>
  <div class="head">
    <div class="hotel">{{hotelName}}</div>
    <div class="meta">{{hotelAddress}}</div>
    <div class="meta">Phone: {{hotelPhone}}</div>
  </div>

  <div class="title">Guest Register / Stay Logs</div>

  <table class="sub"><tr>
    <td>Period: <b>{{rangeLabel}}</b> &#160;&#160; Total entries: <b>{{count}}</b></td>
    <td class="r">Generated: {{generatedAt}}</td>
  </tr></table>

  <table class="logs">
    <thead>
      <tr>
        <th style="width:3%">#</th>
        <th style="width:6%">Room</th>
        <th style="width:13%">Guest Name</th>
        <th style="width:9%">Phone</th>
        <th style="width:6%">Gender</th>
        <th style="width:9%">DOB / Age</th>
        <th style="width:12%">Govt ID</th>
        <th style="width:15%">Address</th>
        <th style="width:9%">Check-In</th>
        <th style="width:9%">Check-Out</th>
        <th style="width:9%">Purpose</th>
      </tr>
    </thead>
    <tbody>
      {{rows}}
    </tbody>
  </table>

  <div class="foot">Computer-generated guest register — {{hotelName}}</div>
</body>
</html>
""".trimIndent()
}
