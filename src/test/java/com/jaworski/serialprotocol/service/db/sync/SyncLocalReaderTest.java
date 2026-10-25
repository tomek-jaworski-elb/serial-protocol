package com.jaworski.serialprotocol.service.db.sync;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The locked read keeps an edit from another connection out until the import's transaction ends. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SyncLocalReaderTest {

  @Autowired
  private DataSource dataSource;
  @Autowired
  private PlatformTransactionManager transactionManager;
  @Autowired
  private SyncLocalReader reader;

  @Test
  void theLockedReadHoldsEveryRowUntilTheTransactionEnds() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    SyncFixture.seed(jdbc);
    ExecutorService other = Executors.newSingleThreadExecutor();
    try {
      new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
        assertThat(reader.readLocked().get("participants")).hasSize(1);
        Future<Integer> edit = other.submit(() -> editAnnaFromAnotherConnection(jdbc));
        assertThatThrownBy(edit::get).hasCauseInstanceOf(DataAccessException.class);
      });
      assertThat(other.submit(() -> editAnnaFromAnotherConnection(jdbc)).get()).isEqualTo(1);
    } finally {
      other.shutdownNow();
    }
  }

  @Test
  void theLockedReadRefusesToRunOutsideATransaction() {
    assertThatThrownBy(reader::readLocked).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("transaction");
  }

  private static int editAnnaFromAnotherConnection(JdbcTemplate jdbc) {
    return jdbc.execute((ConnectionCallback<Integer>) connection -> {
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCK_TIMEOUT 300");
        return statement.executeUpdate("update participants set participants_email = 'late@example'"
            + " where participants_uuid = '" + SyncFixture.PARTICIPANT + "'");
      }
    });
  }
}
