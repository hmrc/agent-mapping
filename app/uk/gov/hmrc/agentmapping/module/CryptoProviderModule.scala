/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.agentmapping.module

import play.api.Configuration
import play.api.Environment
import play.api.inject.Module
import play.api.inject.Binding
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.crypto.SymmetricCryptoFactory

class CryptoProviderModule
extends Module:

  def aesCryptoInstance(configuration: Configuration): Encrypter & Decrypter =

    if configuration.underlying.getBoolean("fieldLevelEncryption.enable") then
      SymmetricCryptoFactory.aesCryptoFromConfig("fieldLevelEncryption", configuration.underlying)
    else
      NoCrypto
    end if

  end aesCryptoInstance

  override def bindings(
    environment: Environment,
    configuration: Configuration
  ): Seq[Binding[?]] =
    Seq(
      bind[Encrypter & Decrypter].qualifiedWith("aes").toInstance(aesCryptoInstance(configuration))
    )
  end bindings

end CryptoProviderModule
