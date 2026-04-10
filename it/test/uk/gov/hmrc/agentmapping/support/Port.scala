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

package uk.gov.hmrc.agentmapping.support

import play.Logger

import java.net.ServerSocket
import scala.annotation.tailrec

// This class was copy-pasted from the hmrctest project, which is now deprecated.
object Port:

  val rnd = new scala.util.Random
  val range: Seq[Int] = 8000 to 39999
  val usedPorts: List[Int] = List[Int]()

  @tailrec
  def randomAvailable: Int =

    range(rnd.nextInt(range.length)) match
      case 8080 => randomAvailable
      case 8090 => randomAvailable
      case p: Int =>

        if available(p) then
          Logger.of("WireMockSupport").debug("Taking port : " + p)
          usedPorts.appended(p)
          p
        else
          Logger.of("WireMockSupport").debug(s"Port $p is in use, trying another")
          randomAvailable
        end if

    end match

  end randomAvailable

  private def available(p: Int): Boolean =
    var socket: ServerSocket = null
    try

      if !usedPorts.contains(p) then
        socket = new ServerSocket(p)
        socket.setReuseAddress(true)
        true
      else
        false
      end if

    catch
      case _: Throwable => false
    finally
      if socket != null then socket.close()
    end try

  end available

end Port
