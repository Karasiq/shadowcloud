--liquibase formatted sql

--changeset shadowcloud:1
CREATE TABLE sc_keys (
  key_id CHAR(36) PRIMARY KEY NOT NULL,
  for_encryption BOOLEAN NOT NULL,
  for_decryption BOOLEAN NOT NULL,
  serialized_key VARBINARY NOT NULL
);

--changeset shadowcloud:2
CREATE TABLE sc_akka_journal (
  ordering BIGINT AUTO_INCREMENT NOT NULL,
  persistence_id VARCHAR(255) NOT NULL,
  sequence_nr BIGINT NOT NULL,
  tags ARRAY NOT NULL,
  message VARBINARY NOT NULL,
  PRIMARY KEY (ordering, persistence_id, sequence_nr)
);

CREATE UNIQUE INDEX message_index ON sc_akka_journal (persistence_id, sequence_nr ASC);

--changeset shadowcloud:3
CREATE TABLE sc_akka_snapshots (
  persistence_id VARCHAR NOT NULL,
  sequence_nr BIGINT NOT NULL,
  timestamp BIGINT NOT NULL,
  snapshot VARBINARY NOT NULL,
  PRIMARY KEY (persistence_id, sequence_nr)
);

CREATE INDEX snapshot_index ON sc_akka_snapshots (persistence_id, sequence_nr DESC, timestamp DESC);