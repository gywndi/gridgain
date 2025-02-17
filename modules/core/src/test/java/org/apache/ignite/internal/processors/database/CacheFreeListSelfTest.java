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

package org.apache.ignite.internal.processors.database;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider;
import org.apache.ignite.internal.metric.IoStatisticsHolderNoOp;
import org.apache.ignite.internal.pagemem.PageIdAllocator;
import org.apache.ignite.internal.pagemem.PageMemory;
import org.apache.ignite.internal.pagemem.PageUtils;
import org.apache.ignite.internal.pagemem.impl.PageMemoryNoStoreImpl;
import org.apache.ignite.internal.processors.cache.CacheObject;
import org.apache.ignite.internal.processors.cache.CacheObjectContext;
import org.apache.ignite.internal.processors.cache.CacheObjectValueContext;
import org.apache.ignite.internal.processors.cache.KeyCacheObject;
import org.apache.ignite.internal.processors.cache.persistence.CacheDataRow;
import org.apache.ignite.internal.processors.cache.persistence.DataRegion;
import org.apache.ignite.internal.processors.cache.persistence.DataRegionMetricsImpl;
import org.apache.ignite.internal.processors.cache.persistence.diagnostic.pagelocktracker.PageLockTrackerManager;
import org.apache.ignite.internal.processors.cache.persistence.evict.NoOpPageEvictionTracker;
import org.apache.ignite.internal.processors.cache.persistence.freelist.CacheFreeList;
import org.apache.ignite.internal.processors.cache.persistence.freelist.FreeList;
import org.apache.ignite.internal.processors.cache.persistence.tree.io.CacheVersionIO;
import org.apache.ignite.internal.processors.cache.version.GridCacheVersion;
import org.apache.ignite.plugin.extensions.communication.MessageReader;
import org.apache.ignite.plugin.extensions.communication.MessageWriter;
import org.apache.ignite.spi.metric.noop.NoopMetricExporterSpi;
import org.apache.ignite.testframework.GridTestUtils;
import org.apache.ignite.testframework.junits.GridTestKernalContext;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
public class CacheFreeListSelfTest extends GridCommonAbstractTest {
    /** */
    private static final long MB = 1024L * 1024L;

    /** */
    private PageMemory pageMem;

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        if (pageMem != null)
            pageMem.stop(true);

        pageMem = null;
    }

    /**
     * @throws Exception if failed.
     */
    @Test
    public void testInsertDeleteSingleThreaded_1024() throws Exception {
        checkInsertDeleteSingleThreaded(1024);
    }

    /**
     * @throws Exception if failed.
     */
    @Test
    public void testInsertDeleteSingleThreaded_2048() throws Exception {
        checkInsertDeleteSingleThreaded(2048);
    }

    /**
     * @throws Exception if failed.
     */
    @Test
    public void testInsertDeleteSingleThreaded_4096() throws Exception {
        checkInsertDeleteSingleThreaded(4096);
    }

    /**
     * @throws Exception if failed.
     */
    @Test
    public void testInsertDeleteSingleThreaded_8192() throws Exception {
        checkInsertDeleteSingleThreaded(8192);
    }

    /**
     * @throws Exception if failed.
     */
    @Test
    public void testInsertDeleteSingleThreaded_16384() throws Exception {
        checkInsertDeleteSingleThreaded(16384);
    }

    /**
     * @throws Exception if failed.
     */
    @Test
    public void testInsertDeleteMultiThreaded_1024() throws Exception {
        checkInsertDeleteMultiThreaded(1024);
    }

    /**
     * @throws Exception if failed.
     */
    @Test
    public void testInsertDeleteMultiThreaded_2048() throws Exception {
        checkInsertDeleteMultiThreaded(2048);
    }

    /**
     * @throws Exception if failed.
     */
    @Test
    public void testInsertDeleteMultiThreaded_4096() throws Exception {
        checkInsertDeleteMultiThreaded(4096);
    }

    /**
     * @throws Exception if failed.
     */
    @Test
    public void testInsertDeleteMultiThreaded_8192() throws Exception {
        checkInsertDeleteMultiThreaded(8192);
    }

    /**
     * @throws Exception if failed.
     */
    @Test
    public void testInsertDeleteMultiThreaded_16384() throws Exception {
        checkInsertDeleteMultiThreaded(16384);
    }

    /**
     * @param pageSize Page size.
     * @throws Exception If failed.
     */
    protected void checkInsertDeleteMultiThreaded(final int pageSize) throws Exception {
        final FreeList list = createFreeList(pageSize);

        Random rnd = new Random();

        final ConcurrentMap<Long, TestDataRow> stored = new ConcurrentHashMap<>();

        for (int i = 0; i < 100; i++) {
            int keySize = rnd.nextInt(pageSize * 3 / 2) + 10;
            int valSize = rnd.nextInt(pageSize * 5 / 2) + 10;

            TestDataRow row = new TestDataRow(keySize, valSize);

            list.insertDataRow(row, IoStatisticsHolderNoOp.INSTANCE);

            assertTrue(row.link() != 0L);

            TestDataRow old = stored.put(row.link(), row);

            assertNull(old);
        }

        final AtomicBoolean grow = new AtomicBoolean(true);

        GridTestUtils.runMultiThreaded(new Callable<Object>() {
            @Override public Object call() throws Exception {
                Random rnd = ThreadLocalRandom.current();

                for (int i = 0; i < 200_000; i++) {
                    boolean grow0 = grow.get();

                    if (grow0) {
                        if (stored.size() > 20_000) {
                            if (grow.compareAndSet(true, false))
                                info("Shrink... [" + stored.size() + ']');

                            grow0 = false;
                        }
                    }
                    else {
                        if (stored.size() < 1_000) {
                            if (grow.compareAndSet(false, true))
                                info("Grow... [" + stored.size() + ']');

                            grow0 = true;
                        }
                    }

                    boolean insert = rnd.nextInt(100) < 70 == grow0;

                    if (insert) {
                        int keySize = rnd.nextInt(pageSize * 3 / 2) + 10;
                        int valSize = rnd.nextInt(pageSize * 3 / 2) + 10;

                        TestDataRow row = new TestDataRow(keySize, valSize);

                        list.insertDataRow(row, IoStatisticsHolderNoOp.INSTANCE);

                        assertTrue(row.link() != 0L);

                        TestDataRow old = stored.put(row.link(), row);

                        assertNull(old);
                    }
                    else {
                        while (true) {
                            Iterator<TestDataRow> it = stored.values().iterator();

                            if (it.hasNext()) {
                                TestDataRow row = it.next();

                                TestDataRow rmvd = stored.remove(row.link);

                                if (rmvd != null) {
                                    list.removeDataRowByLink(row.link, IoStatisticsHolderNoOp.INSTANCE);

                                    break;
                                }
                            }
                        }
                    }
                }

                return null;
            }
        }, 8, "runner");
    }

    /**
     * @throws Exception if failed.
     */
    protected void checkInsertDeleteSingleThreaded(int pageSize) throws Exception {
        FreeList list = createFreeList(pageSize);

        Random rnd = new Random();

        Map<Long, TestDataRow> stored = new HashMap<>();

        for (int i = 0; i < 100; i++) {
            int keySize = rnd.nextInt(pageSize * 3 / 2) + 10;
            int valSize = rnd.nextInt(pageSize * 5 / 2) + 10;

            TestDataRow row = new TestDataRow(keySize, valSize);

            list.insertDataRow(row, IoStatisticsHolderNoOp.INSTANCE);

            assertTrue(row.link() != 0L);

            TestDataRow old = stored.put(row.link(), row);

            assertNull(old);
        }

        boolean grow = true;

        for (int i = 0; i < 1_000_000; i++) {
            if (grow) {
                if (stored.size() > 20_000) {
                    grow = false;

                    info("Shrink... [" + stored.size() + ']');
                }
            }
            else {
                if (stored.size() < 1_000) {
                    grow = true;

                    info("Grow... [" + stored.size() + ']');
                }
            }

            boolean insert = rnd.nextInt(100) < 70 == grow;

            if (insert) {
                int keySize = rnd.nextInt(pageSize * 3 / 2) + 10;
                int valSize = rnd.nextInt(pageSize * 3 / 2) + 10;

                TestDataRow row = new TestDataRow(keySize, valSize);

                list.insertDataRow(row, IoStatisticsHolderNoOp.INSTANCE);

                assertTrue(row.link() != 0L);

                TestDataRow old = stored.put(row.link(), row);

                assertNull(old);
            }
            else {
                Iterator<TestDataRow> it = stored.values().iterator();

                if (it.hasNext()) {
                    TestDataRow row = it.next();

                    TestDataRow rmvd = stored.remove(row.link);

                    assertTrue(rmvd == row);

                    list.removeDataRowByLink(row.link, IoStatisticsHolderNoOp.INSTANCE);
                }
            }
        }
    }

    /**
     * @return Page memory.
     */
    protected PageMemory createPageMemory(int pageSize, DataRegionConfiguration plcCfg) {
        PageMemory pageMem = new PageMemoryNoStoreImpl(log,
            new UnsafeMemoryProvider(log),
            pageSize,
            plcCfg,
            new DataRegionMetricsImpl(plcCfg, new GridTestKernalContext(log())),
            true);

        pageMem.start();

        return pageMem;
    }

    /**
     * @param pageSize Page size.
     * @return Free list.
     * @throws Exception If failed.
     */
    private FreeList<?> createFreeList(int pageSize) throws Exception {
        DataRegionConfiguration plcCfg = new DataRegionConfiguration()
            .setInitialSize(1024 * MB)
            .setMaxSize(1024 * MB);

        pageMem = createPageMemory(pageSize, plcCfg);

        long metaPageId = pageMem.allocatePage(1, 1, PageIdAllocator.FLAG_DATA);

        IgniteConfiguration cfg = new IgniteConfiguration().setMetricExporterSpi(new NoopMetricExporterSpi());

        DataRegionMetricsImpl regionMetrics = new DataRegionMetricsImpl(plcCfg, new GridTestKernalContext(log, cfg));

        DataRegion dataRegion = new DataRegion(pageMem, plcCfg, regionMetrics, new NoOpPageEvictionTracker());

        PageLockTrackerManager pageLockTrackerManager = mock(PageLockTrackerManager.class);

        when(pageLockTrackerManager.createPageLockTracker(anyString())).thenReturn(PageLockTrackerManager.NOOP_LSNR);

        return new CacheFreeList(
            1,
            "freelist",
            dataRegion,
            null,
            metaPageId,
            true,
            pageLockTrackerManager,
            new GridTestKernalContext(log),
            null,
            PageIdAllocator.FLAG_IDX
        );
    }

    /**
     *
     */
    private static class TestDataRow implements CacheDataRow {
        /** */
        private long link;

        /** */
        private TestCacheObject key;

        /** */
        private TestCacheObject val;

        /** */
        private GridCacheVersion ver;

        /**
         * @param keySize Key size.
         * @param valSize Value size.
         */
        private TestDataRow(int keySize, int valSize) {
            key = new TestCacheObject(keySize);
            val = new TestCacheObject(valSize);
            ver = new GridCacheVersion(keySize, valSize, 1);
        }

        /** {@inheritDoc} */
        @Override public KeyCacheObject key() {
            return key;
        }

        /** {@inheritDoc} */
        @Override public void key(KeyCacheObject key) {
            this.key = (TestCacheObject)key;
        }

        /** {@inheritDoc} */
        @Override public CacheObject value() {
            return val;
        }

        /** {@inheritDoc} */
        @Override public GridCacheVersion version() {
            return ver;
        }

        /** {@inheritDoc} */
        @Override public long expireTime() {
            return 0;
        }

        /** {@inheritDoc} */
        @Override public int partition() {
            return 0;
        }

        /** {@inheritDoc} */
        @Override public int size() throws IgniteCheckedException {
            int len = key().valueBytesLength(null);

            len += value().valueBytesLength(null) + CacheVersionIO.size(version(), false) + 8;

            return len + (cacheId() != 0 ? 4 : 0);
        }

        /** {@inheritDoc} */
        @Override public int headerSize() {
            return 0;
        }

        /** {@inheritDoc} */
        @Override public long link() {
            return link;
        }

        /** {@inheritDoc} */
        @Override public void link(long link) {
            this.link = link;
        }

        /** {@inheritDoc} */
        @Override public int hash() {
            throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override public int cacheId() {
            return 0;
        }

        /** {@inheritDoc} */
        @Override public void cacheId(int cacheId) {
            throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override public long newMvccCoordinatorVersion() {
            return 0;
        }

        /** {@inheritDoc} */
        @Override public long newMvccCounter() {
            return 0;
        }

        /** {@inheritDoc} */
        @Override public int newMvccOperationCounter() {
            return 0;
        }

        /** {@inheritDoc} */
        @Override public long mvccCoordinatorVersion() {
            return 0;
        }

        /** {@inheritDoc} */
        @Override public long mvccCounter() {
            return 0;
        }

        /** {@inheritDoc} */
        @Override public int mvccOperationCounter() {
            return 0;
        }

        /** {@inheritDoc} */
        @Override public byte mvccTxState() {
            return 0;
        }

        /** {@inheritDoc} */
        @Override public byte newMvccTxState() {
            return 0;
        }

        /** {@inheritDoc} */
        @Override public boolean tombstone() {
            return false;
        }
    }

    /**
     *
     */
    private static class TestCacheObject implements KeyCacheObject {
        /** */
        private byte[] data;

        /**
         * @param size Object size.
         */
        private TestCacheObject(int size) {
            data = new byte[size];

            Arrays.fill(data, (byte)size);
        }

        /** {@inheritDoc} */
        @Override public boolean internal() {
            return false;
        }

        /** {@inheritDoc} */
        @Override public int partition() {
            return 0;
        }

        /** {@inheritDoc} */
        @Override public void partition(int part) {
            assert false;
        }

        /** {@inheritDoc} */
        @Override public KeyCacheObject copy(int part) {
            assert false;

            return null;
        }

        /** {@inheritDoc} */
        @Nullable @Override public <T> T value(CacheObjectValueContext ctx, boolean cpy) {
            return value(ctx, cpy, null);
        }

        @Override public <T> @Nullable T value(CacheObjectValueContext ctx, boolean cpy, ClassLoader ldr) {
            return (T)data;
        }

        /** {@inheritDoc} */
        @Override public byte[] valueBytes(CacheObjectValueContext ctx) throws IgniteCheckedException {
            return data;
        }

        /** {@inheritDoc} */
        @Override public int valueBytesLength(CacheObjectContext ctx) {
            return data.length;
        }

        /** {@inheritDoc} */
        @Override public boolean putValue(ByteBuffer buf) throws IgniteCheckedException {
            buf.put(data);

            return true;
        }

        /** {@inheritDoc} */
        @Override public int putValue(long addr) throws IgniteCheckedException {
            PageUtils.putBytes(addr, 0, data);

            return data.length;
        }

        /** {@inheritDoc} */
        @Override public boolean putValue(ByteBuffer buf, int off, int len) throws IgniteCheckedException {
            buf.put(data, off, len);

            return true;
        }

        /** {@inheritDoc} */
        @Override public byte cacheObjectType() {
            return 42;
        }

        /** {@inheritDoc} */
        @Override public boolean isPlatformType() {
            return false;
        }

        /** {@inheritDoc} */
        @Override public KeyCacheObject prepareForCache(CacheObjectContext ctx, boolean compress) {
            assert false;

            return this;
        }

        /** {@inheritDoc} */
        @Override public void finishUnmarshal(CacheObjectValueContext ctx, ClassLoader ldr)
            throws IgniteCheckedException {
            assert false;
        }

        /** {@inheritDoc} */
        @Override public void prepareMarshal(CacheObjectValueContext ctx) throws IgniteCheckedException {
            assert false;
        }

        /** {@inheritDoc} */
        @Override public boolean writeTo(ByteBuffer buf, MessageWriter writer) {
            assert false;

            return false;
        }

        /** {@inheritDoc} */
        @Override public boolean readFrom(ByteBuffer buf, MessageReader reader) {
            assert false;

            return false;
        }

        /** {@inheritDoc} */
        @Override public short directType() {
            assert false;

            return 0;
        }

        /** {@inheritDoc} */
        @Override public byte fieldsCount() {
            assert false;

            return 0;
        }

        /** {@inheritDoc} */
        @Override public void onAckReceived() {
            assert false;
        }
    }
}
