package com.operametrix.ignition.git.records;

import com.inductiveautomation.ignition.gateway.localdb.persistence.*;
import com.inductiveautomation.ignition.gateway.web.components.editors.PasswordEditorSource;
import simpleorm.dataset.SFieldFlags;

public class GitUserHttpsCredentialRecord extends PersistentRecord {

    public static final RecordMeta<GitUserHttpsCredentialRecord> META = new RecordMeta<>(
            GitUserHttpsCredentialRecord.class, "GitUserHttpsCredentialRecord");

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }

    public static final IdentityField Id = new IdentityField(META);
    public static final StringField IgnitionUser = new StringField(META, "IgnitionUser",
            SFieldFlags.SMANDATORY, SFieldFlags.SDESCRIPTIVE);
    public static final StringField HostPattern = new StringField(META, "HostPattern",
            SFieldFlags.SMANDATORY, SFieldFlags.SDESCRIPTIVE);
    public static final StringField UserName = new StringField(META, "UserName");
    public static final EncodedStringField Password = new EncodedStringField(META, "Password");

    static final Category HttpsCredentials = new Category(
            "GitUserHttpsCredentialRecord.Category.HttpsCredentials", 1000)
            .include(IgnitionUser, HostPattern, UserName, Password);

    public long getId() {
        return getLong(Id);
    }

    public String getIgnitionUser() {
        return getString(IgnitionUser);
    }

    public void setIgnitionUser(String ignitionUser) {
        setString(IgnitionUser, ignitionUser);
    }

    public String getHostPattern() {
        return getString(HostPattern);
    }

    public void setHostPattern(String hostPattern) {
        setString(HostPattern, hostPattern);
    }

    public String getUserName() {
        return getString(UserName);
    }

    public void setUserName(String userName) {
        setString(UserName, userName);
    }

    public String getPassword() {
        return getString(Password);
    }

    public void setPassword(String password) {
        setString(Password, password);
    }

    static {
        Password.getFormMeta().setEditorSource(PasswordEditorSource.getSharedInstance());
    }
}
