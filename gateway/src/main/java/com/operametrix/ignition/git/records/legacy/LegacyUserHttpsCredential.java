package com.operametrix.ignition.git.records.legacy;

import com.inductiveautomation.ignition.gateway.localdb.persistence.EncodedStringField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.IdentityField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
import com.inductiveautomation.ignition.gateway.localdb.persistence.StringField;

/**
 * Read-only legacy view of the pre-8.3 {@code GitUserHttpsCredentialRecord} SimpleORM table,
 * used solely by {@link GitLegacyImporter}. {@code Password} is an {@link EncodedStringField}
 * so {@code getString(Password)} returns the decoded plaintext for re-encryption into a
 * {@code SecretConfig}.
 */
public class LegacyUserHttpsCredential extends PersistentRecord {
    static final RecordMeta<LegacyUserHttpsCredential> META =
            new RecordMeta<>(LegacyUserHttpsCredential.class, "GitUserHttpsCredentialRecord");
    static final IdentityField Id = new IdentityField(META);
    static final StringField IgnitionUser = new StringField(META, "IgnitionUser");
    static final StringField HostPattern = new StringField(META, "HostPattern");
    static final StringField UserName = new StringField(META, "UserName");
    static final EncodedStringField Password = new EncodedStringField(META, "Password");

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }
}
