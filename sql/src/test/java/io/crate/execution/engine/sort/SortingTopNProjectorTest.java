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

package io.crate.execution.engine.sort;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import io.crate.analyze.symbol.Literal;
import io.crate.data.Bucket;
import io.crate.data.Input;
import io.crate.data.Projector;
import io.crate.data.Row;
import io.crate.execution.engine.collect.CollectExpression;
import io.crate.execution.engine.collect.InputCollectExpression;
import io.crate.execution.engine.pipeline.TopN;
import io.crate.test.integration.CrateUnitTest;
import io.crate.testing.TestingBatchIterators;
import io.crate.testing.TestingRowConsumer;
import org.junit.Test;

import java.util.List;

import static io.crate.testing.TestingHelpers.isRow;
import static org.hamcrest.core.Is.is;

public class SortingTopNProjectorTest extends CrateUnitTest {

    private static final InputCollectExpression INPUT = new InputCollectExpression(0);
    private static final Literal<Boolean> TRUE_LITERAL = Literal.of(true);
    private static final List<Input<?>> INPUT_LITERAL_LIST = ImmutableList.of(INPUT, TRUE_LITERAL);
    private static final List<CollectExpression<Row, ?>> COLLECT_EXPRESSIONS = ImmutableList.<CollectExpression<Row, ?>>of(INPUT);
    private static final Ordering<Object[]> FIRST_CELL_ORDERING = OrderingByPosition.arrayOrdering(0, false, null);

    private TestingRowConsumer consumer = new TestingRowConsumer();

    private Projector getProjector(int numOutputs, int limit, int offset, Ordering<Object[]> ordering) {
        return new SortingTopNProjector(
            INPUT_LITERAL_LIST,
            COLLECT_EXPRESSIONS,
            numOutputs,
            ordering,
            limit,
            offset
        );
    }

    private Projector getProjector(int numOutputs, int limit, int offset) {
        return getProjector(numOutputs, limit, offset, FIRST_CELL_ORDERING);
    }

    @Test
    public void testOrderBy() throws Exception {
        Projector projector = getProjector(1, 3, 5);
        consumer.accept(projector.apply(TestingBatchIterators.range(1, 11)), null);

        Bucket rows = consumer.getBucket();
        assertThat(rows.size(), is(3));

        int iterateLength = 0;
        for (Row row : rows) {
            assertThat(row, isRow(iterateLength + 6));
            iterateLength++;
        }
        assertThat(iterateLength, is(3));
    }

    @Test
    public void testOrderByWithoutOffset() throws Exception {
        Projector projector = getProjector(2, 10, TopN.NO_OFFSET);
        consumer.accept(projector.apply(TestingBatchIterators.range(1, 11)), null);

        Bucket rows = consumer.getBucket();
        assertThat(rows.size(), is(10));
        int iterateLength = 0;
        for (Row row : consumer.getBucket()) {
            assertThat(row, isRow(iterateLength + 1, true));
            iterateLength++;
        }
        assertThat(iterateLength, is(10));
    }

    @Test
    public void testWithHighOffset() throws Exception {
        Projector projector = getProjector(2, 2, 30);
        consumer.accept(projector.apply(TestingBatchIterators.range(1, 10)), null);
        assertThat(consumer.getBucket().size(), is(0));
    }

    @Test
    public void testInvalidNegativeLimit() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Invalid LIMIT: value must be > 0; got: -1");

        new SortingTopNProjector(INPUT_LITERAL_LIST, COLLECT_EXPRESSIONS, 2, FIRST_CELL_ORDERING, -1, 0);
    }

    @Test
    public void testInvalidZeroLimit() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Invalid LIMIT: value must be > 0; got: 0");

        new SortingTopNProjector(INPUT_LITERAL_LIST, COLLECT_EXPRESSIONS, 2, FIRST_CELL_ORDERING, 0, 0);
    }

    @Test
    public void testInvalidOffset() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Invalid OFFSET: value must be >= 0; got: -1");

        new SortingTopNProjector(INPUT_LITERAL_LIST, COLLECT_EXPRESSIONS, 2, FIRST_CELL_ORDERING, 1, -1);
    }

    @Test
    public void testInvalidMaxSize() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Invalid LIMIT + OFFSET: value must be <= 2147483630; got: 2147483646");

        int i = Integer.MAX_VALUE / 2;
        new SortingTopNProjector(INPUT_LITERAL_LIST, COLLECT_EXPRESSIONS, 2, FIRST_CELL_ORDERING, i, i);
    }

    @Test
    public void testInvalidMaxSizeExceedsIntegerRange() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Invalid LIMIT + OFFSET: value must be <= 2147483630; got: -2147483648");

        int i = Integer.MAX_VALUE / 2 + 1;
        new SortingTopNProjector(INPUT_LITERAL_LIST, COLLECT_EXPRESSIONS, 2, FIRST_CELL_ORDERING, i, i);
    }
}
