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

package com.xiaomi.duckling.dimension.numeral.fraction

import com.xiaomi.duckling.Types._
import com.xiaomi.duckling.dimension.DimRules
import com.xiaomi.duckling.dimension.implicits._
import com.xiaomi.duckling.dimension.matcher.{GroupMatch, RegexMatch}
import com.xiaomi.duckling.dimension.numeral.Predicates._
import com.xiaomi.duckling.dimension.numeral.{Numeral, NumeralData}

trait Rules extends DimRules {
  val fenZhi = Rule(
    name = "<number> 分之 <number>",
    pattern = List(isDimension(Numeral).predicate, "分之".regex, isPositive.predicate),
    prod = tokens {
      case Token(_, n1: NumeralData) :: _ :: Token(_, n2: NumeralData) :: _ =>
        val v = if (n1.value == 0) 0 else n2.value / n1.value
        fraction(v, n2.value, n1.value)
    }
  )

  val slashSymbol = Rule(
    name = "<number> / <number>",
    pattern = List(isDimension(Numeral).predicate, "/".regex, isDimension(Numeral).predicate),
    prod = tokens {
      case Token(_, n1: NumeralData) :: _ :: Token(_, n2: NumeralData) :: _ =>
        val v = if (n2.value == 0) 0 else n1.value / n2.value
        fraction(v, n1.value, n2.value)
    }
  )

  val percentLike = Rule(
    name = "|thousand|th of <number>",
    pattern = List("(负的|负)?(百|千|万)分之".regex, isDimension(Numeral).predicate),
    prod = tokens {
      case Token(_, GroupMatch(_ :: s1 :: s2 :: _)) :: Token(_, n: NumeralData) :: _ =>
        val neg = s1.nonEmpty
        val scalar = s2 match {
          case "百" => if (neg) -100.0 else 100.0
          case "千" => if (neg) -1000.0 else 1000.0
          case "万" => if (neg) -10000.0 else 10000.0
        }
        fraction(n.value / scalar, n.value, scalar)
    }
  )

  val percentSymbol = Rule(
    name = "<number> percent symbol",
    pattern = List(isDimension(Numeral).predicate, "(%|‰)".regex),
    prod = tokens {
      case Token(_, n: NumeralData) :: Token(_, GroupMatch(s :: _)) :: _ =>
        val scalar = s match {
          case "%" => 0.01
          case "‰" => 0.001
        }
        fraction(n.value * scalar, n.value, 1/scalar)
    }
  )

  val oneHalf =
    Rule(name = "<1> half", pattern = List(isIntegerBetween(1, 1).predicate, "半".regex), prod = tokens {
      case _ => fraction(0.5, 50.0, 100.0)
    })

  val hundredPercentRule = Rule(
    name = "100%",
    pattern = List("(负的|负)?百分之?百".regex),
    prod = tokens {
      case Token(RegexMatch, GroupMatch(_ :: m :: _)) :: _ =>
        val scalar = if (m.nonEmpty) -100.0 else 100.0
        val value = 100.0 / scalar
        fraction(value, 100.0, scalar)
    }
  )
}
