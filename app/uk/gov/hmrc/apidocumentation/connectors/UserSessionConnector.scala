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

package uk.gov.hmrc.apidocumentation.connectors

import javax.inject.{Inject, Singleton}

import uk.gov.hmrc.apidocumentation.config.ApplicationConfig
import uk.gov.hmrc.apidocumentation.models.JsonFormatters._
import uk.gov.hmrc.apidocumentation.models.{Session, SessionInvalid}
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.metrics.{API, Metrics}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class UserSessionConnector @Inject()(http: HttpClient, appConfig: ApplicationConfig, metrics: Metrics) {

  val api = API("third-party-developer")
  lazy val serviceBaseUrl: String = appConfig.thirdPartyDeveloperUrl

  def fetchSession(sessionId: String)(implicit hc: HeaderCarrier): Future[Session] = metrics.record(api) {
    http.GET[Session](s"$serviceBaseUrl/session/$sessionId") recover {
      case e: NotFoundException => throw new SessionInvalid
    }
  }
}
