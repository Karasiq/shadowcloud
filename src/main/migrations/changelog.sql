--liquibase formatted sql

--changeset shadowcloud:1
create table sc_keys (
  key_id CHAR(36) not null primary key,
  for_encryption BOOLEAN not null,
  for_decryption BOOLEAN not null,
  serialized_key VARBINARY not null
);