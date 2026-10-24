package com.jaworski.serialprotocol.repository.custom;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One row of a person-to-image join table, as ids only.
 *
 * <p>This is how the list pages learn which photos a person has. They must not initialise
 * {@code person.getImages()}: field-level laziness on {@code Image} is inactive in this build
 * (no hibernate-enhance-maven-plugin), so every {@code Image} entity that is loaded brings its
 * two LONGBLOB columns with it, and a page of ten people with six photos each would read up
 * to 600 MB to render a table of names. {@code ListPagesLoadNoImageBlobsTest} fails if a list
 * page loads an {@code Image} entity at all.</p>
 */
public interface ImageLink {

  UUID getOwner();

  UUID getImage();

  /** The image ids per owner; owners without photos are simply absent. */
  static Map<UUID, Set<UUID>> groupByOwner(Collection<ImageLink> links) {
    return links.stream().collect(Collectors.groupingBy(
        ImageLink::getOwner,
        Collectors.mapping(ImageLink::getImage, Collectors.toSet())));
  }
}
