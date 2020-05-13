/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.seata.rm;

import io.seata.core.protocol.MessageType;
import io.seata.core.rpc.netty.RmRpcClient;
import io.seata.core.rpc.processor.RemotingProcessor;
import io.seata.core.rpc.processor.Pair;
import io.seata.core.rpc.processor.client.ClientHeartbeatProcessor;
import io.seata.core.rpc.processor.client.RmBranchCommitProcessor;
import io.seata.core.rpc.processor.client.RmBranchRollbackProcessor;
import io.seata.core.rpc.processor.client.RmUndoLogProcessor;
import io.seata.core.rpc.processor.client.MergeResultMessageProcessor;

import java.util.HashMap;
import java.util.Map;

/**
 * The Rm client Initiator.
 *
 * @author slievrly
 */
public class RMClient {

    /**
     * Init.
     *
     * @param applicationId           the application id
     * @param transactionServiceGroup the transaction service group
     */
    public static void init(String applicationId, String transactionServiceGroup) {
        RmRpcClient rmRpcClient = RmRpcClient.getInstance(applicationId, transactionServiceGroup);
        rmRpcClient.setResourceManager(DefaultResourceManager.get());
        rmRpcClient.setTransactionMessageHandler(DefaultRMHandler.get());
        rmRpcClient.init();
    }

}
