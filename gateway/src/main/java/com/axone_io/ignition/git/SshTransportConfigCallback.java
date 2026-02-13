package com.axone_io.ignition.git;

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

public class SshTransportConfigCallback implements TransportConfigCallback {
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
                        return Collections.emptyList();
                    }

                    @Override
                    public boolean accept(String connectAddress,
                                          InetSocketAddress remoteAddress,
                                          PublicKey serverKey,
                                          Configuration config,
                                          CredentialsProvider provider) {
                        return true;
                    }
                })
                .build(null);
    }

    @Override
    public void configure(Transport transport) {
        SshTransport sshTransport = (SshTransport) transport;
        sshTransport.setSshSessionFactory(createSshSessionFactory());
    }
}
