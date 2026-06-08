package com.example.dreamland_reception.report

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** One movement line in the printed daily book. */
data class DailyBookReportRow(
    val time: String,
    val particulars: String,
    val category: String,
    val by: String,
    val account: String,   // "Cash" | "Bank"
    val inAmount: Double,
    val outAmount: Double,
    val balance: Double,
)

/**
 * Renders an A4 "Daily Book" PDF — a plain-language cash/bank day register (opening, every in/out,
 * closing) via the same openhtmltopdf engine used for logs. Printed/previewed with GrcRenderer.
 */
object DailyBookReportRenderer {

    suspend fun renderPdf(
        hotelName: String,
        hotelAddress: String,
        hotelPhone: String,
        dateLabel: String,
        generatedAt: String,
        openingCash: Double, openingBank: Double,
        inCash: Double, inBank: Double,
        outCash: Double, outBank: Double,
        closingCash: Double, closingBank: Double,
        rows: List<DailyBookReportRow>,
    ): ByteArray = withContext(Dispatchers.IO) {
        val body = StringBuilder()
        if (rows.isEmpty()) {
            body.append("<tr><td colspan=\"8\" class=\"empty\">No cash or bank activity on this day.</td></tr>")
        } else {
            rows.forEachIndexed { i, r ->
                body.append("<tr>")
                body.append("<td class=\"num\">").append(i + 1).append("</td>")
                body.append("<td>").append(esc(r.time)).append("</td>")
                body.append("<td><b>").append(esc(r.category)).append("</b><br /><span class=\"desc\">").append(esc(r.particulars)).append("</span></td>")
                body.append("<td>").append(esc(r.by.ifBlank { "—" })).append("</td>")
                body.append("<td class=\"acct\">").append(esc(r.account)).append("</td>")
                body.append("<td class=\"in\">").append(if (r.inAmount > 0) money(r.inAmount) else "").append("</td>")
                body.append("<td class=\"out\">").append(if (r.outAmount > 0) money(r.outAmount) else "").append("</td>")
                body.append("<td class=\"bal\">").append(money(r.balance)).append("</td>")
                body.append("</tr>")
            }
        }

        val openingTotal = openingCash + openingBank
        val inTotal = inCash + inBank
        val outTotal = outCash + outBank
        val closingTotal = closingCash + closingBank

        val html = TEMPLATE
            .replace("{{hotelName}}", esc(hotelName))
            .replace("{{hotelAddress}}", esc(hotelAddress))
            .replace("{{hotelPhone}}", esc(hotelPhone))
            .replace("{{dateLabel}}", esc(dateLabel))
            .replace("{{generatedAt}}", esc(generatedAt))
            .replace("{{count}}", rows.size.toString())
            .replace("{{openingCash}}", money(openingCash)).replace("{{openingBank}}", money(openingBank)).replace("{{openingTotal}}", money(openingTotal))
            .replace("{{inCash}}", money(inCash)).replace("{{inBank}}", money(inBank)).replace("{{inTotal}}", money(inTotal))
            .replace("{{outCash}}", money(outCash)).replace("{{outBank}}", money(outBank)).replace("{{outTotal}}", money(outTotal))
            .replace("{{closingCash}}", money(closingCash)).replace("{{closingBank}}", money(closingBank)).replace("{{closingTotal}}", money(closingTotal))
            .replace("{{rows}}", body.toString())

        val os = ByteArrayOutputStream()
        val builder = PdfRendererBuilder().useFastMode()
        // The built-in PDF fonts (Helvetica) have no ₹ glyph — without a Unicode font it renders as
        // a missing-glyph box. Register a system font that includes ₹ (and Latin) so amounts print
        // with the real rupee symbol. Falls back to Helvetica if none is found.
        rupeeFontFile?.let { runCatching { builder.useFont(it, "ReportSans") } }
        builder.withHtmlContent(html, null).toStream(os).run()
        os.toByteArray()
    }

    /** A system TrueType font that contains the ₹ glyph (U+20B9), resolved once. */
    private val rupeeFontFile: java.io.File? by lazy {
        listOf(
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "/Library/Fonts/Arial Unicode.ttf",
            "/System/Library/Fonts/Supplemental/NotoSans-Regular.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        ).map { java.io.File(it) }.firstOrNull { it.isFile }
    }

    private fun money(v: Double): String {
        val r = Math.round(v * 100.0) / 100.0
        val s = if (r == r.toLong().toDouble()) "%,d".format(r.toLong()) else "%,.2f".format(r)
        return "&#8377;$s"
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private val TEMPLATE = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8" />
<style>
  @page { size: A4 portrait; margin: 12mm 10mm 12mm 10mm; }
  * { box-sizing: border-box; }
  body { font-family: 'ReportSans', Helvetica, Arial, sans-serif; color: #0f172a; font-size: 9px; }
  .head { width: 100%; border-bottom: 2px solid #15603a; padding-bottom: 6px; }
  .hotel { font-size: 17px; font-weight: 700; color: #15603a; text-align: center; }
  .meta { font-size: 8px; color: #334155; text-align: center; margin-top: 2px; }
  .title { text-align: center; font-size: 11px; font-weight: 700; letter-spacing: 2px; text-transform: uppercase;
           color: #ffffff; background: #14532d; padding: 5px; margin: 8px 0 6px; }
  .sub { width: 100%; font-size: 9px; color: #475569; margin-bottom: 8px; }
  .sub td { padding: 1px 0; }
  .sub td.r { text-align: right; }

  table.sum { width: 100%; border-collapse: collapse; margin-bottom: 10px; }
  table.sum th, table.sum td { border: 1px solid #cbd5e1; padding: 6px 8px; text-align: right; font-size: 10px; }
  table.sum th { background: #f1f5f9; color: #334155; text-transform: uppercase; font-size: 8px; letter-spacing: 0.4px; }
  table.sum td.lbl { text-align: left; font-weight: 700; color: #14532d; background: #f8fafc; }
  table.sum tr.closing td { font-weight: 700; color: #14532d; background: #ecfdf5; border-top: 2px solid #15603a; }
  .pos { color: #15803d; } .neg { color: #b91c1c; }

  table.book { width: 100%; border-collapse: collapse; }
  table.book thead { display: table-header-group; }
  table.book thead th { background: #14532d; color: #fff; font-size: 8px; text-transform: uppercase;
                        letter-spacing: 0.2px; padding: 6px 5px; text-align: left; border: 1px solid #14532d; }
  table.book tbody td { padding: 5px 5px; border: 1px solid #e2e8f0; vertical-align: top; }
  table.book tbody tr:nth-child(even) { background: #f8fafc; }
  table.book tbody tr { page-break-inside: avoid; }
  td.num { text-align: center; color: #64748b; }
  td.acct { font-weight: 700; color: #334155; }
  td.in, th.in { text-align: right; color: #15803d; font-weight: 700; }
  td.out, th.out { text-align: right; color: #b91c1c; font-weight: 700; }
  td.bal, th.bal { text-align: right; font-weight: 700; color: #0f172a; }
  .desc { color: #64748b; font-size: 8px; }
  td.empty { text-align: center; color: #94a3b8; font-style: italic; padding: 16px; }
  .foot { margin-top: 10px; text-align: center; font-size: 7px; color: #94a3b8; }
</style>
</head>
<body>
  <div class="head">
    <div class="hotel">{{hotelName}}</div>
    <div class="meta">{{hotelAddress}}</div>
    <div class="meta">Phone: {{hotelPhone}}</div>
  </div>

  <div class="title">Daily Book — Cash &amp; Bank</div>

  <table class="sub"><tr>
    <td>Date: <b>{{dateLabel}}</b> &#160;&#160; Entries: <b>{{count}}</b></td>
    <td class="r">Generated: {{generatedAt}}</td>
  </tr></table>

  <table class="sum">
    <thead><tr><th class="lbl" style="text-align:left">Summary</th><th>Cash</th><th>Bank</th><th>Total</th></tr></thead>
    <tbody>
      <tr><td class="lbl">Opening balance</td><td>{{openingCash}}</td><td>{{openingBank}}</td><td>{{openingTotal}}</td></tr>
      <tr><td class="lbl">Money in</td><td class="pos">{{inCash}}</td><td class="pos">{{inBank}}</td><td class="pos">{{inTotal}}</td></tr>
      <tr><td class="lbl">Money out</td><td class="neg">{{outCash}}</td><td class="neg">{{outBank}}</td><td class="neg">{{outTotal}}</td></tr>
      <tr class="closing"><td class="lbl">Closing balance</td><td>{{closingCash}}</td><td>{{closingBank}}</td><td>{{closingTotal}}</td></tr>
    </tbody>
  </table>

  <table class="book">
    <thead>
      <tr>
        <th style="width:4%">#</th>
        <th style="width:11%">Time</th>
        <th style="width:34%">Particulars</th>
        <th style="width:12%">By</th>
        <th style="width:8%">Acct</th>
        <th class="in" style="width:10%">In</th>
        <th class="out" style="width:10%">Out</th>
        <th class="bal" style="width:11%">Balance</th>
      </tr>
    </thead>
    <tbody>
      {{rows}}
    </tbody>
  </table>

  <div class="foot">Computer-generated daily book — {{hotelName}}. Closing balance reconciles with the live till.</div>
</body>
</html>
""".trimIndent()
}
