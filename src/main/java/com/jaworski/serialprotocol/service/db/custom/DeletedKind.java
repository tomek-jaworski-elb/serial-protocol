package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.entity.custom.CourseCounter;
import com.jaworski.serialprotocol.entity.custom.CourseType;
import com.jaworski.serialprotocol.entity.custom.Courses;
import com.jaworski.serialprotocol.entity.custom.Lecturer;
import com.jaworski.serialprotocol.entity.custom.Participant;
import com.jaworski.serialprotocol.entity.custom.SoftDeletable;
import com.jaworski.serialprotocol.entity.custom.Technician;
import com.jaworski.serialprotocol.entity.custom.Trainer;
import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * The seven kinds of record an administrator can see in the trash, keyed by the path segment of
 * {@code /admin/deleted/{entity}}. Course types are the only kind addressed by a numeric id.
 */
@Getter
public enum DeletedKind {

  PARTICIPANTS("participants", "Participants", "Participant", Participant.class, "uuid", true),
  LECTURERS("lecturers", "Lecturers", "Lecturer", Lecturer.class, "uuid", true),
  TRAINERS("trainers", "Trainers", "Trainer", Trainer.class, "uuid", true),
  TECHNICIANS("technicians", "Technicians", "Technician", Technician.class, "uuid", true),
  COURSE_TYPES("course-types", "Course types", "Course type", CourseType.class, "id", false),
  COURSE_COUNTERS("course-counters", "Course counters", "Course counter", CourseCounter.class, "uuid", true),
  COURSES("courses", "Courses", "Course", Courses.class, "uuid", true);

  private final String path;
  private final String label;
  private final String singular;
  private final Class<? extends SoftDeletable> entityClass;
  /** Name of the identifier attribute in JPQL. */
  private final String idAttribute;
  private final boolean uuidKey;

  DeletedKind(String path, String label, String singular, Class<? extends SoftDeletable> entityClass,
              String idAttribute, boolean uuidKey) {
    this.path = path;
    this.label = label;
    this.singular = singular;
    this.entityClass = entityClass;
    this.idAttribute = idAttribute;
    this.uuidKey = uuidKey;
  }

  public static Optional<DeletedKind> fromPath(String path) {
    return Arrays.stream(values()).filter(kind -> kind.path.equals(path)).findFirst();
  }

  /** The key as it appears in a URL, turned into the entity's identifier type. */
  public Object parseKey(String key) {
    try {
      return uuidKey ? UUID.fromString(key) : Long.valueOf(key);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("'" + key + "' is not a valid " + singular.toLowerCase() + " key.");
    }
  }
}
