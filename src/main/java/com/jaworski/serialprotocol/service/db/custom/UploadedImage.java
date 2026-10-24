package com.jaworski.serialprotocol.service.db.custom;

/**
 * The bytes of one photo a request carried, before anything has been persisted.
 *
 * <p>The web layer reads these out of the multipart request and hands them to the services,
 * which store them in the same transaction as the record they belong to. That is the whole
 * point of the type: the services used to receive ids of images already committed by the
 * controller, and a failing save then had to delete those rows again as a compensating step.</p>
 *
 * @param data        the raw bytes, never empty
 * @param contentType already normalised by {@link ImageService#sanitizeContentType(String)}
 */
public record UploadedImage(byte[] data, String contentType) {
}
