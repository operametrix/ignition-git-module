package com.operametrix.ignition.git.records.legacy;

import com.inductiveautomation.ignition.gateway.localdb.persistence.IdentityField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
import com.inductiveautomation.ignition.gateway.localdb.persistence.StringField;

/**
 * Read-only legacy view of the pre-8.3 {@code GitProjectsConfigRecord} SimpleORM table,
 * used solely by {@link GitLegacyImporter}. Must be public with a public no-arg constructor
 * so SimpleORM can instantiate it; the table name must match the pre-8.3 RecordMeta name.
 */
public class LegacyProjectsConfig extends PersistentRecord {
    static final RecordMeta<LegacyProjectsConfig> META =
            new RecordMeta<>(LegacyProjectsConfig.class, "GitProjectsConfigRecord");
    static final IdentityField Id = new IdentityField(META);
    static final StringField ProjectName = new StringField(META, "ProjectName");
    static final StringField URI = new StringField(META, "URI");

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }
}
