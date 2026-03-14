package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.entity.custom.CourseType;
import com.jaworski.serialprotocol.entity.custom.Courses;
import com.jaworski.serialprotocol.entity.custom.Lecturer;
import com.jaworski.serialprotocol.entity.custom.Participant;
import com.jaworski.serialprotocol.entity.custom.Trainer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class CustomEntitiesCrudTest {

  @Autowired
  private EntityManager entityManager;

  @Test
  void shouldSaveAndReadParticipant() {
    Participant participant = createParticipant(1001L, "Jan", "Nowak");

    entityManager.persist(participant);
    entityManager.flush();
    entityManager.clear();

    Participant loaded = entityManager.find(Participant.class, participant.getUuid());

    assertNotNull(loaded);
    assertEquals(1001L, loaded.getId());
    assertEquals("Jan", loaded.getName());
    assertEquals("Nowak", loaded.getSurname());
  }

  @Test
  void shouldUpdateTrainer() {
    Trainer trainer = createTrainer("Adam", "Kowalski", "adam@test.com");

    entityManager.persist(trainer);
    entityManager.flush();
    Long trainerId = trainer.getId();
    entityManager.clear();

    Trainer managed = entityManager.find(Trainer.class, trainerId);
    assertNotNull(managed);
    managed.setEmail("adam.updated@test.com");
    entityManager.flush();
    entityManager.clear();

    Trainer updated = entityManager.find(Trainer.class, trainerId);
    assertNotNull(updated);
    assertEquals("adam.updated@test.com", updated.getEmail());
  }

  @Test
  void shouldDeleteLecturer() {
    Lecturer lecturer = createLecturer("Anna", "Zielinska");

    entityManager.persist(lecturer);
    entityManager.flush();
    Long lecturerId = lecturer.getLecturerId();

    Lecturer managed = entityManager.find(Lecturer.class, lecturerId);
    assertNotNull(managed);
    entityManager.remove(managed);
    entityManager.flush();
    entityManager.clear();

    Lecturer deleted = entityManager.find(Lecturer.class, lecturerId);
    assertNull(deleted);
  }

  @Test
  void shouldAddReadAndModifyCourseRelations() {
    Participant participant = createParticipant(2001L, "Piotr", "Wisniewski");
    CourseType courseType = createCourseType("C-A", "Basic", "Basic navigation course");
    Trainer trainer1 = createTrainer("Tomasz", "Krawczyk", "trainer1@test.com");
    Trainer trainer2 = createTrainer("Pawel", "Mazur", "trainer2@test.com");
    Lecturer lecturer1 = createLecturer("Ewa", "Jankowska");

    entityManager.persist(participant);
    entityManager.persist(courseType);
    entityManager.persist(trainer1);
    entityManager.persist(trainer2);
    entityManager.persist(lecturer1);
    entityManager.flush();

    Courses course = createCourse(3001L, participant, courseType);
    course.getTrainers().add(trainer1);
    course.getLecturers().add(lecturer1);

    entityManager.persist(course);
    entityManager.flush();
    entityManager.clear();

    Courses loaded = entityManager.find(Courses.class, course.getUuid());
    assertNotNull(loaded);
    assertEquals(3001L, loaded.getId());
    assertEquals(1, loaded.getTrainers().size());
    assertEquals(1, loaded.getLecturers().size());

    loaded.getTrainers().add(entityManager.find(Trainer.class, trainer2.getId()));
    loaded.getLecturers().removeIf(l -> l.getLecturerId() == lecturer1.getLecturerId());
    entityManager.flush();
    entityManager.clear();

    Courses modified = entityManager.find(Courses.class, course.getUuid());
    assertNotNull(modified);
    assertEquals(2, modified.getTrainers().size());
    assertEquals(0, modified.getLecturers().size());
  }

  private Participant createParticipant(Long id, String name, String surname) {
    Participant participant = new Participant();
    participant.setId(id);
    participant.setName(name);
    participant.setSurname(surname);
    participant.setBirthDate(LocalDate.of(1995, 1, 1));
    participant.setPhoto(new byte[0]);
    return participant;
  }

  private Trainer createTrainer(String name, String surname, String email) {
    Trainer trainer = new Trainer();
    trainer.setName(name);
    trainer.setSurname(surname);
    trainer.setEmail(email);
    trainer.setPhoto(new byte[0]);
    return trainer;
  }

  private Lecturer createLecturer(String name, String surname) {
    Lecturer lecturer = new Lecturer();
    lecturer.setName(name);
    lecturer.setSurname(surname);
    lecturer.setPhoto(new byte[0]);
    return lecturer;
  }

  private CourseType createCourseType(String code, String description, String longDescription) {
    CourseType courseType = new CourseType();
    courseType.setCode(code);
    courseType.setDescription(description);
    courseType.setLongDescription(longDescription);
    return courseType;
  }

  private Courses createCourse(Long id, Participant participant, CourseType courseType) {
    Courses course = new Courses();
    course.setId(id);
    course.setParticipant(participant);
    course.setCourseType(courseType);
    course.setStartDate(LocalDate.of(2026, 1, 10));
    course.setEndDate(LocalDate.of(2026, 1, 20));
    course.setTrainers(new HashSet<>());
    course.setLecturers(new HashSet<>());
    return course;
  }
}

