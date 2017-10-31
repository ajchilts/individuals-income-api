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

package unit.uk.gov.hmrc.individualsincomeapi.services

import java.util.UUID

import org.joda.time.LocalDate
import org.mockito.BDDMockito.given
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.individualsincomeapi.connector.{DesConnector, IndividualsMatchingApiConnector}
import uk.gov.hmrc.individualsincomeapi.domain._
import uk.gov.hmrc.individualsincomeapi.services.{LiveSaIncomeService, SandboxSaIncomeService}
import uk.gov.hmrc.play.test.UnitSpec
import unit.uk.gov.hmrc.individualsincomeapi.util.Dates

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class SaIncomeServiceSpec extends UnitSpec with MockitoSugar with ScalaFutures with Dates {

  val sandboxMatchId = UUID.fromString("57072660-1df9-4aeb-b4ea-cd2d7f96e430")
  val liveMatchId = UUID.randomUUID()
  val liveNino = Nino("AA100009B")
  val taxYearInterval = TaxYearInterval(TaxYear("2013-14"), TaxYear("2016-17"))
  val desIncomes = Seq(
    DesSAIncome(
      taxYear = "2015",
      returnList = Seq(
      DesSAReturn(
        receivedDate = LocalDate.parse("2015-10-06"),
        incomeFromAllEmployments = None))),
    DesSAIncome(
      taxYear = "2016",
      returnList = Seq(
        DesSAReturn(
          receivedDate = LocalDate.parse("2016-06-06"),
          incomeFromAllEmployments = Some(1555.55))))
  )

  trait Setup {
    implicit val hc = HeaderCarrier()
    val matchingConnector = mock[IndividualsMatchingApiConnector]
    val desConnector = mock[DesConnector]

    val sandboxSaIncomeService = new SandboxSaIncomeService()

    val liveSaIncomeService = new LiveSaIncomeService(matchingConnector, desConnector)
  }

  "LiveIncomeService.fetchSaReturnsByMatchId" should {
    "return the saReturns by tax year DESCENDING when the matchId is valid" in new Setup {
      given(matchingConnector.resolve(liveMatchId)).willReturn(MatchedCitizen(liveMatchId, liveNino))
      given(desConnector.fetchSelfAssessmentIncome(liveNino, taxYearInterval)).willReturn(desIncomes)

      val result = await(liveSaIncomeService.fetchSaReturnsByMatchId(liveMatchId, taxYearInterval))

      result shouldBe Seq(
        SaAnnualReturns(TaxYear("2015-16"), Seq(SaReturn(LocalDate.parse("2016-06-06")))),
        SaAnnualReturns(TaxYear("2014-15"), Seq(SaReturn(LocalDate.parse("2015-10-06"))))
      )
    }

    "fail with MatchNotFoundException when the matchId is invalid" in new Setup {
      given(matchingConnector.resolve(liveMatchId)).willReturn(Future.failed(new MatchNotFoundException()))

      intercept[MatchNotFoundException] {
        await(liveSaIncomeService.fetchSaReturnsByMatchId(liveMatchId, taxYearInterval))
      }
    }
  }

  "SandboxSaIncomeService.fetchSaReturnsByMatchId" should {
    "return the saReturns by tax year DESCENDING when the matchId is valid" in new Setup {
      val result = await(sandboxSaIncomeService.fetchSaReturnsByMatchId(sandboxMatchId, taxYearInterval))

      result shouldBe Seq(
        SaAnnualReturns(TaxYear("2014-15"), Seq(SaReturn(LocalDate.parse("2015-10-06")))),
        SaAnnualReturns(TaxYear("2013-14"), Seq(SaReturn(LocalDate.parse("2014-06-06"))))
      )
    }

    "filter out returns not in the requested period" in new Setup {
      val result = await(sandboxSaIncomeService.fetchSaReturnsByMatchId(sandboxMatchId, taxYearInterval.copy(toTaxYear = TaxYear("2013-14"))))

      result shouldBe Seq(
        SaAnnualReturns(TaxYear("2013-14"), Seq(SaReturn(LocalDate.parse("2014-06-06"))))
      )
    }

    "return an empty list when no annual return exists for the requested period" in new Setup {
      val result = await(sandboxSaIncomeService.fetchSaReturnsByMatchId(sandboxMatchId, TaxYearInterval(TaxYear("2015-16"), TaxYear("2015-16"))))

      result shouldBe Seq()
    }

    "fail with MatchNotFoundException when the matchId is not the sandbox matchId" in new Setup {
      intercept[MatchNotFoundException] {
        await(sandboxSaIncomeService.fetchSaReturnsByMatchId(UUID.randomUUID(), TaxYearInterval(TaxYear("2013-14"), TaxYear("2015-16"))))
      }
    }
  }

  "SandboxSaIncomeService.fetchEmploymentsIncomeByMatchId" should {
    "return the employments income by tax year DESCENDING when the matchId is valid" in new Setup {
      val result = await(sandboxSaIncomeService.fetchEmploymentsIncomeByMatchId(sandboxMatchId, TaxYearInterval(TaxYear("2013-14"), TaxYear("2015-16"))))

      result shouldBe Seq(
        SaAnnualEmployments(TaxYear("2014-15"), Seq(SaEmploymentsIncome(0))),
        SaAnnualEmployments(TaxYear("2013-14"), Seq(SaEmploymentsIncome(5000)))
      )
    }

    "filter out employments income not in the requested period" in new Setup {
      val result = await(sandboxSaIncomeService.fetchEmploymentsIncomeByMatchId(sandboxMatchId, taxYearInterval.copy(fromTaxYear = TaxYear("2014-15"))))

      result shouldBe Seq(
        SaAnnualEmployments(TaxYear("2014-15"), Seq(SaEmploymentsIncome(0)))
      )
    }

    "return an empty list when no employments income exists for the requested period" in new Setup {
      val result = await(sandboxSaIncomeService.fetchEmploymentsIncomeByMatchId(sandboxMatchId, TaxYearInterval(TaxYear("2015-16"), TaxYear("2015-16"))))

      result shouldBe Seq()
    }

    "fail with MatchNotFoundException when the matchId is not the sandbox matchId" in new Setup {
      intercept[MatchNotFoundException] {
        await(sandboxSaIncomeService.fetchEmploymentsIncomeByMatchId(UUID.randomUUID(), TaxYearInterval(TaxYear("2013-14"), TaxYear("2015-16"))))
      }
    }

  }
}