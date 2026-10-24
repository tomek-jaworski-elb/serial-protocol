package com.jaworski.serialprotocol.service.db.sync;

/** A sync file the import will not analyse; the message is shown to the user as it is. */
public class SyncRefusedException extends RuntimeException {

  public SyncRefusedException(String message) {
    super(message);
  }

  public SyncRefusedException(String message, Throwable cause) {
    super(message, cause);
  }
}
