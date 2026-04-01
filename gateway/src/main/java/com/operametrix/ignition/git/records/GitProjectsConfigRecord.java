package com.operametrix.ignition.git.records;

import com.inductiveautomation.ignition.gateway.localdb.persistence.*;
import simpleorm.dataset.SFieldFlags;

public class GitProjectsConfigRecord extends PersistentRecord {

    public static final RecordMeta<GitProjectsConfigRecord> META = new RecordMeta<>(
            GitProjectsConfigRecord.class, "GitProjectsConfigRecord");

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }

    public static final IdentityField Id = new IdentityField(META);
    public static final StringField ProjectName = new StringField(META, "ProjectName", SFieldFlags.SMANDATORY, SFieldFlags.SDESCRIPTIVE);
    public static final StringField URI =
            new StringField(META, "URI", SFieldFlags.SMANDATORY, SFieldFlags.SDESCRIPTIVE);


    static final Category ProjectConfiguration = new Category("GitProjectsConfigRecord.Category.ProjectConfiguration", 1000).include(ProjectName, URI);


    public long getId() {
        return this.getLong(Id);
    }

    public String getProjectName() {
        return this.getString(ProjectName);
    }

    public String getURI() {
        return this.getString(URI);
    }

    public void setProjectName(String projectName) {
        setString(ProjectName, projectName);
    }

    public void setURI(String uri) {
        setString(URI, uri);
    }

    public boolean hasRemote() {
        String uri = this.getString(URI);
        return uri != null && !uri.isEmpty();
    }

    public boolean isSSHAuthentication() {
        if (!hasRemote()) {
            return false;
        }
        return !this.getString(URI).toLowerCase().startsWith("http");
    }

    static {
        URI.setWide();
    }
}
