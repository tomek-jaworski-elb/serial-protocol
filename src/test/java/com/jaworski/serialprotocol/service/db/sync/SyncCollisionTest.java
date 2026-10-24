package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.service.db.sync.Collision.Type;
import com.jaworski.serialprotocol.service.db.sync.SyncAnalysis.Action;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every row of the analysis table in docs/sync-spec.md, on hand-built rows: the decision
 * table for a matched row, then C1–C6, then the two refusals.
 */
class SyncCollisionTest {

  static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
  static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-00000000000c");
  static final Instant SINCE = Instant.parse("2026-03-01T12:00:00Z");
  static final Instant BEFORE = SINCE.minusSeconds(3600);
  static final Instant AFTER = SINCE.plusSeconds(3600);
  static final Instant LATER = SINCE.plusSeconds(7200);
  static final SyncManifest.Node FILE_NODE = new SyncManifest.Node(OTHER, "Laptop");

  private final Map<String, List<SyncRow>> local = new LinkedHashMap<>();
  private final Map<String, List<SyncRow>> file = new LinkedHashMap<>();

  // ---- row builders ---------------------------------------------------------------------

  static SyncRow row(String key, UUID origin, long version, Instant updated, Instant deleted, Object... columns) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < columns.length; i += 2) {
      values.put((String) columns[i], columns[i + 1]);
    }
    values.put("version", version);
    values.put("created_at", stamp(BEFORE));
    values.put("updated_at", stamp(updated));
    values.put("deleted_at", deleted == null ? null : stamp(deleted));
    values.put("origin_node", origin == null ? null : origin.toString());
    return new SyncRow(values);
  }

  static String stamp(Instant at) {
    return SyncValues.TIMESTAMP.format(at);
  }

  static String key(int n) {
    return "00000000-0000-0000-0000-" + String.format("%012d", n);
  }

  static SyncRow participant(String uuid, int number, String name, UUID origin, long version, Instant updated,
      Instant deleted) {
    return row(uuid, origin, version, updated, deleted, "participants_uuid", uuid, "participants_id", number,
        "participants_name", name, "participants_surname", "Nowak", "image_uuid", null);
  }

  static SyncRow courseType(String uuid, String code, UUID origin, Instant updated) {
    return row(uuid, origin, 0, updated, null, "course_type_uuid", uuid, "course_type_code", code);
  }

  static SyncRow course(String uuid, int number, String participant, String type, UUID origin, Instant updated) {
    return row(uuid, origin, 0, updated, null, "courses_uuid", uuid, "courses_id", number,
        "courses_participant_id", participant, "courses_course_type_uuid", type, "course_counter_uuid", null);
  }

  void local(String table, SyncRow... rows) {
    local.computeIfAbsent(table, t -> new ArrayList<>()).addAll(List.of(rows));
  }

  void file(String table, SyncRow... rows) {
    file.computeIfAbsent(table, t -> new ArrayList<>()).addAll(List.of(rows));
  }

  SyncAnalysis analyze() {
    return analyze(SINCE, false);
  }

  SyncAnalysis analyze(Instant since, boolean readOnly) {
    return analyze(since, readOnly, null);
  }

  /** @param seen the export time of the last file imported from {@link #FILE_NODE}, {@code null} for none */
  SyncAnalysis analyze(Instant since, boolean readOnly, Instant seen) {
    return SyncAnalyzer.analyze(new SyncAnalyzer.Input(SELF, "Office", since, readOnly, FILE_NODE, seen, local, file,
        ZoneOffset.UTC));
  }

  static SyncAnalysis.TableSummary participants(SyncAnalysis analysis) {
    return analysis.tables().stream().filter(t -> t.table().equals("participants")).findFirst().orElseThrow();
  }

  // ---- the decision table ---------------------------------------------------------------

  @Test
  void aCollisionAcceptsOnlyAResolutionItOffers() {
    local("participants", participant(key(1), 1001, "Anna", SELF, 1, AFTER, null));
    file("participants", participant(key(1), 1001, "Ania", OTHER, 1, AFTER, null));
    Collision c = analyze().collisions().getFirst();

    assertThat(c.chosen(java.util.Map.of(c.id(), "take-file"))).isEqualTo("take-file");
    assertThatThrownBy(() -> c.chosen(java.util.Map.of()))
        .isInstanceOf(SyncRefusedException.class)
        .hasMessageContaining("Choose what to do with every collision");
    assertThatThrownBy(() -> c.chosen(java.util.Map.of(c.id(), "skip-row")))
        .isInstanceOf(SyncRefusedException.class);
  }

  @Test
  void aRowOnlyInTheFileIsInserted() {
    file("participants", participant(key(1), 1001, "Anna", OTHER, 0, AFTER, null));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.plan()).singleElement().satisfies(p -> {
      assertThat(p.action()).isEqualTo(Action.INSERT);
      assertThat(p.table()).isEqualTo("participants");
    });
    assertThat(participants(analysis).inserts()).isEqualTo(1);
    assertThat(analysis.hasCollisions()).isFalse();
  }

  @Test
  void aRowOnlyHereIsKeptWithANoticeOnAnActiveNode() {
    local("participants", participant(key(1), 1001, "Anna", SELF, 0, AFTER, null));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.plan()).isEmpty();
    assertThat(participants(analysis).kept()).isEqualTo(1);
    assertThat(analysis.notices()).singleElement().satisfies(n -> {
      assertThat(n.record()).isEqualTo("Anna Nowak (no. 1001)");
      assertThat(n.text()).contains("not in file");
    });
  }

  @Test
  void anIdenticalRowIsUnchanged() {
    local("participants", participant(key(1), 1001, "Anna", OTHER, 2, BEFORE, null));
    file("participants", participant(key(1), 1001, "Anna", OTHER, 2, BEFORE, null));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.plan()).isEmpty();
    assertThat(participants(analysis).unchanged()).isEqualTo(1);
    assertThat(analysis.notices()).isEmpty();
  }

  @Test
  void aRowChangedOnlyThereIsUpdated() {
    local("participants", participant(key(1), 1001, "Anna", OTHER, 1, BEFORE, null));
    file("participants", participant(key(1), 1001, "Anna-Maria", OTHER, 2, AFTER, null));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.plan()).singleElement().satisfies(p -> assertThat(p.action()).isEqualTo(Action.UPDATE));
    assertThat(participants(analysis).updates()).isEqualTo(1);
  }

  @Test
  void aFileOlderThanThisComputerKeepsTheLocalRowWithANotice() {
    local("participants", participant(key(1), 1001, "Anna", OTHER, 3, BEFORE, null));
    file("participants", participant(key(1), 1001, "Ann", OTHER, 2, BEFORE, null));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.plan()).isEmpty();
    assertThat(analysis.notices()).singleElement().satisfies(n -> assertThat(n.text()).contains("older"));
  }

  @Test
  void aRowChangedOnlyHereIsKept() {
    local("participants", participant(key(1), 1001, "Anna-Maria", SELF, 2, AFTER, null));
    file("participants", participant(key(1), 1001, "Anna", SELF, 1, BEFORE, null));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.plan()).isEmpty();
    assertThat(participants(analysis).kept()).isEqualTo(1);
    assertThat(analysis.hasCollisions()).isFalse();
  }

  @Test
  void aNodeThatNeverExchangedDataCountsEveryLocalRowAsChangedHere() {
    local("participants", participant(key(1), 1001, "Anna", SELF, 1, BEFORE, null));
    file("participants", participant(key(1), 1001, "Ann", OTHER, 2, AFTER, null));

    SyncAnalysis analysis = analyze(null, false);

    assertThat(analysis.collisions()).singleElement().satisfies(c -> assertThat(c.type()).isEqualTo(Type.C2));
  }

  @Test
  void aRowStampedByAnOlderProgramCountsAsUntouchedHere() {
    local("participants", participant(key(1), 1001, "Anna", null, 1, AFTER, null));
    file("participants", participant(key(1), 1001, "Ann", OTHER, 2, AFTER, null));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.plan()).singleElement().satisfies(p -> assertThat(p.action()).isEqualTo(Action.UPDATE));
  }

  @Test
  void bothDeletedTakesTheEarlierDeletion() {
    local("participants", participant(key(1), 1001, "Anna", SELF, 2, AFTER, LATER));
    file("participants", participant(key(1), 1001, "Anna", OTHER, 2, AFTER, AFTER));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.plan()).singleElement().satisfies(p -> assertThat(p.action()).isEqualTo(Action.UPDATE));
    assertThat(analysis.hasCollisions()).isFalse();

    local.clear();
    file.clear();
    local("participants", participant(key(1), 1001, "Anna", SELF, 2, AFTER, AFTER));
    file("participants", participant(key(1), 1001, "Anna", OTHER, 2, AFTER, LATER));
    assertThat(analyze().plan()).isEmpty();
  }

  @Test
  void aTombstoneHereWithALiveFileRowIsUpdatedWhenDeletedBeforeTheLastExchange() {
    local("participants", participant(key(1), 1001, "Anna", SELF, 2, BEFORE, BEFORE));
    file("participants", participant(key(1), 1001, "Anna", OTHER, 3, AFTER, null));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.plan()).singleElement().satisfies(p -> assertThat(p.action()).isEqualTo(Action.UPDATE));
    assertThat(analysis.hasCollisions()).isFalse();
  }

  /**
   * A soft delete keeps the origin of the last edit, so a row created here and deleted on the
   * other computer comes back as a tombstone with origin {@code self}; the tombstone itself
   * is the other computer's change.
   */
  @Test
  void aDeletionMadeThereOfARowCreatedHereIsApplied() {
    local("participants", participant(key(1), 1001, "Anna", SELF, 2, BEFORE, null));
    file("participants", participant(key(1), 1001, "Anna", SELF, 2, BEFORE, AFTER));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.plan()).singleElement().satisfies(p -> {
      assertThat(p.action()).isEqualTo(Action.UPDATE);
      assertThat(p.fileRow().isDeleted()).isTrue();
    });
    assertThat(analysis.hasCollisions()).isFalse();
  }

  @Test
  void aDeletionMadeThereOfARowEditedHereSinceIsAC3() {
    local("participants", participant(key(1), 1001, "Anna Edited", SELF, 3, AFTER, null));
    file("participants", participant(key(1), 1001, "Anna", SELF, 2, BEFORE, AFTER));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.plan()).isEmpty();
    assertThat(analysis.collisions()).singleElement().satisfies(c -> assertThat(c.type()).isEqualTo(Type.C3));
  }

  /** Our own tombstone coming back on the stick is not a change made there. */
  @Test
  void ourOwnTombstoneComingBackIsNothing() {
    local("participants", participant(key(1), 1001, "Anna", SELF, 2, BEFORE, BEFORE));
    file("participants", participant(key(1), 1001, "Anna", SELF, 2, BEFORE, BEFORE));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.plan()).isEmpty();
    assertThat(analysis.hasCollisions()).isFalse();
  }

  /** The stale file still shows the row live; the tombstone here was made before the last exchange. */
  @Test
  void aStaleFileRowWithOurOriginDoesNotReviveOurTombstone() {
    local("participants", participant(key(1), 1001, "Anna", SELF, 2, BEFORE, BEFORE));
    file("participants", participant(key(1), 1001, "Anna", SELF, 2, BEFORE, null));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.plan()).isEmpty();
    assertThat(analysis.hasCollisions()).isFalse();
  }

  // ---- C1–C3 ----------------------------------------------------------------------------

  @Test
  void c1ARowCreatedOnAReadOnlyComputerIsACollision() {
    local("participants", participant(key(1), 1001, "Anna", SELF, 0, AFTER, null));

    SyncAnalysis analysis = analyze(SINCE, true);

    assertThat(analysis.collisions()).singleElement().satisfies(c -> {
      assertThat(c.type()).isEqualTo(Type.C1);
      assertThat(c.record()).isEqualTo("Anna Nowak (no. 1001)");
      assertThat(c.local().nodeName()).isEqualTo("Office");
      assertThat(c.file()).isNull();
      assertThat(c.resolutions()).extracting(Collision.Resolution::code).containsExactly("keep-local", "discard-local");
    });
    assertThat(participants(analysis).collisions()).isEqualTo(1);
  }

  @Test
  void c2ARowEditedOnBothComputersShowsTheDifferingFields() {
    local("participants", participant(key(1), 1001, "Anna-Maria", SELF, 2, AFTER, null));
    file("participants", participant(key(1), 1001, "Ann", OTHER, 2, AFTER, null));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.plan()).isEmpty();
    assertThat(analysis.collisions()).singleElement().satisfies(c -> {
      assertThat(c.type()).isEqualTo(Type.C2);
      assertThat(c.explanation()).contains("Both computers edited this record after the last exchange of data");
      assertThat(c.fields()).singleElement().satisfies(f -> {
        assertThat(f.field()).isEqualTo("participants_name");
        assertThat(f.localValue()).isEqualTo("Anna-Maria");
        assertThat(f.fileValue()).isEqualTo("Ann");
      });
      assertThat(c.local().describe()).isEqualTo("changed on Office on 01/03/2026 13:00");
      assertThat(c.file().describe()).isEqualTo("changed on Laptop on 01/03/2026 13:00");
      assertThat(c.resolutions()).extracting(Collision.Resolution::code).containsExactly("take-file", "keep-local");
    });
  }

  @Test
  void c2AlsoCoversARowRewrittenByAThirdNode() {
    local("participants", participant(key(1), 1001, "Anna-Maria", SELF, 2, AFTER, null));
    file("participants", participant(key(1), 1001, "Ann", THIRD, 2, AFTER, null));

    assertThat(analyze().collisions()).singleElement().satisfies(c -> {
      assertThat(c.type()).isEqualTo(Type.C2);
      assertThat(c.file().nodeName()).isEqualTo("node 00000000");
    });
  }

  @Test
  void c3DeletedHereAndEditedThere() {
    local("participants", participant(key(1), 1001, "Anna", SELF, 2, AFTER, AFTER));
    file("participants", participant(key(1), 1001, "Ann", OTHER, 3, AFTER, null));

    assertThat(analyze().collisions()).singleElement().satisfies(c -> {
      assertThat(c.type()).isEqualTo(Type.C3);
      assertThat(c.explanation()).startsWith("This record was deleted on this computer");
      assertThat(c.local().deleted()).isTrue();
      assertThat(c.local().describe()).startsWith("deleted on Office");
      assertThat(c.resolutions()).extracting(Collision.Resolution::code).containsExactly("keep-deleted", "keep-edited");
    });
  }

  @Test
  void c3EditedHereAndDeletedThere() {
    local("participants", participant(key(1), 1001, "Ann", SELF, 3, AFTER, null));
    file("participants", participant(key(1), 1001, "Anna", OTHER, 2, AFTER, AFTER));

    assertThat(analyze().collisions()).singleElement().satisfies(c -> {
      assertThat(c.type()).isEqualTo(Type.C3);
      assertThat(c.explanation()).startsWith("This record was deleted on Laptop");
      assertThat(c.file().deleted()).isTrue();
    });
  }

  // ---- C4–C6 ----------------------------------------------------------------------------

  @Test
  void c4TheSameNumberOnTwoDifferentRecords() {
    local("participants", participant(key(1), 118, "Anna", SELF, 0, AFTER, null));
    file("participants", participant(key(2), 118, "Jan", OTHER, 0, AFTER, null));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.collisions()).singleElement().satisfies(c -> {
      assertThat(c.type()).isEqualTo(Type.C4);
      assertThat(c.explanation()).isEqualTo("Participant number 118 was given to Anna Nowak (no. 118) on Office "
          + "and to Jan Nowak (no. 118) on Laptop; numbers are unique and are printed on certificates.");
      assertThat(c.resolutions()).extracting(Collision.Resolution::code)
          .containsExactly("keep-file-number", "keep-local-number");
    });
  }

  @Test
  void c4CountsATombstoneBecauseTheDatabaseConstraintDoes() {
    local("participants", participant(key(1), 118, "Anna", SELF, 1, BEFORE, BEFORE));
    file("participants", participant(key(2), 118, "Jan", OTHER, 0, AFTER, null));

    assertThat(analyze().collisions()).singleElement().satisfies(c -> assertThat(c.type()).isEqualTo(Type.C4));
  }

  @Test
  void c4IsNotRaisedForTheSameRecordOnBothSides() {
    local("participants", participant(key(1), 118, "Anna", OTHER, 0, BEFORE, null));
    file("participants", participant(key(1), 118, "Anna", OTHER, 1, AFTER, null));

    assertThat(analyze().hasCollisions()).isFalse();
  }

  @Test
  void c5TheSameCourseTypeCodeOnTwoTypesIgnoringCase() {
    local("course_type", courseType(key(1), "Sync", SELF, AFTER));
    file("course_type", courseType(key(2), "SYNC", OTHER, AFTER));

    assertThat(analyze().collisions()).singleElement().satisfies(c -> {
      assertThat(c.type()).isEqualTo(Type.C5);
      assertThat(c.explanation()).startsWith("Course type code SYNC is used by two different course types");
      assertThat(c.resolutions()).extracting(Collision.Resolution::code).containsExactly("keep-file-code", "keep-local-code");
    });
  }

  @Test
  void c6AFileRowPointingAtARecordNeitherComputerHasCanOnlyBeSkipped() {
    file("course_type", courseType(key(1), "SYNC", OTHER, AFTER));
    file("courses", course(key(2), 2001, key(3), key(1), OTHER, AFTER));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.collisions()).singleElement().satisfies(c -> {
      assertThat(c.type()).isEqualTo(Type.C6);
      assertThat(c.record()).isEqualTo("course 2001, SYNC");
      assertThat(c.explanation()).contains("participant that exists on neither computer");
      assertThat(c.resolutions()).extracting(Collision.Resolution::code).containsExactly("skip-row");
    });
  }

  @Test
  void c6AFileRowPointingAtATombstoneHereMayReviveIt() {
    local("participants", participant(key(3), 1001, "Anna", SELF, 1, BEFORE, BEFORE));
    file("participants", participant(key(3), 1001, "Anna", SELF, 1, BEFORE, BEFORE));
    file("course_type", courseType(key(1), "SYNC", OTHER, AFTER));
    file("courses", course(key(2), 2001, key(3), key(1), OTHER, AFTER));

    assertThat(analyze().collisions()).singleElement().satisfies(c -> {
      assertThat(c.type()).isEqualTo(Type.C6);
      assertThat(c.explanation()).contains("deleted here");
      assertThat(c.resolutions()).extracting(Collision.Resolution::code).containsExactly("skip-row", "revive");
    });
  }

  @Test
  void c6ChecksTheJoinRowsOfAPlannedOwner() {
    file("trainer", row(key(1), OTHER, 0, AFTER, null, "trainer_uuid", key(1), "trainer_name", "Jan",
        "trainer_surname", "Kowalski", "trainer_primary_image_uuid", null));
    file("trainer_image", new SyncRow(Map.of("trainer_uuid", key(1), "image_uuid", key(9))));

    assertThat(analyze().collisions()).singleElement().satisfies(c -> {
      assertThat(c.type()).isEqualTo(Type.C6);
      assertThat(c.record()).isEqualTo("Jan Kowalski");
      assertThat(c.fields()).singleElement().satisfies(f -> assertThat(f.field()).isEqualTo("trainer_image"));
    });
  }

  @Test
  void c6NamesTheReferencedRecordSoTwoDanglingMembersOfOneOwnerGetTwoDecisions() {
    file("trainer", row(key(1), OTHER, 0, AFTER, null, "trainer_uuid", key(1), "trainer_name", "Jan",
        "trainer_surname", "Kowalski", "trainer_primary_image_uuid", null));
    file("trainer_image", new SyncRow(Map.of("trainer_uuid", key(1), "image_uuid", key(8))));
    file("trainer_image", new SyncRow(Map.of("trainer_uuid", key(1), "image_uuid", key(9))));

    assertThat(analyze().collisions()).extracting(Collision::id).containsExactlyInAnyOrder(
        "C6-trainer-" + key(1) + "-trainer_image-" + key(8),
        "C6-trainer-" + key(1) + "-trainer_image-" + key(9));
  }

  @Test
  void c6ChecksTheFileSideOfARowEditedOnBothComputers() {
    local("participants", participant(key(1), 1001, "Anna", SELF, 1, AFTER, null));
    file("participants", participant(key(1), 1001, "Anna", OTHER, 1, AFTER, null).with("image_uuid", key(9)));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.plan()).isEmpty();
    assertThat(analysis.collisions()).extracting(Collision::id).containsExactlyInAnyOrder(
        "C2-participants-" + key(1),
        "C6-participants-" + key(1) + "-image_uuid-" + key(9));
    // The C6 only matters if the C2 is resolved in the file's favour, and the report says so.
    assertThat(analysis.collisions()).filteredOn(c -> c.type() == Type.C6).singleElement()
        .extracting(Collision::explanation).asString()
        .endsWith("This applies only if the file's version of the record is taken.");
  }

  @Test
  void c6OfAPlannedRowIsUnconditional() {
    file("participants", participant(key(1), 1001, "Anna", OTHER, 0, AFTER, null).with("image_uuid", key(9)));

    assertThat(analyze().collisions()).singleElement().extracting(Collision::explanation).asString()
        .doesNotContain("This applies only");
  }

  @Test
  void c6ChecksTheFileSideOfARowDeletedHereAndEditedThere() {
    local("participants", participant(key(1), 1001, "Anna", SELF, 1, AFTER, AFTER));
    file("participants", participant(key(1), 1001, "Anna", OTHER, 1, AFTER, null).with("image_uuid", key(9)));

    assertThat(analyze().collisions()).extracting(Collision::type).containsExactlyInAnyOrder(Type.C3, Type.C6);
  }

  // ---- the base of the last file from the same node -------------------------------------

  static final Instant SEEN = SINCE.plusSeconds(5400);

  @Test
  void aFileRowNotTouchedSinceTheLastFileFromThatNodeIsNotAChangeThere() {
    // Written there, imported here, then edited here: the next file still carries their copy.
    local("participants", participant(key(1), 1001, "Ania", SELF, 2, LATER, null));
    file("participants", participant(key(1), 1001, "Anna", OTHER, 1, AFTER, null));

    SyncAnalysis analysis = analyze(SINCE, false, SEEN);

    assertThat(analysis.plan()).isEmpty();
    assertThat(analysis.hasCollisions()).isFalse();
    assertThat(analysis.notices()).isEmpty();
  }

  @Test
  void aFileRowTouchedAfterTheLastFileFromThatNodeIsAChangeThere() {
    local("participants", participant(key(1), 1001, "Ania", SELF, 2, LATER, null),
        participant(key(2), 1002, "Basia", SELF, 2, LATER, null));
    file("participants", participant(key(1), 1001, "Anna", OTHER, 1, LATER, null),
        participant(key(2), 1002, "Basia", OTHER, 1, AFTER, LATER));

    assertThat(analyze(SINCE, false, SEEN).collisions()).extracting(Collision::id, Collision::type)
        .containsExactlyInAnyOrder(tuple("C2-participants-" + key(1), Type.C2),
            tuple("C3-participants-" + key(2), Type.C3));
  }

  @Test
  void withoutABaseEveryRowFromAnotherNodeCountsAsChangedThere() {
    local("participants", participant(key(1), 1001, "Ania", SELF, 2, LATER, null));
    file("participants", participant(key(1), 1001, "Anna", OTHER, 1, AFTER, null));

    assertThat(analyze(SINCE, false, null).collisions()).extracting(Collision::type).containsExactly(Type.C2);
  }

  @Test
  void aReferenceSatisfiedByTheFileItselfIsNotACollision() {
    file("course_type", courseType(key(1), "SYNC", OTHER, AFTER));
    file("participants", participant(key(3), 1001, "Anna", OTHER, 0, AFTER, null));
    file("courses", course(key(2), 2001, key(3), key(1), OTHER, AFTER));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.hasCollisions()).isFalse();
    assertThat(analysis.inserts()).isEqualTo(3);
  }

  @Test
  void joinTablesAndSequencesAreSummarisedNotDecided() {
    file("id_sequence", new SyncRow(new HashMap<>(Map.of("id_sequence_name", "participants", "id_sequence_next", 5))));

    SyncAnalysis analysis = analyze();

    assertThat(analysis.tables()).filteredOn(t -> t.note() != null)
        .extracting(SyncAnalysis.TableSummary::table)
        .containsExactly("trainer_image", "lecturer_image", "technician_image", "courses_trainers",
            "courses_lecturers", "courses_technicians", "id_sequence");
    assertThat(analysis.collisionsByTable()).isEmpty();
  }

  @Test
  void collisionsAreGroupedByTableInFileOrder() {
    local("course_type", courseType(key(1), "A", SELF, AFTER));
    file("course_type", courseType(key(1), "B", OTHER, AFTER));
    local("participants", participant(key(2), 1, "Anna", SELF, 1, AFTER, null));
    file("participants", participant(key(2), 1, "Ann", OTHER, 1, AFTER, null));

    assertThat(analyze().collisionsByTable().keySet()).containsExactly("Course type", "Participant");
  }

  // ---- refusals -------------------------------------------------------------------------

  static SyncManifest manifest(UUID node, String schema) {
    return new SyncManifest(1, schema, "3.0", new SyncManifest.Node(node, "Laptop"), "2026-01-01T00:00:00.000000Z",
        "ACTIVE", false, Map.of(), 0);
  }

  @Test
  void ourOwnExportIsRefused() {
    assertThatThrownBy(() -> SyncAnalyzer.checkAccepted(manifest(SELF, "10"), SELF, "10"))
        .isInstanceOf(SyncRefusedException.class)
        .hasMessageContaining("our own export");
  }

  @Test
  void aDifferentSchemaVersionIsRefused() {
    assertThatThrownBy(() -> SyncAnalyzer.checkAccepted(manifest(OTHER, "11"), SELF, "10"))
        .isInstanceOf(SyncRefusedException.class)
        .hasMessageContaining("schema version 11")
        .hasMessageContaining("runs version 10");
    SyncAnalyzer.checkAccepted(manifest(OTHER, "10"), SELF, "10");
    SyncAnalyzer.checkAccepted(manifest(OTHER, null), SELF, null);
  }
}
