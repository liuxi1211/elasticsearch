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

import org.elasticsearch.common.lease.Releasable;

/**
 * 生命周期组件接口，定义了具有生命周期管理能力的组件的基本契约。
 * <p>
 * 该接口继承自 {@link Releasable}，意味着实现类需要支持资源释放操作。
 * 在 Elasticsearch 中，许多核心组件（如节点、传输服务、索引服务等）都实现了此接口，
 * 以便统一管理它们的启动、停止和关闭过程。
 * <p>
 * 典型的生命周期状态转换：INITIALIZED → STARTED → STOPPED → CLOSED
 */
public interface LifecycleComponent extends Releasable {

    /**
     * 获取当前组件的生命周期状态。
     *
     * @return 当前的 {@link Lifecycle.State} 状态值
     */
    Lifecycle.State lifecycleState();

    /**
     * 添加生命周期监听器，用于在组件状态变化时接收通知。
     * <p>
     * 监听器会在组件启动前/后、停止前/后、关闭前/后被调用，
     * 允许外部组件在这些关键时间点执行自定义逻辑。
     *
     * @param listener 要添加的 {@link LifecycleListener} 监听器
     */
    void addLifecycleListener(LifecycleListener listener);

    /**
     * 移除已注册的生命周期监听器。
     *
     * @param listener 要移除的 {@link LifecycleListener} 监听器
     */
    void removeLifecycleListener(LifecycleListener listener);

    /**
     * 启动组件。
     * <p>
     * 该方法会触发以下流程：
     * <ol>
     *   <li>检查是否可以转换为 STARTED 状态</li>
     *   <li>调用所有监听器的 {@code beforeStart()} 方法</li>
     *   <li>执行子类实现的 {@code doStart()} 启动逻辑</li>
     *   <li>将状态转换为 STARTED</li>
     *   <li>调用所有监听器的 {@code afterStart()} 方法</li>
     * </ol>
     * 如果组件已经处于 STARTED 状态或无法转换，则直接返回。
     */
    void start();

    /**
     * 停止组件。
     * <p>
     * 该方法会触发以下流程：
     * <ol>
     *   <li>检查是否可以转换为 STOPPED 状态</li>
     *   <li>调用所有监听器的 {@code beforeStop()} 方法</li>
     *   <li>将状态转换为 STOPPED</li>
     *   <li>执行子类实现的 {@code doStop()} 停止逻辑</li>
     *   <li>调用所有监听器的 {@code afterStop()} 方法</li>
     * </ol>
     * 如果组件未处于 STARTED 状态或无法转换，则直接返回。
     */
    void stop();
}
