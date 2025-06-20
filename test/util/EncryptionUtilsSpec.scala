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

package util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.JsString
import play.api.libs.json.Json
import uk.gov.hmrc.agentmapping.util.EncryptionUtils
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypter
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.crypto.SymmetricCryptoFactory

class EncryptionUtilsSpec
extends AnyWordSpecLike
with Matchers {

  implicit val crypto: Encrypter
    with Decrypter = SymmetricCryptoFactory.aesCrypto(secretKey = "GTfz3GZy0+gN0p/5wSqRBpWlbWVDMezXWtX+G9ENwCc=")

  ".decryptString" should {

    "decrypt an encrypted string" in {
      val unencrypted = "my secret"
      val encrypted = stringEncrypter.writes(unencrypted)
      encrypted shouldBe JsString("dw94Wl9CDa8OMrGo1Abgkg==")

      val json = Json.obj("value" -> encrypted)
      EncryptionUtils.decryptString(
        "value",
        Some(true),
        json
      ) shouldBe unencrypted
    }

    "not attempt to decrypt a string that has an encryption flag of false" in {
      val unencrypted = "my secret"
      val json = Json.obj("value" -> unencrypted)
      EncryptionUtils.decryptString(
        "value",
        Some(false),
        json
      ) shouldBe unencrypted
    }

    "not attempt to decrypt a string that has no encryption flag" in {
      val unencrypted = "my secret"
      val json = Json.obj("value" -> unencrypted)
      EncryptionUtils.decryptString(
        "value",
        None,
        json
      ) shouldBe unencrypted
    }
  }

}
