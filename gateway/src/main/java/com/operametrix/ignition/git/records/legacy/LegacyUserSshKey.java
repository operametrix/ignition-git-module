package com.operametrix.ignition.git.records.legacy;

import com.inductiveautomation.ignition.gateway.localdb.persistence.IdentityField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
import com.inductiveautomation.ignition.gateway.localdb.persistence.StringField;

/**
 * Read-only legacy view of the pre-8.3 {@code GitUserSshKeyRecord} SimpleORM table,
 * used solely by {@link GitLegacyImporter}.
 */
public class LegacyUserSshKey extends PersistentRecord {
    static final RecordMeta<LegacyUserSshKey> META =
            new RecordMeta<>(LegacyUserSshKey.class, "GitUserSshKeyRecord");
    static final IdentityField Id = new IdentityField(META);
    static final StringField IgnitionUser = new StringField(META, "IgnitionUser");
    static final StringField KeyName = new StringField(META, "KeyName");
    static final StringField SSHKey = new StringField(META, "SSHKey");

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }
}
