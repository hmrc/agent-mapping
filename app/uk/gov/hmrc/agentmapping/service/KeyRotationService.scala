/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.agentmapping.service

import com.google.inject.Inject
import com.google.inject.Singleton
import play.api.mvc.Request
import uk.gov.hmrc.agentmapping.config.AppConfig
import uk.gov.hmrc.agentmapping.model.LegacyAgentEnrolmentType
import uk.gov.hmrc.agentmapping.repository.MappingRepositories
import uk.gov.hmrc.agentmapping.util.NoRequest
import uk.gov.hmrc.agentmapping.util.RequestAwareLogging
import uk.gov.hmrc.mongo.lock.LockRepository
import uk.gov.hmrc.mongo.lock.LockService

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

@Singleton
class KeyRotationService @Inject (
  lockRepository: LockRepository,
  mappingRepositories: MappingRepositories,
  appConfig: AppConfig
)(using ExecutionContext)
extends RequestAwareLogging:

  private given Request[?] = NoRequest

  private val lockService = LockService(
    lockRepository,
    lockId = "key-rotation-lock",
    ttl = appConfig.keyRotation.maxTotalDuration
  )

  private val migration: Future[Option[Unit]] =
    if !appConfig.keyRotation.enabled then Future.successful(None)
    else
      lockService.withLock:
        val deadline = appConfig.keyRotation.maxTotalDuration.fromNow

        logger.info("Starting key rotation migration placeholder.")
        val results =
          Future.traverse(LegacyAgentEnrolmentType.values): legacyEnrolmentType =>
            mappingRepositories.get(legacyEnrolmentType).migrate(appConfig.keyRotation.batchSize, deadline)
              .map: migrated =>
                logger.info(s"Key rotation migration for ${legacyEnrolmentType.key} completed successfully. Migrated $migrated records.")

        results.map(_ => Some(()))

  migration.onComplete:
    case Failure(error) => logger.error("Key rotation migration failed", error)
    case Success(Some(_)) => logger.info("Key rotation migration completed successfully")
    case Success(None) => logger.warn("Key rotation migration disabled or did not acquire lock, skipping")
