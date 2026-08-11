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

import java.nio.file.Files;
import java.nio.file.Path;
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

    /** Marker file at the data-dir root recording that the one-time migration has completed. */
    private static final String MIGRATION_MARKER = ".git-module-legacy-migrated";

    public static void migrateIfNeeded(GatewayContext ctx) {
        // Once done, skip entirely — otherwise the legacy SimpleORM metas get re-registered
        // (creating empty tables on fresh installs) and re-queried on every single startup.
        // The migration is idempotent regardless, so a missing/failed marker only re-runs it.
        if (alreadyMigrated(ctx)) {
            log.debug("Legacy git migration marker present; skipping.");
            return;
        }
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
        markMigrated(ctx);
    }

    private static Path markerPath(GatewayContext ctx) {
        return ctx.getSystemManager().getDataDir().toPath().resolve(MIGRATION_MARKER);
    }

    private static boolean alreadyMigrated(GatewayContext ctx) {
        try {
            return Files.exists(markerPath(ctx));
        } catch (Exception e) {
            return false;  // unsure → run the (idempotent) migration
        }
    }

    private static void markMigrated(GatewayContext ctx) {
        try {
            Files.writeString(markerPath(ctx),
                    "Legacy SimpleORM git config migrated to the 8.3 resource store.\n");
        } catch (Exception e) {
            // Non-fatal: migration is idempotent, so a missing marker just re-runs it next start.
            log.warn("Could not write legacy-migration marker; migration may re-run next start: {}", e.toString());
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
                    // Legacy URI is intentionally not migrated: .git/config is now the
                    // source of truth for remotes, so the record only carries identity.
                    GitProjectsConfigRecord.handler().create(name,
                            new GitProjectsConfigRecord.Config(id,
                                    r.getString(LegacyProjectsConfig.ProjectName))).join();
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
                    // Encrypt at migration time (symmetric with the HTTPS path) so the key is
                    // never written to the resource JSON in clear. The legacy plaintext component
                    // stays null; getSSHKey() decrypts the embedded SecretConfig.
                    String plainKey = r.getString(LegacyUserSshKey.SSHKey);
                    GitUserSshKeyRecord.handler().create(name,
                            new GitUserSshKeyRecord.Config(id,
                                    r.getString(LegacyUserSshKey.IgnitionUser),
                                    r.getString(LegacyUserSshKey.KeyName),
                                    null,
                                    GitUserSshKeyRecord.encrypt(plainKey))).join();
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
