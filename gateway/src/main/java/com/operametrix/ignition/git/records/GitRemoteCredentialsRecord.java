package com.operametrix.ignition.git.records;

import com.inductiveautomation.ignition.gateway.localdb.persistence.*;
import com.inductiveautomation.ignition.gateway.web.components.editors.PasswordEditorSource;
import com.inductiveautomation.ignition.gateway.web.components.editors.TextAreaEditorSource;
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
    public static final StringField UserName = new StringField(META, "UserName");
    public static final EncodedStringField Password = new EncodedStringField(META, "Password");
    public static final StringField SSHKey = new StringField(META, "SSHKey");

    static final Category RemoteCredentials = new Category(
            "GitRemoteCredentialsRecord.Category.RemoteCredentials", 1000)
            .include(ProjectName, IgnitionUser, RemoteName, UserName, Password, SSHKey);

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

    public void setRemoteName(String remoteName) {
        setString(RemoteName, remoteName);
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

    static {
        SSHKey.getFormMeta().setEditorSource(new TextAreaEditorSource());
        SSHKey.setWide();
        Password.getFormMeta().setEditorSource(PasswordEditorSource.getSharedInstance());
    }
}
