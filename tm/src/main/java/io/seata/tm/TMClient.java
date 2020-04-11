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
package io.seata.tm;

import io.seata.core.protocol.MessageType;
import io.seata.core.rpc.netty.TmRpcClient;
import io.seata.core.rpc.netty.processor.NettyProcessor;
import io.seata.core.rpc.netty.processor.Pair;
import io.seata.core.rpc.netty.processor.client.ClientHeartbeatMessageProcessor;
import io.seata.core.rpc.netty.processor.client.ClientOnResponseMessageProcessor;

import java.util.HashMap;
import java.util.Map;

/**
 * The type Tm client.
 *
 * @author slievrly
 */
public class TMClient {

    /**
     * Init.
     *
     * @param applicationId           the application id
     * @param transactionServiceGroup the transaction service group
     */
    public static void init(String applicationId, String transactionServiceGroup) {
        TmRpcClient tmRpcClient = TmRpcClient.getInstance(applicationId, transactionServiceGroup);

        Map<Integer, Pair<NettyProcessor, Boolean>> processorMap = new HashMap<>();
        // handle TC response processor
        Pair<NettyProcessor, Boolean> onResponseMessageProcessor =
            new Pair<>(new ClientOnResponseMessageProcessor(tmRpcClient.getMergeMsgMap(), tmRpcClient.getFutures(), null), false);
        processorMap.put((int) MessageType.TYPE_SEATA_MERGE_RESULT, onResponseMessageProcessor);
        processorMap.put((int) MessageType.TYPE_GLOBAL_BEGIN_RESULT, onResponseMessageProcessor);
        processorMap.put((int) MessageType.TYPE_GLOBAL_COMMIT_RESULT, onResponseMessageProcessor);
        processorMap.put((int) MessageType.TYPE_GLOBAL_REPORT_RESULT, onResponseMessageProcessor);
        processorMap.put((int) MessageType.TYPE_GLOBAL_ROLLBACK_RESULT, onResponseMessageProcessor);
        processorMap.put((int) MessageType.TYPE_GLOBAL_STATUS_RESULT, onResponseMessageProcessor);
        processorMap.put((int) MessageType.TYPE_REG_CLT_RESULT, onResponseMessageProcessor);

        // handle heartbeat message processor
        Pair<NettyProcessor, Boolean> heartbeatMessageProcessor = new Pair<>(new ClientHeartbeatMessageProcessor(), false);
        processorMap.put((int) MessageType.TYPE_HEARTBEAT_MSG, heartbeatMessageProcessor);
        tmRpcClient.setTmProcessor(processorMap);

        tmRpcClient.init();
    }

}
