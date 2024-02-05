package org.linthaal.qa.primekg

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ ActorRef, Behavior }
import org.linthaal.api.routes.PrimeKGQAReq
import org.linthaal.qa.primekg.PrimeKGQA.PrimeKGAnswer

/** This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
object PrimeKGQARouter {
  sealed trait Command

  case class StartQA(primeKGQAReq: PrimeKGQAReq, replyTo: ActorRef[PrimeKGAnswer]) extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage { case StartQA(primeKGQAReq, replyTo) =>
        ctx.spawnAnonymous(PrimeKGQA(primeKGQAReq.question, replyTo))

        Behaviors.same
      }
    }
}
