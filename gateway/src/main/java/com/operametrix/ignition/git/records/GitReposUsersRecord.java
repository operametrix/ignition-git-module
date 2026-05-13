package com.operametrix.ignition.git.records;

import com.inductiveautomation.ignition.gateway.localdb.persistence.*;
import simpleorm.dataset.SFieldFlags;

public class GitReposUsersRecord extends PersistentRecord {

    public static final RecordMeta<GitReposUsersRecord> META = new RecordMeta<>(
            GitReposUsersRecord.class, "GitReposUsersRecord");

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }

    public static final IdentityField Id = new IdentityField(META);
    public static final LongField ProjectId = new LongField(META, "ProjectId");
    public static final ReferenceField<GitProjectsConfigRecord> ProjectName = new ReferenceField<>(
            META, GitProjectsConfigRecord.META, "ProjectName", ProjectId);

    public static final StringField IgnitionUser = new StringField(META, "IgnitionUser",
            SFieldFlags.SPRIMARY_KEY, SFieldFlags.SMANDATORY, SFieldFlags.SDESCRIPTIVE);

    public int getId() {
        return this.getInt(Id);
    }

    public int getProjectId() {
        return this.getInt(ProjectId);
    }

    public String getIgnitionUser() {
        return this.getString(IgnitionUser);
    }

    public void setProjectId(long projectId) {
        this.setLong(ProjectId, projectId);
    }

    public void setIgnitionUser(String ignitionUser) {
        setString(IgnitionUser, ignitionUser);
    }
}
