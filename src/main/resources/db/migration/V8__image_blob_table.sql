-- The bytes of a photo move out of `image` into `image_blob`, keyed by the same uuid. Field-level
-- laziness is inactive in this build, so any load of the `image` row used to read both LONGBLOBs;
-- with the bytes in their own table, every list and every write path that resolves a person's
-- photos is metadata-only. Nothing reads `image_blob` except the image endpoint, the upload and
-- the thumbnail backfill (ImageService). A soft-deleted image keeps its blob row for now.

CREATE TABLE `image_blob` (
    `image_uuid`       UUID     NOT NULL,
    `image_data`       LONGBLOB NOT NULL,
    `image_thumb_data` LONGBLOB NULL,
    PRIMARY KEY (`image_uuid`),
    CONSTRAINT `FK_image_blob_image` FOREIGN KEY (`image_uuid`) REFERENCES `image` (`image_uuid`)
) ENGINE=InnoDB;

INSERT INTO `image_blob` (`image_uuid`, `image_data`, `image_thumb_data`)
SELECT `image_uuid`, `image_data`, `image_thumb_data` FROM `image`;

ALTER TABLE `image`
    DROP COLUMN `image_data`,
    DROP COLUMN `image_thumb_data`;
