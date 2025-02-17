/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.client;

import java.util.concurrent.Callable;
import javax.net.ssl.SSLHandshakeException;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.ClientConfiguration;
import org.apache.ignite.configuration.ClientConnectorConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.ssl.SslContextFactory;
import org.apache.ignite.testframework.GridTestUtils;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests cases when node connects to cluster with different set of cipher suites.
 */
public class SslParametersTest extends GridCommonAbstractTest {
    /** */
    public static final String TEST_CACHE_NAME = "TEST";

    /** */
    private volatile String[] cipherSuites;

    /** */
    private volatile String[] protocols;

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        cfg.setClientConnectorConfiguration(new ClientConnectorConfiguration()
            .setSslEnabled(true)
            .setUseIgniteSslContextFactory(true));

        cfg.setSslContextFactory(createSslFactory());

        CacheConfiguration ccfg = new CacheConfiguration(TEST_CACHE_NAME);

        cfg.setCacheConfiguration(ccfg);

        return cfg;
    }

    /**
     * @return Client config.
     */
    protected ClientConfiguration getClientConfiguration() {
        return new ClientConfiguration()
                .setAddresses("127.0.0.1:10800")
                .setSslMode(SslMode.REQUIRED)
                .setSslContextFactory(createSslFactory())
                .setAffinityAwarenessEnabled(false);
    }

    /**
     * @return SSL factory.
     */
    @NotNull private SslContextFactory createSslFactory() {
        SslContextFactory factory = (SslContextFactory)GridTestUtils.sslTrustedFactory("node01", "trustone");

        factory.setCipherSuites(cipherSuites);
        factory.setProtocols(protocols);

        return factory;
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();

        protocols = null;
        cipherSuites = null;
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testSameCipherSuite() throws Exception {
        cipherSuites = new String[] {
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
        };

        startGrid();

        checkSuccessfulClientStart(
            new String[] {
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
            },
            null
        );
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testOneCommonCipherSuite() throws Exception {
        cipherSuites = new String[] {
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
        };

        startGrid();

        checkSuccessfulClientStart(
            new String[] {
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
            },
            null
        );
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testNoCommonCipherSuite() throws Exception {
        cipherSuites = new String[] {
            "TLS_RSA_WITH_AES_128_GCM_SHA256"
        };

        startGrid();

        checkClientStartFailure(
            new String[] {
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
            },
            null
        );
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testNonExistentCipherSuite() throws Exception {
        cipherSuites = new String[] {
            "TLS_RSA_WITH_AES_128_GCM_SHA256"
        };

        startGrid();

        checkClientStartFailure(
            new String[] {
                "TLC_FAKE_CIPHER",
                "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
            },
            null,
            IllegalArgumentException.class,
            "TLC_FAKE_CIPHER"
        );
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testNoCommonProtocols() throws Exception {
        protocols = new String[] {
            "TLSv1.1",
            "SSLv3"
        };

        startGrid();

        checkClientStartFailure(
            null,
            new String[] {
                "TLSv1",
                "TLSv1.2"
            }
        );
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testNonExistentProtocol() throws Exception {
        protocols = new String[] {
            "SSLv3"
        };

        startGrid();

        checkClientStartFailure(
            null,
            new String[] {
                "SSLv3",
                "SSLvDoesNotExist"
            },
            IllegalArgumentException.class,
            "SSLvDoesNotExist"
        );
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    @Ignore("Outdated machines do not consider TLS 1.1 deprecated.")
    public void testDeprecatedProtocolThrows() throws Exception {
        protocols = new String[] {
            "TLSv1.1",
            "TLSv1.2"
        };

        startGrid();

        checkClientStartFailure(
            null,
            new String[] {
                "TLSv1.1",
            },
            SSLHandshakeException.class,
            "No appropriate protocol (protocol is disabled or cipher suites are inappropriate)"
        );
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testSameProtocols() throws Exception {
        protocols = new String[] {
            "TLSv1.1",
            "TLSv1.2"
        };

        startGrid();

        checkSuccessfulClientStart(null,
            new String[] {
                "TLSv1.1",
                "TLSv1.2"
            }
        );
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testOneCommonProtocol() throws Exception {
        protocols = new String[] {
            "TLSv1",
            "TLSv1.1",
            "TLSv1.2"
        };

        startGrid();

        checkSuccessfulClientStart(null,
            new String[] {
                "TLSv1.2",
                "SSLv3"
            }
        );
    }

    /**
     * @param cipherSuites list of cipher suites
     * @param protocols list of protocols
     * @throws Exception If failed.
     */
    private void checkSuccessfulClientStart(String[] cipherSuites, String[] protocols) throws Exception {
        this.cipherSuites = F.isEmpty(cipherSuites) ? null : cipherSuites;
        this.protocols = F.isEmpty(protocols) ? null : protocols;

        try (IgniteClient client = Ignition.startClient(getClientConfiguration())) {
            client.getOrCreateCache(TEST_CACHE_NAME);
        }
    }

    /**
     * @param cipherSuites list of cipher suites
     * @param protocols list of protocols
     */
    private void checkClientStartFailure(String[] cipherSuites, String[] protocols) {
        checkClientStartFailure(
            cipherSuites,
            protocols,
            ClientConnectionException.class,
            "SSL handshake failed"
        );
    }

    /**
     * @param cipherSuites list of cipher suites
     * @param protocols list of protocols
     * @param ex expected exception class
     * @param msg exception message
     */
    private void checkClientStartFailure(
        String[] cipherSuites,
        String[] protocols,
        Class<? extends Throwable> ex,
        String msg
    ) {
        this.cipherSuites = F.isEmpty(cipherSuites) ? null : cipherSuites;
        this.protocols = F.isEmpty(protocols) ? null : protocols;

        GridTestUtils.assertThrowsAnyCause(
            null,
            new Callable<Object>() {
                @Override public Object call() {
                    Ignition.startClient(getClientConfiguration());

                    return null;
                }
            },
            ex,
            msg
        );
    }
}
