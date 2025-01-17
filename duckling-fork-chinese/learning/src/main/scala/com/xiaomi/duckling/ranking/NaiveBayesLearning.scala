/*
 * Copyright (c) 2020, Xiaomi and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xiaomi.duckling.ranking

import java.time.Duration
import java.util.{Map => JMap}

import scala.collection.mutable
import scala.collection.JavaConverters._

import org.json4s.jackson.Serialization.writePretty

import com.google.common.collect.Maps
import com.typesafe.scalalogging.LazyLogging

import com.xiaomi.duckling.Types._
import com.xiaomi.duckling.engine.Engine._
import com.xiaomi.duckling.ranking.Bayes.Classifier
import com.xiaomi.duckling.ranking.CorpusSets.{dimExamples, Corpus, Example}
import com.xiaomi.duckling.ranking.Types._
import com.xiaomi.duckling.types.Node
import com.xiaomi.duckling.JsonSerde
import com.xiaomi.duckling.JsonSerde._
import com.xiaomi.duckling.dimension.DimExamples
import com.xiaomi.duckling.ranking.NaiveBayesRank.extractFeatures

object NaiveBayesLearning extends LazyLogging {
  type Classifiers = JMap[String, Classifier]
  type Dataset = mutable.Map[String, List[Datum]]

  /**
    * Probabilistic layer
    * -- Naive Bayes classifier with Laplace smoothing
    * -- Train one classifier per rule, based on the test corpus.
    *
    * @param rules
    * @param corpus
    * @return
    */
  def makeClassifiers(rules: List[Rule], corpus: Corpus): Classifiers = {
    logger.info("training classifiers from corpus")
    val start = System.currentTimeMillis()
    val classifiers = makeDataset(rules, corpus).map {
      case (rule, listDatum) =>
        logger.debug(s"training for $rule")
        (rule, Bayes.train(listDatum))
    }
    val end = System.currentTimeMillis()
    logger.info(s"training done: elapse ${Duration.ofMillis(end - start)}")
    if (rules.length > classifiers.size) {
      val duplicateRules = rules.groupBy(_.name).filter(_._2.length > 1).keys.toList
      if (duplicateRules.nonEmpty) {
        throw new RuntimeException(s"duplicate name of rules detected, fix it $duplicateRules")
      }
    }
    Maps.newHashMap(classifiers.toMap.asJava)
  }

  /**
    * Build a dataset (rule name -> datums)
    * Dataset = Map[Text, List[Datum] ]
    * Datum = (BagOfFeatures, Class)
    * BagOfFeatures = Map[Feature, Int]
    * Feature = Text
    *
    * @param rules
    * @param corpus
    * @return Map[Text, List[Datum] ]
    */
  def makeDataset(rules: List[Rule], corpus: Corpus): Dataset = {
    val (context, options, examples) = corpus
    val ds = examples.foldLeft(mutable.Map(): Dataset)(makeDataset1(rules, context, options))
    ds
  }

  /**
    * Augment the dataset with one example.
    * -- | Add all the nodes contributing to the resolutions of the input sentence.
    * -- | Classes:
    * -- | 1) True (node contributed to a token passing test predicate)
    * -- | 2) False (node didn't contribute to any passing tokens)
    *
    * @param rules
    * @param context
    * @param options
    * @param dataset Map[rule, List[(Map[Feature, Int], Class)] ]
    * @param example
    * @return
    */
  def makeDataset1(rules: List[Rule],
                   context: Context,
                   options: Options)(dataset: Dataset, example: Example): Dataset = {
    val (doc, rv) = example
    val tokens = parseAndResolve(rules, doc, context, options)
    val (ok, ko) = tokens.partition(JsonSerde.simpleCheck(doc, _, rv))

    val nodesOK: Set[Node] = nodes(ok)
    val nodesKO = nodes(ko).diff(nodesOK)

    val ds = updateDataset(klass = false, nodesKO, updateDataset(klass = true, nodesOK, dataset))
    ds
  }

  def subnodes(node: Node): Set[Node] = {
    if (node.children.isEmpty) Set()
    else node.children.map(subnodes).reduce(_ ++ _) + node
  }

  def nodes(rTokens: List[ResolvedToken]): Set[Node] = {
    val nodes = rTokens.map(r => subnodes(r.node))
    if (nodes.nonEmpty) nodes.reduce(_ ++ _)
    else Set()
  }

  def updateDataset(klass: Class, nodes: Set[Node], dataset: Dataset): Dataset = {
    nodes.foldLeft(dataset) {
      case (ds: Dataset, node @ Node(_, _, _, rule, _, _)) =>
        rule match {
          case Some(r) =>
            val features = extractFeatures(node)
            val extracted = List((features, klass))
            if (!ds.contains(r)) ds(r) = extracted
            else ds(r) = ds(r) ++ extracted
            ds
          case None => ds
        }
    }
  }

  def main(args: Array[String]): Unit = {
    if (args.length != 0) {
      val corpus = dimExamples.values.map(d => d.corpus).reduce((a, b) => (a._1, a._2, a._3 ++ b._3))
      val classifiers = makeClassifiers(NaiveBayesRank.rules, corpus)

      val file = args(0)
      val origin = writePretty(classifiers)
      KryoSerde.makeSerializedFile(classifiers, file)
      val out = KryoSerde.loadSerializedFile(file, classOf[Classifiers])

      val after = writePretty(out)
      assert(origin == after)
    }
  }
}
