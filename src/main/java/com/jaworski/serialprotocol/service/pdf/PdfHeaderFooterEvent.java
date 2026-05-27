package com.jaworski.serialprotocol.service.pdf;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfTemplate;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.pdf.ColumnText;

import java.awt.Color;

/**
 * Page event handler that renders a footer on every PDF page.
 *
 * <p>Footer layout (single line, 8pt):
 * <pre>
 *   CompanyName                                       Page X of Y
 * </pre>
 *
 * <p>A thin separator line is drawn above the footer text.
 *
 * <p>The "of Y" part uses a {@link PdfTemplate} placeholder that is filled in
 * when the document is closed ({@link #onCloseDocument}), so the total page
 * count is always accurate.
 */
public class PdfHeaderFooterEvent extends PdfPageEventHelper {

    private final String companyName;
    private final BaseFont baseFont;
    private final float fontSize;

    private PdfTemplate totalPagesTemplate;

    private static final float FOOTER_Y = 25f;
    private static final float LINE_Y = FOOTER_Y + 10f;
    private static final float MARGIN_H = 40f;

    /**
     * @param companyName text shown on the left side of the footer
     * @param baseFont    base font to use for footer text
     * @param fontSize    font size in points (typically 7–8)
     */
    public PdfHeaderFooterEvent(String companyName, BaseFont baseFont, float fontSize) {
        this.companyName = companyName;
        this.baseFont = baseFont;
        this.fontSize = fontSize;
    }

    @Override
    public void onOpenDocument(PdfWriter writer, Document document) {
        totalPagesTemplate = writer.getDirectContent().createTemplate(30, 16);
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        PdfContentByte cb = writer.getDirectContent();
        Rectangle pageSize = document.getPageSize();
        float left = MARGIN_H;
        float right = pageSize.getWidth() - MARGIN_H;

        // Separator line
        cb.setColorStroke(PdfColorScheme.DIVIDER);
        cb.setLineWidth(0.5f);
        cb.moveTo(left, LINE_Y);
        cb.lineTo(right, LINE_Y);
        cb.stroke();

        Font footerFont = new Font(baseFont, fontSize);
        footerFont.setColor(PdfColorScheme.TEXT_SECONDARY);

        // Left: company name
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                new Phrase(companyName, footerFont),
                left, FOOTER_Y, 0);

        // Right: "Page X of "
        int pageNumber = writer.getPageNumber();
        String pageText = "Page " + pageNumber + " of ";
        float pageTextWidth = baseFont.getWidthPoint(pageText, fontSize);

        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                new Phrase(pageText, footerFont),
                right - pageTextWidth - 20, FOOTER_Y, 0);

        // Placeholder for total page count (filled on close)
        cb.addTemplate(totalPagesTemplate, right - 20, FOOTER_Y);
    }

    @Override
    public void onCloseDocument(PdfWriter writer, Document document) {
        Font footerFont = new Font(baseFont, fontSize);
        footerFont.setColor(PdfColorScheme.TEXT_SECONDARY);

        int totalPages = writer.getPageNumber() - 1;
        ColumnText.showTextAligned(
                totalPagesTemplate,
                Element.ALIGN_LEFT,
                new Phrase(String.valueOf(totalPages), footerFont),
                0, 0, 0);
    }
}
