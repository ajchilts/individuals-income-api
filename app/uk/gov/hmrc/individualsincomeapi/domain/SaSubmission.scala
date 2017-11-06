/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.individualsincomeapi.domain

import org.joda.time.LocalDate

case class SaTaxReturn(taxYear: TaxYear, submissions: Seq[SaSubmission])
case class SaSubmission(receivedDate: LocalDate)

object SaTaxReturn {
  def apply(desSaIncome: DesSAIncome): SaTaxReturn = {
    SaTaxReturn(TaxYear.fromEndYear(desSaIncome.taxYear.toInt), desSaIncome.returnList.map(_.receivedDate) map SaSubmission)
  }
}

case class SaAnnualEmployments(taxYear: TaxYear, employments: Seq[SaEmploymentsIncome])
case class SaEmploymentsIncome(employmentIncome: Double)

object SaAnnualEmployments {
  def apply(desSaIncome: DesSAIncome): SaAnnualEmployments = {
    SaAnnualEmployments(TaxYear.fromEndYear(desSaIncome.taxYear.toInt), desSaIncome.returnList.map(_.incomeFromAllEmployments.getOrElse(0.0)) map SaEmploymentsIncome)
  }
}

case class SaAnnualSelfEmployments(taxYear: TaxYear, selfEmployments: Seq[SaSelfEmploymentsIncome])
case class SaSelfEmploymentsIncome(selfEmploymentProfit: Double)

object SaAnnualSelfEmployments {
  def apply(desSAIncome: DesSAIncome): SaAnnualSelfEmployments = {
    SaAnnualSelfEmployments(
      TaxYear.fromEndYear(desSAIncome.taxYear.toInt),
      desSAIncome.returnList.map(x => SaSelfEmploymentsIncome(x.profitFromSelfEmployment.getOrElse(0.0))))
  }
}