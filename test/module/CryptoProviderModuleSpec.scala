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

package module

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Configuration
import uk.gov.hmrc.agentmapping.module.CryptoProviderModule
import uk.gov.hmrc.agentmapping.module.NoCrypto
import uk.gov.hmrc.crypto.Crypted
import uk.gov.hmrc.crypto.PlainBytes
import uk.gov.hmrc.crypto.PlainText

import java.nio.charset.StandardCharsets
import java.util.Base64

class CryptoProviderModuleSpec
extends AnyWordSpecLike
with Matchers {

  private def configuration(fieldLevelEncryptionEnabled: Boolean): Configuration = Configuration(
    ConfigFactory.parseString(s"""fieldLevelEncryption {
                                 |  enable = $fieldLevelEncryptionEnabled
                                 |  key = "oaJdbtyXIUyd+hHefKbMUqtehotAG99pH0bqpkSuQ/Q="
                                 |}
                                 |""".stripMargin)
  )

  "CryptoProviderModule" should {
    "provide a real crypto instance if field-level encryption is enabled in config" in {
      val x = new CryptoProviderModule().aesCryptoInstance(configuration(fieldLevelEncryptionEnabled = true))
      x should not be a[NoCrypto]
    }
    "provide a no-op crypto instance if field-level encryption is disabled in config" in {
      val x = new CryptoProviderModule().aesCryptoInstance(configuration(fieldLevelEncryptionEnabled = false))
      x shouldBe a[NoCrypto]
    }
  }

  "NoCrypto" should {
    val text = "Not a secret"
    val bytes: Array[Byte] = Array(0x13, 0x37)
    val base64Bytes = new String(Base64.getEncoder.encode(bytes), StandardCharsets.UTF_8)

    "pass through data on encryption" in {
      NoCrypto.encrypt(PlainText(text)).value shouldBe text
      NoCrypto.encrypt(PlainBytes(bytes)).value shouldBe base64Bytes
    }

    "pass through data on decryption" in {
      NoCrypto.decrypt(Crypted(text)).value shouldBe text
      NoCrypto.decryptAsBytes(Crypted(base64Bytes)).value shouldBe bytes
    }
  }

}
