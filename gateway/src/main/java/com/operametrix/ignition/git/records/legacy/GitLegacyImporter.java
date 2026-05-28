package com.operametrix.ignition.git.records.legacy;

import com.inductiveautomation.ignition.gateway.localdb.persistence.PersistentRecord;
import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.operametrix.ignition.git.records.GitProjectsConfigRecord;
import com.operametrix.ignition.git.records.GitRemoteCredentialsRecord;
import com.operametrix.ignition.git.records.GitReposUsersRecord;
import com.operametrix.ignition.git.records.GitUserHttpsCredentialRecord;
import com.operametrix.ignition.git.records.GitUserSshKeyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleorm.dataset.SQuery;

import java.util.Collections;
import java.util.List;

/**
 * One-time migration of pre-8.3 SimpleORM rows into the 8.3 resource/config store.
 *
 * <p>Runs on every gateway startup but is idempotent: a legacy row is migrated only if no
 * resource with the same numeric id exists yet, and each successfully-migrated legacy row is
 * deleted from its SimpleORM table so subsequent runs find nothing to do. On a fresh 8.3 install
 * the legacy tables do not exist and every query is skipped. Numeric ids (and therefore the
 * FK relationships between the records) are preserved exactly.
 */
public final class GitLegacyImporter {

    private static final Logger log = LoggerFactory.getLogger(GitLegacyImporter.class);

    private GitLegacyImporter() {
    }

    public static void migrateIfNeeded(GatewayContext ctx) {
        // SimpleORM only resolves a RecordMeta to its table once it has been registered
        // with the schema updater. The pre-8.3 module did this in its GatewayHook; the
        // 8.3 hook no longer does, so register the legacy metas here before querying.
        // updatePersistentRecords is non-destructive on existing matching tables; on a
        // fresh install it creates empty tables that simply yield zero rows.
        try {
            ctx.getSchemaUpdater().updatePersistentRecords(
                    LegacyProjectsConfig.META,
                    LegacyReposUsers.META,
                    LegacyRemoteCredentials.META,
                    LegacyUserSshKey.META,
                    LegacyUserHttpsCredential.META);
        } catch (Exception e) {
            log.warn("Could not register legacy git tables for migration; skipping legacy import.", e);
            return;
        }

        int total = 0;
        total += migrateProjects(ctx);
        total += migrateRepoUsers(ctx);
        total += migrateSshKeys(ctx);
        total += migrateHttpsCreds(ctx);
        total += migrateRemoteCreds(ctx);
        if (total > 0) {
            log.info("Migrated {} legacy git config row(s) from SimpleORM into the 8.3 resource store.", total);
        } else {
            log.debug("No legacy git config rows to migrate.");
        }
    }

    private static <T extends PersistentRecord> List<T> safeQuery(GatewayContext ctx, RecordMeta<T> meta) {
        try {
            return ctx.getPersistenceInterface().query(new SQuery<>(meta));
        } catch (Exception e) {
            log.warn("Legacy query failed for {}: {}", meta, e.toString());
            return Collections.emptyList();
        }
    }

    private static void deleteLegacy(GatewayContext ctx, PersistentRecord row) {
        try {
            row.deleteRecord();
            ctx.getPersistenceInterface().save(row);
        } catch (Exception e) {
            log.warn("Could not remove migrated legacy row (it will be skipped on next start): {}", e.toString());
        }
    }

    private static int migrateProjects(GatewayContext ctx) {
        int n = 0;
        for (LegacyProjectsConfig r : safeQuery(ctx, LegacyProjectsConfig.META)) {
            long id = r.getLong(LegacyProjectsConfig.Id);
            String name = String.valueOf(id);
            try {
                if (GitProjectsConfigRecord.handler().findResource(name).isEmpty()) {
                    GitProjectsConfigRecord.handler().create(name,
                            new GitProjectsConfigRecord.Config(id,
                                    r.getString(LegacyProjectsConfig.ProjectName),
                                    r.getString(LegacyProjectsConfig.URI))).join();
                    n++;
                }
                deleteLegacy(ctx, r);
            } catch (Exception e) {
                log.error("Failed migrating legacy project config id={}", id, e);
            }
        }
        return n;
    }

    private static int migrateRepoUsers(GatewayContext ctx) {
        int n = 0;
        for (LegacyReposUsers r : safeQuery(ctx, LegacyReposUsers.META)) {
            long id = r.getLong(LegacyReposUsers.Id);
            String name = String.valueOf(id);
            try {
                if (GitReposUsersRecord.handler().findResource(name).isEmpty()) {
                    GitReposUsersRecord.handler().create(name,
                            new GitReposUsersRecord.Config(id,
                                    r.getLong(LegacyReposUsers.ProjectId),
                                    r.getString(LegacyReposUsers.IgnitionUser))).join();
                    n++;
                }
                deleteLegacy(ctx, r);
            } catch (Exception e) {
                log.error("Failed migrating legacy repo-user id={}", id, e);
            }
        }
        return n;
    }

    private static int migrateSshKeys(GatewayContext ctx) {
        int n = 0;
        for (LegacyUserSshKey r : safeQuery(ctx, LegacyUserSshKey.META)) {
            long id = r.getLong(LegacyUserSshKey.Id);
            String name = String.valueOf(id);
            try {
                if (GitUserSshKeyRecord.handler().findResource(name).isEmpty()) {
                    GitUserSshKeyRecord.handler().create(name,
                            new GitUserSshKeyRecord.Config(id,
                                    r.getString(LegacyUserSshKey.IgnitionUser),
                                    r.getString(LegacyUserSshKey.KeyName),
                                    r.getString(LegacyUserSshKey.SSHKey))).join();
                    n++;
                }
                deleteLegacy(ctx, r);
            } catch (Exception e) {
                log.error("Failed migrating legacy ssh key id={}", id, e);
            }
        }
        return n;
    }

    private static int migrateHttpsCreds(GatewayContext ctx) {
        int n = 0;
        for (LegacyUserHttpsCredential r : safeQuery(ctx, LegacyUserHttpsCredential.META)) {
            long id = r.getLong(LegacyUserHttpsCredential.Id);
            String name = String.valueOf(id);
            try {
                if (GitUserHttpsCredentialRecord.handler().findResource(name).isEmpty()) {
                    // EncodedStringField.getString decodes the stored value to plaintext;
                    // re-encrypt it into an 8.3 embedded SecretConfig.
                    String plain = r.getString(LegacyUserHttpsCredential.Password);
                    GitUserHttpsCredentialRecord.handler().create(name,
                            new GitUserHttpsCredentialRecord.Config(id,
                                    r.getString(LegacyUserHttpsCredential.IgnitionUser),
                                    r.getString(LegacyUserHttpsCredential.HostPattern),
                                    r.getString(LegacyUserHttpsCredential.UserName),
                                    GitUserHttpsCredentialRecord.encrypt(plain))).join();
                    n++;
                }
                deleteLegacy(ctx, r);
            } catch (Exception e) {
                log.error("Failed migrating legacy https credential id={}", id, e);
            }
        }
        return n;
    }

    private static int migrateRemoteCreds(GatewayContext ctx) {
        int n = 0;
        for (LegacyRemoteCredentials r : safeQuery(ctx, LegacyRemoteCredentials.META)) {
            long id = r.getLong(LegacyRemoteCredentials.Id);
            String name = String.valueOf(id);
            try {
                if (GitRemoteCredentialsRecord.handler().findResource(name).isEmpty()) {
                    GitRemoteCredentialsRecord.handler().create(name,
                            new GitRemoteCredentialsRecord.Config(id,
                                    r.getLong(LegacyRemoteCredentials.ProjectId),
                                    r.getString(LegacyRemoteCredentials.IgnitionUser),
                                    r.getString(LegacyRemoteCredentials.RemoteName),
                                    r.getLong(LegacyRemoteCredentials.SshKeyId),
                                    r.getLong(LegacyRemoteCredentials.HttpsCredentialId))).join();
                    n++;
                }
                deleteLegacy(ctx, r);
            } catch (Exception e) {
                log.error("Failed migrating legacy remote credential id={}", id, e);
            }
        }
        return n;
    }
}

/*
 * The legacy read-only SimpleORM record definitions live in their own public top-level
 * files in this package (LegacyProjectsConfig, LegacyReposUsers, LegacyRemoteCredentials,
 * LegacyUserSshKey, LegacyUserHttpsCredential). SimpleORM instantiates record classes by
 * reflection and requires them to be public with an accessible public no-arg constructor,
 * so they cannot be package-private classes co-located in this file.
 */
