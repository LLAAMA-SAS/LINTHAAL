package org.linthaal

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ ActorSystem, Behavior }
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import org.linthaal.api.routes.PubMedSummarizationRoutes
import org.linthaal.tot.pubmed.PubMedToTManager
import org.linthaal.api.routes.PrimeKGQARoutes
import org.linthaal.qa.primekg.PrimeKGQARouter
import org.apache.pekko.http.scaladsl.server.Directives.*

import scala.util.{ Failure, Success }

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
object LinthaalSupervisor {
  def apply(): Behavior[Nothing] =
    Behaviors.setup[Nothing] { ctx =>
      // define all ToT top actors here

      val pubmedToTMain = ctx.spawn(PubMedToTManager(), "pubmed_tot_main")
      ctx.watch(pubmedToTMain)
      val toTRoutes = PubMedSummarizationRoutes(pubmedToTMain)(ctx.system)
      val primeKGQARouter = ctx.spawn(PrimeKGQARouter(), "primekg_qa_main")
      ctx.watch(primeKGQARouter)
      val primeKGQARoutes = PrimeKGQARoutes(primeKGQARouter)(ctx.system)
      startHttpServer(concat(toTRoutes.pmAISumAllRoutes, primeKGQARoutes.primeKGQARoutes))(ctx.system)
      Behaviors.empty
    }

  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {

    // Akka HTTP still needs a classic ActorSystem to start
    import system.executionContext

    val futureBinding = Http().newServerAt("0.0.0.0", 7847).bind(routes)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }
}
