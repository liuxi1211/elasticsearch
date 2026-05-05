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

package org.elasticsearch.action;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.admin.cluster.allocation.ClusterAllocationExplainAction;
import org.elasticsearch.action.admin.cluster.allocation.TransportClusterAllocationExplainAction;
import org.elasticsearch.action.admin.cluster.configuration.AddVotingConfigExclusionsAction;
import org.elasticsearch.action.admin.cluster.configuration.ClearVotingConfigExclusionsAction;
import org.elasticsearch.action.admin.cluster.configuration.TransportAddVotingConfigExclusionsAction;
import org.elasticsearch.action.admin.cluster.configuration.TransportClearVotingConfigExclusionsAction;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthAction;
import org.elasticsearch.action.admin.cluster.health.TransportClusterHealthAction;
import org.elasticsearch.action.admin.cluster.node.hotthreads.NodesHotThreadsAction;
import org.elasticsearch.action.admin.cluster.node.hotthreads.TransportNodesHotThreadsAction;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoAction;
import org.elasticsearch.action.admin.cluster.node.info.TransportNodesInfoAction;
import org.elasticsearch.action.admin.cluster.node.liveness.TransportLivenessAction;
import org.elasticsearch.action.admin.cluster.node.reload.NodesReloadSecureSettingsAction;
import org.elasticsearch.action.admin.cluster.node.reload.TransportNodesReloadSecureSettingsAction;
import org.elasticsearch.action.admin.cluster.node.stats.NodesStatsAction;
import org.elasticsearch.action.admin.cluster.node.stats.TransportNodesStatsAction;
import org.elasticsearch.action.admin.cluster.node.tasks.cancel.CancelTasksAction;
import org.elasticsearch.action.admin.cluster.node.tasks.cancel.TransportCancelTasksAction;
import org.elasticsearch.action.admin.cluster.node.tasks.get.GetTaskAction;
import org.elasticsearch.action.admin.cluster.node.tasks.get.TransportGetTaskAction;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksAction;
import org.elasticsearch.action.admin.cluster.node.tasks.list.TransportListTasksAction;
import org.elasticsearch.action.admin.cluster.node.usage.NodesUsageAction;
import org.elasticsearch.action.admin.cluster.node.usage.TransportNodesUsageAction;
import org.elasticsearch.action.admin.cluster.remote.RemoteInfoAction;
import org.elasticsearch.action.admin.cluster.remote.TransportRemoteInfoAction;
import org.elasticsearch.action.admin.cluster.repositories.cleanup.CleanupRepositoryAction;
import org.elasticsearch.action.admin.cluster.repositories.cleanup.TransportCleanupRepositoryAction;
import org.elasticsearch.action.admin.cluster.repositories.delete.DeleteRepositoryAction;
import org.elasticsearch.action.admin.cluster.repositories.delete.TransportDeleteRepositoryAction;
import org.elasticsearch.action.admin.cluster.repositories.get.GetRepositoriesAction;
import org.elasticsearch.action.admin.cluster.repositories.get.TransportGetRepositoriesAction;
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryAction;
import org.elasticsearch.action.admin.cluster.repositories.put.TransportPutRepositoryAction;
import org.elasticsearch.action.admin.cluster.repositories.verify.TransportVerifyRepositoryAction;
import org.elasticsearch.action.admin.cluster.repositories.verify.VerifyRepositoryAction;
import org.elasticsearch.action.admin.cluster.reroute.ClusterRerouteAction;
import org.elasticsearch.action.admin.cluster.reroute.TransportClusterRerouteAction;
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsAction;
import org.elasticsearch.action.admin.cluster.settings.TransportClusterUpdateSettingsAction;
import org.elasticsearch.action.admin.cluster.shards.ClusterSearchShardsAction;
import org.elasticsearch.action.admin.cluster.shards.TransportClusterSearchShardsAction;
import org.elasticsearch.action.admin.cluster.snapshots.clone.CloneSnapshotAction;
import org.elasticsearch.action.admin.cluster.snapshots.clone.TransportCloneSnapshotAction;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotAction;
import org.elasticsearch.action.admin.cluster.snapshots.create.TransportCreateSnapshotAction;
import org.elasticsearch.action.admin.cluster.snapshots.delete.DeleteSnapshotAction;
import org.elasticsearch.action.admin.cluster.snapshots.delete.TransportDeleteSnapshotAction;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsAction;
import org.elasticsearch.action.admin.cluster.snapshots.get.TransportGetSnapshotsAction;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotAction;
import org.elasticsearch.action.admin.cluster.snapshots.restore.TransportRestoreSnapshotAction;
import org.elasticsearch.action.admin.cluster.snapshots.status.SnapshotsStatusAction;
import org.elasticsearch.action.admin.cluster.snapshots.status.TransportSnapshotsStatusAction;
import org.elasticsearch.action.admin.cluster.state.ClusterStateAction;
import org.elasticsearch.action.admin.cluster.state.TransportClusterStateAction;
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsAction;
import org.elasticsearch.action.admin.cluster.stats.TransportClusterStatsAction;
import org.elasticsearch.action.admin.cluster.storedscripts.DeleteStoredScriptAction;
import org.elasticsearch.action.admin.cluster.storedscripts.GetScriptContextAction;
import org.elasticsearch.action.admin.cluster.storedscripts.GetScriptLanguageAction;
import org.elasticsearch.action.admin.cluster.storedscripts.GetStoredScriptAction;
import org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptAction;
import org.elasticsearch.action.admin.cluster.storedscripts.TransportDeleteStoredScriptAction;
import org.elasticsearch.action.admin.cluster.storedscripts.TransportGetScriptContextAction;
import org.elasticsearch.action.admin.cluster.storedscripts.TransportGetScriptLanguageAction;
import org.elasticsearch.action.admin.cluster.storedscripts.TransportGetStoredScriptAction;
import org.elasticsearch.action.admin.cluster.storedscripts.TransportPutStoredScriptAction;
import org.elasticsearch.action.admin.cluster.tasks.PendingClusterTasksAction;
import org.elasticsearch.action.admin.cluster.tasks.TransportPendingClusterTasksAction;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesAction;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.TransportIndicesAliasesAction;
import org.elasticsearch.action.admin.indices.alias.exists.AliasesExistAction;
import org.elasticsearch.action.admin.indices.alias.exists.TransportAliasesExistAction;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesAction;
import org.elasticsearch.action.admin.indices.alias.get.TransportGetAliasesAction;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeAction;
import org.elasticsearch.action.admin.indices.analyze.TransportAnalyzeAction;
import org.elasticsearch.action.admin.indices.cache.clear.ClearIndicesCacheAction;
import org.elasticsearch.action.admin.indices.cache.clear.TransportClearIndicesCacheAction;
import org.elasticsearch.action.admin.indices.close.CloseIndexAction;
import org.elasticsearch.action.admin.indices.close.TransportCloseIndexAction;
import org.elasticsearch.action.admin.indices.create.AutoCreateAction;
import org.elasticsearch.action.admin.indices.create.AutoCreateAction;
import org.elasticsearch.action.admin.indices.create.CreateIndexAction;
import org.elasticsearch.action.admin.indices.create.TransportCreateIndexAction;
import org.elasticsearch.action.admin.indices.dangling.delete.DeleteDanglingIndexAction;
import org.elasticsearch.action.admin.indices.dangling.delete.TransportDeleteDanglingIndexAction;
import org.elasticsearch.action.admin.indices.dangling.find.FindDanglingIndexAction;
import org.elasticsearch.action.admin.indices.dangling.find.TransportFindDanglingIndexAction;
import org.elasticsearch.action.admin.indices.dangling.import_index.ImportDanglingIndexAction;
import org.elasticsearch.action.admin.indices.dangling.import_index.TransportImportDanglingIndexAction;
import org.elasticsearch.action.admin.indices.dangling.list.ListDanglingIndicesAction;
import org.elasticsearch.action.admin.indices.dangling.list.TransportListDanglingIndicesAction;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexAction;
import org.elasticsearch.action.admin.indices.delete.TransportDeleteIndexAction;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsAction;
import org.elasticsearch.action.admin.indices.exists.indices.TransportIndicesExistsAction;
import org.elasticsearch.action.admin.indices.exists.types.TransportTypesExistsAction;
import org.elasticsearch.action.admin.indices.exists.types.TypesExistsAction;
import org.elasticsearch.action.admin.indices.flush.FlushAction;
import org.elasticsearch.action.admin.indices.flush.SyncedFlushAction;
import org.elasticsearch.action.admin.indices.flush.TransportFlushAction;
import org.elasticsearch.action.admin.indices.flush.TransportSyncedFlushAction;
import org.elasticsearch.action.admin.indices.forcemerge.ForceMergeAction;
import org.elasticsearch.action.admin.indices.forcemerge.TransportForceMergeAction;
import org.elasticsearch.action.admin.indices.get.GetIndexAction;
import org.elasticsearch.action.admin.indices.get.TransportGetIndexAction;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsAction;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsAction;
import org.elasticsearch.action.admin.indices.mapping.get.TransportGetFieldMappingsAction;
import org.elasticsearch.action.admin.indices.mapping.get.TransportGetFieldMappingsIndexAction;
import org.elasticsearch.action.admin.indices.mapping.get.TransportGetMappingsAction;
import org.elasticsearch.action.admin.indices.mapping.put.AutoPutMappingAction;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingAction;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.action.admin.indices.mapping.put.TransportAutoPutMappingAction;
import org.elasticsearch.action.admin.indices.mapping.put.TransportPutMappingAction;
import org.elasticsearch.action.admin.indices.open.OpenIndexAction;
import org.elasticsearch.action.admin.indices.open.TransportOpenIndexAction;
import org.elasticsearch.action.admin.indices.readonly.AddIndexBlockAction;
import org.elasticsearch.action.admin.indices.readonly.TransportAddIndexBlockAction;
import org.elasticsearch.action.admin.indices.recovery.RecoveryAction;
import org.elasticsearch.action.admin.indices.recovery.TransportRecoveryAction;
import org.elasticsearch.action.admin.indices.refresh.RefreshAction;
import org.elasticsearch.action.admin.indices.refresh.TransportRefreshAction;
import org.elasticsearch.action.admin.indices.resolve.ResolveIndexAction;
import org.elasticsearch.action.admin.indices.rollover.RolloverAction;
import org.elasticsearch.action.admin.indices.rollover.TransportRolloverAction;
import org.elasticsearch.action.admin.indices.segments.IndicesSegmentsAction;
import org.elasticsearch.action.admin.indices.segments.TransportIndicesSegmentsAction;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsAction;
import org.elasticsearch.action.admin.indices.settings.get.TransportGetSettingsAction;
import org.elasticsearch.action.admin.indices.settings.put.TransportUpdateSettingsAction;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsAction;
import org.elasticsearch.action.admin.indices.shards.IndicesShardStoresAction;
import org.elasticsearch.action.admin.indices.shards.TransportIndicesShardStoresAction;
import org.elasticsearch.action.admin.indices.shrink.ResizeAction;
import org.elasticsearch.action.admin.indices.shrink.TransportResizeAction;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsAction;
import org.elasticsearch.action.admin.indices.stats.TransportIndicesStatsAction;
import org.elasticsearch.action.admin.indices.template.delete.DeleteComponentTemplateAction;
import org.elasticsearch.action.admin.indices.template.delete.DeleteComposableIndexTemplateAction;
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateAction;
import org.elasticsearch.action.admin.indices.template.delete.TransportDeleteComponentTemplateAction;
import org.elasticsearch.action.admin.indices.template.delete.TransportDeleteComposableIndexTemplateAction;
import org.elasticsearch.action.admin.indices.template.delete.TransportDeleteIndexTemplateAction;
import org.elasticsearch.action.admin.indices.template.get.GetComponentTemplateAction;
import org.elasticsearch.action.admin.indices.template.get.GetComposableIndexTemplateAction;
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesAction;
import org.elasticsearch.action.admin.indices.template.get.TransportGetComponentTemplateAction;
import org.elasticsearch.action.admin.indices.template.get.TransportGetComposableIndexTemplateAction;
import org.elasticsearch.action.admin.indices.template.get.TransportGetIndexTemplatesAction;
import org.elasticsearch.action.admin.indices.template.post.SimulateIndexTemplateAction;
import org.elasticsearch.action.admin.indices.template.post.SimulateTemplateAction;
import org.elasticsearch.action.admin.indices.template.post.TransportSimulateIndexTemplateAction;
import org.elasticsearch.action.admin.indices.template.post.TransportSimulateTemplateAction;
import org.elasticsearch.action.admin.indices.template.put.PutComponentTemplateAction;
import org.elasticsearch.action.admin.indices.template.put.PutComposableIndexTemplateAction;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateAction;
import org.elasticsearch.action.admin.indices.template.put.TransportPutComponentTemplateAction;
import org.elasticsearch.action.admin.indices.template.put.TransportPutComposableIndexTemplateAction;
import org.elasticsearch.action.admin.indices.template.put.TransportPutIndexTemplateAction;
import org.elasticsearch.action.admin.indices.upgrade.get.TransportUpgradeStatusAction;
import org.elasticsearch.action.admin.indices.upgrade.get.UpgradeStatusAction;
import org.elasticsearch.action.admin.indices.upgrade.post.TransportUpgradeAction;
import org.elasticsearch.action.admin.indices.upgrade.post.TransportUpgradeSettingsAction;
import org.elasticsearch.action.admin.indices.upgrade.post.UpgradeAction;
import org.elasticsearch.action.admin.indices.upgrade.post.UpgradeSettingsAction;
import org.elasticsearch.action.admin.indices.validate.query.TransportValidateQueryAction;
import org.elasticsearch.action.admin.indices.validate.query.ValidateQueryAction;
import org.elasticsearch.action.bulk.BulkAction;
import org.elasticsearch.action.bulk.TransportBulkAction;
import org.elasticsearch.action.bulk.TransportShardBulkAction;
import org.elasticsearch.action.delete.DeleteAction;
import org.elasticsearch.action.delete.TransportDeleteAction;
import org.elasticsearch.action.explain.ExplainAction;
import org.elasticsearch.action.explain.TransportExplainAction;
import org.elasticsearch.action.fieldcaps.FieldCapabilitiesAction;
import org.elasticsearch.action.fieldcaps.TransportFieldCapabilitiesAction;
import org.elasticsearch.action.fieldcaps.TransportFieldCapabilitiesIndexAction;
import org.elasticsearch.action.get.GetAction;
import org.elasticsearch.action.get.MultiGetAction;
import org.elasticsearch.action.get.TransportGetAction;
import org.elasticsearch.action.get.TransportMultiGetAction;
import org.elasticsearch.action.get.TransportShardMultiGetAction;
import org.elasticsearch.action.index.IndexAction;
import org.elasticsearch.action.index.TransportIndexAction;
import org.elasticsearch.action.ingest.DeletePipelineAction;
import org.elasticsearch.action.ingest.DeletePipelineTransportAction;
import org.elasticsearch.action.ingest.GetPipelineAction;
import org.elasticsearch.action.ingest.GetPipelineTransportAction;
import org.elasticsearch.action.ingest.PutPipelineAction;
import org.elasticsearch.action.ingest.PutPipelineTransportAction;
import org.elasticsearch.action.ingest.SimulatePipelineAction;
import org.elasticsearch.action.ingest.SimulatePipelineTransportAction;
import org.elasticsearch.action.main.MainAction;
import org.elasticsearch.action.main.TransportMainAction;
import org.elasticsearch.action.search.ClearScrollAction;
import org.elasticsearch.action.search.MultiSearchAction;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchScrollAction;
import org.elasticsearch.action.search.TransportClearScrollAction;
import org.elasticsearch.action.search.TransportMultiSearchAction;
import org.elasticsearch.action.search.TransportSearchAction;
import org.elasticsearch.action.search.TransportSearchScrollAction;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.AutoCreateIndex;
import org.elasticsearch.action.support.DestructiveOperations;
import org.elasticsearch.action.support.TransportAction;
import org.elasticsearch.action.termvectors.MultiTermVectorsAction;
import org.elasticsearch.action.termvectors.TermVectorsAction;
import org.elasticsearch.action.termvectors.TransportMultiTermVectorsAction;
import org.elasticsearch.action.termvectors.TransportShardMultiTermsVectorAction;
import org.elasticsearch.action.termvectors.TransportTermVectorsAction;
import org.elasticsearch.action.update.TransportUpdateAction;
import org.elasticsearch.action.update.UpdateAction;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.NamedRegistry;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.TypeLiteral;
import org.elasticsearch.common.inject.multibindings.MapBinder;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.index.seqno.RetentionLeaseActions;
import org.elasticsearch.indices.SystemIndices;
import org.elasticsearch.indices.breaker.CircuitBreakerService;
import org.elasticsearch.persistent.CompletionPersistentTaskAction;
import org.elasticsearch.persistent.RemovePersistentTaskAction;
import org.elasticsearch.persistent.StartPersistentTaskAction;
import org.elasticsearch.persistent.UpdatePersistentTaskStatusAction;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.ActionPlugin.ActionHandler;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.rest.RestHeaderDefinition;
import org.elasticsearch.rest.action.RestFieldCapabilitiesAction;
import org.elasticsearch.rest.action.RestMainAction;
import org.elasticsearch.rest.action.admin.cluster.RestAddVotingConfigExclusionAction;
import org.elasticsearch.rest.action.admin.cluster.RestCancelTasksAction;
import org.elasticsearch.rest.action.admin.cluster.RestCleanupRepositoryAction;
import org.elasticsearch.rest.action.admin.cluster.RestClearVotingConfigExclusionsAction;
import org.elasticsearch.rest.action.admin.cluster.RestCloneSnapshotAction;
import org.elasticsearch.rest.action.admin.cluster.RestClusterAllocationExplainAction;
import org.elasticsearch.rest.action.admin.cluster.RestClusterGetSettingsAction;
import org.elasticsearch.rest.action.admin.cluster.RestClusterHealthAction;
import org.elasticsearch.rest.action.admin.cluster.RestClusterRerouteAction;
import org.elasticsearch.rest.action.admin.cluster.RestClusterSearchShardsAction;
import org.elasticsearch.rest.action.admin.cluster.RestClusterStateAction;
import org.elasticsearch.rest.action.admin.cluster.RestClusterStatsAction;
import org.elasticsearch.rest.action.admin.cluster.RestClusterUpdateSettingsAction;
import org.elasticsearch.rest.action.admin.cluster.RestCreateSnapshotAction;
import org.elasticsearch.rest.action.admin.cluster.RestDeleteRepositoryAction;
import org.elasticsearch.rest.action.admin.cluster.RestDeleteSnapshotAction;
import org.elasticsearch.rest.action.admin.cluster.RestDeleteStoredScriptAction;
import org.elasticsearch.rest.action.admin.cluster.RestGetRepositoriesAction;
import org.elasticsearch.rest.action.admin.cluster.RestGetScriptContextAction;
import org.elasticsearch.rest.action.admin.cluster.RestGetScriptLanguageAction;
import org.elasticsearch.rest.action.admin.cluster.RestGetSnapshotsAction;
import org.elasticsearch.rest.action.admin.cluster.RestGetStoredScriptAction;
import org.elasticsearch.rest.action.admin.cluster.RestGetTaskAction;
import org.elasticsearch.rest.action.admin.cluster.RestListTasksAction;
import org.elasticsearch.rest.action.admin.cluster.RestNodesHotThreadsAction;
import org.elasticsearch.rest.action.admin.cluster.RestNodesInfoAction;
import org.elasticsearch.rest.action.admin.cluster.RestNodesStatsAction;
import org.elasticsearch.rest.action.admin.cluster.RestNodesUsageAction;
import org.elasticsearch.rest.action.admin.cluster.RestPendingClusterTasksAction;
import org.elasticsearch.rest.action.admin.cluster.RestPutRepositoryAction;
import org.elasticsearch.rest.action.admin.cluster.RestPutStoredScriptAction;
import org.elasticsearch.rest.action.admin.cluster.RestReloadSecureSettingsAction;
import org.elasticsearch.rest.action.admin.cluster.RestRemoteClusterInfoAction;
import org.elasticsearch.rest.action.admin.cluster.RestRestoreSnapshotAction;
import org.elasticsearch.rest.action.admin.cluster.RestSnapshotsStatusAction;
import org.elasticsearch.rest.action.admin.cluster.RestVerifyRepositoryAction;
import org.elasticsearch.rest.action.admin.cluster.dangling.RestDeleteDanglingIndexAction;
import org.elasticsearch.rest.action.admin.cluster.dangling.RestImportDanglingIndexAction;
import org.elasticsearch.rest.action.admin.cluster.dangling.RestListDanglingIndicesAction;
import org.elasticsearch.rest.action.admin.indices.RestAddIndexBlockAction;
import org.elasticsearch.rest.action.admin.indices.RestAnalyzeAction;
import org.elasticsearch.rest.action.admin.indices.RestClearIndicesCacheAction;
import org.elasticsearch.rest.action.admin.indices.RestCloseIndexAction;
import org.elasticsearch.rest.action.admin.indices.RestCreateIndexAction;
import org.elasticsearch.rest.action.admin.indices.RestDeleteComponentTemplateAction;
import org.elasticsearch.rest.action.admin.indices.RestDeleteComposableIndexTemplateAction;
import org.elasticsearch.rest.action.admin.indices.RestDeleteComposableIndexTemplateAction;
import org.elasticsearch.rest.action.admin.indices.RestDeleteIndexAction;
import org.elasticsearch.rest.action.admin.indices.RestDeleteIndexTemplateAction;
import org.elasticsearch.rest.action.admin.indices.RestFlushAction;
import org.elasticsearch.rest.action.admin.indices.RestForceMergeAction;
import org.elasticsearch.rest.action.admin.indices.RestGetAliasesAction;
import org.elasticsearch.rest.action.admin.indices.RestGetComponentTemplateAction;
import org.elasticsearch.rest.action.admin.indices.RestGetComposableIndexTemplateAction;
import org.elasticsearch.rest.action.admin.indices.RestGetComposableIndexTemplateAction;
import org.elasticsearch.rest.action.admin.indices.RestGetFieldMappingAction;
import org.elasticsearch.rest.action.admin.indices.RestGetIndexTemplateAction;
import org.elasticsearch.rest.action.admin.indices.RestGetIndicesAction;
import org.elasticsearch.rest.action.admin.indices.RestGetMappingAction;
import org.elasticsearch.rest.action.admin.indices.RestGetSettingsAction;
import org.elasticsearch.rest.action.admin.indices.RestIndexDeleteAliasesAction;
import org.elasticsearch.rest.action.admin.indices.RestIndexPutAliasAction;
import org.elasticsearch.rest.action.admin.indices.RestIndicesAliasesAction;
import org.elasticsearch.rest.action.admin.indices.RestIndicesSegmentsAction;
import org.elasticsearch.rest.action.admin.indices.RestIndicesShardStoresAction;
import org.elasticsearch.rest.action.admin.indices.RestIndicesStatsAction;
import org.elasticsearch.rest.action.admin.indices.RestOpenIndexAction;
import org.elasticsearch.rest.action.admin.indices.RestPutComponentTemplateAction;
import org.elasticsearch.rest.action.admin.indices.RestPutComposableIndexTemplateAction;
import org.elasticsearch.rest.action.admin.indices.RestPutIndexTemplateAction;
import org.elasticsearch.rest.action.admin.indices.RestPutMappingAction;
import org.elasticsearch.rest.action.admin.indices.RestRecoveryAction;
import org.elasticsearch.rest.action.admin.indices.RestRefreshAction;
import org.elasticsearch.rest.action.admin.indices.RestResizeHandler;
import org.elasticsearch.rest.action.admin.indices.RestResolveIndexAction;
import org.elasticsearch.rest.action.admin.indices.RestRolloverIndexAction;
import org.elasticsearch.rest.action.admin.indices.RestSimulateIndexTemplateAction;
import org.elasticsearch.rest.action.admin.indices.RestSimulateTemplateAction;
import org.elasticsearch.rest.action.admin.indices.RestSyncedFlushAction;
import org.elasticsearch.rest.action.admin.indices.RestUpdateSettingsAction;
import org.elasticsearch.rest.action.admin.indices.RestUpgradeAction;
import org.elasticsearch.rest.action.admin.indices.RestUpgradeStatusAction;
import org.elasticsearch.rest.action.admin.indices.RestValidateQueryAction;
import org.elasticsearch.rest.action.cat.AbstractCatAction;
import org.elasticsearch.rest.action.cat.RestAliasAction;
import org.elasticsearch.rest.action.cat.RestAllocationAction;
import org.elasticsearch.rest.action.cat.RestCatAction;
import org.elasticsearch.rest.action.cat.RestCatRecoveryAction;
import org.elasticsearch.rest.action.cat.RestFielddataAction;
import org.elasticsearch.rest.action.cat.RestHealthAction;
import org.elasticsearch.rest.action.cat.RestIndicesAction;
import org.elasticsearch.rest.action.cat.RestMasterAction;
import org.elasticsearch.rest.action.cat.RestNodeAttrsAction;
import org.elasticsearch.rest.action.cat.RestNodesAction;
import org.elasticsearch.rest.action.cat.RestPluginsAction;
import org.elasticsearch.rest.action.cat.RestRepositoriesAction;
import org.elasticsearch.rest.action.cat.RestSegmentsAction;
import org.elasticsearch.rest.action.cat.RestShardsAction;
import org.elasticsearch.rest.action.cat.RestSnapshotAction;
import org.elasticsearch.rest.action.cat.RestTasksAction;
import org.elasticsearch.rest.action.cat.RestTemplatesAction;
import org.elasticsearch.rest.action.cat.RestThreadPoolAction;
import org.elasticsearch.rest.action.document.RestBulkAction;
import org.elasticsearch.rest.action.document.RestDeleteAction;
import org.elasticsearch.rest.action.document.RestGetAction;
import org.elasticsearch.rest.action.document.RestGetSourceAction;
import org.elasticsearch.rest.action.document.RestIndexAction;
import org.elasticsearch.rest.action.document.RestIndexAction.AutoIdHandler;
import org.elasticsearch.rest.action.document.RestIndexAction.CreateHandler;
import org.elasticsearch.rest.action.document.RestMultiGetAction;
import org.elasticsearch.rest.action.document.RestMultiTermVectorsAction;
import org.elasticsearch.rest.action.document.RestTermVectorsAction;
import org.elasticsearch.rest.action.document.RestUpdateAction;
import org.elasticsearch.rest.action.ingest.RestDeletePipelineAction;
import org.elasticsearch.rest.action.ingest.RestGetPipelineAction;
import org.elasticsearch.rest.action.ingest.RestPutPipelineAction;
import org.elasticsearch.rest.action.ingest.RestSimulatePipelineAction;
import org.elasticsearch.rest.action.search.RestClearScrollAction;
import org.elasticsearch.rest.action.search.RestCountAction;
import org.elasticsearch.rest.action.search.RestExplainAction;
import org.elasticsearch.rest.action.search.RestMultiSearchAction;
import org.elasticsearch.rest.action.search.RestSearchAction;
import org.elasticsearch.rest.action.search.RestSearchScrollAction;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.usage.UsageService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.unmodifiableMap;

/**
 * 构建并绑定通用的 Action 映射、所有的 {@link TransportAction} 以及 {@link ActionFilters}。
 * 
 * ActionModule 是 Elasticsearch 中非常重要的核心模块，主要职责包括：
 * 1. 注册所有的 Action（包括内置和插件提供的）
 * 2. 初始化 REST 控制器和 REST 处理器
 * 3. 配置 Action 过滤器链
 * 4. 管理依赖注入绑定
 * 5. 处理自动创建索引、破坏性操作等配置
 */
public class ActionModule extends AbstractModule {

    private static final Logger logger = LogManager.getLogger(ActionModule.class);

    /** 是否为传输客户端模式 */
    private final boolean transportClient;
    /** 全局配置 */
    private final Settings settings;
    /** 索引名称表达式解析器，用于解析索引名模式（如通配符） */
    private final IndexNameExpressionResolver indexNameExpressionResolver;
    /** 索引范围的配置 */
    private final IndexScopedSettings indexScopedSettings;
    /** 集群配置 */
    private final ClusterSettings clusterSettings;
    /** 配置过滤器，用于过滤敏感配置信息 */
    private final SettingsFilter settingsFilter;
    /** 所有 Action 插件列表 */
    private final List<ActionPlugin> actionPlugins;
    /** Action 名称到 ActionHandler 的映射，存储所有注册的 Action */
    private final Map<String, ActionHandler<?, ?>> actions;
    /** Action 过滤器链，用于在 Action 执行前后进行拦截处理 */
    private final ActionFilters actionFilters;
    /** 自动创建索引功能，控制索引是否可以自动创建 */
    private final AutoCreateIndex autoCreateIndex;
    /** 破坏性操作控制，限制可能导致数据丢失的操作 */
    private final DestructiveOperations destructiveOperations;
    /** REST 控制器，用于管理和路由 REST 请求 */
    private final RestController restController;
    /** 映射更新请求验证器 */
    private final RequestValidators<PutMappingRequest> mappingRequestValidators;
    /** 索引别名请求验证器 */
    private final RequestValidators<IndicesAliasesRequest> indicesAliasesRequestRequestValidators;
    /** 线程池，用于异步执行任务 */
    private final ThreadPool threadPool;

    /**
     * 构造函数，初始化 ActionModule
     * 
     * @param transportClient 是否为传输客户端模式
     * @param settings 全局配置
     * @param indexNameExpressionResolver 索引名称表达式解析器
     * @param indexScopedSettings 索引范围配置
     * @param clusterSettings 集群配置
     * @param settingsFilter 配置过滤器
     * @param threadPool 线程池
     * @param actionPlugins Action 插件列表
     * @param nodeClient 节点客户端
     * @param circuitBreakerService 熔断器服务
     * @param usageService 使用统计服务
     * @param systemIndices 系统索引
     */
    public ActionModule(boolean transportClient, Settings settings, IndexNameExpressionResolver indexNameExpressionResolver,
                        IndexScopedSettings indexScopedSettings, ClusterSettings clusterSettings, SettingsFilter settingsFilter,
                        ThreadPool threadPool, List<ActionPlugin> actionPlugins, NodeClient nodeClient,
                        CircuitBreakerService circuitBreakerService, UsageService usageService, SystemIndices systemIndices) {
        this.transportClient = transportClient;
        this.settings = settings;
        this.indexNameExpressionResolver = indexNameExpressionResolver;
        this.indexScopedSettings = indexScopedSettings;
        this.clusterSettings = clusterSettings;
        this.settingsFilter = settingsFilter;
        this.actionPlugins = actionPlugins;
        this.threadPool = threadPool;
        // 初始化所有 Action（包括内置和插件提供的）
        actions = setupActions(actionPlugins);
        // 初始化 Action 过滤器
        actionFilters = setupActionFilters(actionPlugins);
        // 初始化自动创建索引功能（仅非传输客户端模式）
        autoCreateIndex = transportClient
            ? null
            : new AutoCreateIndex(settings, clusterSettings, indexNameExpressionResolver, systemIndices);
        // 初始化破坏性操作控制
        destructiveOperations = new DestructiveOperations(settings, clusterSettings);
        // 收集所有 REST 头定义
        Set<RestHeaderDefinition> headers = Stream.concat(
            actionPlugins.stream().flatMap(p -> p.getRestHeaders().stream()),
            Stream.of(new RestHeaderDefinition(Task.X_OPAQUE_ID, false))
        ).collect(Collectors.toSet());
        // 收集 REST 处理器包装器（最多一个）
        UnaryOperator<RestHandler> restWrapper = null;
        for (ActionPlugin plugin : actionPlugins) {
            UnaryOperator<RestHandler> newRestWrapper = plugin.getRestHandlerWrapper(threadPool.getThreadContext());
            if (newRestWrapper != null) {
                logger.debug("Using REST wrapper from plugin " + plugin.getClass().getName());
                if (restWrapper != null) {
                    throw new IllegalArgumentException("Cannot have more than one plugin implementing a REST wrapper");
                }
                restWrapper = newRestWrapper;
            }
        }
        // 初始化请求验证器
        mappingRequestValidators = new RequestValidators<>(
            actionPlugins.stream().flatMap(p -> p.mappingRequestValidators().stream()).collect(Collectors.toList()));
        indicesAliasesRequestRequestValidators = new RequestValidators<>(
                actionPlugins.stream().flatMap(p -> p.indicesAliasesRequestValidators().stream()).collect(Collectors.toList()));

        // 初始化 REST 控制器（仅非传输客户端模式）
        if (transportClient) {
            restController = null;
        } else {
            restController = new RestController(headers, restWrapper, nodeClient, circuitBreakerService, usageService);
        }
    }


    /**
     * 获取所有注册的 Action
     * 
     * @return Action 名称到 ActionHandler 的映射
     */
    public Map<String, ActionHandler<?, ?>> getActions() {
        return actions;
    }

    /**
     * 设置并注册所有的 Action（包括内置和插件提供的）
     * 
     * 这是 ActionModule 的核心方法之一，负责：
     * 1. 创建 Action 注册表
     * 2. 注册所有内置的 Action
     * 3. 注册插件提供的 Action
     * 4. 返回不可修改的 Action 映射
     * 
     * @param actionPlugins Action 插件列表
     * @return Action 名称到 ActionHandler 的不可修改映射
     */
    static Map<String, ActionHandler<?, ?>> setupActions(List<ActionPlugin> actionPlugins) {
        // 继承 NamedRegistry 以便于注册
        class ActionRegistry extends NamedRegistry<ActionHandler<?, ?>> {
            ActionRegistry() {
                super("action");
            }

            /**
             * 注册 ActionHandler
             */
            public void register(ActionHandler<?, ?> handler) {
                register(handler.getAction().name(), handler);
            }

            /**
             * 注册 ActionType 和对应的 TransportAction
             * 
             * @param action Action 类型
             * @param transportAction 传输 Action 类
             * @param supportTransportActions 支持的传输 Action 类
             */
            public <Request extends ActionRequest, Response extends ActionResponse> void register(
                ActionType<Response> action, Class<? extends TransportAction<Request, Response>> transportAction,
                Class<?>... supportTransportActions) {
                register(new ActionHandler<>(action, transportAction, supportTransportActions));
            }
        }
        ActionRegistry actions = new ActionRegistry();

        // ========== 节点和任务相关 Action ==========
        // 主信息 Action
        actions.register(MainAction.INSTANCE, TransportMainAction.class);
        // 节点信息 Action
        actions.register(NodesInfoAction.INSTANCE, TransportNodesInfoAction.class);
        // 远程集群信息 Action
        actions.register(RemoteInfoAction.INSTANCE, TransportRemoteInfoAction.class);
        // 节点统计 Action
        actions.register(NodesStatsAction.INSTANCE, TransportNodesStatsAction.class);
        // 节点使用情况 Action
        actions.register(NodesUsageAction.INSTANCE, TransportNodesUsageAction.class);
        // 节点热点线程 Action
        actions.register(NodesHotThreadsAction.INSTANCE, TransportNodesHotThreadsAction.class);
        // 任务列表 Action
        actions.register(ListTasksAction.INSTANCE, TransportListTasksAction.class);
        // 获取任务 Action
        actions.register(GetTaskAction.INSTANCE, TransportGetTaskAction.class);
        // 取消任务 Action
        actions.register(CancelTasksAction.INSTANCE, TransportCancelTasksAction.class);

        // ========== 集群管理 Action ==========
        // 添加投票配置排除项 Action
        actions.register(AddVotingConfigExclusionsAction.INSTANCE, TransportAddVotingConfigExclusionsAction.class);
        // 清除投票配置排除项 Action
        actions.register(ClearVotingConfigExclusionsAction.INSTANCE, TransportClearVotingConfigExclusionsAction.class);
        // 集群分配解释 Action
        actions.register(ClusterAllocationExplainAction.INSTANCE, TransportClusterAllocationExplainAction.class);
        // 集群统计 Action
        actions.register(ClusterStatsAction.INSTANCE, TransportClusterStatsAction.class);
        // 集群状态 Action
        actions.register(ClusterStateAction.INSTANCE, TransportClusterStateAction.class);
        // 集群健康 Action
        actions.register(ClusterHealthAction.INSTANCE, TransportClusterHealthAction.class);
        // 集群更新配置 Action
        actions.register(ClusterUpdateSettingsAction.INSTANCE, TransportClusterUpdateSettingsAction.class);
        // 集群重路由 Action
        actions.register(ClusterRerouteAction.INSTANCE, TransportClusterRerouteAction.class);
        // 集群搜索分片 Action
        actions.register(ClusterSearchShardsAction.INSTANCE, TransportClusterSearchShardsAction.class);
        // 待处理集群任务 Action
        actions.register(PendingClusterTasksAction.INSTANCE, TransportPendingClusterTasksAction.class);
        // ========== 快照仓库 Action ==========
        // 创建仓库 Action
        actions.register(PutRepositoryAction.INSTANCE, TransportPutRepositoryAction.class);
        // 获取仓库 Action
        actions.register(GetRepositoriesAction.INSTANCE, TransportGetRepositoriesAction.class);
        // 删除仓库 Action
        actions.register(DeleteRepositoryAction.INSTANCE, TransportDeleteRepositoryAction.class);
        // 验证仓库 Action
        actions.register(VerifyRepositoryAction.INSTANCE, TransportVerifyRepositoryAction.class);
        // 清理仓库 Action
        actions.register(CleanupRepositoryAction.INSTANCE, TransportCleanupRepositoryAction.class);
        // ========== 快照管理 Action ==========
        // 获取快照 Action
        actions.register(GetSnapshotsAction.INSTANCE, TransportGetSnapshotsAction.class);
        // 删除快照 Action
        actions.register(DeleteSnapshotAction.INSTANCE, TransportDeleteSnapshotAction.class);
        // 创建快照 Action
        actions.register(CreateSnapshotAction.INSTANCE, TransportCreateSnapshotAction.class);
        // 克隆快照 Action
        actions.register(CloneSnapshotAction.INSTANCE, TransportCloneSnapshotAction.class);
        // 恢复快照 Action
        actions.register(RestoreSnapshotAction.INSTANCE, TransportRestoreSnapshotAction.class);
        // 快照状态 Action
        actions.register(SnapshotsStatusAction.INSTANCE, TransportSnapshotsStatusAction.class);

        // ========== 索引管理 Action ==========
        // 索引统计 Action
        actions.register(IndicesStatsAction.INSTANCE, TransportIndicesStatsAction.class);
        // 索引段 Action
        actions.register(IndicesSegmentsAction.INSTANCE, TransportIndicesSegmentsAction.class);
        // 索引分片存储 Action
        actions.register(IndicesShardStoresAction.INSTANCE, TransportIndicesShardStoresAction.class);
        // 创建索引 Action
        actions.register(CreateIndexAction.INSTANCE, TransportCreateIndexAction.class);
        // 调整索引大小（收缩/拆分/克隆）Action
        actions.register(ResizeAction.INSTANCE, TransportResizeAction.class);
        // 索引滚动 Action
        actions.register(RolloverAction.INSTANCE, TransportRolloverAction.class);
        // 删除索引 Action
        actions.register(DeleteIndexAction.INSTANCE, TransportDeleteIndexAction.class);
        // 获取索引 Action
        actions.register(GetIndexAction.INSTANCE, TransportGetIndexAction.class);
        // 打开索引 Action
        actions.register(OpenIndexAction.INSTANCE, TransportOpenIndexAction.class);
        // 关闭索引 Action
        actions.register(CloseIndexAction.INSTANCE, TransportCloseIndexAction.class);
        // 索引存在检查 Action
        actions.register(IndicesExistsAction.INSTANCE, TransportIndicesExistsAction.class);
        // 类型存在检查 Action
        actions.register(TypesExistsAction.INSTANCE, TransportTypesExistsAction.class);
        // 添加索引块 Action
        actions.register(AddIndexBlockAction.INSTANCE, TransportAddIndexBlockAction.class);
        // ========== 映射管理 Action ==========
        // 获取映射 Action
        actions.register(GetMappingsAction.INSTANCE, TransportGetMappingsAction.class);
        // 获取字段映射 Action
        actions.register(GetFieldMappingsAction.INSTANCE, TransportGetFieldMappingsAction.class,
                TransportGetFieldMappingsIndexAction.class);
        // 更新映射 Action
        actions.register(PutMappingAction.INSTANCE, TransportPutMappingAction.class);
        // 自动更新映射 Action
        actions.register(AutoPutMappingAction.INSTANCE, TransportAutoPutMappingAction.class);
        // ========== 索引别名和配置 Action ==========
        // 索引别名 Action
        actions.register(IndicesAliasesAction.INSTANCE, TransportIndicesAliasesAction.class);
        // 更新索引配置 Action
        actions.register(UpdateSettingsAction.INSTANCE, TransportUpdateSettingsAction.class);
        // 分析 Action
        actions.register(AnalyzeAction.INSTANCE, TransportAnalyzeAction.class);
        // ========== 索引模板 Action ==========
        // 创建索引模板 Action
        actions.register(PutIndexTemplateAction.INSTANCE, TransportPutIndexTemplateAction.class);
        // 获取索引模板 Action
        actions.register(GetIndexTemplatesAction.INSTANCE, TransportGetIndexTemplatesAction.class);
        // 删除索引模板 Action
        actions.register(DeleteIndexTemplateAction.INSTANCE, TransportDeleteIndexTemplateAction.class);
        // 创建组件模板 Action
        actions.register(PutComponentTemplateAction.INSTANCE, TransportPutComponentTemplateAction.class);
        // 获取组件模板 Action
        actions.register(GetComponentTemplateAction.INSTANCE, TransportGetComponentTemplateAction.class);
        // 删除组件模板 Action
        actions.register(DeleteComponentTemplateAction.INSTANCE, TransportDeleteComponentTemplateAction.class);
        // 创建组合索引模板 Action
        actions.register(PutComposableIndexTemplateAction.INSTANCE, TransportPutComposableIndexTemplateAction.class);
        // 获取组合索引模板 Action
        actions.register(GetComposableIndexTemplateAction.INSTANCE, TransportGetComposableIndexTemplateAction.class);
        // 删除组合索引模板 Action
        actions.register(DeleteComposableIndexTemplateAction.INSTANCE, TransportDeleteComposableIndexTemplateAction.class);
        // 模拟索引模板 Action
        actions.register(SimulateIndexTemplateAction.INSTANCE, TransportSimulateIndexTemplateAction.class);
        // 模拟模板 Action
        actions.register(SimulateTemplateAction.INSTANCE, TransportSimulateTemplateAction.class);
        // ========== 查询验证和索引操作 Action ==========
        // 验证查询 Action
        actions.register(ValidateQueryAction.INSTANCE, TransportValidateQueryAction.class);
        // 刷新索引 Action
        actions.register(RefreshAction.INSTANCE, TransportRefreshAction.class);
        // 刷新索引到磁盘 Action
        actions.register(FlushAction.INSTANCE, TransportFlushAction.class);
        // 同步刷新 Action
        actions.register(SyncedFlushAction.INSTANCE, TransportSyncedFlushAction.class);
        // 强制合并 Action
        actions.register(ForceMergeAction.INSTANCE, TransportForceMergeAction.class);
        // 升级 Action
        actions.register(UpgradeAction.INSTANCE, TransportUpgradeAction.class);
        // 升级状态 Action
        actions.register(UpgradeStatusAction.INSTANCE, TransportUpgradeStatusAction.class);
        // 升级配置 Action
        actions.register(UpgradeSettingsAction.INSTANCE, TransportUpgradeSettingsAction.class);
        // 清除索引缓存 Action
        actions.register(ClearIndicesCacheAction.INSTANCE, TransportClearIndicesCacheAction.class);
        // ========== 别名和配置获取 Action ==========
        // 获取别名 Action
        actions.register(GetAliasesAction.INSTANCE, TransportGetAliasesAction.class);
        // 别名存在检查 Action
        actions.register(AliasesExistAction.INSTANCE, TransportAliasesExistAction.class);
        // 获取配置 Action
        actions.register(GetSettingsAction.INSTANCE, TransportGetSettingsAction.class);

        // ========== 文档操作 Action ==========
        // 索引文档 Action
        actions.register(IndexAction.INSTANCE, TransportIndexAction.class);
        // 获取文档 Action
        actions.register(GetAction.INSTANCE, TransportGetAction.class);
        // 词向量 Action
        actions.register(TermVectorsAction.INSTANCE, TransportTermVectorsAction.class);
        // 批量词向量 Action
        actions.register(MultiTermVectorsAction.INSTANCE, TransportMultiTermVectorsAction.class,
                TransportShardMultiTermsVectorAction.class);
        // 删除文档 Action
        actions.register(DeleteAction.INSTANCE, TransportDeleteAction.class);
        // 更新文档 Action
        actions.register(UpdateAction.INSTANCE, TransportUpdateAction.class);
        // 批量获取文档 Action
        actions.register(MultiGetAction.INSTANCE, TransportMultiGetAction.class,
                TransportShardMultiGetAction.class);
        // 批量操作 Action
        actions.register(BulkAction.INSTANCE, TransportBulkAction.class,
                TransportShardBulkAction.class);
        // ========== 搜索 Action ==========
        // 搜索 Action
        actions.register(SearchAction.INSTANCE, TransportSearchAction.class);
        // 滚动搜索 Action
        actions.register(SearchScrollAction.INSTANCE, TransportSearchScrollAction.class);
        // 批量搜索 Action
        actions.register(MultiSearchAction.INSTANCE, TransportMultiSearchAction.class);
        // 解释查询 Action
        actions.register(ExplainAction.INSTANCE, TransportExplainAction.class);
        // 清除滚动上下文 Action
        actions.register(ClearScrollAction.INSTANCE, TransportClearScrollAction.class);
        // ========== 其他 Action ==========
        // 恢复 Action
        actions.register(RecoveryAction.INSTANCE, TransportRecoveryAction.class);
        // 重新加载安全配置 Action
        actions.register(NodesReloadSecureSettingsAction.INSTANCE, TransportNodesReloadSecureSettingsAction.class);
        // 自动创建索引 Action
        actions.register(AutoCreateAction.INSTANCE, AutoCreateAction.TransportAction.class);
        // 解析索引 Action
        actions.register(ResolveIndexAction.INSTANCE, ResolveIndexAction.TransportAction.class);

        // ========== 存储脚本 Action ==========
        // 创建存储脚本 Action
        actions.register(PutStoredScriptAction.INSTANCE, TransportPutStoredScriptAction.class);
        // 获取存储脚本 Action
        actions.register(GetStoredScriptAction.INSTANCE, TransportGetStoredScriptAction.class);
        // 删除存储脚本 Action
        actions.register(DeleteStoredScriptAction.INSTANCE, TransportDeleteStoredScriptAction.class);
        // 获取脚本上下文 Action
        actions.register(GetScriptContextAction.INSTANCE, TransportGetScriptContextAction.class);
        // 获取脚本语言 Action
        actions.register(GetScriptLanguageAction.INSTANCE, TransportGetScriptLanguageAction.class);

        // ========== 字段能力 Action ==========
        actions.register(FieldCapabilitiesAction.INSTANCE, TransportFieldCapabilitiesAction.class,
            TransportFieldCapabilitiesIndexAction.class);

        // ========== 摄取管道 Action ==========
        // 创建管道 Action
        actions.register(PutPipelineAction.INSTANCE, PutPipelineTransportAction.class);
        // 获取管道 Action
        actions.register(GetPipelineAction.INSTANCE, GetPipelineTransportAction.class);
        // 删除管道 Action
        actions.register(DeletePipelineAction.INSTANCE, DeletePipelineTransportAction.class);
        // 模拟管道 Action
        actions.register(SimulatePipelineAction.INSTANCE, SimulatePipelineTransportAction.class);

        // ========== 插件提供的 Action ==========
        // 注册所有插件提供的 Action
        actionPlugins.stream().flatMap(p -> p.getActions().stream()).forEach(actions::register);

        // ========== 持久化任务 Action ==========
        actions.register(StartPersistentTaskAction.INSTANCE, StartPersistentTaskAction.TransportAction.class);
        actions.register(UpdatePersistentTaskStatusAction.INSTANCE, UpdatePersistentTaskStatusAction.TransportAction.class);
        actions.register(CompletionPersistentTaskAction.INSTANCE, CompletionPersistentTaskAction.TransportAction.class);
        actions.register(RemovePersistentTaskAction.INSTANCE, RemovePersistentTaskAction.TransportAction.class);

        // ========== 保留租约 Action ==========
        actions.register(RetentionLeaseActions.Add.INSTANCE, RetentionLeaseActions.Add.TransportAction.class);
        actions.register(RetentionLeaseActions.Renew.INSTANCE, RetentionLeaseActions.Renew.TransportAction.class);
        actions.register(RetentionLeaseActions.Remove.INSTANCE, RetentionLeaseActions.Remove.TransportAction.class);

        // ========== 悬空索引 Action ==========
        actions.register(ListDanglingIndicesAction.INSTANCE, TransportListDanglingIndicesAction.class);
        actions.register(ImportDanglingIndexAction.INSTANCE, TransportImportDanglingIndexAction.class);
        actions.register(DeleteDanglingIndexAction.INSTANCE, TransportDeleteDanglingIndexAction.class);
        actions.register(FindDanglingIndexAction.INSTANCE, TransportFindDanglingIndexAction.class);

        // 返回不可修改的 Action 映射
        return unmodifiableMap(actions.getRegistry());
    }

    /**
     * 设置并初始化 Action 过滤器链
     * 
     * @param actionPlugins Action 插件列表
     * @return ActionFilters 实例，包含所有插件提供的过滤器
     */
    private ActionFilters setupActionFilters(List<ActionPlugin> actionPlugins) {
        return new ActionFilters(
            Collections.unmodifiableSet(actionPlugins.stream().flatMap(p -> p.getActionFilters().stream()).collect(Collectors.toSet())));
    }

    /**
     * 初始化并注册所有的 REST 处理器
     * 
     * 这是 ActionModule 的另一个核心方法，负责：
     * 1. 注册所有内置的 REST 处理器
     * 2. 注册插件提供的 REST 处理器
     * 3. 最后注册 CAT 主处理器
     * 
     * @param nodesInCluster 集群中节点的提供者
     */
    public void initRestHandlers(Supplier<DiscoveryNodes> nodesInCluster) {
        List<AbstractCatAction> catActions = new ArrayList<>();
        // 注册处理器的消费者，同时收集 CAT 处理器
        Consumer<RestHandler> registerHandler = handler -> {
            if (handler instanceof AbstractCatAction) {
                catActions.add((AbstractCatAction) handler);
            }
            restController.registerHandler(handler);
        };
        // ========== 节点和集群管理 REST 处理器 ==========
        registerHandler.accept(new RestAddVotingConfigExclusionAction());
        registerHandler.accept(new RestClearVotingConfigExclusionsAction());
        registerHandler.accept(new RestMainAction());
        registerHandler.accept(new RestNodesInfoAction(settingsFilter));
        registerHandler.accept(new RestRemoteClusterInfoAction());
        registerHandler.accept(new RestNodesStatsAction());
        registerHandler.accept(new RestNodesUsageAction());
        registerHandler.accept(new RestNodesHotThreadsAction());
        registerHandler.accept(new RestClusterAllocationExplainAction());
        registerHandler.accept(new RestClusterStatsAction());
        registerHandler.accept(new RestClusterStateAction(settingsFilter));
        registerHandler.accept(new RestClusterHealthAction());
        registerHandler.accept(new RestClusterUpdateSettingsAction());
        registerHandler.accept(new RestClusterGetSettingsAction(settings, clusterSettings, settingsFilter));
        registerHandler.accept(new RestClusterRerouteAction(settingsFilter));
        registerHandler.accept(new RestClusterSearchShardsAction());
        registerHandler.accept(new RestPendingClusterTasksAction());
        // ========== 快照仓库 REST 处理器 ==========
        registerHandler.accept(new RestPutRepositoryAction());
        registerHandler.accept(new RestGetRepositoriesAction(settingsFilter));
        registerHandler.accept(new RestDeleteRepositoryAction());
        registerHandler.accept(new RestVerifyRepositoryAction());
        registerHandler.accept(new RestCleanupRepositoryAction());
        // ========== 快照管理 REST 处理器 ==========
        registerHandler.accept(new RestGetSnapshotsAction());
        registerHandler.accept(new RestCreateSnapshotAction());
        registerHandler.accept(new RestCloneSnapshotAction());
        registerHandler.accept(new RestRestoreSnapshotAction());
        registerHandler.accept(new RestDeleteSnapshotAction());
        registerHandler.accept(new RestSnapshotsStatusAction());
        // ========== 索引管理 REST 处理器 ==========
        registerHandler.accept(new RestGetIndicesAction());
        registerHandler.accept(new RestIndicesStatsAction());
        registerHandler.accept(new RestIndicesSegmentsAction());
        registerHandler.accept(new RestIndicesShardStoresAction());
        registerHandler.accept(new RestGetAliasesAction());
        registerHandler.accept(new RestIndexDeleteAliasesAction());
        registerHandler.accept(new RestIndexPutAliasAction());
        registerHandler.accept(new RestIndicesAliasesAction());
        registerHandler.accept(new RestCreateIndexAction());
        registerHandler.accept(new RestResizeHandler.RestShrinkIndexAction());
        registerHandler.accept(new RestResizeHandler.RestSplitIndexAction());
        registerHandler.accept(new RestResizeHandler.RestCloneIndexAction());
        registerHandler.accept(new RestRolloverIndexAction());
        registerHandler.accept(new RestDeleteIndexAction());
        registerHandler.accept(new RestCloseIndexAction());
        registerHandler.accept(new RestOpenIndexAction());
        registerHandler.accept(new RestAddIndexBlockAction());
        registerHandler.accept(new RestUpdateSettingsAction());
        registerHandler.accept(new RestGetSettingsAction());
        registerHandler.accept(new RestAnalyzeAction());
        // ========== 索引模板 REST 处理器 ==========
        registerHandler.accept(new RestGetIndexTemplateAction());
        registerHandler.accept(new RestPutIndexTemplateAction());
        registerHandler.accept(new RestDeleteIndexTemplateAction());
        registerHandler.accept(new RestPutComponentTemplateAction());
        registerHandler.accept(new RestGetComponentTemplateAction());
        registerHandler.accept(new RestDeleteComponentTemplateAction());
        registerHandler.accept(new RestPutComposableIndexTemplateAction());
        registerHandler.accept(new RestGetComposableIndexTemplateAction());
        registerHandler.accept(new RestDeleteComposableIndexTemplateAction());
        registerHandler.accept(new RestSimulateIndexTemplateAction());
        registerHandler.accept(new RestSimulateTemplateAction());
        // ========== 映射管理 REST 处理器 ==========
        registerHandler.accept(new RestPutMappingAction());
        registerHandler.accept(new RestGetMappingAction(threadPool));
        registerHandler.accept(new RestGetFieldMappingAction());
        // ========== 索引操作 REST 处理器 ==========
        registerHandler.accept(new RestRefreshAction());
        registerHandler.accept(new RestFlushAction());
        registerHandler.accept(new RestSyncedFlushAction());
        registerHandler.accept(new RestForceMergeAction());
        registerHandler.accept(new RestUpgradeAction());
        registerHandler.accept(new RestUpgradeStatusAction());
        registerHandler.accept(new RestClearIndicesCacheAction());
        registerHandler.accept(new RestResolveIndexAction());
        // ========== 文档操作 REST 处理器 ==========
        registerHandler.accept(new RestIndexAction());
        registerHandler.accept(new CreateHandler());
        registerHandler.accept(new AutoIdHandler(nodesInCluster));
        registerHandler.accept(new RestGetAction());
        registerHandler.accept(new RestGetSourceAction());
        registerHandler.accept(new RestMultiGetAction(settings));
        registerHandler.accept(new RestDeleteAction());
        registerHandler.accept(new RestCountAction());
        registerHandler.accept(new RestTermVectorsAction());
        registerHandler.accept(new RestMultiTermVectorsAction());
        registerHandler.accept(new RestBulkAction(settings));
        registerHandler.accept(new RestUpdateAction());
        // ========== 搜索 REST 处理器 ==========
        registerHandler.accept(new RestSearchAction());
        registerHandler.accept(new RestSearchScrollAction());
        registerHandler.accept(new RestClearScrollAction());
        registerHandler.accept(new RestMultiSearchAction(settings));
        registerHandler.accept(new RestValidateQueryAction());
        registerHandler.accept(new RestExplainAction());
        // ========== 其他 REST 处理器 ==========
        registerHandler.accept(new RestRecoveryAction());
        registerHandler.accept(new RestReloadSecureSettingsAction());
        // ========== 存储脚本 API ==========
        registerHandler.accept(new RestGetStoredScriptAction());
        registerHandler.accept(new RestPutStoredScriptAction());
        registerHandler.accept(new RestDeleteStoredScriptAction());
        registerHandler.accept(new RestGetScriptContextAction());
        registerHandler.accept(new RestGetScriptLanguageAction());
        registerHandler.accept(new RestFieldCapabilitiesAction());
        // ========== 任务 API ==========
        registerHandler.accept(new RestListTasksAction(nodesInCluster));
        registerHandler.accept(new RestGetTaskAction());
        registerHandler.accept(new RestCancelTasksAction(nodesInCluster));
        // ========== 摄取管道 API ==========
        registerHandler.accept(new RestPutPipelineAction());
        registerHandler.accept(new RestGetPipelineAction());
        registerHandler.accept(new RestDeletePipelineAction());
        registerHandler.accept(new RestSimulatePipelineAction());
        // ========== 悬空索引 API ==========
        registerHandler.accept(new RestListDanglingIndicesAction());
        registerHandler.accept(new RestImportDanglingIndexAction());
        registerHandler.accept(new RestDeleteDanglingIndexAction());
        // ========== CAT API ==========
        registerHandler.accept(new RestAllocationAction());
        registerHandler.accept(new RestShardsAction());
        registerHandler.accept(new RestMasterAction());
        registerHandler.accept(new RestNodesAction());
        registerHandler.accept(new RestTasksAction(nodesInCluster));
        registerHandler.accept(new RestIndicesAction());
        registerHandler.accept(new RestSegmentsAction());
        // 完全限定以避免与 rest.action.count.RestCountAction 冲突
        registerHandler.accept(new org.elasticsearch.rest.action.cat.RestCountAction());
        // 完全限定以避免与 rest.action.indices.RestRecoveryAction 冲突
        registerHandler.accept(new RestCatRecoveryAction());
        registerHandler.accept(new RestHealthAction());
        registerHandler.accept(new org.elasticsearch.rest.action.cat.RestPendingClusterTasksAction());
        registerHandler.accept(new RestAliasAction());
        registerHandler.accept(new RestThreadPoolAction());
        registerHandler.accept(new RestPluginsAction());
        registerHandler.accept(new RestFielddataAction());
        registerHandler.accept(new RestNodeAttrsAction());
        registerHandler.accept(new RestRepositoriesAction());
        registerHandler.accept(new RestSnapshotAction());
        registerHandler.accept(new RestTemplatesAction());
        // ========== 插件提供的 REST 处理器 ==========
        // 注册所有插件提供的 REST 处理器
        for (ActionPlugin plugin : actionPlugins) {
            for (RestHandler handler : plugin.getRestHandlers(settings, restController, clusterSettings, indexScopedSettings,
                    settingsFilter, indexNameExpressionResolver, nodesInCluster)) {
                registerHandler.accept(handler);
            }
        }
        // 最后注册 CAT 主处理器，它会聚合所有 CAT 处理器
        registerHandler.accept(new RestCatAction(catActions));
    }

    /**
     * 配置依赖注入绑定
     * 
     * 这是 Guice 模块的核心方法，负责：
     * 1. 绑定 ActionFilters、DestructiveOperations 等核心组件
     * 2. 绑定请求验证器
     * 3. 绑定 AutoCreateIndex（仅非传输客户端模式）
     * 4. 绑定所有 TransportAction 为单例
     * 5. 注册 ActionType 到 TransportAction 的映射
     */
    @Override
    protected void configure() {
        // 绑定核心组件
        bind(ActionFilters.class).toInstance(actionFilters);
        bind(DestructiveOperations.class).toInstance(destructiveOperations);
        // 绑定请求验证器
        bind(new TypeLiteral<RequestValidators<PutMappingRequest>>() {}).toInstance(mappingRequestValidators);
        bind(new TypeLiteral<RequestValidators<IndicesAliasesRequest>>() {}).toInstance(indicesAliasesRequestRequestValidators);

        if (false == transportClient) {
            // 仅在非传输客户端模式下绑定的组件
            bind(AutoCreateIndex.class).toInstance(autoCreateIndex);
            bind(TransportLivenessAction.class).asEagerSingleton();

            // 注册 ActionType -> TransportAction 的映射，供 NodeClient 使用
            @SuppressWarnings("rawtypes")
            MapBinder<ActionType, TransportAction> transportActionsBinder
                    = MapBinder.newMapBinder(binder(), ActionType.class, TransportAction.class);
            for (ActionHandler<?, ?> action : actions.values()) {
                // 将 TransportAction 绑定为急切单例，以便映射绑定器可以重用它
                bind(action.getTransportAction()).asEagerSingleton();
                transportActionsBinder.addBinding(action.getAction()).to(action.getTransportAction()).asEagerSingleton();
                for (Class<?> supportAction : action.getSupportTransportActions()) {
                    bind(supportAction).asEagerSingleton();
                }
            }
        }
    }

    /**
     * 获取 Action 过滤器
     * 
     * @return ActionFilters 实例
     */
    public ActionFilters getActionFilters() {
        return actionFilters;
    }

    /**
     * 获取 REST 控制器
     * 
     * @return RestController 实例
     */
    public RestController getRestController() {
        return restController;
    }
}
