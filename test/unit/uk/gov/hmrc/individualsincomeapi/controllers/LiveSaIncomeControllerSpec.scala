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

package unit.uk.gov.hmrc.individualsincomeapi.controllers

import java.util.UUID

import org.joda.time.LocalDate
import org.mockito.BDDMockito.given
import org.mockito.Matchers.{any, refEq}
import org.mockito.Mockito.verifyZeroInteractions
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.retrieve.EmptyRetrieval
import uk.gov.hmrc.auth.core.{Enrolment, InsufficientEnrolments}
import uk.gov.hmrc.individualsincomeapi.config.ServiceAuthConnector
import uk.gov.hmrc.individualsincomeapi.controllers.LiveSaIncomeController
import uk.gov.hmrc.individualsincomeapi.domain.JsonFormatters._
import uk.gov.hmrc.individualsincomeapi.domain._
import uk.gov.hmrc.individualsincomeapi.services.LiveSaIncomeService
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future.{failed, successful}

class LiveSaIncomeControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication {
  implicit lazy val materializer = fakeApplication.materializer

  val matchId = UUID.randomUUID()
  val fromTaxYear = TaxYear("2015-16")
  val toTaxYear = TaxYear("2016-17")
  val taxYearInterval = TaxYearInterval(fromTaxYear, toTaxYear)
  val requestParameters = s"matchId=$matchId&fromTaxYear=${fromTaxYear.formattedTaxYear}&toTaxYear=${toTaxYear.formattedTaxYear}"

  trait Setup {
    val mockAuthConnector = mock[ServiceAuthConnector]
    val mockLiveSaIncomeService = mock[LiveSaIncomeService]

    val liveSaIncomeController = new LiveSaIncomeController(mockLiveSaIncomeService, mockAuthConnector)

    given(mockAuthConnector.authorise(any(), refEq(EmptyRetrieval))(any(), any())).willReturn(successful(()))
  }

  "LiveSaIncomeController.saReturns" should {
    val fakeRequest = FakeRequest("GET", s"/individuals/income/sa?$requestParameters")
    val saReturns = Seq(SaAnnualReturns(TaxYear("2015-16"), Seq(SaReturn(LocalDate.parse("2016-06-01")))))

    "return 200 (OK) with the self assessment returns for the period" in new Setup {
      given(mockLiveSaIncomeService.fetchSaReturnsByMatchId(refEq(matchId), refEq(taxYearInterval))(any()))
        .willReturn(successful(saReturns))

      val result = await(liveSaIncomeController.saReturns(matchId, taxYearInterval)(fakeRequest))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe Json.parse(expectedSaRootPayload(requestParameters, saReturns))
    }

    "return 200 (Ok) and the links without toTaxYear when it is not passed in the request" in new Setup {
      val requestParametersWithoutToTaxYear = s"matchId=$matchId&fromTaxYear=${fromTaxYear.formattedTaxYear}"
      val fakeRequestWithoutToTaxYear = FakeRequest("GET", s"/individuals/income/sa?$requestParametersWithoutToTaxYear")

      given(mockLiveSaIncomeService.fetchSaReturnsByMatchId(refEq(matchId), refEq(taxYearInterval))(any()))
        .willReturn(successful(saReturns))

      val result = await(liveSaIncomeController.saReturns(matchId, taxYearInterval)(fakeRequestWithoutToTaxYear))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe Json.parse(expectedSaRootPayload(requestParametersWithoutToTaxYear, saReturns))
    }

    "return 404 (Not Found) for an invalid matchId" in new Setup {
      given(mockLiveSaIncomeService.fetchSaReturnsByMatchId(refEq(matchId), refEq(taxYearInterval))(any()))
        .willReturn(failed(new MatchNotFoundException()))

      val result = await(liveSaIncomeController.saReturns(matchId, taxYearInterval)(fakeRequest))

      status(result) shouldBe NOT_FOUND
      jsonBodyOf(result) shouldBe Json.parse( s"""{"code":"NOT_FOUND", "message":"The resource can not be found"}""")
    }

    "require read:individuals-income-sa privileged scope" in new Setup {
      given(mockAuthConnector.authorise(refEq(Enrolment("read:individuals-income-sa")), refEq(EmptyRetrieval))(any(), any()))
        .willReturn(failed(InsufficientEnrolments()))

      intercept[InsufficientEnrolments]{await(liveSaIncomeController.saReturns(matchId, taxYearInterval)(fakeRequest))}
      verifyZeroInteractions(mockLiveSaIncomeService)
    }
  }

  "LiveSaIncomeController.employmentsIncome" should {
    val fakeRequest = FakeRequest("GET", s"/individuals/income/sa/employments?$requestParameters")
    val employmentsIncomes = Seq(SaAnnualEmployments(TaxYear("2015-16"), Seq(SaEmploymentsIncome(9000))))

    "return 200 (OK) with the employments income returns for the period" in new Setup {
      given(mockLiveSaIncomeService.fetchEmploymentsIncomeByMatchId(refEq(matchId), refEq(taxYearInterval))(any()))
        .willReturn(successful(employmentsIncomes))

      val result = await(liveSaIncomeController.employmentsIncome(matchId, taxYearInterval)(fakeRequest))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe Json.parse(expectedSaPayload(fakeRequest.uri, Json.toJson(employmentsIncomes)))
    }

    "return 200 (Ok) and the self link without toTaxYear when it is not passed in the request" in new Setup {
      val requestParametersWithoutToTaxYear = s"matchId=$matchId&fromTaxYear=${fromTaxYear.formattedTaxYear}"
      val fakeRequestWithoutToTaxYear = FakeRequest("GET", s"/individuals/income/sa/employments?$requestParametersWithoutToTaxYear")

      given(mockLiveSaIncomeService.fetchEmploymentsIncomeByMatchId(refEq(matchId), refEq(taxYearInterval))(any()))
        .willReturn(successful(employmentsIncomes))

      val result = await(liveSaIncomeController.employmentsIncome(matchId, taxYearInterval)(fakeRequestWithoutToTaxYear))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe Json.parse(expectedSaPayload(fakeRequestWithoutToTaxYear.uri, Json.toJson(employmentsIncomes)))
    }

    "return 404 (Not Found) for an invalid matchId" in new Setup {
      given(mockLiveSaIncomeService.fetchEmploymentsIncomeByMatchId(refEq(matchId), refEq(taxYearInterval))(any()))
        .willReturn(failed(new MatchNotFoundException()))

      val result = await(liveSaIncomeController.employmentsIncome(matchId, taxYearInterval)(fakeRequest))

      status(result) shouldBe NOT_FOUND
      jsonBodyOf(result) shouldBe Json.parse( s"""{"code":"NOT_FOUND", "message":"The resource can not be found"}""")
    }

    "require read:individuals-income-sa-employments privileged scope" in new Setup {
      given(mockAuthConnector.authorise(refEq(Enrolment("read:individuals-income-sa-employments")), refEq(EmptyRetrieval))(any(), any()))
        .willReturn(failed(InsufficientEnrolments()))

      intercept[InsufficientEnrolments]{await(liveSaIncomeController.employmentsIncome(matchId, taxYearInterval)(fakeRequest))}
      verifyZeroInteractions(mockLiveSaIncomeService)
    }
  }

  private def expectedSaRootPayload(requestParameters: String, saReturns: Seq[SaAnnualReturns]) = {
    s"""
       {
         "_links": {
           "self": {"href": "/individuals/income/sa?$requestParameters"},
           "employments": {"href": "/individuals/income/sa/employments?$requestParameters"},
           "self-employments": {"href": "/individuals/income/sa/self-employments?$requestParameters"}
         },
         "_embedded": {
           "income": ${Json.toJson(saReturns)}
         }
       }
      """
  }

  private def expectedSaPayload(fakeRequest: String, income: JsValue) = {
    s"""
       {
         "_links": {
           "self": {"href": "$fakeRequest"}
         },
         "_embedded": {
           "income": $income
         }
       }
      """
  }

}
