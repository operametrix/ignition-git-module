package com.operametrix.ignition.git.records.legacy;

import com.inductiveautomation.ignition.gateway.localdb.persistence.IdentityField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.LongField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
import com.inductiveautomation.ignition.gateway.localdb.persistence.StringField;

/**
 * Read-only legacy view of the pre-8.3 {@code GitReposUsersRecord} SimpleORM table,
 * used solely by {@link GitLegacyImporter}.
 */
public class LegacyReposUsers extends PersistentRecord {
    static final RecordMeta<LegacyReposUsers> META =
            new RecordMeta<>(LegacyReposUsers.class, "GitReposUsersRecord");
    static final IdentityField Id = new IdentityField(META);
    static final LongField ProjectId = new LongField(META, "ProjectId");
    static final StringField IgnitionUser = new StringField(META, "IgnitionUser");

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }
}
