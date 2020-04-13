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
package io.seata.core.rpc.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.util.concurrent.EventExecutorGroup;
import io.seata.common.exception.FrameworkErrorCode;
import io.seata.common.exception.FrameworkException;
import io.seata.common.thread.NamedThreadFactory;
import io.seata.core.model.Resource;
import io.seata.core.model.ResourceManager;
import io.seata.core.protocol.AbstractMessage;
import io.seata.core.protocol.RegisterRMRequest;
import io.seata.core.protocol.RegisterRMResponse;
import io.seata.core.rpc.netty.NettyPoolKey.TransactionRole;
import io.seata.core.rpc.processor.RemotingProcessor;
import io.seata.core.rpc.processor.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static io.seata.common.Constants.DBKEYS_SPLIT_CHAR;

/**
 * The Rm netty client.
 *
 * @author slievrly
 * @author zhaojun
 * @author zhangchenghui.dev@gmail.com
 */
@Sharable
public final class RmNettyClient extends AbstractNettyRemotingClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RmNettyClient.class);
    private ResourceManager resourceManager;
    private static volatile RmNettyClient instance;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final long KEEP_ALIVE_TIME = Integer.MAX_VALUE;
    private static final int MAX_QUEUE_SIZE = 20000;
    private String applicationId;
    private String transactionServiceGroup;

    private Map<Integer/*MessageType*/, Pair<RemotingProcessor,
        Boolean/*Whether thread pool processing is required*/>> rmProcessorTable = null;

    private RmNettyClient(NettyClientConfig nettyClientConfig, EventExecutorGroup eventExecutorGroup,
                          ThreadPoolExecutor messageExecutor) {
        super(nettyClientConfig, eventExecutorGroup, messageExecutor, TransactionRole.RMROLE);
    }

    /**
     * Gets instance.
     *
     * @param applicationId           the application id
     * @param transactionServiceGroup the transaction service group
     * @return the instance
     */
    public static RmNettyClient getInstance(String applicationId, String transactionServiceGroup) {
        RmNettyClient rmNettyClient = getInstance();
        rmNettyClient.setApplicationId(applicationId);
        rmNettyClient.setTransactionServiceGroup(transactionServiceGroup);
        return rmNettyClient;
    }

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static RmNettyClient getInstance() {
        if (null == instance) {
            synchronized (RmNettyClient.class) {
                if (null == instance) {
                    NettyClientConfig nettyClientConfig = new NettyClientConfig();
                    final ThreadPoolExecutor messageExecutor = new ThreadPoolExecutor(
                        nettyClientConfig.getClientWorkerThreads(), nettyClientConfig.getClientWorkerThreads(),
                        KEEP_ALIVE_TIME, TimeUnit.SECONDS, new LinkedBlockingQueue<>(MAX_QUEUE_SIZE),
                        new NamedThreadFactory(nettyClientConfig.getRmDispatchThreadPrefix(),
                            nettyClientConfig.getClientWorkerThreads()), new ThreadPoolExecutor.CallerRunsPolicy());
                    instance = new RmNettyClient(nettyClientConfig, null, messageExecutor);
                }
            }
        }
        return instance;
    }

    /**
     * Sets application id.
     *
     * @param applicationId the application id
     */
    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    /**
     * Sets transaction service group.
     *
     * @param transactionServiceGroup the transaction service group
     */
    public void setTransactionServiceGroup(String transactionServiceGroup) {
        this.transactionServiceGroup = transactionServiceGroup;
    }

    /**
     * Sets resource manager.
     *
     * @param resourceManager the resource manager
     */
    public void setResourceManager(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public void setRmProcessor(Map<Integer, Pair<RemotingProcessor, Boolean>> processorMap) {
        this.rmProcessorTable = processorMap;
    }

    @Override
    public void init() {
        // registry processor
        if (rmProcessorTable != null) {
            for (Map.Entry<Integer, Pair<RemotingProcessor, Boolean>> entry : rmProcessorTable.entrySet()) {
                registerProcessor(entry.getKey(), entry.getValue().getObject1(), entry.getValue().getObject2() ? messageExecutor : null);
            }
        }
        if (initialized.compareAndSet(false, true)) {
            super.init();
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        initialized.getAndSet(false);
        instance = null;
    }

    @Override
    protected Function<String, NettyPoolKey> getPoolKeyFunction() {
        return (serverAddress) -> {
            String resourceIds = getMergedResourceKeys();
            if (null != resourceIds && LOGGER.isInfoEnabled()) {
                LOGGER.info("RM will register :{}", resourceIds);
            }
            RegisterRMRequest message = new RegisterRMRequest(applicationId, transactionServiceGroup);
            message.setResourceIds(resourceIds);
            return new NettyPoolKey(NettyPoolKey.TransactionRole.RMROLE, serverAddress, message);
        };
    }


    @Override
    protected String getTransactionServiceGroup() {
        return transactionServiceGroup;
    }

    @Override
    public void onRegisterMsgSuccess(String serverAddress, Channel channel, Object response,
                                     AbstractMessage requestMessage) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("register RM success. server version:{},channel:{}", ((RegisterRMResponse) response).getVersion(), channel);
        }
        getClientChannelManager().registerChannel(serverAddress, channel);
        String dbKey = getMergedResourceKeys();
        RegisterRMRequest message = (RegisterRMRequest) requestMessage;
        if (message.getResourceIds() != null) {
            if (!message.getResourceIds().equals(dbKey)) {
                sendRegisterMessage(serverAddress, channel, dbKey);
            }
        }

    }

    @Override
    public void onRegisterMsgFail(String serverAddress, Channel channel, Object response,
                                  AbstractMessage requestMessage) {

        if (response instanceof RegisterRMResponse && LOGGER.isInfoEnabled()) {
            LOGGER.info("register RM failed. server version:{}", ((RegisterRMResponse) response).getVersion());
        }
        throw new FrameworkException("register RM failed, channel:" + channel);
    }

    /**
     * Register new db key.
     *
     * @param resourceGroupId the resource group id
     * @param resourceId      the db key
     */
    public void registerResource(String resourceGroupId, String resourceId) {
        if (getClientChannelManager().getChannels().isEmpty()) {
            getClientChannelManager().reconnect(transactionServiceGroup);
            return;
        }
        synchronized (getClientChannelManager().getChannels()) {
            for (Map.Entry<String, Channel> entry : getClientChannelManager().getChannels().entrySet()) {
                String serverAddress = entry.getKey();
                Channel rmChannel = entry.getValue();
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("will register resourceId:{}", resourceId);
                }
                sendRegisterMessage(serverAddress, rmChannel, resourceId);
            }
        }
    }

    public void sendRegisterMessage(String serverAddress, Channel channel, String resourceId) {
        RegisterRMRequest message = new RegisterRMRequest(applicationId, transactionServiceGroup);
        message.setResourceIds(resourceId);
        try {
            super.sendAsyncRequest(channel, message);
        } catch (FrameworkException e) {
            if (e.getErrcode() == FrameworkErrorCode.ChannelIsNotWritable && serverAddress != null) {
                getClientChannelManager().releaseChannel(channel, serverAddress);
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("remove not writable channel:{}", channel);
                }
            } else {
                LOGGER.error("register resource failed, channel:{},resourceId:{}", channel, resourceId, e);
            }
        }
    }

    public String getMergedResourceKeys() {
        Map<String, Resource> managedResources = resourceManager.getManagedResources();
        Set<String> resourceIds = managedResources.keySet();
        if (!resourceIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String resourceId : resourceIds) {
                if (first) {
                    first = false;
                } else {
                    sb.append(DBKEYS_SPLIT_CHAR);
                }
                sb.append(resourceId);
            }
            return sb.toString();
        }
        return null;
    }

    @Override
    public void registerProcessor(int requestCode, RemotingProcessor processor, ExecutorService executor) {
        Pair<RemotingProcessor, ExecutorService> pair = new Pair<>(processor, executor);
        this.processorTable.put(requestCode, pair);
    }
}
