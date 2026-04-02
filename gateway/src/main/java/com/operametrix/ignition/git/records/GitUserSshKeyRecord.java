package com.operametrix.ignition.git.records;

import com.inductiveautomation.ignition.gateway.localdb.persistence.*;
import com.inductiveautomation.ignition.gateway.web.components.editors.TextAreaEditorSource;
import simpleorm.dataset.SFieldFlags;

public class GitUserSshKeyRecord extends PersistentRecord {

    public static final RecordMeta<GitUserSshKeyRecord> META = new RecordMeta<>(
            GitUserSshKeyRecord.class, "GitUserSshKeyRecord");

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }

    public static final IdentityField Id = new IdentityField(META);
    public static final StringField IgnitionUser = new StringField(META, "IgnitionUser",
            SFieldFlags.SMANDATORY, SFieldFlags.SDESCRIPTIVE);
    public static final StringField KeyName = new StringField(META, "KeyName",
            SFieldFlags.SMANDATORY, SFieldFlags.SDESCRIPTIVE);
    public static final StringField SSHKey = new StringField(META, "SSHKey");

    public long getId() {
        return getLong(Id);
    }

    public String getIgnitionUser() {
        return getString(IgnitionUser);
    }

    public void setIgnitionUser(String ignitionUser) {
        setString(IgnitionUser, ignitionUser);
    }

    public String getKeyName() {
        return getString(KeyName);
    }

    public void setKeyName(String keyName) {
        setString(KeyName, keyName);
    }

    public String getSSHKey() {
        return getString(SSHKey);
    }

    public void setSSHKey(String sshKey) {
        setString(SSHKey, sshKey);
    }

    static {
        SSHKey.getFormMeta().setEditorSource(new TextAreaEditorSource());
        SSHKey.setWide();
    }
}
