package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.DeletedRecordDTO;
import com.jaworski.serialprotocol.entity.custom.CourseCounter;
import com.jaworski.serialprotocol.entity.custom.CourseType;
import com.jaworski.serialprotocol.entity.custom.Courses;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.Lecturer;
import com.jaworski.serialprotocol.entity.custom.Participant;
import com.jaworski.serialprotocol.entity.custom.PersonBase;
import com.jaworski.serialprotocol.entity.custom.SoftDeletable;
import com.jaworski.serialprotocol.entity.custom.Technician;
import com.jaworski.serialprotocol.entity.custom.Trainer;
import com.jaworski.serialprotocol.repository.custom.CoursesRepository;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The administrator's trash: lists soft-deleted records, puts them back, or removes them for good.
 *
 * <p>Everything here runs with the "active only" filter switched off, which is the only way to reach a
 * deleted row. The rules are the mirror image of the user-side guards: a course cannot come back while a
 * parent it points at is still in the trash, and nothing can be purged while a course, deleted or not,
 * still points at it. A purge is a JPQL bulk delete, which bypasses the {@code @SQLDelete} safety net and
 * binds the key through Hibernate, so it is the same statement on H2 and MariaDB.</p>
 *
 * <p>Every hidden row this service loads is detached again before it returns, whether the operation went
 * through or was refused. A session answers {@code find} from its cache before the filter can get in the
 * way, so without that a caller sharing the transaction could be handed a deleted entity.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DeletedRecordsService {

  private static final Logger LOG = LoggerFactory.getLogger(DeletedRecordsService.class);
  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final String SEPARATOR = " · ";

  private final SoftDeleteSupport softDeleteSupport;
  private final CoursesRepository coursesRepository;
  private final ImageRepository imageRepository;

  @PersistenceContext
  private EntityManager entityManager;

  @Transactional(readOnly = true)
  public Page<DeletedRecordDTO> list(DeletedKind kind, Pageable pageable) {
    return softDeleteSupport.withDeleted(() -> {
      String entity = kind.getEntityClass().getSimpleName();
      // Rows are mapped here, inside withDeleted: a course's parents load with it, and a deleted
      // parent could not be loaded once the filter is back on.
      List<DeletedRecordDTO> rows = entityManager
          .createQuery("select e from " + entity + " e where e.deletedAt is not null order by e.deletedAt desc",
              kind.getEntityClass())
          .setFirstResult((int) pageable.getOffset())
          .setMaxResults(pageable.getPageSize())
          .getResultList().stream()
          .map(entityRow -> {
            DeletedRecordDTO row = toRow(entityRow);
            forget(entityRow, hiddenParents(entityRow));
            return row;
          })
          .toList();
      long total = entityManager
          .createQuery("select count(e) from " + entity + " e where e.deletedAt is not null", Long.class)
          .getSingleResult();
      return new PageImpl<>(rows, pageable, total);
    });
  }

  /**
   * Puts a deleted record back.
   *
   * @throws IllegalArgumentException when the key is malformed or names no deleted record
   * @throws IllegalStateException when the record is a course whose parent is still deleted
   */
  public void restore(DeletedKind kind, String key) {
    softDeleteSupport.runWithDeleted(() -> {
      SoftDeletable entity = loadDeleted(kind, key);
      List<SoftDeletable> parentsInTrash = hiddenParents(entity);   // only a course has parents
      if (!parentsInTrash.isEmpty()) {
        forget(entity, parentsInTrash);
        throw new IllegalStateException("Restore " + parentsInTrash.stream()
            .map(DeletedRecordsService::describe).collect(Collectors.joining(", ")) + " first.");
      }
      entity.restore();
      entityManager.flush();
      LOG.info("Restored {} {} (by {})", kind.getSingular().toLowerCase(), key, softDeleteSupport.currentUser());
    });
  }

  /**
   * Removes a deleted record for good, together with its join rows and any photo nothing else uses.
   *
   * @throws IllegalArgumentException when the key is malformed or names no deleted record
   * @throws IllegalStateException when a course, deleted or not, still points at the record
   */
  public void purge(DeletedKind kind, String key) {
    softDeleteSupport.runWithDeleted(() -> {
      SoftDeletable entity = loadDeleted(kind, key);
      List<SoftDeletable> hiddenParents = hiddenParents(entity);
      String stillUsed = stillReferenced(entity);
      if (stillUsed != null) {
        forget(entity, hiddenParents);
        throw new IllegalStateException(stillUsed);
      }
      Set<UUID> photos = releaseLinks(entity);
      entityManager.flush();   // join rows are gone before the row itself
      entityManager.createQuery("delete from " + kind.getEntityClass().getSimpleName()
              + " e where e." + kind.getIdAttribute() + " = :id")
          .setParameter("id", kind.parseKey(key))
          .executeUpdate();
      forget(entity, hiddenParents);
      photos.forEach(this::deleteIfOrphan);
      LOG.warn("Purged {} {} (by {})", kind.getSingular().toLowerCase(), key, softDeleteSupport.currentUser());
    });
  }

  // ---- loading and rows -------------------------------------------------------------------------

  private SoftDeletable loadDeleted(DeletedKind kind, String key) {
    SoftDeletable entity = entityManager.find(kind.getEntityClass(), kind.parseKey(key));
    if (entity == null || !entity.isDeleted()) {
      throw new IllegalArgumentException(kind.getSingular() + " " + key + " is not in the trash.");
    }
    return entity;
  }

  private DeletedRecordDTO toRow(SoftDeletable entity) {
    String key;
    String title;
    String details;
    switch (entity) {
      case Participant p -> {
        key = p.getUuid().toString();
        title = person(p);
        details = "No. " + p.getId() + (contact(p).isEmpty() ? "" : SEPARATOR + contact(p));
      }
      case Lecturer l -> {
        key = l.getUuid().toString();
        title = person(l);
        details = contact(l);
      }
      case Trainer t -> {
        key = t.getUuid().toString();
        title = person(t);
        details = contact(t);
      }
      case Technician t -> {
        key = t.getUuid().toString();
        title = person(t);
        details = contact(t);
      }
      case CourseType ct -> {
        key = String.valueOf(ct.getId());
        title = ct.getCode();
        details = nullToEmpty(ct.getDescription());
      }
      case CourseCounter cc -> {
        key = cc.getUuid().toString();
        title = "Counter " + cc.getCounter();
        details = "";
      }
      case Courses c -> {
        key = c.getUuid().toString();
        title = "Course no. " + c.getId();
        details = person(c.getParticipant()) + SEPARATOR + c.getCourseType().getCode()
            + SEPARATOR + day(c.getStartDate()) + " - " + day(c.getEndDate());
      }
      default -> throw new IllegalStateException("Not a soft-deletable kind: " + entity.getClass());
    }
    return new DeletedRecordDTO(key, title, details,
        entity.getDeletedAt() == null ? "" : STAMP.format(entity.getDeletedAt()),
        nullToEmpty(entity.getDeletedBy()));
  }

  private static String person(PersonBase p) {
    return (nullToEmpty(p.getSurname()) + " " + nullToEmpty(p.getName())).trim();
  }

  private static String contact(PersonBase p) {
    return p.getEmail() == null || p.getEmail().isBlank() ? "" : p.getEmail();
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static String day(LocalDate date) {
    return date == null ? "?" : DAY.format(date);
  }

  // ---- rules --------------------------------------------------------------------------------------

  /** The records a course points at that are themselves in the trash; empty for every other kind. */
  private static List<SoftDeletable> hiddenParents(SoftDeletable entity) {
    if (!(entity instanceof Courses course)) {
      return List.of();
    }
    List<SoftDeletable> parents = new ArrayList<>();
    parents.add(course.getParticipant());
    parents.add(course.getCourseType());
    if (course.getCourseCounter() != null) {
      parents.add(course.getCourseCounter());
    }
    parents.addAll(course.getTrainers());
    parents.addAll(course.getLecturers());
    parents.addAll(course.getTechnicians());
    return parents.stream().filter(SoftDeletable::isDeleted).toList();
  }

  /** How a blocking parent is named in the message, matching what its own trash tab shows. */
  private static String describe(SoftDeletable entity) {
    return switch (entity) {
      case Participant p -> "participant " + person(p);
      case Trainer t -> "trainer " + person(t);
      case Lecturer l -> "lecturer " + person(l);
      case Technician t -> "technician " + person(t);
      case CourseType ct -> "course type " + ct.getCode();
      case CourseCounter cc -> "course counter " + cc.getCounter();
      default -> entity.getClass().getSimpleName();
    };
  }

  /** Takes the hidden rows back out of the session (see the class comment). */
  private void forget(SoftDeletable entity, List<SoftDeletable> hiddenParents) {
    hiddenParents.forEach(entityManager::detach);
    entityManager.detach(entity);
  }

  /** Why the record cannot be purged yet, or null. Runs with the filter off, so deleted courses count. */
  private String stillReferenced(SoftDeletable entity) {
    boolean used = switch (entity) {
      case Participant p -> coursesRepository.existsByParticipant_Uuid(p.getUuid());
      case Lecturer l -> coursesRepository.existsByLecturers_Uuid(l.getUuid());
      case Trainer t -> coursesRepository.existsByTrainers_Uuid(t.getUuid());
      case Technician t -> coursesRepository.existsByTechnicians_Uuid(t.getUuid());
      case CourseType ct -> coursesRepository.existsByCourseType_Id(ct.getId());
      case CourseCounter cc -> coursesRepository.existsByCourseCounter_Uuid(cc.getUuid());
      default -> false;
    };
    return used
        ? "A course still uses this record (deleted courses count). Purge or restore that course first."
        : null;
  }

  /**
   * Drops the links the record owns, its photo set and, for a course, its staff, and returns the photos
   * that may now be orphaned. Single photos ({@code Participant}, {@code CourseCounter}) need no unlinking:
   * deleting the row drops that reference.
   */
  private static Set<UUID> releaseLinks(SoftDeletable entity) {
    Set<UUID> photos = new LinkedHashSet<>();
    switch (entity) {
      case Participant p -> {
        if (p.getImage() != null) {
          photos.add(p.getImage().getId());
        }
      }
      case CourseCounter cc -> {
        if (cc.getImage() != null) {
          photos.add(cc.getImage().getId());
        }
      }
      case Lecturer l -> photos.addAll(release(l.getImages()));
      case Trainer t -> photos.addAll(release(t.getImages()));
      case Technician t -> photos.addAll(release(t.getImages()));
      case Courses c -> {
        c.getTrainers().clear();
        c.getLecturers().clear();
        c.getTechnicians().clear();
      }
      default -> { }
    }
    return photos;
  }

  private static Set<UUID> release(Collection<Image> images) {
    Set<UUID> ids = new LinkedHashSet<>();
    images.forEach(image -> ids.add(image.getId()));
    images.clear();
    return ids;
  }

  /** Removes a photo once nothing, deleted owners included, points at it any more. */
  private void deleteIfOrphan(UUID imageId) {
    long references = count("select count(p) from Participant p where p.image.id = :id", imageId)
        + count("select count(c) from CourseCounter c where c.image.id = :id", imageId)
        + count("select count(t) from Trainer t join t.images i where i.id = :id", imageId)
        + count("select count(l) from Lecturer l join l.images i where i.id = :id", imageId)
        + count("select count(t) from Technician t join t.images i where i.id = :id", imageId);
    if (references == 0) {
      imageRepository.deleteById(imageId);
    }
  }

  private long count(String jpql, UUID imageId) {
    return entityManager.createQuery(jpql, Long.class).setParameter("id", imageId).getSingleResult();
  }
}
