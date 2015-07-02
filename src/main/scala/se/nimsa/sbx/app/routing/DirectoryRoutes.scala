/*
 * Copyright 2015 Lars Edenbrandt
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

package se.nimsa.sbx.app.routing

import akka.pattern.ask
import spray.httpx.SprayJsonSupport._
import spray.routing._
import spray.http.StatusCodes._
import se.nimsa.sbx.app.RestApi
import se.nimsa.sbx.directory.DirectoryWatchProtocol._
import se.nimsa.sbx.app.AuthInfo
import se.nimsa.sbx.app.UserProtocol.UserRole

trait DirectoryRoutes { this: RestApi =>

  def directoryRoutes(authInfo: AuthInfo): Route =
    pathPrefix("directorywatches") {
      pathEndOrSingleSlash {
        get {
          onSuccess(directoryService.ask(GetWatchedDirectories)) {
            case WatchedDirectories(directories) =>
              complete(directories)
          }
        } ~ post {
          authorize(authInfo.hasPermission(UserRole.ADMINISTRATOR)) {
            entity(as[WatchDirectory]) { directory =>
              onSuccess(directoryService.ask(directory)) {
                case dir: WatchedDirectory =>
                  complete((Created, dir))
              }
            }
          }
        }
      } ~ path(LongNumber) { watchDirectoryId =>
        delete {
          authorize(authInfo.hasPermission(UserRole.ADMINISTRATOR)) {
            onSuccess(directoryService.ask(UnWatchDirectory(watchDirectoryId))) {
              case DirectoryUnwatched(watchedDirectoryId) =>
                complete(NoContent)
            }
          }
        }
      }
    }

}