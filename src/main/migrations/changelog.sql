--liquibase formatted sql

--changeset shadowcloud:1
create table sc_keys (
  key_id CHAR(36) PRIMARY KEY NOT NULL,
  for_encryption BOOLEAN NOT NULL,
  for_decryption BOOLEAN NOT NULL,
  serialized_key VARBINARY NOT NULL
);

--changeset shadowcloud:2
create table sc_akka_journal (
  ordering BIGINT AUTO_INCREMENT NOT NULL,
  persistence_id VARCHAR(255) NOT NULL,
  sequence_nr BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL,
  tags ARRAY NOT NULL,
  message VARBINARY NOT NULL,
  PRIMARY KEY (ordering, persistence_id, sequence_nr)
);