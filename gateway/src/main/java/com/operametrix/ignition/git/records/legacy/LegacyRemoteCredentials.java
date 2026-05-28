package com.operametrix.ignition.git.records.legacy;

import com.inductiveautomation.ignition.gateway.localdb.persistence.IdentityField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.LongField;
import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
import com.inductiveautomation.ignition.gateway.localdb.persistence.StringField;

/**
 * Read-only legacy view of the pre-8.3 {@code GitRemoteCredentialsRecord} SimpleORM table,
 * used solely by {@link GitLegacyImporter}.
 */
public class LegacyRemoteCredentials extends PersistentRecord {
    static final RecordMeta<LegacyRemoteCredentials> META =
            new RecordMeta<>(LegacyRemoteCredentials.class, "GitRemoteCredentialsRecord");
    static final IdentityField Id = new IdentityField(META);
    static final LongField ProjectId = new LongField(META, "ProjectId");
    static final StringField IgnitionUser = new StringField(META, "IgnitionUser");
    static final StringField RemoteName = new StringField(META, "RemoteName");
    static final LongField SshKeyId = new LongField(META, "SshKeyId");
    static final LongField HttpsCredentialId = new LongField(META, "HttpsCredentialId");

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }
}
