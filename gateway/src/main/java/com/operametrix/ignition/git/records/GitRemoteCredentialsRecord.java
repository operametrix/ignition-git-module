package com.operametrix.ignition.git.records;

import com.inductiveautomation.ignition.gateway.localdb.persistence.*;
import simpleorm.dataset.SFieldFlags;

public class GitRemoteCredentialsRecord extends PersistentRecord {

    public static final RecordMeta<GitRemoteCredentialsRecord> META = new RecordMeta<>(
            GitRemoteCredentialsRecord.class, "GitRemoteCredentialsRecord");

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }

    public static final IdentityField Id = new IdentityField(META);
    public static final LongField ProjectId = new LongField(META, "ProjectId");
    public static final ReferenceField<GitProjectsConfigRecord> ProjectName = new ReferenceField<>(
            META, GitProjectsConfigRecord.META, "ProjectName", ProjectId);

    public static final StringField IgnitionUser = new StringField(META, "IgnitionUser",
            SFieldFlags.SMANDATORY, SFieldFlags.SDESCRIPTIVE);
    public static final StringField RemoteName = new StringField(META, "RemoteName",
            SFieldFlags.SMANDATORY, SFieldFlags.SDESCRIPTIVE);

    public static final LongField SshKeyId = new LongField(META, "SshKeyId");
    public static final LongField HttpsCredentialId = new LongField(META, "HttpsCredentialId");

    static final Category RemoteCredentials = new Category(
            "GitRemoteCredentialsRecord.Category.RemoteCredentials", 1000)
            .include(ProjectName, IgnitionUser, RemoteName, SshKeyId, HttpsCredentialId);

    public int getId() {
        return this.getInt(Id);
    }

    public long getProjectId() {
        return this.getLong(ProjectId);
    }

    public String getIgnitionUser() {
        return this.getString(IgnitionUser);
    }

    public String getRemoteName() {
        return this.getString(RemoteName);
    }

    public void setProjectId(long projectId) {
        this.setLong(ProjectId, projectId);
    }

    public void setIgnitionUser(String ignitionUser) {
        setString(IgnitionUser, ignitionUser);
    }

    public void setRemoteName(String remoteName) {
        setString(RemoteName, remoteName);
    }

    public long getSshKeyId() {
        return getLong(SshKeyId);
    }

    public void setSshKeyId(long sshKeyId) {
        setLong(SshKeyId, sshKeyId);
    }

    public long getHttpsCredentialId() {
        return getLong(HttpsCredentialId);
    }

    public void setHttpsCredentialId(long httpsCredentialId) {
        setLong(HttpsCredentialId, httpsCredentialId);
    }
}
