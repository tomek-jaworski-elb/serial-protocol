package com.jaworski.serialprotocol.service.pdf;

import java.awt.Color;

/**
 * Centralized color palette for PDF reports (gray-black minimalist theme).
 * All colors are defined as static constants for easy maintenance and consistency.
 */
public final class PdfColorScheme {

    private PdfColorScheme() {
        // Utility class, no instantiation
    }

    // ── Primary Colors ──────────────────────────────────────────────────────

    /** Header background: dark gray #1F2937 (31, 41, 55) */
    public static final Color HEADER_BG = new Color(31, 41, 55);

    /** Header text: white #FFFFFF on dark background */
    public static final Color HEADER_TEXT = Color.WHITE;

    /** Main text: very dark gray #111827 (17, 24, 39) */
    public static final Color TEXT_PRIMARY = new Color(17, 24, 39);

    /** Secondary text: medium gray #6B7280 (107, 114, 128) */
    public static final Color TEXT_SECONDARY = new Color(107, 114, 128);

    // ── Structural Colors ───────────────────────────────────────────────────

    /** Separator lines: medium gray #D1D5DB (209, 213, 219) */
    public static final Color DIVIDER = new Color(209, 213, 219);

    /** Table row background (odd rows): white #FFFFFF */
    public static final Color ROW_ODD = Color.WHITE;

    /** Table row background (even rows): light gray #F9FAFB (249, 250, 251) */
    public static final Color ROW_EVEN = new Color(249, 250, 251);

    /** Table header background: dark gray #1F2937 (same as HEADER_BG) */
    public static final Color TABLE_HEADER_BG = HEADER_BG;

    /** Table header text: white #FFFFFF */
    public static final Color TABLE_HEADER_TEXT = HEADER_TEXT;

    // ── Card/Element Colors ─────────────────────────────────────────────────

    /** Card header border: medium gray #D1D5DB */
    public static final Color CARD_BORDER = DIVIDER;

    /** Card counter badge: dark gray #4B5563 (75, 85, 99) */
    public static final Color CARD_COUNTER_BG = new Color(75, 85, 99);

    /** Card counter text: white */
    public static final Color CARD_COUNTER_TEXT = Color.WHITE;

    // ── Card Frame Colors ────────────────────────────────────────────────────

    /** Card frame background: very light gray #F3F4F6 (243, 244, 246) */
    public static final Color CARD_FRAME_BG = new Color(243, 244, 246);

    /** Card frame border: light gray #E5E7EB (229, 231, 235) */
    public static final Color CARD_FRAME_BORDER = new Color(229, 231, 235);

    // ── Accent Colors ───────────────────────────────────────────────────────

    /** Accent bar on cover page: dark blue #1E40AF (30, 64, 175) */
    public static final Color ACCENT_BLUE = new Color(30, 64, 175);

    /** Highlight for warnings/info: dark orange #EA580C (234, 88, 12) */
    public static final Color ACCENT_ORANGE = new Color(234, 88, 12);

    // ── Utility Methods ─────────────────────────────────────────────────────

    /**
     * Returns alternating row background color based on index.
     *
     * @param rowIndex 0-based row index
     * @return ROW_ODD for even indices, ROW_EVEN for odd indices
     */
    public static Color getRowBackgroundColor(int rowIndex) {
        return (rowIndex % 2 == 0) ? ROW_ODD : ROW_EVEN;
    }

    /**
     * Returns a contrasting text color for the given background.
     * For dark backgrounds (like HEADER_BG), returns white; otherwise returns TEXT_PRIMARY.
     *
     * @param bgColor background color to check
     * @return appropriate text color
     */
    public static Color getTextColorForBackground(Color bgColor) {
        // Simple brightness calculation: (R*299 + G*587 + B*114) / 1000
        int brightness = (bgColor.getRed() * 299 + bgColor.getGreen() * 587 + bgColor.getBlue() * 114) / 1000;
        return brightness < 128 ? Color.WHITE : TEXT_PRIMARY;
    }
}
