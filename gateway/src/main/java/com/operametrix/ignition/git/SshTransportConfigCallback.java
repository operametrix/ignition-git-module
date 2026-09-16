package com.operametrix.ignition.git;

import com.operametrix.ignition.git.records.GitKnownHostRecord;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;

/**
 * SSH transport for git remotes: supplies the user's private key and verifies the <em>server's</em>
 * host key against the keys an operator has accepted ({@link GitKnownHostRecord}).
 *
 * <p>An unknown or changed host key aborts the operation with one of the markers below rather than
 * connecting. The Designer recognises {@link #UNKNOWN_HOST_KEY}, shows the fingerprint and offers to
 * accept it; {@link #CHANGED_HOST_KEY} is never offered for acceptance, because a key that changes
 * after being accepted is what interception looks like.
 */
public class SshTransportConfigCallback implements TransportConfigCallback {

    /** Marker + {@code host|keyType|fingerprint}: this host has no accepted key yet. */
    public static final String UNKNOWN_HOST_KEY = "UNKNOWN_HOST_KEY:";

    /** Marker + {@code host|fingerprint}: the host presented a key that does not match the accepted one. */
    public static final String CHANGED_HOST_KEY = "CHANGED_HOST_KEY:";

    String sshKey;

    public SshTransportConfigCallback(String sshKey) {
        this.sshKey = sshKey;
    }

    private SshSessionFactory createSshSessionFactory() {
        return new SshdSessionFactoryBuilder()
                .setHomeDirectory(new File(System.getProperty("user.home")))
                .setSshDirectory(new File(new File(System.getProperty("user.home")), ".ssh"))
                .setDefaultKeysProvider(f -> {
                    try {
                        Iterable<KeyPair> keyPairs = SecurityUtils.loadKeyPairIdentities(
                                null, null, new ByteArrayInputStream(sshKey.getBytes()), null);
                        return keyPairs;
                    } catch (IOException | GeneralSecurityException e) {
                        throw new RuntimeException("Failed to load SSH key", e);
                    }
                })
                .setServerKeyDatabase((homeDir, sshDir) -> new ServerKeyDatabase() {
                    @Override
                    public List<PublicKey> lookup(String connectAddress,
                                                 InetSocketAddress remoteAddress,
                                                 Configuration config) {
                        // Accepted keys are pinned by fingerprint, so there is nothing to hand back
                        // here; accept() below makes the decision.
                        return Collections.emptyList();
                    }

                    @Override
                    public boolean accept(String connectAddress,
                                          InetSocketAddress remoteAddress,
                                          PublicKey serverKey,
                                          Configuration config,
                                          CredentialsProvider provider) {
                        String host = hostOf(connectAddress, remoteAddress);
                        String fingerprint = KeyUtils.getFingerPrint(serverKey);
                        List<GitKnownHostRecord> accepted = GitKnownHostRecord.findByHost(host);

                        for (GitKnownHostRecord known : accepted) {
                            if (fingerprint != null && fingerprint.equals(known.getFingerprint())) {
                                return true;
                            }
                        }
                        if (accepted.isEmpty()) {
                            throw new RuntimeException(UNKNOWN_HOST_KEY + host + "|"
                                    + KeyUtils.getKeyType(serverKey) + "|" + fingerprint);
                        }
                        // Accepted before, different key now: refuse and make the operator look.
                        throw new RuntimeException(CHANGED_HOST_KEY + host + "|" + fingerprint);
                    }
                })
                .build(null);
    }

    /** Host identity used to pin a key: "host:port", or "host" on the default SSH port. */
    static String hostOf(String connectAddress, InetSocketAddress remoteAddress) {
        if (connectAddress != null && !connectAddress.isBlank()) {
            return connectAddress.trim().toLowerCase();
        }
        if (remoteAddress == null) {
            return "";
        }
        String host = remoteAddress.getHostString();
        int port = remoteAddress.getPort();
        return (port == 22 || port <= 0 ? host : host + ":" + port).toLowerCase();
    }

    @Override
    public void configure(Transport transport) {
        SshTransport sshTransport = (SshTransport) transport;
        sshTransport.setSshSessionFactory(createSshSessionFactory());
    }
}
