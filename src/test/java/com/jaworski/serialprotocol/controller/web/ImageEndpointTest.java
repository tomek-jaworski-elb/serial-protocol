package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.ImageBlob;
import com.jaworski.serialprotocol.repository.custom.ImageBlobRepository;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import com.jaworski.serialprotocol.service.db.custom.ImageService;
import com.jaworski.serialprotocol.service.db.custom.ThumbnailBackfill;
import com.jaworski.serialprotocol.service.db.custom.ThumbnailGenerator;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers {@code GET /custom/image/{uuid}} — the endpoint had no controller test at all
 * before the thumbnail and caching work.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ImageEndpointTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ImageService imageService;
    @Autowired
    private ImageRepository imageRepository;
    @Autowired
    private ImageBlobRepository imageBlobRepository;
    @Autowired
    private ThumbnailBackfill thumbnailBackfill;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void original_isServedWithItsContentType() throws Exception {
        UUID id = storePng(400, 300);

        mockMvc.perform(get("/custom/image/{uuid}", id).header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().exists(HttpHeaders.ETAG));
    }

    @Test
    void unknownUuid_is404() throws Exception {
        mockMvc.perform(get("/custom/image/{uuid}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isNotFound());
    }

    /**
     * private, but revalidated every time: a deleted photo must stop being reachable
     * at once, which max-age would not guarantee — the browser would keep serving it
     * from disk while the server already answers 404.
     */
    @Test
    void cacheControlIsPrivateAndAlwaysRevalidated() throws Exception {
        UUID id = storePng(400, 300);

        // Spring Security would otherwise stamp no-store on every response; it backs
        // off because the controller sets Cache-Control itself.
        mockMvc.perform(get("/custom/image/{uuid}", id).header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-cache"));
    }

    @Test
    void matchingIfNoneMatch_returns304() throws Exception {
        UUID id = storePng(400, 300);
        String etag = mockMvc.perform(get("/custom/image/{uuid}", id)
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/custom/image/{uuid}", id)
                        .header(HttpHeaders.IF_NONE_MATCH, etag)
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isNotModified());
    }

    @Test
    void thumbAndOriginal_haveDifferentEtags() throws Exception {
        UUID id = storePng(400, 300);
        String original = etagOf(get("/custom/image/{uuid}", id));
        String thumb = etagOf(get("/custom/image/{uuid}", id).param("size", "thumb"));

        assertThat(thumb).isNotEqualTo(original);
        assertThat(thumb).contains(ThumbnailGenerator.VERSION);
    }

    @Test
    void thumb_isSmallerThanTheOriginalAndIsStoredOnce() throws Exception {
        UUID id = storePng(600, 450);
        int originalSize = imageService.getOriginal(id).data().length;

        byte[] first = body(get("/custom/image/{uuid}", id).param("size", "thumb"));

        assertThat(first.length).isLessThan(originalSize);
        assertThat(imageBlobRepository.findThumbData(id))
                .as("generated once and kept, not rebuilt per request")
                .isPresent();

        byte[] second = body(get("/custom/image/{uuid}", id).param("size", "thumb"));
        assertThat(second).isEqualTo(first);
    }

    @Test
    void thumb_ofAnAlreadySmallImage_fallsBackToTheOriginal() throws Exception {
        UUID id = storePng(80, 60);
        byte[] original = imageService.getOriginal(id).data();

        byte[] thumb = body(get("/custom/image/{uuid}", id).param("size", "thumb"));

        assertThat(thumb).isEqualTo(original);
    }

    /** ImageIO in JDK 21 has no reader for these, so the original must come back intact. */
    @Test
    void thumb_ofUnsupportedFormat_servesTheOriginal() throws Exception {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><rect width='10' height='10'/></svg>"
                .getBytes();
        UUID id = imageService.saveImage(svg, "image/svg+xml").getId();

        byte[] served = body(get("/custom/image/{uuid}", id).param("size", "thumb"));

        assertThat(served).isEqualTo(svg);
    }

    /**
     * Regression: the ETag is derived from the uuid alone, so answering a conditional
     * request purely by comparing tags told browsers "unchanged" about a photo that
     * had already been deleted — and they kept serving it from cache. Deleting a photo
     * has to be visible immediately, so a conditional request for a gone image is 404,
     * not 304.
     */
    @Test
    void conditionalRequestForADeletedImage_is404NotNotModified() throws Exception {
        UUID id = storePng(400, 300);
        String etag = mockMvc.perform(get("/custom/image/{uuid}", id)
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        imageRepository.deleteById(id);

        mockMvc.perform(get("/custom/image/{uuid}", id)
                        .header(HttpHeaders.IF_NONE_MATCH, etag)
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isNotFound());
    }

    /**
     * An image ImageIO cannot read must be marked as "tried and cannot be done", or
     * every single request re-runs the conversion that is guaranteed to fail. The
     * marker is a content type with no data — deliberately not a copy of the original,
     * which for a 10 MB upload would be a poor trade.
     */
    @Test
    void thumb_ofUnsupportedFormat_isNotRegeneratedOnEveryRequest() throws Exception {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><rect width='9' height='9'/></svg>"
                .getBytes();
        UUID id = imageService.saveImage(svg, "image/svg+xml").getId();

        body(get("/custom/image/{uuid}", id).param("size", "thumb"));

        Image stored = imageRepository.findById(id).orElseThrow();
        assertThat(stored.getThumbVersion())
                .as("attempt recorded against the generator version, not a borrowed field")
                .isEqualTo(ThumbnailGenerator.VERSION);
        assertThat(imageBlobRepository.findThumbData(id)).as("original not duplicated").isEmpty();

        assertThat(body(get("/custom/image/{uuid}", id).param("size", "thumb")))
                .as("still serves the original")
                .isEqualTo(svg);
    }

    /**
     * Serving a thumbnail is a read. The stored copy is made at upload; a row whose copy
     * predates the current generator version is scaled for the response and left alone.
     * The GET used to write the regenerated bytes back, which made every image request a
     * potential write and, under replication, change traffic for derived data.
     */
    @Test
    void thumb_producedByAnOlderVersion_isServedFreshButNotWrittenByTheGet() throws Exception {
        UUID id = storePng(600, 450);
        Image stored = imageRepository.findById(id).orElseThrow();
        assertThat(stored.getThumbVersion()).as("made at upload").isEqualTo(ThumbnailGenerator.VERSION);
        staleThumbnail(id, stored);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        long updatesBefore = statistics.getEntityUpdateCount();

        byte[] served = body(get("/custom/image/{uuid}", id).param("size", "thumb"));

        assertThat(served).as("stale bytes must not be served").isNotEqualTo(new byte[]{1, 2, 3});
        assertThat(statistics.getEntityUpdateCount()).as("a GET never writes").isEqualTo(updatesBefore);
        assertThat(imageRepository.findById(id).orElseThrow().getThumbVersion())
                .as("left for the backfill").isEqualTo("v0");
    }

    /**
     * A version bump must actually rebuild the stored bytes. Before the version was
     * recorded per image, bumping it changed every ETag — so every client refetched —
     * and the server handed back the identical old thumbnail: full cache invalidation
     * with no effect. The rebuild is the backfill's job, run once after start-up.
     */
    @Test
    void theBackfillRebuildsThumbnailsOfOlderVersions() throws Exception {
        UUID id = storePng(600, 450);
        staleThumbnail(id, imageRepository.findById(id).orElseThrow());

        assertThat(thumbnailBackfill.refreshStale()).isGreaterThanOrEqualTo(1);

        Image refreshed = imageRepository.findById(id).orElseThrow();
        byte[] refreshedThumb = imageBlobRepository.findThumbData(id).orElseThrow();
        assertThat(refreshed.getThumbVersion()).isEqualTo(ThumbnailGenerator.VERSION);
        assertThat(refreshedThumb).isNotEqualTo(new byte[]{1, 2, 3});
        assertThat(body(get("/custom/image/{uuid}", id).param("size", "thumb")))
                .isEqualTo(refreshedThumb);
    }

    /**
     * The generator version describes the thumbnail, not the original. Embedding it in the
     * original's tag would make a thumbnail-size tweak invalidate every cached full-size photo,
     * whose bytes never changed.
     */
    @Test
    void theOriginalEtagDoesNotDependOnTheThumbnailVersion() throws Exception {
        UUID id = storePng(400, 300);

        String original = etagOf(get("/custom/image/{uuid}", id));
        String thumb = etagOf(get("/custom/image/{uuid}", id).param("size", "thumb"));

        assertThat(original).doesNotContain(ThumbnailGenerator.VERSION);
        assertThat(thumb).contains(ThumbnailGenerator.VERSION);
        assertThat(original).isNotEqualTo(thumb);
    }

    /**
     * A transient failure — a truncated read, a decoder blowing up under load — says nothing
     * about the image. Recording the version would pin a perfectly good photo to the full-size
     * path until somebody bumps VERSION, which rebuilds every thumbnail in the table.
     */
    @Test
    void aFailedGenerationIsNotRecordedAsAVerdict() throws Exception {
        // Declares itself as a PNG, but the bytes are not one: ImageIO.read returns null,
        // which is a genuine verdict about these bytes and IS recorded.
        UUID unreadable = imageService.saveImage("not really a png".getBytes(), "image/png").getId();

        body(get("/custom/image/{uuid}", unreadable).param("size", "thumb"));

        assertThat(imageRepository.findById(unreadable).orElseThrow().getThumbVersion())
                .as("unreadable bytes are a permanent verdict, so they stay recorded")
                .isEqualTo(ThumbnailGenerator.VERSION);
    }

    @Test
    void unknownSizeValue_is400() throws Exception {
        UUID id = storePng(400, 300);

        mockMvc.perform(get("/custom/image/{uuid}", id)
                        .param("size", "huge")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    /** Makes the stored thumbnail look like the product of an older generator. */
    private void staleThumbnail(UUID id, Image stored) {
        ImageBlob blob = imageBlobRepository.findById(id).orElseThrow();
        blob.setThumbData(new byte[]{1, 2, 3});
        imageBlobRepository.save(blob);
        stored.setThumbVersion("v0");
        imageRepository.save(stored);
    }

    private UUID storePng(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        // Noise, not a flat fill: a uniform image compresses to almost nothing and the
        // "thumbnail is smaller" assertion would stop meaning anything.
        for (int x = 0; x < width; x += 3) {
            for (int y = 0; y < height; y += 3) {
                g.setColor(new Color((x * 7) % 255, (y * 13) % 255, ((x + y) * 3) % 255));
                g.fillRect(x, y, 3, 3);
            }
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return imageService.saveImage(out.toByteArray(), "image/png").getId();
    }

    private byte[] body(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        MvcResult result = mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsByteArray();
    }

    private String etagOf(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, auth()))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }

    private String auth() {
        return "Basic " + Base64.getEncoder().encodeToString("user:user".getBytes());
    }
}
