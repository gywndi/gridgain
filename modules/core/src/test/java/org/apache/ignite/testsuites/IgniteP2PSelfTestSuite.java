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

package org.apache.ignite.testsuites;

import org.apache.ignite.internal.managers.deployment.GridDeploymentMessageCountSelfTest;
import org.apache.ignite.internal.managers.deployment.GridDifferentLocalDeploymentSelfTest;
import org.apache.ignite.internal.managers.deployment.P2PCacheOperationIntoComputeTest;
import org.apache.ignite.internal.managers.deployment.P2PClassLoadingIssuesTest;
import org.apache.ignite.p2p.DeploymentClassLoaderCallableTest;
import org.apache.ignite.p2p.GridP2PClassLoadingSelfTest;
import org.apache.ignite.p2p.GridP2PComputeWithNestedEntryProcessorTest;
import org.apache.ignite.p2p.GridP2PContinuousDeploymentClientDisconnectTest;
import org.apache.ignite.p2p.GridP2PContinuousDeploymentSelfTest;
import org.apache.ignite.p2p.GridP2PCountTiesLoadClassDirectlyFromClassLoaderTest;
import org.apache.ignite.p2p.GridP2PDifferentClassLoaderSelfTest;
import org.apache.ignite.p2p.GridP2PDoubleDeploymentSelfTest;
import org.apache.ignite.p2p.GridP2PHotRedeploymentSelfTest;
import org.apache.ignite.p2p.GridP2PJobClassLoaderSelfTest;
import org.apache.ignite.p2p.GridP2PLocalDeploymentSelfTest;
import org.apache.ignite.p2p.GridP2PMissedResourceCacheSizeSelfTest;
import org.apache.ignite.p2p.GridP2PNodeLeftSelfTest;
import org.apache.ignite.p2p.GridP2PRecursionTaskSelfTest;
import org.apache.ignite.p2p.GridP2PRemoteClassLoadersSelfTest;
import org.apache.ignite.p2p.GridP2PSameClassLoaderSelfTest;
import org.apache.ignite.p2p.GridP2PScanQueryWithTransformerTest;
import org.apache.ignite.p2p.GridP2PTimeoutSelfTest;
import org.apache.ignite.p2p.GridP2PUndeploySelfTest;
import org.apache.ignite.p2p.P2PClassLoadingFailureHandlingTest;
import org.apache.ignite.p2p.P2PScanQueryUndeployTest;
import org.apache.ignite.p2p.P2PStreamingClassLoaderTest;
import org.apache.ignite.p2p.P2PUnsupportedClassVersionTest;
import org.apache.ignite.p2p.SharedDeploymentTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * P2P test suite.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    GridP2PDoubleDeploymentSelfTest.class,
    GridP2PHotRedeploymentSelfTest.class,
    GridP2PClassLoadingSelfTest.class,
    GridP2PUndeploySelfTest.class,
    GridP2PRemoteClassLoadersSelfTest.class,
    GridP2PNodeLeftSelfTest.class,
    GridP2PDifferentClassLoaderSelfTest.class,
    GridP2PSameClassLoaderSelfTest.class,
    GridP2PJobClassLoaderSelfTest.class,
    GridP2PRecursionTaskSelfTest.class,
    GridP2PLocalDeploymentSelfTest.class,
    //GridP2PTestTaskExecutionTest.class,
    GridP2PTimeoutSelfTest.class,
    GridP2PMissedResourceCacheSizeSelfTest.class,
    GridP2PContinuousDeploymentSelfTest.class,
    DeploymentClassLoaderCallableTest.class,
    P2PStreamingClassLoaderTest.class,
    SharedDeploymentTest.class,
    P2PScanQueryUndeployTest.class,
    GridDeploymentMessageCountSelfTest.class,
    GridP2PComputeWithNestedEntryProcessorTest.class,
    GridP2PCountTiesLoadClassDirectlyFromClassLoaderTest.class,
    GridP2PScanQueryWithTransformerTest.class,
    P2PCacheOperationIntoComputeTest.class,
    GridDifferentLocalDeploymentSelfTest.class,
    GridP2PContinuousDeploymentClientDisconnectTest.class,
    P2PUnsupportedClassVersionTest.class,
    P2PClassLoadingFailureHandlingTest.class,
    P2PClassLoadingIssuesTest.class
})
public class IgniteP2PSelfTestSuite {
}
