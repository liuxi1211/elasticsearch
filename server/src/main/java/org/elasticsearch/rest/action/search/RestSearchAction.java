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

package org.elasticsearch.rest.action.search;

import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchContextId;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.Booleans;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.logging.DeprecationLogger;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestActions;
import org.elasticsearch.rest.action.RestCancellableNodeClient;
import org.elasticsearch.rest.action.RestStatusToXContentListener;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.StoredFieldsContext;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.term.TermSuggestionBuilder.SuggestMode;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.elasticsearch.action.ValidateActions.addValidationError;
import static org.elasticsearch.common.unit.TimeValue.parseTimeValue;
import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.search.suggest.SuggestBuilders.termSuggestion;

/**
 * REST 搜索操作处理器
 * 
 * 这是 Elasticsearch 搜索 API 的 REST 层入口，负责：
 * 1. 定义搜索 API 的 REST 路由（如 GET/POST /_search）
 * 2. 解析 HTTP 请求参数和请求体
 * 3. 构建 SearchRequest 对象
 * 4. 调用内部的 SearchAction 执行实际搜索逻辑
 * 
 * 完整调用流程：
 * HTTP Request → RestController → RestSearchAction → SearchAction → TransportSearchAction → 各分片执行 → 合并结果 → 返回响应
 */
public class RestSearchAction extends BaseRestHandler {
    /**
     * 指示 hits.total 在 REST 搜索响应中应该呈现为整数还是对象
     * 当设置为 true 时，total_hits 以整数形式返回；否则以对象形式返回（包含 value 和 relation）
     */
    public static final String TOTAL_HITS_AS_INT_PARAM = "rest_total_hits_as_int";
    /** 是否使用带类型的键名返回聚合、建议器等结果 */
    public static final String TYPED_KEYS_PARAM = "typed_keys";
    private static final Set<String> RESPONSE_PARAMS;

    static {
        final Set<String> responseParams = new HashSet<>(Arrays.asList(TYPED_KEYS_PARAM, TOTAL_HITS_AS_INT_PARAM));
        RESPONSE_PARAMS = Collections.unmodifiableSet(responseParams);
    }

    private static final DeprecationLogger deprecationLogger = DeprecationLogger.getLogger(RestSearchAction.class);
    /** 类型（type）相关的弃用警告消息 */
    public static final String TYPES_DEPRECATION_MESSAGE = "[types removal]" +
        " Specifying types in search requests is deprecated.";

    /**
     * 获取此处理器的名称标识
     * @return 处理器名称 "search_action"
     */
    @Override
    public String getName() {
        return "search_action";
    }

    /**
     * 定义此处理器支持的 REST 路由
     * 支持以下路径：
     * - GET/POST /_search （在所有索引上搜索）
     * - GET/POST /{index}/_search （在指定索引上搜索）
     * - GET/POST /{index}/{type}/_search （已弃用，在指定索引和类型上搜索）
     * 
     * @return 路由列表
     */
    @Override
    public List<Route> routes() {
        return unmodifiableList(asList(
            new Route(GET, "/_search"),
            new Route(POST, "/_search"),
            new Route(GET, "/{index}/_search"),
            new Route(POST, "/{index}/_search"),
            // Deprecated typed endpoints.
            new Route(GET, "/{index}/{type}/_search"),
            new Route(POST, "/{index}/{type}/_search")));
    }

    /**
     * 准备搜索请求的核心方法
     * 
     * 此方法执行以下关键步骤：
     * 1. 创建 SearchRequest 对象
     * 2. 解析 URL 参数和请求体，填充 SearchRequest
     * 3. 返回一个 RestChannelConsumer，在实际发送响应时执行搜索
     * 
     * 注意：这里使用了 IntConsumer 来延迟设置 size，因为 _update_by_query 和 _delete_by_query
     * 也使用相同的解析路径，但对 size 参数的处理方式不同。
     * 
     * @param request HTTP 请求对象，包含查询参数和请求体
     * @param client 节点客户端，用于执行内部 Action
     * @return RestChannelConsumer，在调用时执行搜索并发送响应
     * @throws IOException 解析请求时可能发生 IO 异常
     */
    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        // 创建空的搜索请求对象
        SearchRequest searchRequest = new SearchRequest();
        /*
         * We have to pull out the call to `source().size(size)` because
         * _update_by_query and _delete_by_query uses this same parsing
         * path but sets a different variable when it sees the `size`
         * url parameter.
         *
         * Note that we can't use `searchRequest.source()::size` because
         * `searchRequest.source()` is null right now. We don't have to
         * guard against it being null in the IntConsumer because it can't
         * be null later. If that is confusing to you then you are in good
         * company.
         */
        // 创建一个消费者，用于后续设置搜索结果的大小（size）
        IntConsumer setSize = size -> searchRequest.source().size(size);
        // 解析请求内容或源参数，填充 searchRequest 对象
        request.withContentOrSourceParamParserOrNull(parser ->
            parseSearchRequest(searchRequest, request, parser, client.getNamedWriteableRegistry(), setSize));

        // 返回一个 lambda，当被调用时执行搜索操作
        return channel -> {
            // 创建可取消的客户端，支持 HTTP 连接断开时取消搜索
            RestCancellableNodeClient cancelClient = new RestCancellableNodeClient(client, request.getHttpChannel());
            // 执行搜索 Action：SearchAction.INSTANCE
            // 这会触发 TransportSearchAction 的执行
            cancelClient.execute(SearchAction.INSTANCE, searchRequest, new RestStatusToXContentListener<>(channel));
        };
    }

    /**
     * 解析 REST 请求并填充 SearchRequest 对象
     * 
     * 此方法是搜索请求解析的核心逻辑，负责将 HTTP 请求的各种参数转换为 SearchRequest 的内部表示。
     * 它会保留 SearchRequest 中已有的值，只覆盖请求中明确指定的参数。
     * 
     * 主要解析内容包括：
     * 1. 索引名称（从 URL 参数）
     * 2. 请求体中的查询 DSL（JSON/XContent）
     * 3. 各种 URL 查询参数（from, size, sort, timeout 等）
     * 4. 滚动搜索参数（scroll）
     * 5. 路由和偏好设置
     * 6. 跨集群搜索相关参数
     * 
     * @param searchRequest 要填充的搜索请求对象
     * @param request HTTP 请求对象
     * @param requestContentParser 请求体的内容解析器（可能为 null，如果请求体为空）
     * @param namedWriteableRegistry 命名可写对象注册表，用于反序列化
     * @param setSize size 参数的设置消费者，允许不同的处理方式
     * @throws IOException 解析过程中可能发生 IO 异常
     */
    public static void parseSearchRequest(SearchRequest searchRequest, RestRequest request,
                                          XContentParser requestContentParser,
                                          NamedWriteableRegistry namedWriteableRegistry,
                                          IntConsumer setSize) throws IOException {

        // 确保 SearchSourceBuilder 已初始化
        if (searchRequest.source() == null) {
            searchRequest.source(new SearchSourceBuilder());
        }
        // 从 URL 参数中解析索引名称（支持逗号分隔的多个索引）
        searchRequest.indices(Strings.splitStringByCommaToArray(request.param("index")));
        // 如果请求体不为空，解析查询 DSL
        if (requestContentParser != null) {
            searchRequest.source().parseXContent(requestContentParser, true);
        }

        // 设置批量归约大小（用于优化跨集群搜索的性能）
        final int batchedReduceSize = request.paramAsInt("batched_reduce_size", searchRequest.getBatchedReduceSize());
        searchRequest.setBatchedReduceSize(batchedReduceSize);
        // 设置预过滤分片大小（控制并行度）
        if (request.hasParam("pre_filter_shard_size")) {
            searchRequest.setPreFilterShardSize(request.paramAsInt("pre_filter_shard_size", SearchRequest.DEFAULT_PRE_FILTER_SHARD_SIZE));
        }

        // 设置最大并发分片请求数（根据集群节点数自动调整）
        if (request.hasParam("max_concurrent_shard_requests")) {
            // only set if we have the parameter since we auto adjust the max concurrency on the coordinator
            // based on the number of nodes in the cluster
            final int maxConcurrentShardRequests = request.paramAsInt("max_concurrent_shard_requests",
                searchRequest.getMaxConcurrentShardRequests());
            searchRequest.setMaxConcurrentShardRequests(maxConcurrentShardRequests);
        }

        // 设置是否允许部分搜索结果（某些分片失败时仍返回部分结果）
        if (request.hasParam("allow_partial_search_results")) {
            // only set if we have the parameter passed to override the cluster-level default
            searchRequest.allowPartialSearchResults(request.paramAsBoolean("allow_partial_search_results", null));
        }

        // 不允许从 REST 层使用 'query_and_fetch' 或 'dfs_query_and_fetch' 搜索类型
        // 这些模式是内部优化，不应由用户显式指定
        String searchType = request.param("search_type");
        if ("query_and_fetch".equals(searchType) ||
                "dfs_query_and_fetch".equals(searchType)) {
            throw new IllegalArgumentException("Unsupported search type [" + searchType + "]");
        } else {
            searchRequest.searchType(searchType);
        }
        // 解析搜索源的其他参数（from, size, sort, timeout 等）
        parseSearchSource(searchRequest.source(), request, setSize);
        // 设置是否使用请求缓存
        searchRequest.requestCache(request.paramAsBoolean("request_cache", searchRequest.requestCache()));

        // 处理滚动搜索参数
        String scroll = request.param("scroll");
        if (scroll != null) {
            searchRequest.scroll(new Scroll(parseTimeValue(scroll, null, "scroll")));
        }

        // 处理已弃用的类型参数
        if (request.hasParam("type")) {
            deprecationLogger.deprecate("search_with_types", TYPES_DEPRECATION_MESSAGE);
            searchRequest.types(Strings.splitStringByCommaToArray(request.param("type")));
        }
        // 设置路由值和偏好设置
        searchRequest.routing(request.param("routing"));
        searchRequest.preference(request.param("preference"));
        // 设置索引选项（如何处理不存在的索引、通配符等）
        searchRequest.indicesOptions(IndicesOptions.fromRequest(request, searchRequest.indicesOptions()));

        // 检查并处理 total_hits 的显示格式
        checkRestTotalHits(request, searchRequest);

        // 处理时间点（Point-in-Time）搜索或跨集群搜索最小化往返
        if (searchRequest.pointInTimeBuilder() != null) {
            preparePointInTime(searchRequest, request, namedWriteableRegistry);
        } else {
            searchRequest.setCcsMinimizeRoundtrips(
                request.paramAsBoolean("ccs_minimize_roundtrips", searchRequest.isCcsMinimizeRoundtrips()));
        }
    }

    /**
     * 解析 SearchSourceBuilder 的各种参数
     * 
     * 此方法处理所有与搜索源相关的 URL 参数，包括：
     * - 查询参数（q 参数）
     * - 分页参数（from, size）
     * - 解释和版本信息（explain, version, seq_no_primary_term）
     * - 超时和终止条件（timeout, terminate_after）
     * - 字段选择（stored_fields, docvalue_fields, _source）
     * - 排序参数（sort）
     * - 统计和跟踪参数（stats, track_scores, track_total_hits）
     * - 建议器参数（suggest_field, suggest_text 等）
     * 
     * @param searchSourceBuilder 要填充的搜索源构建器
     * @param request HTTP 请求对象
     * @param setSize size 参数的设置消费者
     */
    private static void parseSearchSource(final SearchSourceBuilder searchSourceBuilder, RestRequest request, IntConsumer setSize) {
        QueryBuilder queryBuilder = RestActions.urlParamsToQueryBuilder(request);
        if (queryBuilder != null) {
            searchSourceBuilder.query(queryBuilder);
        }

        int from = request.paramAsInt("from", -1);
        if (from != -1) {
            searchSourceBuilder.from(from);
        }
        int size = request.paramAsInt("size", -1);
        if (size != -1) {
            setSize.accept(size);
        }

        if (request.hasParam("explain")) {
            searchSourceBuilder.explain(request.paramAsBoolean("explain", null));
        }
        if (request.hasParam("version")) {
            searchSourceBuilder.version(request.paramAsBoolean("version", null));
        }
        if (request.hasParam("seq_no_primary_term")) {
            searchSourceBuilder.seqNoAndPrimaryTerm(request.paramAsBoolean("seq_no_primary_term", null));
        }
        if (request.hasParam("timeout")) {
            searchSourceBuilder.timeout(request.paramAsTime("timeout", null));
        }
        if (request.hasParam("terminate_after")) {
            int terminateAfter = request.paramAsInt("terminate_after",
                    SearchContext.DEFAULT_TERMINATE_AFTER);
            if (terminateAfter < 0) {
                throw new IllegalArgumentException("terminateAfter must be > 0");
            } else if (terminateAfter > 0) {
                searchSourceBuilder.terminateAfter(terminateAfter);
            }
        }

        StoredFieldsContext storedFieldsContext =
            StoredFieldsContext.fromRestRequest(SearchSourceBuilder.STORED_FIELDS_FIELD.getPreferredName(), request);
        if (storedFieldsContext != null) {
            searchSourceBuilder.storedFields(storedFieldsContext);
        }
        String sDocValueFields = request.param("docvalue_fields");
        if (sDocValueFields != null) {
            if (Strings.hasText(sDocValueFields)) {
                String[] sFields = Strings.splitStringByCommaToArray(sDocValueFields);
                for (String field : sFields) {
                    searchSourceBuilder.docValueField(field, null);
                }
            }
        }
        FetchSourceContext fetchSourceContext = FetchSourceContext.parseFromRestRequest(request);
        if (fetchSourceContext != null) {
            searchSourceBuilder.fetchSource(fetchSourceContext);
        }

        if (request.hasParam("track_scores")) {
            searchSourceBuilder.trackScores(request.paramAsBoolean("track_scores", false));
        }


        if (request.hasParam("track_total_hits")) {
            if (Booleans.isBoolean(request.param("track_total_hits"))) {
                searchSourceBuilder.trackTotalHits(
                    request.paramAsBoolean("track_total_hits", true)
                );
            } else {
                searchSourceBuilder.trackTotalHitsUpTo(
                    request.paramAsInt("track_total_hits", SearchContext.DEFAULT_TRACK_TOTAL_HITS_UP_TO)
                );
            }
        }

        String sSorts = request.param("sort");
        if (sSorts != null) {
            String[] sorts = Strings.splitStringByCommaToArray(sSorts);
            for (String sort : sorts) {
                int delimiter = sort.lastIndexOf(":");
                if (delimiter != -1) {
                    String sortField = sort.substring(0, delimiter);
                    String reverse = sort.substring(delimiter + 1);
                    if ("asc".equals(reverse)) {
                        searchSourceBuilder.sort(sortField, SortOrder.ASC);
                    } else if ("desc".equals(reverse)) {
                        searchSourceBuilder.sort(sortField, SortOrder.DESC);
                    }
                } else {
                    searchSourceBuilder.sort(sort);
                }
            }
        }

        String sStats = request.param("stats");
        if (sStats != null) {
            searchSourceBuilder.stats(Arrays.asList(Strings.splitStringByCommaToArray(sStats)));
        }

        String suggestField = request.param("suggest_field");
        if (suggestField != null) {
            String suggestText = request.param("suggest_text", request.param("q"));
            int suggestSize = request.paramAsInt("suggest_size", 5);
            String suggestMode = request.param("suggest_mode");
            searchSourceBuilder.suggest(new SuggestBuilder().addSuggestion(suggestField,
                    termSuggestion(suggestField)
                        .text(suggestText).size(suggestSize)
                        .suggestMode(SuggestMode.resolve(suggestMode))));
        }
    }

    static void preparePointInTime(SearchRequest request, RestRequest restRequest, NamedWriteableRegistry namedWriteableRegistry) {
        assert request.pointInTimeBuilder() != null;
        ActionRequestValidationException validationException = null;
        if (request.indices().length > 0) {
            validationException = addValidationError("[indices] cannot be used with point in time", validationException);
        }
        if (request.indicesOptions() != SearchRequest.DEFAULT_INDICES_OPTIONS) {
            validationException = addValidationError("[indicesOptions] cannot be used with point in time", validationException);
        }
        if (request.routing() != null) {
            validationException = addValidationError("[routing] cannot be used with point in time", validationException);
        }
        if (request.preference() != null) {
            validationException = addValidationError("[preference] cannot be used with point in time", validationException);
        }
        if (restRequest.paramAsBoolean("ccs_minimize_roundtrips", false)) {
            validationException =
                addValidationError("[ccs_minimize_roundtrips] cannot be used with point in time", validationException);
            request.setCcsMinimizeRoundtrips(false);
        }
        ExceptionsHelper.reThrowIfNotNull(validationException);

        final IndicesOptions indicesOptions = request.indicesOptions();
        final IndicesOptions stricterIndicesOptions = IndicesOptions.fromOptions(
            indicesOptions.ignoreUnavailable(), indicesOptions.allowNoIndices(), false, false, false,
            true, true, indicesOptions.ignoreThrottled());
        request.indicesOptions(stricterIndicesOptions);
        final SearchContextId searchContextId = SearchContextId.decode(namedWriteableRegistry, request.pointInTimeBuilder().getId());
        request.indices(searchContextId.getActualIndices());
    }

    /**
     * Modify the search request to accurately count the total hits that match the query
     * if {@link #TOTAL_HITS_AS_INT_PARAM} is set.
     *
     * @throws IllegalArgumentException if {@link #TOTAL_HITS_AS_INT_PARAM}
     * is used in conjunction with a lower bound value (other than {@link SearchContext#DEFAULT_TRACK_TOTAL_HITS_UP_TO})
     * for the track_total_hits option.
     */
    public static void checkRestTotalHits(RestRequest restRequest, SearchRequest searchRequest) {
        boolean totalHitsAsInt = restRequest.paramAsBoolean(TOTAL_HITS_AS_INT_PARAM, false);
        if (totalHitsAsInt == false) {
            return;
        }
        if (searchRequest.source() == null) {
            searchRequest.source(new SearchSourceBuilder());
        }
        Integer trackTotalHitsUpTo = searchRequest.source().trackTotalHitsUpTo();
        if (trackTotalHitsUpTo == null) {
            searchRequest.source().trackTotalHits(true);
        } else if (trackTotalHitsUpTo != SearchContext.TRACK_TOTAL_HITS_ACCURATE
                && trackTotalHitsUpTo != SearchContext.TRACK_TOTAL_HITS_DISABLED) {
            throw new IllegalArgumentException("[" + TOTAL_HITS_AS_INT_PARAM + "] cannot be used " +
                "if the tracking of total hits is not accurate, got " + trackTotalHitsUpTo);
        }
    }

    @Override
    protected Set<String> responseParams() {
        return RESPONSE_PARAMS;
    }

    @Override
    public boolean allowsUnsafeBuffers() {
        return true;
    }
}
