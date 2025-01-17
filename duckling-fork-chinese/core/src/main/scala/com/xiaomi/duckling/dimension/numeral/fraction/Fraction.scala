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

import com.xiaomi.duckling.Types
import com.xiaomi.duckling.Types.{NumeralValue, Resolvable}
import com.xiaomi.duckling.dimension.Dimension
import com.xiaomi.duckling.dimension.implicits._
import com.xiaomi.duckling.dimension.numeral.Numeral

case object Fraction extends Dimension with Rules {
  override val name: String = "Fraction"

  override val dimDependents: List[Dimension] = List(Numeral)
}

case class FractionData(n: Double, numerator: Double, denominator: Double) extends NumeralValue with Resolvable {

  override def resolve(context: Types.Context,
                       options: Types.Options): Option[(FractionData, Boolean)] =
    (FractionData(n, numerator: Double, denominator: Double), false)

  override def schema: Option[String] =
    if (numerator * denominator >= 0) Some(s"$numerator/$denominator")
    else Some(s"-${Math.abs(numerator)}/${Math.abs(denominator)}")
}