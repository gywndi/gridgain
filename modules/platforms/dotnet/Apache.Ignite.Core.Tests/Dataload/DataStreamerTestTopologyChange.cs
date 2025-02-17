﻿/*
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

namespace Apache.Ignite.Core.Tests.Dataload
{
    using System;
    using System.IO;
    using System.Threading;
    using NUnit.Framework;

    /// <summary>
    /// Streamer test with topology change.
    /// </summary>
    public class DataStreamerTestTopologyChange
    {
        /// <summary>
        /// Tests the streamer on a node without cache.
        /// </summary>
        [Test]
        public void TestNoCacheNode()
        {
            const string cacheName = "cache";

            var cacheNodeCfg = new IgniteConfiguration(TestUtils.GetTestConfiguration())
            {
                SpringConfigUrl = Path.Combine("Config", "cache-local-node.xml"),
                IgniteInstanceName = "cacheGrid"
            };

            using (var gridNoCache = Ignition.Start(TestUtils.GetTestConfiguration()))
            {
                Assert.Throws<ArgumentException>(() => gridNoCache.GetCache<int, int>(cacheName));

                var gridWithCache = Ignition.Start(cacheNodeCfg);

                var streamer = gridNoCache.GetDataStreamer<int, int>(cacheName);

                streamer.Add(1, 2);
                streamer.Flush();

                Ignition.Stop(gridWithCache.Name, true);

                Thread.Sleep(500);  // Wait for node to stop

                streamer.Add(2, 3);

                var ex = Assert.Throws<AggregateException>(() => streamer.Flush()).GetBaseException();

                Assert.IsNotNull(ex);

                Assert.AreEqual("Java exception occurred [class=org.apache.ignite.cache." +
                                "CacheServerNotFoundException, message=Failed to find server node for cache " +
                                "(all affinity nodes have left the grid or cache was stopped): cache]", ex.Message);
            }
        }
    }
}
