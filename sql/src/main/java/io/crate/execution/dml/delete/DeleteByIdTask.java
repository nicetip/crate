/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.execution.dml.delete;

import io.crate.analyze.symbol.SelectSymbol;
import io.crate.data.Row;
import io.crate.data.RowConsumer;
import io.crate.execution.dml.ShardRequestExecutor;
import io.crate.metadata.Functions;
import io.crate.execution.engine.indexing.ShardingUpsertExecutor;
import io.crate.planner.node.dml.DeleteById;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.shard.ShardId;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DeleteByIdTask {

    private final ShardRequestExecutor<ShardDeleteRequest> executor;

    public DeleteByIdTask(UUID jobId,
                          ClusterService clusterService,
                          Functions functions,
                          TransportShardDeleteAction deleteAction,
                          DeleteById deleteById) {
        TimeValue requestTimeout = ShardingUpsertExecutor.BULK_REQUEST_TIMEOUT_SETTING
            .setting().get(clusterService.state().metaData().settings());
        DeleteRequests deleteRequests = new DeleteRequests(jobId, requestTimeout);
        executor = new ShardRequestExecutor<>(
            clusterService,
            functions,
            deleteById.table(),
            deleteRequests,
            deleteAction::execute,
            deleteById.docKeys()
        );
    }

    public void execute(final RowConsumer consumer, Row parameters, Map<SelectSymbol, Object> valuesBySubQuery) {
        executor.execute(consumer, parameters, valuesBySubQuery);
    }

    public final List<CompletableFuture<Long>> executeBulk(List<Row> bulkParams, Map<SelectSymbol, Object> valuesBySubQuery) {
        return executor.executeBulk(bulkParams, valuesBySubQuery);
    }


    static class DeleteRequests implements ShardRequestExecutor.RequestGrouper<ShardDeleteRequest> {

        private final UUID jobId;
        private final TimeValue requestTimeout;

        DeleteRequests(UUID jobId, TimeValue requestTimeout) {
            this.jobId = jobId;
            this.requestTimeout = requestTimeout;
        }

        @Override
        public ShardDeleteRequest newRequest(ShardId shardId, String routing) {
            ShardDeleteRequest request = new ShardDeleteRequest(shardId, routing, jobId);
            request.timeout(requestTimeout);
            return request;
        }

        @Override
        public void bind(Row parameters, Map<SelectSymbol, Object> valuesBySubQuery) {
        }

        @Override
        public void addItem(ShardDeleteRequest request, int location, String id, Long version) {
            ShardDeleteRequest.Item item = new ShardDeleteRequest.Item(id);
            if (version != null) {
                item.version(version);
            }
            request.add(location, item);
        }
    }
}
