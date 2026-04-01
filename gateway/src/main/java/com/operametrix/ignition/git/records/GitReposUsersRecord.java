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

    // Legacy fields kept for schema compatibility and commissioning support
    public static final StringField Email = new StringField(META, "Email").setDefault("");
    public static final StringField UserName = new StringField(META, "UserName");
    public static final StringField SSHKey = new StringField(META, "SSHKey");
    public static final EncodedStringField Password = new EncodedStringField(META, "Password");

    public int getId() {
        return this.getInt(Id);
    }

    public int getProjectId() {
        return this.getInt(ProjectId);
    }

    public String getIgnitionUser() {
        return this.getString(IgnitionUser);
    }

    public String getEmail() {
        return this.getString(Email);
    }

    public String getUserName() {
        return this.getString(UserName);
    }

    public String getPassword() {
        return this.getString(Password);
    }

    public String getSSHKey() {
        return this.getString(SSHKey);
    }

    public void setProjectId(long projectId) {
        this.setLong(ProjectId, projectId);
    }

    public void setIgnitionUser(String ignitionUser) {
        setString(IgnitionUser, ignitionUser);
    }

    public void setEmail(String email) {
        setString(Email, email);
    }

    public void setUserName(String userName) {
        setString(UserName, userName);
    }

    public void setPassword(String password) {
        setString(Password, password);
    }

    public void setSSHKey(String sshKey) {
        setString(SSHKey, sshKey);
    }
}
