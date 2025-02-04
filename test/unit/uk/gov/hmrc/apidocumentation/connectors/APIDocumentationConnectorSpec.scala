/*
 * Copyright 2019 HM Revenue & Customs
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

package unit.uk.gov.hmrc.apidocumentation.connectors

import org.mockito.Mockito.when
import org.mockito.Matchers.{any, eq => meq}
import play.api.libs.json.Json
import play.api.http.Status._
import uk.gov.hmrc.apidocumentation.config.ApplicationConfig
import uk.gov.hmrc.apidocumentation.connectors.APIDocumentationConnector
import uk.gov.hmrc.apidocumentation.models.{APIAccessType, APIDefinition, ExtendedAPIDefinition, VersionVisibility}
import uk.gov.hmrc.apidocumentation.models.JsonFormatters._
import uk.gov.hmrc.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.metrics.{API, NoopMetrics}

import scala.concurrent.Future

class APIDocumentationConnectorSpec extends ConnectorSpec {
  val apiDocumentationUrl = "https://api-documentation.example.com"

  trait Setup {
    implicit val hc = HeaderCarrier()
    val mockHttpClient = mock[HttpClient]
    val mockAppConfig = mock[ApplicationConfig]
    val connector = new APIDocumentationConnector(mockHttpClient, mockAppConfig, NoopMetrics)

    when(mockAppConfig.apiDocumentationUrl).thenReturn(apiDocumentationUrl)
  }

  "api" should {
    "be api-documentation" in new Setup {
      connector.api shouldEqual API("api-documentation")
    }
  }

  "fetchExtendedDefinitionByServiceName" should {

    "return a fetched API Definition" in new Setup {
      val serviceName = "calendar"
      when(mockHttpClient.GET[ExtendedAPIDefinition](meq(s"$apiDocumentationUrl/apis/$serviceName/definition"))(any(), any(), any()))
        .thenReturn(Future.successful(extendedApiDefinition("Calendar")))

      val result = await(connector.fetchExtendedDefinitionByServiceName(serviceName))
      result.name shouldBe "Calendar"
      result.versions should have size 2
      result.versions.find(_.version == "1.0").flatMap(_.visibility) shouldBe Some(VersionVisibility(APIAccessType.PUBLIC, false, true))
    }

    "return a fetched API Definition with access levels" in new Setup {
      val serviceName = "calendar"
      when(mockHttpClient.GET[ExtendedAPIDefinition](meq(s"$apiDocumentationUrl/apis/$serviceName/definition"))(any(), any(), any()))
        .thenReturn(Future.successful(extendedApiDefinition("Hello with access levels")))

      val result = await(connector.fetchExtendedDefinitionByServiceName(serviceName))
      result.name shouldBe "Hello with access levels"
      result.versions.size shouldBe 2
      result.versions flatMap (_.visibility.map(_.privacy)) shouldBe Seq(APIAccessType.PUBLIC, APIAccessType.PRIVATE)

      result.versions should have size 2
      result.versions.find(_.version == "2.0").flatMap(_.visibility) shouldBe Some(VersionVisibility(APIAccessType.PRIVATE, false, false))

    }

    "throw an http-verbs Upstream5xxResponse exception if the API Definition service responds with an error" in new Setup {
      val serviceName = "calendar"
      when(mockHttpClient.GET[ExtendedAPIDefinition](meq(s"$apiDocumentationUrl/apis/$serviceName/definition"))(any(), any(), any()))
        .thenReturn(Future.failed(new Upstream5xxResponse("Internal server error", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      intercept[Upstream5xxResponse](await(connector.fetchExtendedDefinitionByServiceName(serviceName)))
    }
  }

  "fetchExtendedDefinitionByServiceName with logged in email" should {

    val loggedInUserEmail = "test@example.com"
    val params = Seq("email" -> loggedInUserEmail)

    "return a fetched API Definition" in new Setup {
      val serviceName = "calendar"
      when(mockHttpClient.GET[ExtendedAPIDefinition](meq(s"$apiDocumentationUrl/apis/$serviceName/definition"), meq(params))(any(), any(), any()))
        .thenReturn(Future.successful(extendedApiDefinition("Calendar")))

      val result = await(connector.fetchExtendedDefinitionByServiceNameAndEmail(serviceName, loggedInUserEmail))
      result.name shouldBe "Calendar"
      result.versions should have size 2
      result.versions.find(_.version == "1.0").flatMap(_.visibility) shouldBe Some(VersionVisibility(APIAccessType.PUBLIC, false, true))
    }

    "throw an http-verbs Upstream5xxResponse exception if the API Definition service responds with an error" in new Setup {
      val serviceName = "calendar"
      when(mockHttpClient.GET[ExtendedAPIDefinition](meq(s"$apiDocumentationUrl/apis/$serviceName/definition"), meq(params))(any(), any(), any()))
      .thenReturn(Future.failed(new Upstream5xxResponse("Internal server error", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      intercept[Upstream5xxResponse](await(connector.fetchExtendedDefinitionByServiceNameAndEmail(serviceName, loggedInUserEmail)))
    }
  }

  "fetchAll" should {

    "return all API Definitions sorted by name" in new Setup {
      when(mockHttpClient.GET[Seq[APIDefinition]](meq(s"$apiDocumentationUrl/apis/definition"))(any(), any(), any()))
        .thenReturn(Future.successful(apiDefinitions("Hello", "Calendar")))

      val result = await(connector.fetchAll())
      result.size shouldBe 2
      result(0).name shouldBe "Calendar"
      result(1).name shouldBe "Hello"
    }

    "throw an http-verbs Upstream5xxResponse exception if the API Definition service responds with an error" in new Setup {
      when(mockHttpClient.GET[Seq[APIDefinition]](meq(s"$apiDocumentationUrl/apis/definition"))(any(), any(), any()))
        .thenReturn(Future.failed(new Upstream5xxResponse("Internal server error", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      intercept[Upstream5xxResponse](await(connector.fetchAll()))
    }
  }

  "fetchByEmail" should {

    val loggedInUserEmail = "email@example.com"
    val params = Seq("email" -> loggedInUserEmail)

    "return all API Definitions sorted by name for an email address" in new Setup {
      when(mockHttpClient.GET[Seq[APIDefinition]](meq(s"$apiDocumentationUrl/apis/definition"), meq(params))(any(), any(), any()))
        .thenReturn(Future.successful(apiDefinitions("Hello", "Calendar")))

      val result = await(connector.fetchByEmail(loggedInUserEmail))
      result.size shouldBe 2
      result(0).name shouldBe "Calendar"
      result(1).name shouldBe "Hello"
    }

    "return all API Definitions sorted by name for a strange email address" in new Setup {
      val loggedInUserStrangeEmail = "email+strange@example.com"
      val strangeParams = Seq("email" -> loggedInUserStrangeEmail)
      when(mockHttpClient.GET[Seq[APIDefinition]](meq(s"$apiDocumentationUrl/apis/definition"), meq(strangeParams))(any(), any(), any()))
        .thenReturn(Future.successful(apiDefinitions("Hello", "Calendar")))

      val result = await(connector.fetchByEmail(loggedInUserStrangeEmail))
      result.size shouldBe 2
      result(0).name shouldBe "Calendar"
      result(1).name shouldBe "Hello"
    }

    "throw an http-verbs Upstream5xxResponse exception if the API Definition service responds with an error" in new Setup {
      when(mockHttpClient.GET[Seq[APIDefinition]](meq(s"$apiDocumentationUrl/apis/definition"), meq(params))(any(), any(), any()))
        .thenReturn(Future.failed(new Upstream5xxResponse("Internal server error", 500, 500)))

      intercept[Upstream5xxResponse](await(connector.fetchByEmail(loggedInUserEmail)))
    }
  }

  private def apiDefinitions(names: String*) = names.map(apiDefinition)
  
  private def extendedApiDefinition(name: String) = {
    Json.parse(s"""{
       |  "name" : "$name",
       |  "description" : "Test API",
       |  "context" : "test",
       |  "serviceBaseUrl" : "http://test",
       |  "serviceName" : "test",
       |  "requiresTrust": false,
       |  "isTestSupport": false,
       |  "versions" : [
       |    {
       |      "version" : "1.0",
       |      "status" : "STABLE",
       |      "endpoints" : [
       |        {
       |          "uriPattern" : "/hello",
       |          "endpointName" : "Say Hello",
       |          "method" : "GET",
       |          "authType" : "NONE",
       |          "throttlingTier" : "UNLIMITED"
       |        }
       |      ],
       |      "productionAvailability": {
       |        "endpointsEnabled": true,
       |        "access": {
       |          "type": "PUBLIC"
       |        },
       |        "loggedIn": false,
       |        "authorised": true
       |      }
       |    },
       |    {
       |      "version" : "2.0",
       |      "status" : "STABLE",
       |      "endpoints" : [
       |        {
       |          "uriPattern" : "/hello",
       |          "endpointName" : "Say Hello",
       |          "method" : "GET",
       |          "authType" : "NONE",
       |          "throttlingTier" : "UNLIMITED",
       |          "scope": "read:hello"
       |        }
       |      ],
       |      "productionAvailability": {
       |        "endpointsEnabled": true,
       |        "access": {
       |          "type": "PRIVATE"
       |        },
       |        "loggedIn": false,
       |        "authorised": false
       |      }
       |    }
       |  ]
       |}
     """.stripMargin).as[ExtendedAPIDefinition]
  }

  private def apiDefinition(name: String) = {
    Json.parse(s"""{
        |  "name" : "$name",
        |  "description" : "Test API",
        |  "context" : "test",
        |  "serviceBaseUrl" : "http://test",
        |  "serviceName" : "test",
        |  "versions" : [
        |    {
        |      "version" : "1.0",
        |      "status" : "STABLE",
        |      "endpoints" : [
        |        {
        |          "uriPattern" : "/hello",
        |          "endpointName" : "Say Hello",
        |          "method" : "GET",
        |          "authType" : "NONE",
        |          "throttlingTier" : "UNLIMITED"
        |        }
        |      ]
        |    },
        |    {
        |      "version" : "2.0",
        |      "status" : "STABLE",
        |      "endpoints" : [
        |        {
        |          "uriPattern" : "/hello",
        |          "endpointName" : "Say Hello",
        |          "method" : "GET",
        |          "authType" : "NONE",
        |          "throttlingTier" : "UNLIMITED",
        |          "scope": "read:hello"
        |        }
        |      ]
        |    }
        |  ]
        |}""".stripMargin.replaceAll("\n", " ")).as[APIDefinition]
  }
}
