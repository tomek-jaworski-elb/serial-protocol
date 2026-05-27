package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.Courses;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CoursesRepository extends JpaRepository<Courses, UUID> {

  List<Courses> findByParticipant_Uuid(UUID participantUuid);

  List<Courses> findByCourseCounter_Uuid(UUID courseCounterUuid);

  boolean existsByCourseType_Id(Long courseTypeId);

  boolean existsByParticipant_Uuid(UUID participantUuid);

  boolean existsByTrainers_Uuid(UUID trainerUuid);

  boolean existsByLecturers_Uuid(UUID lecturerUuid);

  boolean existsByTechnicians_Uuid(UUID technicianUuid);

  @Query("SELECT COALESCE(MAX(c.id), 0) FROM Courses c")
  Long findMaxCoursesId();

  void deleteCoursesByUuid(UUID uuid);
}
