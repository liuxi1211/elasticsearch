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

package org.elasticsearch.threadpool;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.Version;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.SizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.util.concurrent.EsRejectedExecutionException;
import org.elasticsearch.common.util.concurrent.EsThreadPoolExecutor;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.util.concurrent.XRejectedExecutionHandler;
import org.elasticsearch.common.xcontent.ToXContentFragment;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.ReportingService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.Collections.unmodifiableMap;

/**
 * Elasticsearch 线程池管理类，是 ES 中处理并发任务的核心组件
 * 负责管理多种类型的线程池，支持任务调度、执行和监控
 */
public class ThreadPool implements ReportingService<ThreadPoolInfo>, Scheduler {

    private static final Logger logger = LogManager.getLogger(ThreadPool.class);

    /**
     * 线程池名称常量类，定义了 ES 中所有内置线程池的名称
     */
    public static class Names {
        public static final String SAME = "same";                    // 直接在当前线程执行
        public static final String GENERIC = "generic";              // 通用线程池，处理各种通用任务
        @Deprecated public static final String LISTENER = "listener";
        public static final String GET = "get";                      // GET 请求处理
        public static final String ANALYZE = "analyze";              // 分析任务
        public static final String WRITE = "write";                  // 写操作（索引、更新等）
        public static final String SEARCH = "search";                // 搜索操作
        public static final String SEARCH_THROTTLED = "search_throttled"; // 限流搜索
        public static final String MANAGEMENT = "management";        // 管理任务
        public static final String FLUSH = "flush";                  // 刷盘操作
        public static final String REFRESH = "refresh";              // 刷新操作
        public static final String WARMER = "warmer";                // 预热操作
        public static final String SNAPSHOT = "snapshot";            // 快照操作
        public static final String FORCE_MERGE = "force_merge";      // 强制合并
        public static final String FETCH_SHARD_STARTED = "fetch_shard_started";
        public static final String FETCH_SHARD_STORE = "fetch_shard_store";
        public static final String SYSTEM_READ = "system_read";      // 系统读操作
        public static final String SYSTEM_WRITE = "system_write";    // 系统写操作
    }

    /**
     * 线程池类型枚举
     */
    public enum ThreadPoolType {
        DIRECT("direct"),                    // 直接执行，不使用线程池
        FIXED("fixed"),                      // 固定大小线程池
        FIXED_AUTO_QUEUE_SIZE("fixed_auto_queue_size"), // 固定大小但队列大小自动调整
        SCALING("scaling");                  // 可伸缩线程池

        private final String type;

        public String getType() {
            return type;
        }

        ThreadPoolType(String type) {
            this.type = type;
        }

        private static final Map<String, ThreadPoolType> TYPE_MAP;

        static {
            Map<String, ThreadPoolType> typeMap = new HashMap<>();
            for (ThreadPoolType threadPoolType : ThreadPoolType.values()) {
                typeMap.put(threadPoolType.getType(), threadPoolType);
            }
            TYPE_MAP = Collections.unmodifiableMap(typeMap);
        }

        public static ThreadPoolType fromType(String type) {
            ThreadPoolType threadPoolType = TYPE_MAP.get(type);
            if (threadPoolType == null) {
                throw new IllegalArgumentException("no ThreadPoolType for " + type);
            }
            return threadPoolType;
        }
    }

    /**
     * 线程池名称到类型的映射表
     */
    public static final Map<String, ThreadPoolType> THREAD_POOL_TYPES;

    static {
        HashMap<String, ThreadPoolType> map = new HashMap<>();
        map.put(Names.SAME, ThreadPoolType.DIRECT);
        map.put(Names.GENERIC, ThreadPoolType.SCALING);
        map.put(Names.LISTENER, ThreadPoolType.FIXED);
        map.put(Names.GET, ThreadPoolType.FIXED);
        map.put(Names.ANALYZE, ThreadPoolType.FIXED);
        map.put(Names.WRITE, ThreadPoolType.FIXED);
        map.put(Names.SEARCH, ThreadPoolType.FIXED_AUTO_QUEUE_SIZE);
        map.put(Names.MANAGEMENT, ThreadPoolType.SCALING);
        map.put(Names.FLUSH, ThreadPoolType.SCALING);
        map.put(Names.REFRESH, ThreadPoolType.SCALING);
        map.put(Names.WARMER, ThreadPoolType.SCALING);
        map.put(Names.SNAPSHOT, ThreadPoolType.SCALING);
        map.put(Names.FORCE_MERGE, ThreadPoolType.FIXED);
        map.put(Names.FETCH_SHARD_STARTED, ThreadPoolType.SCALING);
        map.put(Names.FETCH_SHARD_STORE, ThreadPoolType.SCALING);
        map.put(Names.SEARCH_THROTTLED, ThreadPoolType.FIXED_AUTO_QUEUE_SIZE);
        map.put(Names.SYSTEM_READ, ThreadPoolType.FIXED);
        map.put(Names.SYSTEM_WRITE, ThreadPoolType.FIXED);
        THREAD_POOL_TYPES = Collections.unmodifiableMap(map);
    }

    private final Map<String, ExecutorHolder> executors;              // 线程池名称到执行器的映射

    private final ThreadPoolInfo threadPoolInfo;                     // 线程池信息

    private final CachedTimeThread cachedTimeThread;                 // 缓存时间的线程

    static final ExecutorService DIRECT_EXECUTOR = EsExecutors.newDirectExecutorService(); // 直接执行器

    private final ThreadContext threadContext;                       // 线程上下文

    private final Map<String, ExecutorBuilder> builders;             // 执行器构建器

    private final ScheduledThreadPoolExecutor scheduler;             // 调度线程池，用于定时任务

    public Collection<ExecutorBuilder> builders() {
        return Collections.unmodifiableCollection(builders.values());
    }

    public static Setting<TimeValue> ESTIMATED_TIME_INTERVAL_SETTING =
        Setting.timeSetting("thread_pool.estimated_time_interval",
            TimeValue.timeValueMillis(200), TimeValue.ZERO, Setting.Property.NodeScope);

    /**
     * 构造函数：初始化线程池
     * @param settings 配置信息
     * @param customBuilders 自定义执行器构建器
     */
    public ThreadPool(final Settings settings, final ExecutorBuilder<?>... customBuilders) {
        assert Node.NODE_NAME_SETTING.exists(settings);

        final Map<String, ExecutorBuilder> builders = new HashMap<>();
        final int allocatedProcessors = EsExecutors.allocatedProcessors(settings);  // 获取分配的处理器数量
        final int halfProcMaxAt5 = halfAllocatedProcessorsMaxFive(allocatedProcessors);
        final int halfProcMaxAt10 = halfAllocatedProcessorsMaxTen(allocatedProcessors);
        final int genericThreadPoolMax = boundedBy(4 * allocatedProcessors, 128, 512);
        
        // 初始化各种类型的线程池构建器
        builders.put(Names.GENERIC, new ScalingExecutorBuilder(Names.GENERIC, 4, genericThreadPoolMax, TimeValue.timeValueSeconds(30)));
        builders.put(Names.WRITE, new FixedExecutorBuilder(settings, Names.WRITE, allocatedProcessors, 10000));
        builders.put(Names.GET, new FixedExecutorBuilder(settings, Names.GET, allocatedProcessors, 1000));
        builders.put(Names.ANALYZE, new FixedExecutorBuilder(settings, Names.ANALYZE, 1, 16));
        builders.put(Names.SEARCH, new AutoQueueAdjustingExecutorBuilder(settings,
                        Names.SEARCH, searchThreadPoolSize(allocatedProcessors), 1000, 1000, 1000, 2000));
        builders.put(Names.SEARCH_THROTTLED, new AutoQueueAdjustingExecutorBuilder(settings,
            Names.SEARCH_THROTTLED, 1, 100, 100, 100, 200));
        builders.put(Names.MANAGEMENT, new ScalingExecutorBuilder(Names.MANAGEMENT, 1, 5, TimeValue.timeValueMinutes(5)));
        // listener 线程池没有队列，假设监听器应该很轻量
        builders.put(Names.LISTENER, new FixedExecutorBuilder(settings, Names.LISTENER, halfProcMaxAt10, -1, true));
        builders.put(Names.FLUSH, new ScalingExecutorBuilder(Names.FLUSH, 1, halfProcMaxAt5, TimeValue.timeValueMinutes(5)));
        builders.put(Names.REFRESH, new ScalingExecutorBuilder(Names.REFRESH, 1, halfProcMaxAt10, TimeValue.timeValueMinutes(5)));
        builders.put(Names.WARMER, new ScalingExecutorBuilder(Names.WARMER, 1, halfProcMaxAt5, TimeValue.timeValueMinutes(5)));
        builders.put(Names.SNAPSHOT, new ScalingExecutorBuilder(Names.SNAPSHOT, 1, halfProcMaxAt5, TimeValue.timeValueMinutes(5)));
        builders.put(Names.FETCH_SHARD_STARTED,
                new ScalingExecutorBuilder(Names.FETCH_SHARD_STARTED, 1, 2 * allocatedProcessors, TimeValue.timeValueMinutes(5)));
        builders.put(Names.FORCE_MERGE, new FixedExecutorBuilder(settings, Names.FORCE_MERGE, 1, -1));
        builders.put(Names.FETCH_SHARD_STORE,
                new ScalingExecutorBuilder(Names.FETCH_SHARD_STORE, 1, 2 * allocatedProcessors, TimeValue.timeValueMinutes(5)));
        builders.put(Names.SYSTEM_READ, new FixedExecutorBuilder(settings, Names.SYSTEM_READ, halfProcMaxAt5, 2000, false));
        builders.put(Names.SYSTEM_WRITE, new FixedExecutorBuilder(settings, Names.SYSTEM_WRITE, halfProcMaxAt5, 1000, false));

        // 处理自定义构建器
        for (final ExecutorBuilder<?> builder : customBuilders) {
            if (builders.containsKey(builder.name())) {
                throw new IllegalArgumentException("builder with name [" + builder.name() + "] already exists");
            }
            builders.put(builder.name(), builder);
        }
        this.builders = Collections.unmodifiableMap(builders);

        threadContext = new ThreadContext(settings);

        // 构建所有线程池
        final Map<String, ExecutorHolder> executors = new HashMap<>();
        for (final Map.Entry<String, ExecutorBuilder> entry : builders.entrySet()) {
            final ExecutorBuilder.ExecutorSettings executorSettings = entry.getValue().getSettings(settings);
            final ExecutorHolder executorHolder = entry.getValue().build(executorSettings, threadContext);
            if (executors.containsKey(executorHolder.info.getName())) {
                throw new IllegalStateException("duplicate executors with name [" + executorHolder.info.getName() + "] registered");
            }
            logger.debug("created thread pool: {}", entry.getValue().formatInfo(executorHolder.info));
            executors.put(entry.getKey(), executorHolder);
        }

        // 添加 SAME 线程池（直接执行）
        executors.put(Names.SAME, new ExecutorHolder(DIRECT_EXECUTOR, new Info(Names.SAME, ThreadPoolType.DIRECT)));
        this.executors = unmodifiableMap(executors);

        // 收集线程池信息
        final List<Info> infos =
                executors
                        .values()
                        .stream()
                        .filter(holder -> holder.info.getName().equals("same") == false)
                        .map(holder -> holder.info)
                        .collect(Collectors.toList());
        this.threadPoolInfo = new ThreadPoolInfo(infos);
        
        // 初始化调度器和缓存时间线程
        this.scheduler = Scheduler.initScheduler(settings);
        TimeValue estimatedTimeInterval = ESTIMATED_TIME_INTERVAL_SETTING.get(settings);
        this.cachedTimeThread = new CachedTimeThread(EsExecutors.threadName(settings, "[timer]"), estimatedTimeInterval.millis());
        this.cachedTimeThread.start();
    }

    /**
     * 返回相对时间（毫秒），用于计算时间差
     * 不要用于获取绝对时间戳
     */
    public long relativeTimeInMillis() {
        return TimeValue.nsecToMSec(relativeTimeInNanos());
    }

    /**
     * 返回相对时间（纳秒），用于计算时间差
     */
    public long relativeTimeInNanos() {
        return cachedTimeThread.relativeTimeInNanos();
    }

    /**
     * 返回绝对时间（从 Unix 纪元开始的毫秒数）
     * 用于日期时间格式化，不要用于计算时间差
     */
    public long absoluteTimeInMillis() {
        return cachedTimeThread.absoluteTimeInMillis();
    }

    @Override
    public ThreadPoolInfo info() {
        return threadPoolInfo;
    }

    /**
     * 获取指定名称的线程池信息
     */
    public Info info(String name) {
        ExecutorHolder holder = executors.get(name);
        if (holder == null) {
            return null;
        }
        return holder.info;
    }

    /**
     * 获取所有线程池的统计信息
     */
    public ThreadPoolStats stats() {
        List<ThreadPoolStats.Stats> stats = new ArrayList<>();
        for (ExecutorHolder holder : executors.values()) {
            final String name = holder.info.getName();
            // 忽略 "same" 线程池
            if ("same".equals(name)) {
                continue;
            }
            int threads = -1;
            int queue = -1;
            int active = -1;
            long rejected = -1;
            int largest = -1;
            long completed = -1;
            if (holder.executor() instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) holder.executor();
                threads = threadPoolExecutor.getPoolSize();           // 当前线程数
                queue = threadPoolExecutor.getQueue().size();         // 队列大小
                active = threadPoolExecutor.getActiveCount();         // 活跃线程数
                largest = threadPoolExecutor.getLargestPoolSize();    // 历史最大线程数
                completed = threadPoolExecutor.getCompletedTaskCount(); // 已完成任务数
                RejectedExecutionHandler rejectedExecutionHandler = threadPoolExecutor.getRejectedExecutionHandler();
                if (rejectedExecutionHandler instanceof XRejectedExecutionHandler) {
                    rejected = ((XRejectedExecutionHandler) rejectedExecutionHandler).rejected(); // 拒绝的任务数
                }
            }
            stats.add(new ThreadPoolStats.Stats(name, threads, queue, active, rejected, largest, completed));
        }
        return new ThreadPoolStats(stats);
    }

    /**
     * 获取通用线程池
     */
    public ExecutorService generic() {
        return executor(Names.GENERIC);
    }

    /**
     * 获取指定名称的线程池
     * @param name 线程池名称
     * @return 对应的执行器服务
     * @throws IllegalArgumentException 如果指定名称的线程池不存在
     */
    public ExecutorService executor(String name) {
        final ExecutorHolder holder = executors.get(name);
        if (holder == null) {
            throw new IllegalArgumentException("no executor service found for [" + name + "]");
        }
        return holder.executor();
    }

    /**
     * 调度一个延迟执行的一次性任务
     * @param command 要执行的任务
     * @param delay 延迟时间
     * @param executor 执行任务的线程池名称，SAME 表示在调度线程上执行
     * @return 可取消的调度任务
     */
    @Override
    public ScheduledCancellable schedule(Runnable command, TimeValue delay, String executor) {
        command = threadContext.preserveContext(command);  // 保留线程上下文
        if (!Names.SAME.equals(executor)) {
            command = new ThreadedRunnable(command, executor(executor));
        }
        return new ScheduledCancellableAdapter(scheduler.schedule(command, delay.millis(), TimeUnit.MILLISECONDS));
    }

    public void scheduleUnlessShuttingDown(TimeValue delay, String executor, Runnable command) {
        try {
            schedule(command, delay, executor);
        } catch (EsRejectedExecutionException e) {
            if (e.isExecutorShutdown()) {
                logger.debug(new ParameterizedMessage("could not schedule execution of [{}] after [{}] on [{}] as executor is shut down",
                    command, delay, executor), e);
            } else {
                throw e;
            }
        }
    }

    @Override
    public Cancellable scheduleWithFixedDelay(Runnable command, TimeValue interval, String executor) {
        return new ReschedulingRunnable(command, interval, executor, this,
                (e) -> {
                    if (logger.isDebugEnabled()) {
                        logger.debug(() -> new ParameterizedMessage("scheduled task [{}] was rejected on thread pool [{}]",
                                command, executor), e);
                    }
                },
                (e) -> logger.warn(() -> new ParameterizedMessage("failed to run scheduled task [{}] on thread pool [{}]",
                        command, executor), e));
    }

    protected final void stopCachedTimeThread() {
        cachedTimeThread.running = false;
        cachedTimeThread.interrupt();
    }

    /**
     * 优雅关闭线程池，允许已提交的任务继续执行
     */
    public void shutdown() {
        stopCachedTimeThread();
        scheduler.shutdown();
        for (ExecutorHolder executor : executors.values()) {
            if (executor.executor() instanceof ThreadPoolExecutor) {
                executor.executor().shutdown();
            }
        }
    }

    /**
     * 立即关闭线程池，尝试停止所有正在执行的任务
     */
    public void shutdownNow() {
        stopCachedTimeThread();
        scheduler.shutdownNow();
        for (ExecutorHolder executor : executors.values()) {
            if (executor.executor() instanceof ThreadPoolExecutor) {
                executor.executor().shutdownNow();
            }
        }
    }

    /**
     * 等待线程池终止
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 是否在超时前终止
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        boolean result = scheduler.awaitTermination(timeout, unit);
        for (ExecutorHolder executor : executors.values()) {
            if (executor.executor() instanceof ThreadPoolExecutor) {
                result &= executor.executor().awaitTermination(timeout, unit);
            }
        }
        cachedTimeThread.join(unit.toMillis(timeout));
        return result;
    }

    public ScheduledExecutorService scheduler() {
        return this.scheduler;
    }

    /**
     * Constrains a value between minimum and maximum values
     * (inclusive).
     *
     * @param value the value to constrain
     * @param min   the minimum acceptable value
     * @param max   the maximum acceptable value
     * @return min if value is less than min, max if value is greater
     * than value, otherwise value
     */
    static int boundedBy(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    static int halfAllocatedProcessorsMaxFive(final int allocatedProcessors) {
        return boundedBy((allocatedProcessors + 1) / 2, 1, 5);
    }

    static int halfAllocatedProcessorsMaxTen(final int allocatedProcessors) {
        return boundedBy((allocatedProcessors + 1) / 2, 1, 10);
    }

    static int twiceAllocatedProcessors(final int allocatedProcessors) {
        return boundedBy(2 * allocatedProcessors, 2, Integer.MAX_VALUE);
    }

    public static int searchThreadPoolSize(final int allocatedProcessors) {
        return ((allocatedProcessors * 3) / 2) + 1;
    }

    class LoggingRunnable implements Runnable {

        private final Runnable runnable;

        LoggingRunnable(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void run() {
            try {
                runnable.run();
            } catch (Exception e) {
                logger.warn(() -> new ParameterizedMessage("failed to run {}", runnable.toString()), e);
                throw e;
            }
        }

        @Override
        public int hashCode() {
            return runnable.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return runnable.equals(obj);
        }

        @Override
        public String toString() {
            return "[threaded] " + runnable.toString();
        }
    }

    class ThreadedRunnable implements Runnable {

        private final Runnable runnable;

        private final Executor executor;

        ThreadedRunnable(Runnable runnable, Executor executor) {
            this.runnable = runnable;
            this.executor = executor;
        }

        @Override
        public void run() {
            try {
                executor.execute(runnable);
            } catch (EsRejectedExecutionException e) {
                if (e.isExecutorShutdown()) {
                    logger.debug(new ParameterizedMessage("could not schedule execution of [{}] on [{}] as executor is shut down",
                        runnable, executor), e);
                } else {
                    throw e;
                }
            }
        }

        @Override
        public int hashCode() {
            return runnable.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return runnable.equals(obj);
        }

        @Override
        public String toString() {
            return "[threaded] " + runnable.toString();
        }
    }

    /**
     * 缓存时间的线程，定期更新相对时间和绝对时间
     * 避免频繁调用 System.currentTimeMillis() 和 System.nanoTime()
     */
    static class CachedTimeThread extends Thread {

        final long interval;              // 更新间隔
        volatile boolean running = true;  // 运行标志
        volatile long relativeNanos;      // 缓存的相对时间（纳秒）
        volatile long absoluteMillis;     // 缓存的绝对时间（毫秒）

        CachedTimeThread(String name, long interval) {
            super(name);
            this.interval = interval;
            this.relativeNanos = System.nanoTime();
            this.absoluteMillis = System.currentTimeMillis();
            setDaemon(true);  // 设置为守护线程
        }

        /**
         * 获取相对时间（纳秒）
         */
        long relativeTimeInNanos() {
            if (0 < interval) {
                return relativeNanos;
            }
            return System.nanoTime();
        }

        /**
         * 获取绝对时间（毫秒）
         */
        long absoluteTimeInMillis() {
            if (0 < interval) {
                return absoluteMillis;
            }
            return System.currentTimeMillis();
        }

        @Override
        public void run() {
            while (running && 0 < interval) {
                relativeNanos = System.nanoTime();
                absoluteMillis = System.currentTimeMillis();
                try {
                    Thread.sleep(interval);  // 等待指定间隔
                } catch (InterruptedException e) {
                    running = false;
                    return;
                }
            }
        }
    }

    /**
     * 执行器持有者，封装执行器和其信息
     */
    static class ExecutorHolder {
        private final ExecutorService executor;
        public final Info info;

        ExecutorHolder(ExecutorService executor, Info info) {
            assert executor instanceof EsThreadPoolExecutor || executor == DIRECT_EXECUTOR;
            this.executor = executor;
            this.info = info;
        }

        ExecutorService executor() {
            return executor;
        }
    }

    /**
     * 线程池信息类，包含线程池的配置信息
     */
    public static class Info implements Writeable, ToXContentFragment {

        private final String name;           // 线程池名称
        private final ThreadPoolType type;   // 线程池类型
        private final int min;               // 最小线程数
        private final int max;               // 最大线程数
        private final TimeValue keepAlive;   // 线程空闲存活时间
        private final SizeValue queueSize;   // 队列大小

        public Info(String name, ThreadPoolType type) {
            this(name, type, -1);
        }

        public Info(String name, ThreadPoolType type, int size) {
            this(name, type, size, size, null, null);
        }

        public Info(String name, ThreadPoolType type, int min, int max, @Nullable TimeValue keepAlive, @Nullable SizeValue queueSize) {
            this.name = name;
            this.type = type;
            this.min = min;
            this.max = max;
            this.keepAlive = keepAlive;
            this.queueSize = queueSize;
        }

        public Info(StreamInput in) throws IOException {
            name = in.readString();
            type = ThreadPoolType.fromType(in.readString());
            min = in.readInt();
            max = in.readInt();
            keepAlive = in.readOptionalTimeValue();
            queueSize = in.readOptionalWriteable(SizeValue::new);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(name);
            if (type == ThreadPoolType.FIXED_AUTO_QUEUE_SIZE &&
                    out.getVersion().before(Version.V_6_0_0_alpha1)) {
                // 5.x doesn't know about the "fixed_auto_queue_size" thread pool type, just write fixed.
                out.writeString(ThreadPoolType.FIXED.getType());
            } else {
                out.writeString(type.getType());
            }
            out.writeInt(min);
            out.writeInt(max);
            out.writeOptionalTimeValue(keepAlive);
            out.writeOptionalWriteable(queueSize);
        }

        public String getName() {
            return this.name;
        }

        public ThreadPoolType getThreadPoolType() {
            return this.type;
        }

        public int getMin() {
            return this.min;
        }

        public int getMax() {
            return this.max;
        }

        @Nullable
        public TimeValue getKeepAlive() {
            return this.keepAlive;
        }

        @Nullable
        public SizeValue getQueueSize() {
            return this.queueSize;
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject(name);
            builder.field("type", type.getType());

            if (type == ThreadPoolType.SCALING) {
                assert min != -1;
                builder.field("core", min);
                assert max != -1;
                builder.field("max", max);
            } else {
                assert max != -1;
                builder.field("size", max);
            }
            if (keepAlive != null) {
                builder.field("keep_alive", keepAlive.toString());
            }
            if (queueSize == null) {
                builder.field("queue_size", -1);
            } else {
                builder.field("queue_size", queueSize.singles());
            }
            builder.endObject();
            return builder;
        }

    }

    /**
     * Returns <code>true</code> if the given service was terminated successfully. If the termination timed out,
     * the service is <code>null</code> this method will return <code>false</code>.
     */
    public static boolean terminate(ExecutorService service, long timeout, TimeUnit timeUnit) {
        if (service != null) {
            service.shutdown();
            if (awaitTermination(service, timeout, timeUnit)) return true;
            service.shutdownNow();
            return awaitTermination(service, timeout, timeUnit);
        }
        return false;
    }

    private static boolean awaitTermination(
            final ExecutorService service,
            final long timeout,
            final TimeUnit timeUnit) {
        try {
            if (service.awaitTermination(timeout, timeUnit)) {
                return true;
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    /**
     * Returns <code>true</code> if the given pool was terminated successfully. If the termination timed out,
     * the service is <code>null</code> this method will return <code>false</code>.
     */
    public static boolean terminate(ThreadPool pool, long timeout, TimeUnit timeUnit) {
        if (pool != null) {
            // Leverage try-with-resources to close the threadpool
            pool.shutdown();
            if (awaitTermination(pool, timeout, timeUnit)) {
                return true;
            }
            // last resort
            pool.shutdownNow();
            return awaitTermination(pool, timeout, timeUnit);
        }
        return false;
    }

    private static boolean awaitTermination(
            final ThreadPool threadPool,
            final long timeout,
            final TimeUnit timeUnit) {
        try {
            if (threadPool.awaitTermination(timeout, timeUnit)) {
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    public ThreadContext getThreadContext() {
        return threadContext;
    }

    public static boolean assertNotScheduleThread(String reason) {
        assert Thread.currentThread().getName().contains("scheduler") == false :
            "Expected current thread [" + Thread.currentThread() + "] to not be the scheduler thread. Reason: [" + reason + "]";
        return true;
    }

    public static boolean assertCurrentMethodIsNotCalledRecursively() {
        final StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        assert stackTraceElements.length >= 3 : stackTraceElements.length;
        assert stackTraceElements[0].getMethodName().equals("getStackTrace") : stackTraceElements[0];
        assert stackTraceElements[1].getMethodName().equals("assertCurrentMethodIsNotCalledRecursively") : stackTraceElements[1];
        final StackTraceElement testingMethod = stackTraceElements[2];
        for (int i = 3; i < stackTraceElements.length; i++) {
            assert stackTraceElements[i].getClassName().equals(testingMethod.getClassName()) == false
                || stackTraceElements[i].getMethodName().equals(testingMethod.getMethodName()) == false :
                testingMethod.getClassName() + "#" + testingMethod.getMethodName() + " is called recursively";
        }
        return true;
    }
}
