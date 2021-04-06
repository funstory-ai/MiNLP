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

package duckling.dimension.age

import duckling.Types._
import duckling.dimension.matcher.GroupMatch
import duckling.dimension.numeral._
import duckling.dimension.numeral.Predicates._
import duckling.dimension.time.enums.IntervalDirection._
import duckling.dimension.time.enums.IntervalType.Closed
import duckling.dimension.DimRules
import duckling.dimension.implicits._

trait Rules extends DimRules {
  val concrete = Rule(
    name = "<number> concrete age",
    pattern = List(isDimension(Numeral).predicate, "岁半?".regex),
    prod = {
      case t1 :: Token(_, GroupMatch(s :: _)) :: _ =>
        for (i <- getIntValue(t1)) yield {
          val n = if (s.endsWith("半")) i + 0.5 else i
          token(NumeralValue(n))
        }
    }
  )

  // TODO 如果有需要，细化区间左的开闭
  val greater = Rule(
    name = "greater than <age>",
    pattern = List("(大于|高于|超过|不低于)".regex, isConcreteAge.predicate),
    prod = {
      case Token(_, GroupMatch(_ :: _)) :: Token(_, AgeData(NumeralValue(n, _))) :: _ =>
        token(OpenIntervalValue(n, After))
    }
  )

  // TODO 如果有需要，细化区间右的开闭
  val less = Rule(
    name = "less than <age>",
    pattern = List("(小于|低于|不超过|不高于)".regex, isConcreteAge.predicate),
    prod = {
      case _ :: Token(_, AgeData(NumeralValue(n, _))) :: _ =>
        token(OpenIntervalValue(n, Before))
    }
  )

  val ageGreaterOrLess = Rule(
    name = "<age> up/down",
    pattern = List(isConcreteAge.predicate, "以(上|下)".regex),
    prod = {
      case Token(_, AgeData(NumeralValue(n, _))) :: Token(_, GroupMatch(_ :: s :: _)) :: _ =>
        val direction = if (s == "上") After else Before
        token(OpenIntervalValue(n, direction))
    }
  )

  val ageToAge = Rule(
    name = "<age> to <age>",
    pattern = List(or(isConcreteAge, isPositive).predicate, "(到|至)".regex, isConcreteAge.predicate),
    prod = {
      case Token(_, data) :: _ :: Token(_, AgeData(NumeralValue(n2, _))) :: _ =>
        val n1 = data match {
          case AgeData(NumeralValue(n, _))       => n
          case NumeralData(value, _, _, _, _, _) => value
        }
        val interval = DoubleSideIntervalValue(n1, n2, rightType = Closed)
        token(interval)
    }
  )
}
