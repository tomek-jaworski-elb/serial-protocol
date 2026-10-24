package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.entity.custom.CourseType;
import com.jaworski.serialprotocol.entity.custom.SyncNode;
import com.jaworski.serialprotocol.repository.custom.CourseTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 1 of docs/sync-spec.md: the installation has an identity after start-up and every
 * row it writes carries it in {@code origin_node}, which the import later uses to tell this
 * node's own state coming back on the stick from an edit made elsewhere.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SyncNodeTest {

  @Autowired
  private SyncNodeService nodeService;
  @Autowired
  private CourseTypeRepository courseTypes;
  @Autowired
  private JdbcTemplate jdbc;

  @Test
  void theNodeRowExistsAfterStartUpAndIsCached() {
    SyncNode node = nodeService.current();
    assertThat(node.getId()).isNotNull();
    assertThat(node.getName()).isNotBlank();
    assertThat(nodeService.currentNodeId()).isEqualTo(node.getId());
    assertThat(jdbc.queryForObject("select count(*) from sync_node", Integer.class)).isEqualTo(1);
  }

  @Test
  void aSaveStampsTheRowWithThisNodesId() {
    CourseType saved = courseTypes.save(
        new CourseType("SYNC-" + UUID.randomUUID().toString().substring(0, 8), "stamped", "stamped"));

    UUID origin = jdbc.queryForObject(
        "select origin_node from course_type where course_type_uuid = ?", UUID.class, saved.getId());
    assertThat(origin).isEqualTo(nodeService.currentNodeId());

    courseTypes.delete(saved);
  }
}
