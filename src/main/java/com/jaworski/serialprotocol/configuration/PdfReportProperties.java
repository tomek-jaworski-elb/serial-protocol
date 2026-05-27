package com.jaworski.serialprotocol.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for PDF report generation.
 *
 * <p>Example {@code application.properties}:
 * <pre>
 * pdf.report.max-records=200
 * pdf.report.company-name=Serial Protocol
 * pdf.report.table-view-enabled=true
 * pdf.report.table-max-columns=6
 * pdf.report.font.name=DejaVuSans
 * pdf.report.font.size.regular=10
 * pdf.report.font.size.small=8
 * pdf.report.font.size.heading=13
 * pdf.report.font.size.title=16
 * </pre>
 *
 * <p>Font TTF files must be placed in {@code src/main/resources/fonts/} as:
 * <ul>
 *   <li>{fontName}.ttf — regular variant</li>
 *   <li>{fontName}-Bold.ttf — bold variant</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "pdf.report")
@Validated
public class PdfReportProperties {

    @Valid
    private FontConfig font = new FontConfig();

    /** Maximum number of records allowed per single PDF report request. */
    @Min(1) @Max(10000)
    private int maxRecords = 100;

    /** Company/organization name displayed in PDF header */
    private String companyName = "Serial Protocol";

    /** Enable table view for compatible entities */
    private boolean tableViewEnabled = true;

    /** Maximum columns threshold for automatic TABLE_VIEW selection */
    @Min(1) @Max(20)
    private int tableMaxColumns = 6;

    public FontConfig getFont() { return font; }
    public void setFont(FontConfig font) { this.font = font; }
    public int getMaxRecords() { return maxRecords; }
    public void setMaxRecords(int maxRecords) { this.maxRecords = maxRecords; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public boolean isTableViewEnabled() { return tableViewEnabled; }
    public void setTableViewEnabled(boolean tableViewEnabled) { this.tableViewEnabled = tableViewEnabled; }
    public int getTableMaxColumns() { return tableMaxColumns; }
    public void setTableMaxColumns(int tableMaxColumns) { this.tableMaxColumns = tableMaxColumns; }

    /**
     * Font configuration — defines the font family name and size variants.
     */
    public static class FontConfig {

        /**
         * Font family name matching {@code /fonts/{name}.ttf} and
         * {@code /fonts/{name}-Bold.ttf} on the classpath.
         */
        private String name = "DejaVuSans";

        @Valid
        private SizeConfig size = new SizeConfig();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public SizeConfig getSize() { return size; }
        public void setSize(SizeConfig size) { this.size = size; }

        /**
         * Font size variants used throughout the PDF report.
         */
        public static class SizeConfig {

            @Min(6) @Max(24)
            private int regular = 10;

            @Min(6) @Max(18)
            private int small = 8;

            @Min(8) @Max(30)
            private int heading = 13;

            @Min(10) @Max(36)
            private int title = 16;

            public int getRegular() { return regular; }
            public void setRegular(int regular) { this.regular = regular; }
            public int getSmall() { return small; }
            public void setSmall(int small) { this.small = small; }
            public int getHeading() { return heading; }
            public void setHeading(int heading) { this.heading = heading; }
            public int getTitle() { return title; }
            public void setTitle(int title) { this.title = title; }
        }
    }
}
