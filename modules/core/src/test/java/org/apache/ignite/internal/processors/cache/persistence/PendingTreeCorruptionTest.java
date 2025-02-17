/*
 * Copyright 2020 GridGain Systems, Inc. and Contributors.
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

package org.apache.ignite.internal.processors.cache.persistence;

import java.util.concurrent.TimeUnit;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.affinity.AffinityFunction;
import org.apache.ignite.cache.affinity.AffinityKeyMapped;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.cluster.ClusterState;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.processors.cache.CacheGroupContext;
import org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManager;
import org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl;
import org.apache.ignite.internal.processors.cache.tree.PendingEntriesTree;
import org.apache.ignite.internal.processors.cache.tree.PendingRow;
import org.apache.ignite.internal.util.lang.GridCursor;
import org.apache.ignite.internal.util.typedef.internal.CU;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.testframework.GridTestUtils;
import org.apache.ignite.testframework.junits.WithSystemProperty;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.apache.ignite.util.deque.FastSizeDeque;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static java.util.concurrent.TimeUnit.MINUTES;

/** */
public class PendingTreeCorruptionTest extends GridCommonAbstractTest {
    /** */
    @Before
    public void before() throws Exception {
        stopAllGrids();

        cleanPersistenceDir();
    }

    /** */
    @After
    public void after() throws Exception {
        stopAllGrids();

        cleanPersistenceDir();
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);

        cfg.setConsistentId(igniteInstanceName);

        cfg.setDataStorageConfiguration(new DataStorageConfiguration()
            .setDefaultDataRegionConfiguration(new DataRegionConfiguration()
                .setPersistenceEnabled(true)
            )
            .setWalSegments(3)
            .setWalSegmentSize(512 * 1024)
        );

        return cfg;
    }

    /**
     * Checks correctness tombstone partition determination on pending tree.
     *
     * @throws Exception If failed.
     */
    @Test
    @WithSystemProperty(key = "CLEANUP_WORKER_SLEEP_INTERVAL", value = "3000000")
    @WithSystemProperty(key = "DEFAULT_TOMBSTONE_TTL", value = "50")
    public void testCorrectnessPartition() throws Exception {
        IgniteEx ig = startGrid(0);

        ig.cluster().state(ClusterState.ACTIVE);

        AffinityFunction aff = new RendezvousAffinityFunction(false, 16);

        IgniteCache cache = ig.getOrCreateCache(new CacheConfiguration<>(DEFAULT_CACHE_NAME)
            .setAffinity(aff));

        CustomKey key = null;

        for (int i = 0; i < 100; i++) {
            CustomKey testKey = new CustomKey(i, "Key");

            if (aff.partition(testKey) != aff.partition(i)) {
                key = testKey;

                break;
            }
        }

        assertNotNull("Can not find key.", key);

        int part = aff.partition(key.id);

        info("Key was found [key=" + key + ", part=" + part + ']');

        cache.put(key, new Object());

        CacheGroupContext grp = ig.context().cache().cacheGroup(CU.cacheId(DEFAULT_CACHE_NAME));

        IgniteCacheOffheapManager.CacheDataStore store = ((IgniteCacheOffheapManagerImpl)grp.offheap())
            .dataStore(0, true);

        // Get pending tree of expire cache.
        PendingEntriesTree pendingTree = store.pendingTree();

        assertTrue(pendingTree.isEmpty());

        cache.remove(key);

        assertFalse(pendingTree.isEmpty());

        FastSizeDeque<PendingRow> expireQueue = grp.shared().evict().evictQueue(true);

        assertTrue(GridTestUtils.waitForCondition(() ->
            !expireQueue.isEmptyx(), 10_000));

        PendingRow pendingRow = expireQueue.peek();

        assertEquals(part, pendingRow.key.partition());
    }

    /** */
    @Test
    public void testCorruptionWhileLoadingData() throws Exception {
        IgniteEx ig = startGrid(0);

        ig.cluster().state(ClusterState.ACTIVE);

        String expireCacheName = "cacheWithExpire";
        String regularCacheName = "cacheWithoutExpire";
        String grpName = "cacheGroup";

        IgniteCache<Object, Object> expireCache = ig.getOrCreateCache(
            new CacheConfiguration<>(expireCacheName)
                .setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(new Duration(MINUTES, 10)))
                .setGroupName(grpName)
        );

        IgniteCache<Object, Object> regularCache = ig.getOrCreateCache(
            new CacheConfiguration<>(regularCacheName)
                .setGroupName(grpName)
        );

        // This will initialize partition and cache structures.
        expireCache.put(0, 0);
        expireCache.remove(0);

        int expireCacheId = CU.cacheGroupId(expireCacheName, grpName);

        CacheGroupContext grp = ig.context().cache().cacheGroup(CU.cacheId(grpName));
        IgniteCacheOffheapManager.CacheDataStore store = ((IgniteCacheOffheapManagerImpl)grp.offheap()).dataStore(0, true);

        // Get pending tree of expire cache.
        PendingEntriesTree pendingTree = store.pendingTree();

        long year = TimeUnit.DAYS.toMillis(365);
        long expiration = System.currentTimeMillis() + year;

        ig.context().cache().context().database().checkpointReadLock();

        try {
            // Carefully calculated number. Just enough for the first split to happen, but not more.
            for (int i = 0; i < 202; i++)
                pendingTree.putx(new PendingRow(expireCacheId, false, expiration, expiration + i)); // link != 0

            // Open cursor, it'll cache first leaf of the tree.
            GridCursor<PendingRow> cur = pendingTree.find(
                null,
                new PendingRow(expireCacheId, false, expiration + year, 0),
                PendingEntriesTree.WITHOUT_KEY
            );

            // Required for "do" loop to work.
            assertTrue(cur.next());

            int cnt = 0;

            // Emulate real expiry loop but with a more precise control.
            do {
                PendingRow row = cur.get();

                pendingTree.removex(row);

                // Another carefully calculated moment. Here the page cache is exhausted AND the real page is merged
                // with its sibling, meaning that cached "nextPageId" points to empty page from reuse list.
                if (row.link - row.expireTime == 100) {
                    // Put into another cache will take a page from reuse list first. This means that cached
                    // "nextPageId" points to a data page.
                    regularCache.put(0, 0);
                }

                cnt++;
            }
            while (cur.next());

            assertEquals(202, cnt);
        }
        finally {
            ig.context().cache().context().database().checkpointReadUnlock();
        }
    }

    /**
     * Custom affinity key.
     */
    public static class CustomKey {
        /** Id. */
        @AffinityKeyMapped
        private int id;

        /** Name. */
        private String name;

        /**
         * Constructor.
         *
         * @param id Id.
         * @param name Name.
         */
        public CustomKey(int id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * Gets id.
         *
         * @return Id.
         */
        public int getId() {
            return id;
        }

        /**
         * Gets name.
         *
         * @return Name.
         */
        public String getName() {
            return name;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(CustomKey.class, this);
        }
    }
}
