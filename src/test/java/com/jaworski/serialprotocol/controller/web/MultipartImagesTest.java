package com.jaworski.serialprotocol.controller.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The per-photo cap is enforced in code since the container limit is sized for a sync file. */
class MultipartImagesTest {

  private final MultipartImages images = new MultipartImages(DataSize.ofKilobytes(1));

  @Test
  void aPhotoOverTheCapIsRefusedLikeAnOversizedUpload() {
    MockMultipartFile big = new MockMultipartFile("file", "big.png", "image/png", new byte[1025]);

    assertThatThrownBy(() -> images.read(big)).isInstanceOf(MaxUploadSizeExceededException.class);
    assertThatThrownBy(() -> images.read(new MockMultipartFile[] {big}))
        .isInstanceOf(MaxUploadSizeExceededException.class);
  }

  @Test
  void aPhotoWithinTheCapIsRead() {
    MockMultipartFile small = new MockMultipartFile("file", "small.png", "image/png", new byte[1024]);

    assertThat(images.read(small)).isNotNull();
    assertThat(images.maxUploadSize()).isEqualTo(DataSize.ofKilobytes(1));
  }
}
