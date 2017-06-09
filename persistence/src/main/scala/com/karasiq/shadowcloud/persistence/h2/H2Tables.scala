package com.karasiq.shadowcloud.persistence.h2

import com.karasiq.shadowcloud.persistence.model.DBKey

trait H2Tables { self: H2Context ⇒
  import db._

  object schema {
    implicit val keySchemaMeta = schemaMeta[DBKey]("sc_keys", _.id → "key_id",
      _.forEncryption → "for_encryption", _.forDecryption → "for_decryption",
      _.key → "serialized_key")
  }
}
