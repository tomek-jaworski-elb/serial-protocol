package com.jaworski.serialprotocol.service.db.custom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

/**
 * Downscales uploaded photos so a 56px table cell stops pulling a full camera image.
 *
 * <p>Uses only {@code javax.imageio} from the JDK — no extra dependency. That fixes
 * the set of formats it can read: JPEG, PNG and GIF. WebP and SVG have no reader in
 * JDK 21, and a raster thumbnail of a vector file would be pointless anyway, so those
 * are reported as unsupported and the caller serves the original.</p>
 */
public final class ThumbnailGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(ThumbnailGenerator.class);

  /** Long edge in pixels: enough for a 56px cell at 2x, and for a future 32px avatar. */
  public static final int MAX_EDGE = 160;

  /**
   * Bump when the output changes in any way — size, format, quality. Stored thumbnails
   * record the version that produced them and are rebuilt when it no longer matches, so
   * a bump both invalidates client caches and regenerates. The ETag embeds
   * this, so without a bump clients would keep a stale thumbnail forever: their
   * If-None-Match would keep matching and every response would be a 304.
   */
  public static final String VERSION = "v1";

  private static final Set<String> READABLE = Set.of("image/jpeg", "image/png", "image/gif");
  private static final String PNG = "image/png";

  private ThumbnailGenerator() {
  }

  public static boolean supports(String contentType) {
    return contentType != null && READABLE.contains(contentType);
  }

  /** Format the thumbnail is written in — PNG keeps transparency that GIF/PNG may carry. */
  public static String outputContentType(String sourceContentType) {
    return "image/jpeg".equals(sourceContentType) ? "image/jpeg" : PNG;
  }

  /**
   * @return the scaled bytes, or {@code null} if the source could not be read or is
   *     already small enough to be worth serving as-is.
   */
  public static byte[] scale(byte[] source, String contentType) {
    if (source == null || source.length == 0 || !supports(contentType)) {
      return null;
    }
    try {
      BufferedImage original = ImageIO.read(new ByteArrayInputStream(source));
      if (original == null) {
        // Content type said one thing, the bytes say another.
        LOG.debug("No ImageIO reader accepted the data despite contentType={}", contentType);
        return null;
      }
      int width = original.getWidth();
      int height = original.getHeight();
      if (width <= MAX_EDGE && height <= MAX_EDGE) {
        return null;
      }

      double factor = (double) MAX_EDGE / Math.max(width, height);
      int targetWidth = Math.max(1, (int) Math.round(width * factor));
      int targetHeight = Math.max(1, (int) Math.round(height * factor));

      String outputType = outputContentType(contentType);
      // JPEG has no alpha channel; writing an ARGB raster to it produces broken colours.
      int imageType = PNG.equals(outputType) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
      BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, imageType);

      Graphics2D g = scaled.createGraphics();
      try {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(original, 0, 0, targetWidth, targetHeight, null);
      } finally {
        g.dispose();
      }

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      String formatName = PNG.equals(outputType) ? "png" : "jpg";
      if (!ImageIO.write(scaled, formatName, out)) {
        LOG.debug("No ImageIO writer for format={}", formatName);
        return null;
      }
      return out.toByteArray();
    } catch (IOException | RuntimeException e) {
      // Never fail the request over a thumbnail — the caller serves the original.
      LOG.warn("Thumbnail generation failed for contentType={}", contentType, e);
      return null;
    }
  }
}
