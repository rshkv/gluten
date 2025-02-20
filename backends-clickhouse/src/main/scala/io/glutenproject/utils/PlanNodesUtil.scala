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
package io.glutenproject.utils

import io.glutenproject.expression.{ConverterUtils, ExpressionConverter}
import io.glutenproject.substrait.`type`.TypeNode
import io.glutenproject.substrait.SubstraitContext
import io.glutenproject.substrait.expression.ExpressionNode
import io.glutenproject.substrait.plan.{PlanBuilder, PlanNode}
import io.glutenproject.substrait.rel.{LocalFilesBuilder, RelBuilder}

import org.apache.spark.sql.catalyst.expressions.{Attribute, AttributeReference, BoundReference, Expression}

import com.google.common.collect.Lists

import java.util

object PlanNodesUtil {

  def genProjectionsPlanNode(key: Expression, output: Seq[Attribute]): PlanNode = {
    val context = new SubstraitContext

    // input
    val iteratorIndex: Long = context.nextIteratorIndex
    var operatorId = context.nextOperatorId("ClickHouseBuildSideRelationReadIter")
    val inputIter = LocalFilesBuilder.makeLocalFiles(
      ConverterUtils.ITERATOR_PREFIX.concat(iteratorIndex.toString))
    context.setIteratorNode(iteratorIndex, inputIter)

    val typeList = new util.ArrayList[TypeNode]()
    val nameList = new util.ArrayList[String]()
    for (attr <- output) {
      typeList.add(ConverterUtils.getTypeNode(attr.dataType, attr.nullable))
      nameList.add(ConverterUtils.genColumnNameWithExprId(attr))
    }
    val readRel =
      RelBuilder.makeReadRel(typeList, nameList, null, iteratorIndex, context, operatorId)

    // replace attribute to BoundRefernce according to the output
    val newBoundRefKey = key.transformDown {
      case expression: AttributeReference =>
        val columnInOutput = output.zipWithIndex.filter {
          p: (Attribute, Int) => p._1.exprId == expression.exprId || p._1.name == expression.name
        }
        if (columnInOutput.isEmpty) {
          throw new IllegalStateException(
            s"Key $expression not found from build side relation output: $output")
        }
        if (columnInOutput.size != 1) {
          throw new IllegalStateException(
            s"More than one key $expression found from build side relation output: $output")
        }
        val boundReference = columnInOutput.head
        BoundReference(boundReference._2, boundReference._1.dataType, boundReference._1.nullable)
      case other => other
    }

    // project
    operatorId = context.nextOperatorId("ClickHouseBuildSideRelationProjection")
    val args = context.registeredFunction

    val columnarProjExpr = ExpressionConverter
      .replaceWithExpressionTransformer(newBoundRefKey, attributeSeq = output)

    val projExprNodeList = new java.util.ArrayList[ExpressionNode]()
    projExprNodeList.add(columnarProjExpr.doTransform(args))

    PlanBuilder.makePlan(
      context,
      Lists.newArrayList(
        RelBuilder.makeProjectRel(readRel, projExprNodeList, context, operatorId, output.size)),
      Lists.newArrayList(
        ConverterUtils.genColumnNameWithExprId(ConverterUtils.getAttrFromExpr(key)))
    )
  }
}
