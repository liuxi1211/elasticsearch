/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.node;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.Constants;
import org.apache.lucene.util.SetOnce;
import org.elasticsearch.Assertions;
import org.elasticsearch.Build;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchTimeoutException;
import org.elasticsearch.Version;
import org.elasticsearch.action.ActionModule;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.admin.cluster.snapshots.status.TransportNodesSnapshotsStatus;
import org.elasticsearch.index.IndexingPressure;
import org.elasticsearch.action.search.SearchExecutionStatsCollector;
import org.elasticsearch.action.search.SearchPhaseController;
import org.elasticsearch.action.search.SearchTransportService;
import org.elasticsearch.action.support.TransportAction;
import org.elasticsearch.action.update.UpdateHelper;
import org.elasticsearch.bootstrap.BootstrapCheck;
import org.elasticsearch.bootstrap.BootstrapContext;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.cluster.ClusterInfoService;
import org.elasticsearch.cluster.ClusterModule;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateObserver;
import org.elasticsearch.cluster.InternalClusterInfoService;
import org.elasticsearch.cluster.NodeConnectionsService;
import org.elasticsearch.cluster.action.index.MappingUpdatedAction;
import org.elasticsearch.cluster.metadata.AliasValidator;
import org.elasticsearch.cluster.metadata.IndexTemplateMetadata;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.metadata.MetadataCreateDataStreamService;
import org.elasticsearch.cluster.metadata.MetadataCreateIndexService;
import org.elasticsearch.cluster.metadata.MetadataIndexUpgradeService;
import org.elasticsearch.cluster.metadata.SystemIndexMetadataUpgradeService;
import org.elasticsearch.cluster.metadata.TemplateUpgradeService;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodeRole;
import org.elasticsearch.cluster.routing.BatchedRerouteService;
import org.elasticsearch.cluster.routing.RerouteService;
import org.elasticsearch.cluster.routing.allocation.DiskThresholdMonitor;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.StopWatch;
import org.elasticsearch.common.breaker.CircuitBreaker;
import org.elasticsearch.common.component.Lifecycle;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.Key;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.lease.Releasables;
import org.elasticsearch.common.logging.DeprecationLogger;
import org.elasticsearch.common.logging.HeaderWarning;
import org.elasticsearch.common.logging.NodeAndClusterIdStateListener;
import org.elasticsearch.common.network.NetworkAddress;
import org.elasticsearch.common.network.NetworkModule;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.ConsistentSettingsService;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.settings.SettingUpgrader;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.common.transport.BoundTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.common.util.PageCacheRecycler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.core.internal.io.IOUtils;
import org.elasticsearch.discovery.Discovery;
import org.elasticsearch.discovery.DiscoveryModule;
import org.elasticsearch.discovery.DiscoverySettings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.env.NodeMetadata;
import org.elasticsearch.gateway.GatewayAllocator;
import org.elasticsearch.gateway.GatewayMetaState;
import org.elasticsearch.gateway.GatewayModule;
import org.elasticsearch.gateway.GatewayService;
import org.elasticsearch.gateway.MetaStateService;
import org.elasticsearch.gateway.PersistedClusterStateService;
import org.elasticsearch.http.HttpServerTransport;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.IndexingPressure;
import org.elasticsearch.index.analysis.AnalysisRegistry;
import org.elasticsearch.index.engine.EngineFactory;
import org.elasticsearch.indices.IndicesModule;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.indices.ShardLimitValidator;
import org.elasticsearch.indices.SystemIndexDescriptor;
import org.elasticsearch.indices.SystemIndices;
import org.elasticsearch.indices.analysis.AnalysisModule;
import org.elasticsearch.indices.breaker.BreakerSettings;
import org.elasticsearch.indices.breaker.CircuitBreakerService;
import org.elasticsearch.indices.breaker.HierarchyCircuitBreakerService;
import org.elasticsearch.indices.breaker.NoneCircuitBreakerService;
import org.elasticsearch.indices.cluster.IndicesClusterStateService;
import org.elasticsearch.indices.recovery.PeerRecoverySourceService;
import org.elasticsearch.indices.recovery.PeerRecoveryTargetService;
import org.elasticsearch.indices.recovery.RecoverySettings;
import org.elasticsearch.indices.store.IndicesStore;
import org.elasticsearch.ingest.IngestService;
import org.elasticsearch.monitor.MonitorService;
import org.elasticsearch.monitor.fs.FsHealthService;
import org.elasticsearch.monitor.jvm.JvmInfo;
import org.elasticsearch.persistent.PersistentTasksClusterService;
import org.elasticsearch.persistent.PersistentTasksExecutor;
import org.elasticsearch.persistent.PersistentTasksExecutorRegistry;
import org.elasticsearch.persistent.PersistentTasksService;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.CircuitBreakerPlugin;
import org.elasticsearch.plugins.ClusterPlugin;
import org.elasticsearch.plugins.DiscoveryPlugin;
import org.elasticsearch.plugins.EnginePlugin;
import org.elasticsearch.plugins.IndexStorePlugin;
import org.elasticsearch.plugins.IngestPlugin;
import org.elasticsearch.plugins.MapperPlugin;
import org.elasticsearch.plugins.MetadataUpgrader;
import org.elasticsearch.plugins.NetworkPlugin;
import org.elasticsearch.plugins.PersistentTaskPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.PluginsService;
import org.elasticsearch.plugins.RepositoryPlugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.plugins.SearchPlugin;
import org.elasticsearch.plugins.SystemIndexPlugin;
import org.elasticsearch.repositories.RepositoriesModule;
import org.elasticsearch.repositories.RepositoriesService;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.ScriptModule;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.search.SearchModule;
import org.elasticsearch.search.SearchService;
import org.elasticsearch.search.aggregations.support.AggregationUsageService;
import org.elasticsearch.search.fetch.FetchPhase;
import org.elasticsearch.snapshots.InternalSnapshotsInfoService;
import org.elasticsearch.snapshots.RestoreService;
import org.elasticsearch.snapshots.SnapshotShardsService;
import org.elasticsearch.snapshots.SnapshotsInfoService;
import org.elasticsearch.snapshots.SnapshotsService;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.tasks.TaskCancellationService;
import org.elasticsearch.tasks.TaskResultsService;
import org.elasticsearch.threadpool.ExecutorBuilder;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.RemoteClusterService;
import org.elasticsearch.transport.Transport;
import org.elasticsearch.transport.TransportInterceptor;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.usage.UsageService;
import org.elasticsearch.watcher.ResourceWatcherService;

import javax.net.ssl.SNIHostName;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * A node represent a node within a cluster ({@code cluster.name}). The {@link #client()} can be used
 * in order to use a {@link Client} to perform actions/operations against the cluster.
 */
public class Node implements Closeable {
    public static final Setting<Boolean> WRITE_PORTS_FILE_SETTING =
        Setting.boolSetting("node.portsfile", false, Property.NodeScope);
    private static final Setting<Boolean> NODE_DATA_SETTING =
        Setting.boolSetting("node.data", true, Property.Deprecated, Property.NodeScope);
    private static final Setting<Boolean> NODE_MASTER_SETTING =
        Setting.boolSetting("node.master", true, Property.Deprecated, Property.NodeScope);
    private static final Setting<Boolean> NODE_INGEST_SETTING =
        Setting.boolSetting("node.ingest", true, Property.Deprecated, Property.NodeScope);
    private static final Setting<Boolean> NODE_REMOTE_CLUSTER_CLIENT = Setting.boolSetting(
        "node.remote_cluster_client",
        RemoteClusterService.ENABLE_REMOTE_CLUSTERS,
        Property.Deprecated,
        Property.NodeScope);

    /**
    * controls whether the node is allowed to persist things like metadata to disk
    * Note that this does not control whether the node stores actual indices (see
    * {@link #NODE_DATA_SETTING}). However, if this is false, {@link #NODE_DATA_SETTING}
    * and {@link #NODE_MASTER_SETTING} must also be false.
    *
    */
    public static final Setting<Boolean> NODE_LOCAL_STORAGE_SETTING =
        Setting.boolSetting("node.local_storage", true, Property.Deprecated, Property.NodeScope);
    public static final Setting<String> NODE_NAME_SETTING = Setting.simpleString("node.name", Property.NodeScope);
    public static final Setting.AffixSetting<String> NODE_ATTRIBUTES = Setting.prefixKeySetting("node.attr.", (key) ->
        new Setting<>(key, "", (value) -> {
            if (value.length() > 0
                && (Character.isWhitespace(value.charAt(0)) || Character.isWhitespace(value.charAt(value.length() - 1)))) {
                throw new IllegalArgumentException(key + " cannot have leading or trailing whitespace " +
                    "[" + value + "]");
            }
            if (value.length() > 0 && "node.attr.server_name".equals(key)) {
                try {
                    new SNIHostName(value);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("invalid node.attr.server_name [" + value + "]", e );
                }
            }
            return value;
        }, Property.NodeScope));
    public static final Setting<String> BREAKER_TYPE_KEY = new Setting<>("indices.breaker.type", "hierarchy", (s) -> {
        switch (s) {
            case "hierarchy":
            case "none":
                return s;
            default:
                throw new IllegalArgumentException("indices.breaker.type must be one of [hierarchy, none] but was: " + s);
        }
    }, Setting.Property.NodeScope);

    private static final String CLIENT_TYPE = "node";

    private final Lifecycle lifecycle = new Lifecycle();

    /**
     * This logger instance is an instance field as opposed to a static field. This ensures that the field is not
     * initialized until an instance of Node is constructed, which is sure to happen after the logging infrastructure
     * has been initialized to include the hostname. If this field were static, then it would be initialized when the
     * class initializer runs. Alas, this happens too early, before logging is initialized as this class is referred to
     * in InternalSettingsPreparer#finalizeSettings, which runs when creating the Environment, before logging is
     * initialized.
     */
    private final Logger logger = LogManager.getLogger(Node.class);
    private final DeprecationLogger deprecationLogger = DeprecationLogger.getLogger(Node.class);
    private final Injector injector;
    private final Environment environment;
    private final NodeEnvironment nodeEnvironment;
    private final PluginsService pluginsService;
    private final NodeClient client;
    private final Collection<LifecycleComponent> pluginLifecycleComponents;
    private final LocalNodeFactory localNodeFactory;
    private final NodeService nodeService;

    public Node(Environment environment) {
        this(environment, Collections.emptyList(), true);
    }

    /**
     * 构造一个节点实例
     *
     * @param initialEnvironment         节点的初始环境配置，插件可能会对其进行扩展
     * @param classpathPlugins           从classpath加载的插件列表
     * @param forbidPrivateIndexSettings 是否在创建索引时禁止使用私有索引设置；在测试框架中用于允许设置私有设置的测试
     */
    protected Node(final Environment initialEnvironment,
                   Collection<Class<? extends Plugin>> classpathPlugins, boolean forbidPrivateIndexSettings) {
        // 注册所有需要释放的资源，以便在发生错误时能够正确清理
        final List<Closeable> resourcesToClose = new ArrayList<>();
        boolean success = false;
        try {
            // ========================================
            // 阶段一：基础环境初始化与日志记录
            // ========================================
            
            // 构建临时设置，添加客户端类型标识
            Settings tmpSettings = Settings.builder().put(initialEnvironment.settings())
                .put(Client.CLIENT_TYPE_SETTING_S.getKey(), CLIENT_TYPE).build();

            // 获取JVM信息并记录版本、构建、操作系统等详细信息
            final JvmInfo jvmInfo = JvmInfo.jvmInfo();
            logger.info(
                "version[{}], pid[{}], build[{}/{}/{}/{}], OS[{}/{}/{}], JVM[{}/{}/{}/{}]",
                Build.CURRENT.getQualifiedVersion(),
                jvmInfo.pid(),
                Build.CURRENT.flavor().displayName(),
                Build.CURRENT.type().displayName(),
                Build.CURRENT.hash(),
                Build.CURRENT.date(),
                Constants.OS_NAME,
                Constants.OS_VERSION,
                Constants.OS_ARCH,
                Constants.JVM_VENDOR,
                Constants.JVM_NAME,
                Constants.JAVA_VERSION,
                Constants.JVM_VERSION);
            
            // 记录JVM home路径和是否使用bundled JDK
            if (jvmInfo.getBundledJdk()) {
                logger.info("JVM home [{}], using bundled JDK [{}]", System.getProperty("java.home"), jvmInfo.getUsingBundledJdk());
            } else {
                logger.info("JVM home [{}]", System.getProperty("java.home"));
                deprecationLogger.deprecate(
                    "no-jdk",
                    "no-jdk distributions that do not bundle a JDK are deprecated and will be removed in a future release");
            }
            
            // 记录JVM参数和非生产版本的警告
            logger.info("JVM arguments {}", Arrays.toString(jvmInfo.getInputArguments()));
            if (Build.CURRENT.isProductionRelease() == false) {
                logger.warn(
                    "version [{}] is a pre-release version of Elasticsearch and is not suitable for production",
                    Build.CURRENT.getQualifiedVersion());
            }

            // 调试模式下记录配置文件、数据目录、日志目录和插件目录
            if (logger.isDebugEnabled()) {
                logger.debug("using config [{}], data [{}], logs [{}], plugins [{}]",
                    initialEnvironment.configFile(), Arrays.toString(initialEnvironment.dataFiles()),
                    initialEnvironment.logsFile(), initialEnvironment.pluginsFile());
            }

            // ========================================
            // 阶段二：插件服务初始化
            // ========================================
            
            // 创建插件服务，加载模块和插件
            this.pluginsService = new PluginsService(tmpSettings, initialEnvironment.configFile(), initialEnvironment.modulesFile(),
                initialEnvironment.pluginsFile(), classpathPlugins);
            // 获取插件更新后的设置（插件可能会修改配置）
            final Settings settings = pluginsService.updatedSettings();

            // 收集插件提供的额外节点角色
            final Set<DiscoveryNodeRole> additionalRoles = pluginsService.filterPlugins(Plugin.class)
                .stream()
                .map(Plugin::getRoles)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
            DiscoveryNode.setAdditionalRoles(additionalRoles);

            /*
             * 基于最终确定的设置创建环境，确保所有组件获取到相同的设置值
             */
            this.environment = new Environment(settings, initialEnvironment.configFile(), Node.NODE_LOCAL_STORAGE_SETTING.get(settings));
            Environment.assertEquivalent(initialEnvironment, this.environment);
            
            // 创建节点环境（管理数据目录等）
            nodeEnvironment = new NodeEnvironment(tmpSettings, environment);
            logger.info("node name [{}], node ID [{}], cluster name [{}], roles {}",
                NODE_NAME_SETTING.get(tmpSettings), nodeEnvironment.nodeId(), ClusterName.CLUSTER_NAME_SETTING.get(tmpSettings).value(),
                DiscoveryNode.getRolesFromSettings(settings).stream()
                    .map(DiscoveryNodeRole::roleName)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            resourcesToClose.add(nodeEnvironment);
            
            // 创建本地节点工厂（用于生成本地节点信息）
            localNodeFactory = new LocalNodeFactory(settings, nodeEnvironment.nodeId());

            // ========================================
            // 阶段三：线程池和资源监控服务
            // ========================================
            
            // 获取插件提供的执行器构建器
            final List<ExecutorBuilder<?>> executorBuilders = pluginsService.getExecutorBuilders(settings);

            // 创建线程池（管理所有线程执行器）
            final ThreadPool threadPool = new ThreadPool(settings, executorBuilders.toArray(new ExecutorBuilder[0]));
            resourcesToClose.add(() -> ThreadPool.terminate(threadPool, 10, TimeUnit.SECONDS));
            
            // 创建资源监控服务（监控配置文件变化等）
            final ResourceWatcherService resourceWatcherService = new ResourceWatcherService(settings, threadPool);
            resourcesToClose.add(resourceWatcherService);
            
            // 将线程上下文添加到DeprecationLogger，避免在每个地方都注入
            HeaderWarning.setThreadContext(threadPool.getThreadContext());
            resourcesToClose.add(() -> HeaderWarning.removeThreadContext(threadPool.getThreadContext()));

            // ========================================
            // 阶段四：设置模块和脚本服务
            // ========================================
            
            // 收集额外的设置项
            final List<Setting<?>> additionalSettings = new ArrayList<>();
            // 注册node.data、node.ingest、node.master、node.remote_cluster_client设置为私有
            additionalSettings.add(NODE_DATA_SETTING);
            additionalSettings.add(NODE_INGEST_SETTING);
            additionalSettings.add(NODE_MASTER_SETTING);
            additionalSettings.add(NODE_REMOTE_CLUSTER_CLIENT);
            additionalSettings.addAll(pluginsService.getPluginSettings());
            
            // 收集设置的过滤器
            final List<String> additionalSettingsFilter = new ArrayList<>(pluginsService.getPluginSettingsFilter());
            for (final ExecutorBuilder<?> builder : threadPool.builders()) {
                additionalSettings.addAll(builder.getRegisteredSettings());
            }
            
            // 创建节点客户端（用于执行操作）
            client = new NodeClient(settings, threadPool);

            // 创建脚本模块和脚本服务
            final ScriptModule scriptModule = new ScriptModule(settings, pluginsService.filterPlugins(ScriptPlugin.class));
            final ScriptService scriptService = newScriptService(settings, scriptModule.engines, scriptModule.contexts);
            
            // 创建分析模块（分词器等）
            AnalysisModule analysisModule = new AnalysisModule(this.environment, pluginsService.filterPlugins(AnalysisPlugin.class));
            
            // 在此处验证设置（已经传递给ScriptModule和ThreadPool，所以可能有点晚了）
            final Set<SettingUpgrader<?>> settingsUpgraders = pluginsService.filterPlugins(Plugin.class)
                    .stream()
                    .map(Plugin::getSettingUpgraders)
                    .flatMap(List::stream)
                    .collect(Collectors.toSet());

            // 创建设置模块
            final SettingsModule settingsModule =
                    new SettingsModule(settings, additionalSettings, additionalSettingsFilter, settingsUpgraders);
            scriptModule.registerClusterSettingsListeners(scriptService, settingsModule.getClusterSettings());
            
            // 创建网络服务
            final NetworkService networkService = new NetworkService(
                getCustomNameResolvers(pluginsService.filterPlugins(DiscoveryPlugin.class)));

            // ========================================
            // 阶段五：集群服务和Ingest服务
            // ========================================
            
            List<ClusterPlugin> clusterPlugins = pluginsService.filterPlugins(ClusterPlugin.class);
            // 创建集群服务（管理集群状态）
            final ClusterService clusterService = new ClusterService(settings, settingsModule.getClusterSettings(), threadPool);
            clusterService.addStateApplier(scriptService);
            resourcesToClose.add(clusterService);
            
            // 如果有一致性设置，添加哈希发布器
            final Set<Setting<?>> consistentSettings = settingsModule.getConsistentSettings();
            if (consistentSettings.isEmpty() == false) {
                clusterService.addLocalNodeMasterListener(
                        new ConsistentSettingsService(settings, clusterService, consistentSettings).newHashPublisher());
            }
            
            // 创建Ingest服务（数据处理管道）
            final IngestService ingestService = new IngestService(clusterService, threadPool, this.environment,
                scriptService, analysisModule.getAnalysisRegistry(),
                pluginsService.filterPlugins(IngestPlugin.class), client);
            
            // ========================================
            // 阶段六：Guice依赖注入模块配置
            // ========================================
            
            final SetOnce<RepositoriesService> repositoriesServiceReference = new SetOnce<>();
            // 创建集群信息服务（收集集群级别的信息，如磁盘使用情况）
            final ClusterInfoService clusterInfoService = newClusterInfoService(settings, clusterService, threadPool, client);
            final UsageService usageService = new UsageService();

            ModulesBuilder modules = new ModulesBuilder();
            // 插件模块必须在这里添加，否则可能出现依赖注入错误
            for (Module pluginModule : pluginsService.createGuiceModules()) {
                modules.add(pluginModule);
            }
            
            // 创建监控服务
            final MonitorService monitorService = new MonitorService(settings, nodeEnvironment, threadPool);
            // 创建文件系统健康服务
            final FsHealthService fsHealthService = new FsHealthService(settings, clusterService.getClusterSettings(), threadPool,
                nodeEnvironment);
            
            // 创建重路由服务引用（稍后设置）
            final SetOnce<RerouteService> rerouteServiceReference = new SetOnce<>();
            // 创建快照信息服务
            final InternalSnapshotsInfoService snapshotsInfoService = new InternalSnapshotsInfoService(settings, clusterService,
                repositoriesServiceReference::get, rerouteServiceReference::get);
            
            // 创建集群模块
            final ClusterModule clusterModule = new ClusterModule(settings, clusterService, clusterPlugins, clusterInfoService,
                snapshotsInfoService, threadPool.getThreadContext());
            modules.add(clusterModule);
            
            // 创建索引模块（处理映射器等）
            IndicesModule indicesModule = new IndicesModule(pluginsService.filterPlugins(MapperPlugin.class));
            modules.add(indicesModule);

            // 创建搜索模块
            SearchModule searchModule = new SearchModule(settings, false, pluginsService.filterPlugins(SearchPlugin.class));
            
            // ========================================
            // 阶段七：熔断器服务
            // ========================================
            
            // 收集插件提供的熔断器设置
            List<BreakerSettings> pluginCircuitBreakers = pluginsService.filterPlugins(CircuitBreakerPlugin.class)
                .stream()
                .map(plugin -> plugin.getCircuitBreaker(settings))
                .collect(Collectors.toList());
            
            // 创建熔断器服务（防止内存溢出）
            final CircuitBreakerService circuitBreakerService = createCircuitBreakerService(settingsModule.getSettings(),
                pluginCircuitBreakers,
                settingsModule.getClusterSettings());
            
            // 为每个插件设置熔断器
            pluginsService.filterPlugins(CircuitBreakerPlugin.class)
                .forEach(plugin -> {
                    CircuitBreaker breaker = circuitBreakerService.getBreaker(plugin.getCircuitBreaker(settings).getName());
                    plugin.setCircuitBreaker(breaker);
                });
            resourcesToClose.add(circuitBreakerService);
            
            // 添加网关模块
            modules.add(new GatewayModule());

            // ========================================
            // 阶段八：缓存和大数组
            // ========================================
            
            // 创建页面缓存回收器
            PageCacheRecycler pageCacheRecycler = createPageCacheRecycler(settings);
            // 创建大数组（用于聚合等操作）
            BigArrays bigArrays = createBigArrays(pageCacheRecycler, circuitBreakerService);
            modules.add(settingsModule);
            
            // ========================================
            // 阶段九：可写注册表和XContent注册表
            // ========================================
            
            // 收集所有可写条目（用于网络传输序列化）
            List<NamedWriteableRegistry.Entry> namedWriteables = Stream.of(
                NetworkModule.getNamedWriteables().stream(),
                indicesModule.getNamedWriteables().stream(),
                searchModule.getNamedWriteables().stream(),
                pluginsService.filterPlugins(Plugin.class).stream()
                    .flatMap(p -> p.getNamedWriteables().stream()),
                ClusterModule.getNamedWriteables().stream())
                .flatMap(Function.identity()).collect(Collectors.toList());
            final NamedWriteableRegistry namedWriteableRegistry = new NamedWriteableRegistry(namedWriteables);
            
            // 创建XContent注册表（用于JSON/XML等解析）
            NamedXContentRegistry xContentRegistry = new NamedXContentRegistry(Stream.of(
                NetworkModule.getNamedXContents().stream(),
                IndicesModule.getNamedXContents().stream(),
                searchModule.getNamedXContents().stream(),
                pluginsService.filterPlugins(Plugin.class).stream()
                    .flatMap(p -> p.getNamedXContent().stream()),
                ClusterModule.getNamedXWriteables().stream())
                .flatMap(Function.identity()).collect(toList()));
            
            // ========================================
            // 阶段十：元数据服务和持久化
            // ========================================
            
            // 创建元数据状态服务（管理集群元数据的持久化）
            final MetaStateService metaStateService = new MetaStateService(nodeEnvironment, xContentRegistry);
            // 创建持久化集群状态服务（使用Lucene存储集群状态）
            final PersistedClusterStateService lucenePersistedStateFactory
                = new PersistedClusterStateService(nodeEnvironment, xContentRegistry, bigArrays, clusterService.getClusterSettings(),
                threadPool::relativeTimeInMillis);

            // ========================================
            // 阶段十一：引擎工厂和索引存储
            // ========================================
            
            // 从插件收集引擎工厂提供者
            final Collection<EnginePlugin> enginePlugins = pluginsService.filterPlugins(EnginePlugin.class);
            final Collection<Function<IndexSettings, Optional<EngineFactory>>> engineFactoryProviders =
                    enginePlugins.stream().map(plugin -> (Function<IndexSettings, Optional<EngineFactory>>)plugin::getEngineFactory)
                            .collect(Collectors.toList());

            // 收集索引存储目录工厂
            final Map<String, IndexStorePlugin.DirectoryFactory> indexStoreFactories =
                    pluginsService.filterPlugins(IndexStorePlugin.class)
                            .stream()
                            .map(IndexStorePlugin::getDirectoryFactories)
                            .flatMap(m -> m.entrySet().stream())
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            // 收集恢复状态工厂
            final Map<String, IndexStorePlugin.RecoveryStateFactory> recoveryStateFactories =
                pluginsService.filterPlugins(IndexStorePlugin.class)
                    .stream()
                    .map(IndexStorePlugin::getRecoveryStateFactories)
                    .flatMap(m -> m.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            // ========================================
            // 阶段十二：系统索引
            // ========================================
            
            // 收集系统索引描述符
            final Map<String, Collection<SystemIndexDescriptor>> systemIndexDescriptorMap = Collections.unmodifiableMap(pluginsService
                .filterPlugins(SystemIndexPlugin.class)
                .stream()
                .collect(Collectors.toMap(
                    plugin -> plugin.getClass().getSimpleName(),
                    plugin -> plugin.getSystemIndexDescriptors(settings))));
            final SystemIndices systemIndices = new SystemIndices(systemIndexDescriptorMap);

            // ========================================
            // 阶段十三：重路由服务和索引服务
            // ========================================
            
            // 创建批量重路由服务
            final RerouteService rerouteService
                = new BatchedRerouteService(clusterService, clusterModule.getAllocationService()::reroute);
            rerouteServiceReference.set(rerouteService);
            clusterService.setRerouteService(rerouteService);

            // 创建索引服务（管理所有索引的分片）
            final IndicesService indicesService =
                new IndicesService(settings, pluginsService, nodeEnvironment, xContentRegistry, analysisModule.getAnalysisRegistry(),
                    clusterModule.getIndexNameExpressionResolver(), indicesModule.getMapperRegistry(), namedWriteableRegistry,
                    threadPool, settingsModule.getIndexScopedSettings(), circuitBreakerService, bigArrays, scriptService,
                    clusterService, client, metaStateService, engineFactoryProviders, indexStoreFactories,
                    searchModule.getValuesSourceRegistry(), recoveryStateFactories);

            // ========================================
            // 阶段十四：元数据创建服务
            // ========================================
            
            // 创建别名验证器
            final AliasValidator aliasValidator = new AliasValidator();

            // 创建分片限制验证器
            final ShardLimitValidator shardLimitValidator = new ShardLimitValidator(settings, clusterService);
            
            // 创建元数据创建索引服务
            final MetadataCreateIndexService metadataCreateIndexService = new MetadataCreateIndexService(
                    settings,
                    clusterService,
                    indicesService,
                    clusterModule.getAllocationService(),
                    aliasValidator,
                shardLimitValidator,
                    environment,
                    settingsModule.getIndexScopedSettings(),
                    threadPool,
                    xContentRegistry,
                    systemIndices,
                    forbidPrivateIndexSettings
            );
            
            // 添加插件提供的额外索引设置提供者
            pluginsService.filterPlugins(Plugin.class)
                .forEach(p -> p.getAdditionalIndexSettingProviders()
                    .forEach(metadataCreateIndexService::addAdditionalIndexSettingProvider));

            // 创建元数据创建数据流服务
            final MetadataCreateDataStreamService metadataCreateDataStreamService =
                new MetadataCreateDataStreamService(threadPool, clusterService, metadataCreateIndexService);

            // ========================================
            // 阶段十五：插件组件创建
            // ========================================
            
            // 调用插件创建组件
            Collection<Object> pluginComponents = pluginsService.filterPlugins(Plugin.class).stream()
                .flatMap(p -> p.createComponents(client, clusterService, threadPool, resourceWatcherService,
                                                 scriptService, xContentRegistry, environment, nodeEnvironment,
                                                 namedWriteableRegistry, clusterModule.getIndexNameExpressionResolver(),
                                                 repositoriesServiceReference::get).stream())
                .collect(Collectors.toList());

            // ========================================
            // 阶段十六：动作模块和网络模块
            // ========================================
            
            // 创建动作模块（处理各种API请求）
            ActionModule actionModule = new ActionModule(false, settings, clusterModule.getIndexNameExpressionResolver(),
                settingsModule.getIndexScopedSettings(), settingsModule.getClusterSettings(), settingsModule.getSettingsFilter(),
                threadPool, pluginsService.filterPlugins(ActionPlugin.class), client, circuitBreakerService, usageService, systemIndices);
            modules.add(actionModule);

            final RestController restController = actionModule.getRestController();
            
            // 创建网络模块（处理HTTP和Transport通信）
            final NetworkModule networkModule = new NetworkModule(settings, false, pluginsService.filterPlugins(NetworkPlugin.class),
                threadPool, bigArrays, pageCacheRecycler, circuitBreakerService, namedWriteableRegistry, xContentRegistry,
                networkService, restController, clusterService.getClusterSettings());
            
            // 收集索引模板元数据升级器
            Collection<UnaryOperator<Map<String, IndexTemplateMetadata>>> indexTemplateMetadataUpgraders =
                pluginsService.filterPlugins(Plugin.class).stream()
                    .map(Plugin::getIndexTemplateMetadataUpgrader)
                    .collect(Collectors.toList());
            final MetadataUpgrader metadataUpgrader = new MetadataUpgrader(indexTemplateMetadataUpgraders);
            
            // 创建元数据索引升级服务
            final MetadataIndexUpgradeService metadataIndexUpgradeService = new MetadataIndexUpgradeService(settings, xContentRegistry,
                indicesModule.getMapperRegistry(), settingsModule.getIndexScopedSettings(), systemIndices, scriptService);
            
            // 如果是主节点，添加系统索引元数据升级监听器
            if (DiscoveryNode.isMasterNode(settings)) {
                clusterService.addListener(new SystemIndexMetadataUpgradeService(systemIndices, clusterService));
            }
            new TemplateUpgradeService(client, clusterService, threadPool, indexTemplateMetadataUpgraders);
            
            // ========================================
            // 阶段十七：传输服务
            // ========================================
            
            // 获取传输层实现
            final Transport transport = networkModule.getTransportSupplier().get();
            
            // 收集任务头信息
            Set<String> taskHeaders = Stream.concat(
                pluginsService.filterPlugins(ActionPlugin.class).stream().flatMap(p -> p.getTaskHeaders().stream()),
                Stream.of(Task.X_OPAQUE_ID)
            ).collect(Collectors.toSet());
            
            // 创建传输服务
            final TransportService transportService = newTransportService(settings, transport, threadPool,
                networkModule.getTransportInterceptor(), localNodeFactory, settingsModule.getClusterSettings(), taskHeaders);
            
            // ========================================
            // 阶段十八：搜索和HTTP服务
            // ========================================
            
            final GatewayMetaState gatewayMetaState = new GatewayMetaState();
            final ResponseCollectorService responseCollectorService = new ResponseCollectorService(clusterService);
            final SearchTransportService searchTransportService =  new SearchTransportService(transportService,
                SearchExecutionStatsCollector.makeWrapper(responseCollectorService));
            
            // 创建HTTP服务器传输
            final HttpServerTransport httpServerTransport = newHttpTransport(networkModule);
            
            // 创建索引压力限制服务
            final IndexingPressure indexingLimits = new IndexingPressure(settings);

            // ========================================
            // 阶段十九：快照和恢复服务
            // ========================================
            
            final RecoverySettings recoverySettings = new RecoverySettings(settings, settingsModule.getClusterSettings());
            
            // 创建仓库模块（管理快照仓库）
            RepositoriesModule repositoriesModule = new RepositoriesModule(this.environment,
                pluginsService.filterPlugins(RepositoryPlugin.class), transportService, clusterService, threadPool, xContentRegistry,
                recoverySettings);
            RepositoriesService repositoryService = repositoriesModule.getRepositoryService();
            repositoriesServiceReference.set(repositoryService);
            
            // 创建快照服务
            SnapshotsService snapshotsService = new SnapshotsService(settings, clusterService,
                    clusterModule.getIndexNameExpressionResolver(), repositoryService, transportService, actionModule.getActionFilters());
            
            // 创建快照分片服务
            SnapshotShardsService snapshotShardsService = new SnapshotShardsService(settings, clusterService, repositoryService,
                    transportService, indicesService);
            
            // 创建快照状态传输节点
            TransportNodesSnapshotsStatus nodesSnapshotsStatus = new TransportNodesSnapshotsStatus(threadPool, clusterService,
                transportService, snapshotShardsService, actionModule.getActionFilters());
            
            // 创建恢复服务
            RestoreService restoreService = new RestoreService(clusterService, repositoryService, clusterModule.getAllocationService(),
                metadataCreateIndexService, metadataIndexUpgradeService, clusterService.getClusterSettings(), shardLimitValidator);

            // ========================================
            // 阶段二十：磁盘监控和发现模块
            // ========================================
            
            // 创建磁盘阈值监控器
            final DiskThresholdMonitor diskThresholdMonitor = new DiskThresholdMonitor(settings, clusterService::state,
                clusterService.getClusterSettings(), client, threadPool::relativeTimeInMillis, rerouteService);
            clusterInfoService.addListener(diskThresholdMonitor::onNewInfo);

            // 创建发现模块（节点发现和集群协调）
            final DiscoveryModule discoveryModule = new DiscoveryModule(settings, threadPool, transportService, namedWriteableRegistry,
                networkService, clusterService.getMasterService(), clusterService.getClusterApplierService(),
                clusterService.getClusterSettings(), pluginsService.filterPlugins(DiscoveryPlugin.class),
                clusterModule.getAllocationService(), environment.configFile(), gatewayMetaState, rerouteService,
                fsHealthService);
            
            // 创建节点服务（聚合各种监控信息）
            this.nodeService = new NodeService(settings, threadPool, monitorService, discoveryModule.getDiscovery(),
                transportService, indicesService, pluginsService, circuitBreakerService, scriptService,
                httpServerTransport, ingestService, clusterService, settingsModule.getSettingsFilter(), responseCollectorService,
                searchTransportService, indexingLimits, searchModule.getValuesSourceRegistry().getUsageService());

            // ========================================
            // 阶段二十一：搜索服务和持久任务
            // ========================================
            
            // 创建搜索服务
            final SearchService searchService = newSearchService(clusterService, indicesService,
                threadPool, scriptService, bigArrays, searchModule.getFetchPhase(),
                responseCollectorService, circuitBreakerService);

            // 收集持久任务执行器
            final List<PersistentTasksExecutor<?>> tasksExecutors = pluginsService
                .filterPlugins(PersistentTaskPlugin.class).stream()
                .map(p -> p.getPersistentTasksExecutor(clusterService, threadPool, client, settingsModule,
                    clusterModule.getIndexNameExpressionResolver()))
                .flatMap(List::stream)
                .collect(toList());

            // 创建持久任务执行器注册表
            final PersistentTasksExecutorRegistry registry = new PersistentTasksExecutorRegistry(tasksExecutors);
            final PersistentTasksClusterService persistentTasksClusterService =
                new PersistentTasksClusterService(settings, registry, clusterService, threadPool);
            resourcesToClose.add(persistentTasksClusterService);
            final PersistentTasksService persistentTasksService = new PersistentTasksService(clusterService, threadPool, client);

            // ========================================
            // 阶段二十二：Guice绑定配置
            // ========================================
            
            modules.add(b -> {
                    // 绑定核心服务实例
                    b.bind(Node.class).toInstance(this);
                    b.bind(NodeService.class).toInstance(nodeService);
                    b.bind(NamedXContentRegistry.class).toInstance(xContentRegistry);
                    b.bind(PluginsService.class).toInstance(pluginsService);
                    b.bind(Client.class).toInstance(client);
                    b.bind(NodeClient.class).toInstance(client);
                    b.bind(Environment.class).toInstance(this.environment);
                    b.bind(ThreadPool.class).toInstance(threadPool);
                    b.bind(NodeEnvironment.class).toInstance(nodeEnvironment);
                    b.bind(ResourceWatcherService.class).toInstance(resourceWatcherService);
                    b.bind(CircuitBreakerService.class).toInstance(circuitBreakerService);
                    b.bind(BigArrays.class).toInstance(bigArrays);
                    b.bind(PageCacheRecycler.class).toInstance(pageCacheRecycler);
                    b.bind(ScriptService.class).toInstance(scriptService);
                    b.bind(AnalysisRegistry.class).toInstance(analysisModule.getAnalysisRegistry());
                    b.bind(IngestService.class).toInstance(ingestService);
                    b.bind(IndexingPressure.class).toInstance(indexingLimits);
                    b.bind(UsageService.class).toInstance(usageService);
                    b.bind(AggregationUsageService.class).toInstance(searchModule.getValuesSourceRegistry().getUsageService());
                    b.bind(NamedWriteableRegistry.class).toInstance(namedWriteableRegistry);
                    b.bind(MetadataUpgrader.class).toInstance(metadataUpgrader);
                    b.bind(MetaStateService.class).toInstance(metaStateService);
                    b.bind(PersistedClusterStateService.class).toInstance(lucenePersistedStateFactory);
                    b.bind(IndicesService.class).toInstance(indicesService);
                    b.bind(AliasValidator.class).toInstance(aliasValidator);
                    b.bind(MetadataCreateIndexService.class).toInstance(metadataCreateIndexService);
                    b.bind(MetadataCreateDataStreamService.class).toInstance(metadataCreateDataStreamService);
                    b.bind(SearchService.class).toInstance(searchService);
                    b.bind(SearchTransportService.class).toInstance(searchTransportService);
                    b.bind(SearchPhaseController.class).toInstance(new SearchPhaseController(
                        namedWriteableRegistry, searchService::aggReduceContextBuilder));
                    b.bind(Transport.class).toInstance(transport);
                    b.bind(TransportService.class).toInstance(transportService);
                    b.bind(NetworkService.class).toInstance(networkService);
                    b.bind(UpdateHelper.class).toInstance(new UpdateHelper(scriptService));
                    b.bind(MetadataIndexUpgradeService.class).toInstance(metadataIndexUpgradeService);
                    b.bind(ClusterInfoService.class).toInstance(clusterInfoService);
                    b.bind(SnapshotsInfoService.class).toInstance(snapshotsInfoService);
                    b.bind(GatewayMetaState.class).toInstance(gatewayMetaState);
                    b.bind(Discovery.class).toInstance(discoveryModule.getDiscovery());
                    {
                        processRecoverySettings(settingsModule.getClusterSettings(), recoverySettings);
                        b.bind(PeerRecoverySourceService.class).toInstance(new PeerRecoverySourceService(transportService,
                                indicesService, recoverySettings));
                        b.bind(PeerRecoveryTargetService.class).toInstance(new PeerRecoveryTargetService(threadPool,
                                transportService, recoverySettings, clusterService));
                    }
                    b.bind(HttpServerTransport.class).toInstance(httpServerTransport);
                    pluginComponents.stream().forEach(p -> b.bind((Class) p.getClass()).toInstance(p));
                    b.bind(PersistentTasksService.class).toInstance(persistentTasksService);
                    b.bind(PersistentTasksClusterService.class).toInstance(persistentTasksClusterService);
                    b.bind(PersistentTasksExecutorRegistry.class).toInstance(registry);
                    b.bind(RepositoriesService.class).toInstance(repositoryService);
                    b.bind(SnapshotsService.class).toInstance(snapshotsService);
                    b.bind(SnapshotShardsService.class).toInstance(snapshotShardsService);
                    b.bind(TransportNodesSnapshotsStatus.class).toInstance(nodesSnapshotsStatus);
                    b.bind(RestoreService.class).toInstance(restoreService);
                    b.bind(RerouteService.class).toInstance(rerouteService);
                    b.bind(ShardLimitValidator.class).toInstance(shardLimitValidator);
                    b.bind(FsHealthService.class).toInstance(fsHealthService);
                    b.bind(SystemIndices.class).toInstance(systemIndices);
                }
            );
            
            // 创建Guice注入器
            injector = modules.createInjector();

            // 分配现有分片的副本：通过查找集群中可行的分片副本并分配分片到那里。
            // 搜索可行副本由分配尝试触发（即重路由），并异步执行。完成后会触发另一次重路由来再次尝试分配。
            // 这意味着存在循环依赖：分配服务需要访问现有分片分配器（如GatewayAllocator），
            // 而它们需要能够触发重路由，这需要调用分配服务。在这里关闭循环：
            clusterModule.setExistingShardsAllocators(injector.getInstance(GatewayAllocator.class));

            // ========================================
            // 阶段二十三：插件生命周期组件
            // ========================================
            
            List<LifecycleComponent> pluginLifecycleComponents = pluginComponents.stream()
                .filter(p -> p instanceof LifecycleComponent)
                .map(p -> (LifecycleComponent) p).collect(Collectors.toList());
            pluginLifecycleComponents.addAll(pluginsService.getGuiceServiceClasses().stream()
                .map(injector::getInstance).collect(Collectors.toList()));
            resourcesToClose.addAll(pluginLifecycleComponents);
            resourcesToClose.add(injector.getInstance(PeerRecoverySourceService.class));
            this.pluginLifecycleComponents = Collections.unmodifiableList(pluginLifecycleComponents);
            
            // ========================================
            // 阶段二十四：客户端初始化和REST处理器
            // ========================================
            
            // 初始化客户端
            client.initialize(injector.getInstance(new Key<Map<ActionType, TransportAction>>() {}),
                    () -> clusterService.localNode().getId(), transportService.getRemoteClusterService(),
                    namedWriteableRegistry);
            
            logger.debug("initializing HTTP handlers ...");
            // 初始化REST处理器
            actionModule.initRestHandlers(() -> clusterService.state().nodes());
            logger.info("initialized");

            success = true;
        } catch (IOException ex) {
            throw new ElasticsearchException("failed to bind service", ex);
        } finally {
            // 如果初始化失败，关闭所有已创建的资源
            if (!success) {
                IOUtils.closeWhileHandlingException(resourcesToClose);
            }
        }
    }

    protected TransportService newTransportService(Settings settings, Transport transport, ThreadPool threadPool,
                                                   TransportInterceptor interceptor,
                                                   Function<BoundTransportAddress, DiscoveryNode> localNodeFactory,
                                                   ClusterSettings clusterSettings, Set<String> taskHeaders) {
        return new TransportService(settings, transport, threadPool, interceptor, localNodeFactory, clusterSettings, taskHeaders);
    }

    protected void processRecoverySettings(ClusterSettings clusterSettings, RecoverySettings recoverySettings) {
        // Noop in production, overridden by tests
    }

    /**
     * The settings that are used by this node. Contains original settings as well as additional settings provided by plugins.
     */
    public Settings settings() {
        return this.environment.settings();
    }

    /**
     * A client that can be used to execute actions (operations) against the cluster.
     */
    public Client client() {
        return client;
    }

    /**
     * Returns the environment of the node
     */
    public Environment getEnvironment() {
        return environment;
    }

    /**
     * Returns the {@link NodeEnvironment} instance of this node
     */
    public NodeEnvironment getNodeEnvironment() {
        return nodeEnvironment;
    }


    /**
     * 启动节点。如果节点已经启动，这种方法就是无操作的。
     */
    public Node start() throws NodeValidationException {
        // ========================================
        // 阶段一：生命周期检查与插件启动
        // ========================================
        if (!lifecycle.moveToStarted()) {
            return this;
        }

        logger.info("starting ...");
        // 启动所有插件的生命周期组件
        pluginLifecycleComponents.forEach(LifecycleComponent::start);

        // ========================================
        // 阶段二：核心服务启动（索引、搜索、快照等）
        // ========================================
        // 设置 MappingUpdatedAction 的客户端
        injector.getInstance(MappingUpdatedAction.class).setClient(client);
        // 启动索引服务（负责管理索引生命周期）
        injector.getInstance(IndicesService.class).start();
        // 启动索引集群状态服务（负责将集群状态应用到索引）
        injector.getInstance(IndicesClusterStateService.class).start();
        // 启动快照服务（负责创建和管理快照）
        injector.getInstance(SnapshotsService.class).start();
        // 启动快照分片服务（负责快照的分片级操作）
        injector.getInstance(SnapshotShardsService.class).start();
        // 启动仓库服务（负责管理快照仓库）
        injector.getInstance(RepositoriesService.class).start();
        // 启动搜索服务（负责执行搜索请求）
        injector.getInstance(SearchService.class).start();
        // 启动文件系统健康服务（监控磁盘状态）
        injector.getInstance(FsHealthService.class).start();
        // 启动监控服务（监控节点状态）
        nodeService.getMonitorService().start();

        // ========================================
        // 阶段三：集群服务与网络连接
        // ========================================
        final ClusterService clusterService = injector.getInstance(ClusterService.class);

        // 启动节点连接服务（负责管理与其他节点的连接）
        final NodeConnectionsService nodeConnectionsService = injector.getInstance(NodeConnectionsService.class);
        nodeConnectionsService.start();
        clusterService.setNodeConnectionsService(nodeConnectionsService);

        // 启动网关服务（负责从磁盘恢复集群状态）
        injector.getInstance(GatewayService.class).start();
        // 获取发现服务（负责节点发现和集群协调）
        Discovery discovery = injector.getInstance(Discovery.class);
        // 设置集群状态发布者（使用发现服务发布集群状态）
        clusterService.getMasterService().setClusterStatePublisher(discovery::publish);

        // ========================================
        // 阶段四：传输服务与元数据恢复
        // ========================================
        // 启动传输服务，以便发布地址可以添加到 ClusterService 中的本地发现节点
        TransportService transportService = injector.getInstance(TransportService.class);
        // 设置任务管理器的任务结果服务
        transportService.getTaskManager().setTaskResultsService(injector.getInstance(TaskResultsService.class));
        // 设置任务管理器的任务取消服务
        transportService.getTaskManager().setTaskCancellationService(new TaskCancellationService(transportService));
        transportService.start();
        // 断言：本地节点工厂创建的节点不为空
        assert localNodeFactory.getNode() != null;
        // 断言：传输服务的本地节点与工厂提供的节点一致
        assert transportService.getLocalNode().equals(localNodeFactory.getNode())
            : "transportService has a different local node than the factory provided";
        // 启动对等恢复源服务（负责向其他节点提供分片恢复数据）
        injector.getInstance(PeerRecoverySourceService.class).start();

        // 加载（并可能升级）存储在磁盘上的元数据
        final GatewayMetaState gatewayMetaState = injector.getInstance(GatewayMetaState.class);
        gatewayMetaState.start(settings(), transportService, clusterService, injector.getInstance(MetaStateService.class),
            injector.getInstance(MetadataIndexUpgradeService.class), injector.getInstance(MetadataUpgrader.class),
            injector.getInstance(PersistedClusterStateService.class));
        // 断言检查（仅在启用断言时执行）
        if (Assertions.ENABLED) {
            try {
                // 如果不是 Zen 发现类型，断言 MetaStateService 加载的完整状态为空
                if (DiscoveryModule.DISCOVERY_TYPE_SETTING.get(environment.settings()).equals(
                    DiscoveryModule.ZEN_DISCOVERY_TYPE) == false) {
                    assert injector.getInstance(MetaStateService.class).loadFullState().v1().isEmpty();
                }
                // 从磁盘加载节点元数据并进行断言验证
                final NodeMetadata nodeMetadata = NodeMetadata.FORMAT.loadLatestState(logger, NamedXContentRegistry.EMPTY,
                    nodeEnvironment.nodeDataPaths());
                assert nodeMetadata != null;
                assert nodeMetadata.nodeVersion().equals(Version.CURRENT);
                assert nodeMetadata.nodeId().equals(localNodeFactory.getNode().getId());
            } catch (IOException e) {
                assert false : e;
            }
        }
        // 在这里加载全局状态（存储在磁盘上的集群状态的持久化部分），
        // 以便将其传递给引导检查，允许插件根据恢复的状态强制执行某些前置条件。
        final Metadata onDiskMetadata = gatewayMetaState.getPersistedState().getLastAcceptedState().metadata();
        assert onDiskMetadata != null : "metadata is null but shouldn't"; // 这永远不会为 null
        // 在接受请求之前验证节点（执行引导检查）
        validateNodeBeforeAcceptingRequests(new BootstrapContext(environment, onDiskMetadata), transportService.boundAddress(),
            pluginsService.filterPlugins(Plugin.class).stream()
                .flatMap(p -> p.getBootstrapChecks().stream()).collect(Collectors.toList()));

        // ========================================
        // 阶段五：集群服务启动与发现
        // ========================================
        // 将传输服务的任务管理器添加为集群状态应用器
        clusterService.addStateApplier(transportService.getTaskManager());
        // 在传输服务之后启动，以便本地发现已知
        discovery.start(); // 在集群服务之前启动，以便它可以在 ClusterApplierService 上设置初始状态
        clusterService.start();
        // 断言：集群服务的本地节点与工厂提供的节点一致
        assert clusterService.localNode().equals(localNodeFactory.getNode())
            : "clusterService has a different local node than the factory provided";
        // 开始接受传入请求
        transportService.acceptIncomingRequests();
        // 开始初始加入（发现集群中的其他节点）
        discovery.startInitialJoin();
        // 获取初始状态超时设置
        final TimeValue initialStateTimeout = DiscoverySettings.INITIAL_STATE_TIMEOUT_SETTING.get(settings());
        // 配置节点和集群 ID 状态监听器
        configureNodeAndClusterIdStateListener(clusterService);

        // ========================================
        // 阶段六：等待初始集群状态（可选）
        // ========================================
        if (initialStateTimeout.millis() > 0) {
            final ThreadPool thread = injector.getInstance(ThreadPool.class);
            ClusterState clusterState = clusterService.state();
            // 创建集群状态观察器，等待集群状态变化
            ClusterStateObserver observer =
                new ClusterStateObserver(clusterState, clusterService, null, logger, thread.getThreadContext());

            // 如果当前集群状态中没有主节点，等待加入集群
            if (clusterState.nodes().getMasterNodeId() == null) {
                logger.debug("waiting to join the cluster. timeout [{}]", initialStateTimeout);
                final CountDownLatch latch = new CountDownLatch(1);
                // 等待下一次状态变化，直到有主节点或超时
                observer.waitForNextChange(new ClusterStateObserver.Listener() {
                    @Override
                    public void onNewClusterState(ClusterState state) { latch.countDown(); }

                    @Override
                    public void onClusterServiceClose() {
                        latch.countDown();
                    }

                    @Override
                    public void onTimeout(TimeValue timeout) {
                        logger.warn("timed out while waiting for initial discovery state - timeout: {}",
                            initialStateTimeout);
                        latch.countDown();
                    }
                }, state -> state.nodes().getMasterNodeId() != null, initialStateTimeout);

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new ElasticsearchTimeoutException("Interrupted while waiting for initial discovery state");
                }
            }
        }

        // ========================================
        // 阶段七：HTTP服务启动与完成
        // ========================================
        // 启动 HTTP 服务器传输（接受 HTTP 请求）
        injector.getInstance(HttpServerTransport.class).start();

        // 如果配置了写入端口文件，记录 transport 和 http 的绑定地址
        if (WRITE_PORTS_FILE_SETTING.get(settings())) {
            TransportService transport = injector.getInstance(TransportService.class);
            writePortsFile("transport", transport.boundAddress());
            HttpServerTransport http = injector.getInstance(HttpServerTransport.class);
            writePortsFile("http", http.boundAddress());
        }

        logger.info("started");

        // 通知所有集群插件节点已启动
        pluginsService.filterPlugins(ClusterPlugin.class).forEach(ClusterPlugin::onNodeStarted);

        return this;
    }

    protected void configureNodeAndClusterIdStateListener(ClusterService clusterService) {
        NodeAndClusterIdStateListener.getAndSetNodeIdAndClusterId(clusterService,
            injector.getInstance(ThreadPool.class).getThreadContext());
    }

    private Node stop() {
        if (!lifecycle.moveToStopped()) {
            return this;
        }
        logger.info("stopping ...");

        injector.getInstance(ResourceWatcherService.class).close();
        injector.getInstance(HttpServerTransport.class).stop();

        injector.getInstance(SnapshotsService.class).stop();
        injector.getInstance(SnapshotShardsService.class).stop();
        injector.getInstance(RepositoriesService.class).stop();
        // stop any changes happening as a result of cluster state changes
        injector.getInstance(IndicesClusterStateService.class).stop();
        // close discovery early to not react to pings anymore.
        // This can confuse other nodes and delay things - mostly if we're the master and we're running tests.
        injector.getInstance(Discovery.class).stop();
        // we close indices first, so operations won't be allowed on it
        injector.getInstance(ClusterService.class).stop();
        injector.getInstance(NodeConnectionsService.class).stop();
        injector.getInstance(FsHealthService.class).stop();
        nodeService.getMonitorService().stop();
        injector.getInstance(GatewayService.class).stop();
        injector.getInstance(SearchService.class).stop();
        injector.getInstance(TransportService.class).stop();

        pluginLifecycleComponents.forEach(LifecycleComponent::stop);
        // we should stop this last since it waits for resources to get released
        // if we had scroll searchers etc or recovery going on we wait for to finish.
        injector.getInstance(IndicesService.class).stop();
        logger.info("stopped");

        return this;
    }

    // During concurrent close() calls we want to make sure that all of them return after the node has completed it's shutdown cycle.
    // If not, the hook that is added in Bootstrap#setup() will be useless:
    // close() might not be executed, in case another (for example api) call to close() has already set some lifecycles to stopped.
    // In this case the process will be terminated even if the first call to close() has not finished yet.
    @Override
    public synchronized void close() throws IOException {
        synchronized (lifecycle) {
            if (lifecycle.started()) {
                stop();
            }
            if (!lifecycle.moveToClosed()) {
                return;
            }
        }

        logger.info("closing ...");
        List<Closeable> toClose = new ArrayList<>();
        StopWatch stopWatch = new StopWatch("node_close");
        toClose.add(() -> stopWatch.start("node_service"));
        toClose.add(nodeService);
        toClose.add(() -> stopWatch.stop().start("http"));
        toClose.add(injector.getInstance(HttpServerTransport.class));
        toClose.add(() -> stopWatch.stop().start("snapshot_service"));
        toClose.add(injector.getInstance(SnapshotsService.class));
        toClose.add(injector.getInstance(SnapshotShardsService.class));
        toClose.add(injector.getInstance(RepositoriesService.class));
        toClose.add(() -> stopWatch.stop().start("client"));
        Releasables.close(injector.getInstance(Client.class));
        toClose.add(() -> stopWatch.stop().start("indices_cluster"));
        toClose.add(injector.getInstance(IndicesClusterStateService.class));
        toClose.add(() -> stopWatch.stop().start("indices"));
        toClose.add(injector.getInstance(IndicesService.class));
        // close filter/fielddata caches after indices
        toClose.add(injector.getInstance(IndicesStore.class));
        toClose.add(injector.getInstance(PeerRecoverySourceService.class));
        toClose.add(() -> stopWatch.stop().start("cluster"));
        toClose.add(injector.getInstance(ClusterService.class));
        toClose.add(() -> stopWatch.stop().start("node_connections_service"));
        toClose.add(injector.getInstance(NodeConnectionsService.class));
        toClose.add(() -> stopWatch.stop().start("discovery"));
        toClose.add(injector.getInstance(Discovery.class));
        toClose.add(() -> stopWatch.stop().start("monitor"));
        toClose.add(nodeService.getMonitorService());
        toClose.add(() -> stopWatch.stop().start("fsHealth"));
        toClose.add(injector.getInstance(FsHealthService.class));
        toClose.add(() -> stopWatch.stop().start("gateway"));
        toClose.add(injector.getInstance(GatewayService.class));
        toClose.add(() -> stopWatch.stop().start("search"));
        toClose.add(injector.getInstance(SearchService.class));
        toClose.add(() -> stopWatch.stop().start("transport"));
        toClose.add(injector.getInstance(TransportService.class));

        for (LifecycleComponent plugin : pluginLifecycleComponents) {
            toClose.add(() -> stopWatch.stop().start("plugin(" + plugin.getClass().getName() + ")"));
            toClose.add(plugin);
        }
        toClose.addAll(pluginsService.filterPlugins(Plugin.class));

        toClose.add(() -> stopWatch.stop().start("script"));
        toClose.add(injector.getInstance(ScriptService.class));

        toClose.add(() -> stopWatch.stop().start("thread_pool"));
        toClose.add(() -> injector.getInstance(ThreadPool.class).shutdown());
        // Don't call shutdownNow here, it might break ongoing operations on Lucene indices.
        // See https://issues.apache.org/jira/browse/LUCENE-7248. We call shutdownNow in
        // awaitClose if the node doesn't finish closing within the specified time.

        toClose.add(() -> stopWatch.stop().start("gateway_meta_state"));
        toClose.add(injector.getInstance(GatewayMetaState.class));

        toClose.add(() -> stopWatch.stop().start("node_environment"));
        toClose.add(injector.getInstance(NodeEnvironment.class));
        toClose.add(stopWatch::stop);

        if (logger.isTraceEnabled()) {
            toClose.add(() -> logger.trace("Close times for each service:\n{}", stopWatch.prettyPrint()));
        }
        IOUtils.close(toClose);
        logger.info("closed");
    }

    /**
     * Wait for this node to be effectively closed.
     */
    // synchronized to prevent running concurrently with close()
    public synchronized boolean awaitClose(long timeout, TimeUnit timeUnit) throws InterruptedException {
        if (lifecycle.closed() == false) {
            // We don't want to shutdown the threadpool or interrupt threads on a node that is not
            // closed yet.
            throw new IllegalStateException("Call close() first");
        }


        ThreadPool threadPool = injector.getInstance(ThreadPool.class);
        final boolean terminated = ThreadPool.terminate(threadPool, timeout, timeUnit);
        if (terminated) {
            // All threads terminated successfully. Because search, recovery and all other operations
            // that run on shards run in the threadpool, indices should be effectively closed by now.
            if (nodeService.awaitClose(0, TimeUnit.MILLISECONDS) == false) {
                throw new IllegalStateException("Some shards are still open after the threadpool terminated. " +
                        "Something is leaking index readers or store references.");
            }
        }
        return terminated;
    }

    /**
     * Returns {@code true} if the node is closed.
     */
    public boolean isClosed() {
        return lifecycle.closed();
    }

    public Injector injector() {
        return this.injector;
    }

    /**
     * Hook for validating the node after network
     * services are started but before the cluster service is started
     * and before the network service starts accepting incoming network
     * requests.
     *
     * @param context               the bootstrap context for this node
     * @param boundTransportAddress the network addresses the node is
     *                              bound and publishing to
     */
    @SuppressWarnings("unused")
    protected void validateNodeBeforeAcceptingRequests(
        final BootstrapContext context,
        final BoundTransportAddress boundTransportAddress, List<BootstrapCheck> bootstrapChecks) throws NodeValidationException {
    }

    /** Writes a file to the logs dir containing the ports for the given transport type */
    private void writePortsFile(String type, BoundTransportAddress boundAddress) {
        Path tmpPortsFile = environment.logsFile().resolve(type + ".ports.tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(tmpPortsFile, Charset.forName("UTF-8"))) {
            for (TransportAddress address : boundAddress.boundAddresses()) {
                InetAddress inetAddress = InetAddress.getByName(address.getAddress());
                writer.write(NetworkAddress.format(new InetSocketAddress(inetAddress, address.getPort())) + "\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write ports file", e);
        }
        Path portsFile = environment.logsFile().resolve(type + ".ports");
        try {
            Files.move(tmpPortsFile, portsFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to rename ports file", e);
        }
    }

    /**
     * The {@link PluginsService} used to build this node's components.
     */
    protected PluginsService getPluginsService() {
        return pluginsService;
    }

    /**
     * Creates a new {@link CircuitBreakerService} based on the settings provided.
     * @see #BREAKER_TYPE_KEY
     */
    public static CircuitBreakerService createCircuitBreakerService(Settings settings,
                                                                    List<BreakerSettings> breakerSettings,
                                                                    ClusterSettings clusterSettings) {
        String type = BREAKER_TYPE_KEY.get(settings);
        if (type.equals("hierarchy")) {
            return new HierarchyCircuitBreakerService(settings, breakerSettings, clusterSettings);
        } else if (type.equals("none")) {
            return new NoneCircuitBreakerService();
        } else {
            throw new IllegalArgumentException("Unknown circuit breaker type [" + type + "]");
        }
    }

    /**
     * Creates a new {@link BigArrays} instance used for this node.
     * This method can be overwritten by subclasses to change their {@link BigArrays} implementation for instance for testing
     */
    BigArrays createBigArrays(PageCacheRecycler pageCacheRecycler, CircuitBreakerService circuitBreakerService) {
        return new BigArrays(pageCacheRecycler, circuitBreakerService, CircuitBreaker.REQUEST);
    }

    /**
     * Creates a new {@link BigArrays} instance used for this node.
     * This method can be overwritten by subclasses to change their {@link BigArrays} implementation for instance for testing
     */
    PageCacheRecycler createPageCacheRecycler(Settings settings) {
        return new PageCacheRecycler(settings);
    }

    /**
     * Creates a new the SearchService. This method can be overwritten by tests to inject mock implementations.
     */
    protected SearchService newSearchService(ClusterService clusterService, IndicesService indicesService,
                                             ThreadPool threadPool, ScriptService scriptService, BigArrays bigArrays,
                                             FetchPhase fetchPhase, ResponseCollectorService responseCollectorService,
                                             CircuitBreakerService circuitBreakerService) {
        return new SearchService(clusterService, indicesService, threadPool,
            scriptService, bigArrays, fetchPhase, responseCollectorService, circuitBreakerService);
    }

    /**
     * Creates a new the ScriptService. This method can be overwritten by tests to inject mock implementations.
     */
    protected ScriptService newScriptService(Settings settings, Map<String, ScriptEngine> engines, Map<String, ScriptContext<?>> contexts) {
        return new ScriptService(settings, engines, contexts);
    }

    /**
     * Get Custom Name Resolvers list based on a Discovery Plugins list
     * @param discoveryPlugins Discovery plugins list
     */
    private List<NetworkService.CustomNameResolver> getCustomNameResolvers(List<DiscoveryPlugin> discoveryPlugins) {
        List<NetworkService.CustomNameResolver> customNameResolvers = new ArrayList<>();
        for (DiscoveryPlugin discoveryPlugin : discoveryPlugins) {
            NetworkService.CustomNameResolver customNameResolver = discoveryPlugin.getCustomNameResolver(settings());
            if (customNameResolver != null) {
                customNameResolvers.add(customNameResolver);
            }
        }
        return customNameResolvers;
    }

    /** Constructs a ClusterInfoService which may be mocked for tests. */
    protected ClusterInfoService newClusterInfoService(Settings settings, ClusterService clusterService,
                                                       ThreadPool threadPool, NodeClient client) {
        final InternalClusterInfoService service = new InternalClusterInfoService(settings, clusterService, threadPool, client);
        if (DiscoveryNode.isMasterNode(settings)) {
            // listen for state changes (this node starts/stops being the elected master, or new nodes are added)
            clusterService.addListener(service);
        }
        return service;
    }

    /** Constructs a {@link org.elasticsearch.http.HttpServerTransport} which may be mocked for tests. */
    protected HttpServerTransport newHttpTransport(NetworkModule networkModule) {
        return networkModule.getHttpServerTransportSupplier().get();
    }

    private static class LocalNodeFactory implements Function<BoundTransportAddress, DiscoveryNode> {
        private final SetOnce<DiscoveryNode> localNode = new SetOnce<>();
        private final String persistentNodeId;
        private final Settings settings;

        private LocalNodeFactory(Settings settings, String persistentNodeId) {
            this.persistentNodeId = persistentNodeId;
            this.settings = settings;
        }

        @Override
        public DiscoveryNode apply(BoundTransportAddress boundTransportAddress) {
            localNode.set(DiscoveryNode.createLocal(settings, boundTransportAddress.publishAddress(), persistentNodeId));
            return localNode.get();
        }

        DiscoveryNode getNode() {
            assert localNode.get() != null;
            return localNode.get();
        }
    }
}
