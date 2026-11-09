package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * The photo limit is the application's own, separate from the multipart limit that a backup restore needs.
 * Lowered to 1 KB here so the tests do not have to upload megabytes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "custom.upload.max-image-size=1KB")
@AutoConfigureMockMvc
class ImageSizeLimitTest {

  private static final String TOO_LARGE = "A photo is too large. Maximum allowed size is 1 KB.";

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ImageRepository imageRepository;

  @Test
  void aPhotoOverTheLimitIsRefusedWithTheReasonAndNothingIsSaved() throws Exception {
    long before = imageRepository.count();

    var result = mockMvc.perform(multipart("/trainer-service/add")
            .file(photo("imageFiles", 1024))
            .file(photo("imageFiles", 1025))   // the second one is over: the first must not be saved either
            .param("name", "Big")
            .param("surname", "Photo")
            .with(csrf())
            .header(HttpHeaders.AUTHORIZATION, auth()))
        .andReturn();

    assertThat(result.getFlashMap().get("errorMessage")).isEqualTo(TOO_LARGE);
    assertThat(imageRepository.count()).isEqualTo(before);
  }

  @Test
  void theSingleImagePathHasTheSameLimit() throws Exception {
    long before = imageRepository.count();

    var result = mockMvc.perform(multipart("/participant-service/add")
            .file(photo("imageFile", 1025))
            .param("name", "Big")
            .param("surname", "Photo")
            .with(csrf())
            .header(HttpHeaders.AUTHORIZATION, auth()))
        .andReturn();

    assertThat(result.getFlashMap().get("errorMessage")).isEqualTo(TOO_LARGE);
    assertThat(imageRepository.count()).isEqualTo(before);
  }

  @Test
  void aPhotoAtTheLimitIsAccepted() throws Exception {
    long before = imageRepository.count();

    var result = mockMvc.perform(multipart("/trainer-service/add")
            .file(photo("imageFiles", 1024))
            .param("name", "Exact")
            .param("surname", "Photo")
            .with(csrf())
            .header(HttpHeaders.AUTHORIZATION, auth()))
        .andReturn();

    assertThat(result.getFlashMap().get("errorMessage")).isNull();
    assertThat(imageRepository.count()).isEqualTo(before + 1);
  }

  @Test
  void limitsAreDescribedTheWayAPersonWritesThem() {
    assertThat(ImageUploadCoordinator.describe(DataSize.ofMegabytes(200))).isEqualTo("200 MB");
    assertThat(ImageUploadCoordinator.describe(DataSize.ofKilobytes(500))).isEqualTo("500 KB");
    assertThat(ImageUploadCoordinator.describe(DataSize.ofBytes(1536))).isEqualTo("1536 bytes");
  }

  private static MockMultipartFile photo(String field, int bytes) {
    return new MockMultipartFile(field, "photo.jpg", "image/jpeg", new byte[bytes]);
  }

  private static String auth() {
    return "Basic " + Base64.getEncoder().encodeToString("user:user".getBytes(StandardCharsets.UTF_8));
  }
}
