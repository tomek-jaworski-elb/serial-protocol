package com.jaworski.serialprotocol.mappers.custom;

import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.Lecturer;
import com.jaworski.serialprotocol.entity.custom.Technician;
import com.jaworski.serialprotocol.entity.custom.Trainer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The primary-image pointer has to survive both mapper directions.
 *
 * <p>Each direction is asserted against a <em>known</em> uuid rather than against the other
 * side's value. The obvious round-trip shape — {@code assertThat(back.getX()).isEqualTo(dto.getX())}
 * — passes when <strong>neither</strong> direction was implemented, because null equals null:
 * it would shield a forgotten mapper from exactly the test meant to catch it.</p>
 */
class PrimaryImageMappingTest {

    private static final UUID POINTER = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    @Test
    void trainerMapper_carriesThePointerBothWays() {
        Trainer entity = new Trainer();
        entity.setUuid(UUID.randomUUID());
        entity.setName("Anna");
        entity.setSurname("Kowalska");
        entity.setPrimaryImageUuid(POINTER);

        TrainerDTO dto = TrainerMapper.mapToDTO(entity);
        assertThat(dto.getPrimaryImageUuid()).as("entity -> dto").isEqualTo(POINTER);

        assertThat(TrainerMapper.mapToEntity(dto).getPrimaryImageUuid())
                .as("dto -> entity")
                .isEqualTo(POINTER);
    }

    @Test
    void lecturerMapper_carriesThePointerBothWays() {
        Lecturer entity = new Lecturer();
        entity.setUuid(UUID.randomUUID());
        entity.setName("Ewa");
        entity.setSurname("Wisniewska");
        entity.setPrimaryImageUuid(POINTER);

        LecturerDTO dto = LecturerMapper.mapToDTO(entity);
        assertThat(dto.getPrimaryImageUuid()).as("entity -> dto").isEqualTo(POINTER);

        assertThat(LecturerMapper.mapToEntity(dto).getPrimaryImageUuid())
                .as("dto -> entity")
                .isEqualTo(POINTER);
    }

    @Test
    void technicianMapper_carriesThePointerBothWays() {
        Technician entity = new Technician();
        entity.setUuid(UUID.randomUUID());
        entity.setName("Jan");
        entity.setSurname("Zielinski");
        entity.setPrimaryImageUuid(POINTER);

        TechnicianDTO dto = TechnicianMapper.mapToDTO(entity);
        assertThat(dto.getPrimaryImageUuid()).as("entity -> dto").isEqualTo(POINTER);

        assertThat(TechnicianMapper.mapToEntity(dto).getPrimaryImageUuid())
                .as("dto -> entity")
                .isEqualTo(POINTER);
    }

    /** Separate from the cases above so nobody "fixes" a failure by hard-coding the constant. */
    @Test
    void anAbsentPointerStaysAbsent() {
        Trainer entity = new Trainer();
        entity.setUuid(UUID.randomUUID());
        entity.setName("Piotr");
        entity.setSurname("Nowak");

        TrainerDTO dto = TrainerMapper.mapToDTO(entity);

        assertThat(dto.getPrimaryImageUuid()).isNull();
        assertThat(TrainerMapper.mapToEntity(dto).getPrimaryImageUuid()).isNull();
    }
}
