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

package org.elasticsearch.common.component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 生命周期组件的抽象基类，提供了生命周期管理的通用实现。
 * <p>
 * 该类实现了 {@link LifecycleComponent} 接口，封装了状态管理、监听器通知等通用逻辑，
 * 子类只需实现具体的启动、停止和关闭操作（{@code doStart()}, {@code doStop()}, {@code doClose()}）。
 * <p>
 * 核心特性：
 * <ul>
 *   <li>线程安全的状态转换：通过同步锁确保状态转换的原子性</li>
 *   <li>监听器机制：支持在生命周期关键节点注册回调</li>
 *   <li>模板方法模式：定义流程框架，具体实现由子类完成</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * public class MyComponent extends AbstractLifecycleComponent {
 *     @Override
 *     protected void doStart() {
 *         // 初始化资源、启动服务等
 *     }
 *
 *     @Override
 *     protected void doStop() {
 *         // 停止服务、释放部分资源
 *     }
 *
 *     @Override
 *     protected void doClose() throws IOException {
 *         // 彻底清理资源、关闭连接等
 *     }
 * }
 * }</pre>
 */
public abstract class AbstractLifecycleComponent implements LifecycleComponent {

    /**
     * 生命周期状态管理器，负责维护和转换组件的状态。
     * <p>
     * 支持的状态包括：INITIALIZED、STARTED、STOPPED、CLOSED
     */
    protected final Lifecycle lifecycle = new Lifecycle();

    /**
     * 生命周期监听器列表，采用线程安全的 CopyOnWriteArrayList。
     * <p>
     * 监听器会在组件状态变化时收到通知，用于执行额外的初始化或清理工作。
     * 使用 CopyOnWriteArrayList 是因为监听器列表通常读多写少，
     * 且在迭代过程中允许并发修改而不抛出异常。
     */
    private final List<LifecycleListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 默认构造函数。
     */
    protected AbstractLifecycleComponent() {}

    @Override
    public Lifecycle.State lifecycleState() {
        return this.lifecycle.state();
    }

    @Override
    public void addLifecycleListener(LifecycleListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeLifecycleListener(LifecycleListener listener) {
        listeners.remove(listener);
    }

    /**
     * 启动组件，按照预定义的流程执行启动操作。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>获取 lifecycle 对象的同步锁，确保线程安全</li>
     *   <li>检查是否可以转换为 STARTED 状态（通过 {@link Lifecycle#canMoveToStarted()}）</li>
     *   <li>如果不能转换，直接返回（例如已经启动或已关闭）</li>
     *   <li>通知所有监听器即将启动（{@code beforeStart()}）</li>
     *   <li>调用子类实现的 {@link #doStart()} 执行具体启动逻辑</li>
     *   <li>将状态正式转换为 STARTED（通过 {@link Lifecycle#moveToStarted()}）</li>
     *   <li>通知所有监听器已完成启动（{@code afterStart()}）</li>
     * </ol>
     * <p>
     * 注意：整个启动过程在同步块中执行，防止并发启动导致的状态不一致。
     */
    @Override
    public void start() {
        synchronized (lifecycle) {
            if (!lifecycle.canMoveToStarted()) {
                return;
            }
            for (LifecycleListener listener : listeners) {
                listener.beforeStart();
            }
            doStart();
            lifecycle.moveToStarted();
            for (LifecycleListener listener : listeners) {
                listener.afterStart();
            }
        }
    }

    /**
     * 抽象方法，由子类实现具体的启动逻辑。
     * <p>
     * 在此方法中，子类应该：
     * <ul>
     *   <li>初始化必要的资源</li>
     *   <li>启动后台服务或线程</li>
     *   <li>建立网络连接或打开文件句柄</li>
     *   <li>执行其他启动时需要的工作</li>
     * </ul>
     * <p>
     * 注意：此方法在同步块中被调用，应避免执行耗时操作或再次获取 lifecycle 锁，
     * 否则可能导致死锁。
     */
    protected abstract void doStart();

    /**
     * 停止组件，按照预定义的流程执行停止操作。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>获取 lifecycle 对象的同步锁，确保线程安全</li>
     *   <li>检查是否可以转换为 STOPPED 状态（通过 {@link Lifecycle#canMoveToStopped()}）</li>
     *   <li>如果不能转换，直接返回（例如未启动或已停止）</li>
     *   <li>通知所有监听器即将停止（{@code beforeStop()}）</li>
     *   <li>将状态正式转换为 STOPPED（通过 {@link Lifecycle#moveToStopped()}）</li>
     *   <li>调用子类实现的 {@link #doStop()} 执行具体停止逻辑</li>
     *   <li>通知所有监听器已完成停止（{@code afterStop()}）</li>
     * </ol>
     * <p>
     * 注意：与启动不同，状态转换在实际停止操作之前执行，
     * 这样可以防止重复停止，但也意味着在 doStop() 执行期间组件已标记为 STOPPED。
     */
    @Override
    public void stop() {
        synchronized (lifecycle) {
            if (!lifecycle.canMoveToStopped()) {
                return;
            }
            for (LifecycleListener listener : listeners) {
                listener.beforeStop();
            }
            lifecycle.moveToStopped();
            doStop();
            for (LifecycleListener listener : listeners) {
                listener.afterStop();
            }
        }
    }

    /**
     * 抽象方法，由子类实现具体的停止逻辑。
     * <p>
     * 在此方法中，子类应该：
     * <ul>
     *   <li>停止后台服务或线程</li>
     *   <li>暂停正在进行的任务</li>
     *   <li>释放部分资源（但保留可重用的资源）</li>
     *   <li>执行其他停止时需要的工作</li>
     * </ul>
     * <p>
     * 注意：停止操作应该是可逆的，即停止后可以再次启动。
     * 如果需要彻底释放资源，应在 {@link #doClose()} 中执行。
     */
    protected abstract void doStop();

    /**
     * 关闭组件，执行最终的清理和资源释放操作。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>获取 lifecycle 对象的同步锁，确保线程安全</li>
     *   <li>如果组件仍处于 STARTED 状态，先调用 {@link #stop()} 停止组件</li>
     *   <li>检查是否可以转换为 CLOSED 状态（通过 {@link Lifecycle#canMoveToClosed()}）</li>
     *   <li>如果不能转换，直接返回（例如已经关闭）</li>
     *   <li>通知所有监听器即将关闭（{@code beforeClose()}）</li>
     *   <li>将状态正式转换为 CLOSED（通过 {@link Lifecycle#moveToClosed()}）</li>
     *   <li>调用子类实现的 {@link #doClose()} 执行具体关闭逻辑</li>
     *   <li>无论是否发生异常，都通知所有监听器已完成关闭（{@code afterClose()}）</li>
     * </ol>
     * <p>
     * 注意：
     * <ul>
     *   <li>关闭是最终状态，组件关闭后不能再启动</li>
     *   <li>IO 异常会被包装为 {@link UncheckedIOException} 抛出</li>
     *   <li>监听器的 {@code afterClose()} 在 finally 块中调用，确保一定会执行</li>
     * </ul>
     */
    @Override
    public void close() {
        synchronized (lifecycle) {
            if (lifecycle.started()) {
                stop();
            }
            if (!lifecycle.canMoveToClosed()) {
                return;
            }
            for (LifecycleListener listener : listeners) {
                listener.beforeClose();
            }
            lifecycle.moveToClosed();
            try {
                doClose();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                for (LifecycleListener listener : listeners) {
                    listener.afterClose();
                }
            }
        }
    }

    /**
     * 抽象方法，由子类实现具体的关闭逻辑。
     * <p>
     * 在此方法中，子类应该：
     * <ul>
     *   <li>彻底释放所有资源（文件句柄、网络连接、内存缓冲区等）</li>
     *   <li>关闭底层的服务或依赖</li>
     *   <li>执行最终的清理工作</li>
     *   <li>保存需要持久化的状态</li>
     * </ul>
     * <p>
     * 注意：
     * <ul>
     *   <li>此方法可能抛出 {@link IOException}，会被包装为运行时异常</li>
     *   <li>关闭操作不可逆，组件关闭后不能再次使用</li>
     *   <li>应确保即使发生异常也能正确释放资源（建议使用 try-finally）</li>
     * </ul>
     *
     * @throws IOException 如果关闭过程中发生 IO 错误
     */
    protected abstract void doClose() throws IOException;
}
