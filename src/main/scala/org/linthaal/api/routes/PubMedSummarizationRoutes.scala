package org.linthaal.api.routes

import akka.http.scaladsl.server.Directives._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri.Path.Segment
import akka.http.scaladsl.server.Route
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import org.linthaal.tot.pubmed.PubMedToTManager

import scala.concurrent.Future

/**
  *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program. If not, see <http://www.gnu.org/licenses/>.
  *
  */
class PubMedSummarizationRoutes(pmToT: ActorRef[PubMedToTManager.Command])
                               (implicit val system: ActorSystem[_]) {


  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import org.linthaal.api.protocols.APIJsonFormats._

  // If ask takes more time than this to complete the request is failed
  private implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))

  def getUsers(): Future[Users] =
    pmToT.ask(GetUsers)

  def getUser(name: String): Future[GetUserResponse] =
    pmToT.ask(GetUser(name, _))

  def createUser(user: User): Future[ActionPerformed] =
    pmToT.ask(CreateUser(user, _))

  def deleteUser(name: String): Future[ActionPerformed] =
    pmToT.ask(DeleteUser(name, _))


  //#all-routes
  //#users-get-post
  //#users-get-delete
  val userRoutes: Route =
  pathPrefix("users") {
    concat(
      //#users-get-delete
      pathEnd {
        concat(
          get {
            complete(getUsers())
          },
          post {
            entity(as[User]) { user =>
              onSuccess(createUser(user)) { performed =>
                complete((StatusCodes.Created, performed))
              }
            }
          })
      },
      //#users-get-delete
      //#users-get-post
      path(Segment) { name =>
        concat(
          get {
            //#retrieve-user-info
            rejectEmptyResponse {
              onSuccess(getUser(name)) { response =>
                complete(response.maybeUser)
              }
            }
            //#retrieve-user-info
          },
          delete {
            //#users-delete-logic
            onSuccess(deleteUser(name)) { performed =>
              complete((StatusCodes.OK, performed))
            }
            //#users-delete-logic
          })
      })
    //#users-get-delete
  }
  //#all-routes
}
