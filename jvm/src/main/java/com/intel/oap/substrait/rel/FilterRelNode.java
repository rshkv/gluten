/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intel.oap.substrait.rel;

import com.intel.oap.substrait.expression.ExpressionNode;
import io.substrait.proto.FilterRel;
import io.substrait.proto.Rel;
import io.substrait.proto.RelCommon;

import java.io.Serializable;

public class FilterRelNode implements RelNode, Serializable {
    private final RelNode input;
    private final ExpressionNode condition;

    FilterRelNode(RelNode input,
                  ExpressionNode condition) {
        this.input = input;
        this.condition = condition;
    }

    @Override
    public Rel toProtobuf() {
        RelCommon.Builder relCommonBuilder = RelCommon.newBuilder();
        relCommonBuilder.setDirect(RelCommon.Direct.newBuilder());

        FilterRel.Builder filterBuilder = FilterRel.newBuilder();
        filterBuilder.setCommon(relCommonBuilder.build());
        if (input != null) {
            filterBuilder.setInput(input.toProtobuf());
        }
        filterBuilder.setCondition(condition.toProtobuf());
        Rel.Builder builder = Rel.newBuilder();
        builder.setFilter(filterBuilder.build());
        return builder.build();
    }
}
