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

package se.nimsa.sbx.scu

import java.nio.file.Path
import scala.language.postfixOps
import scala.concurrent.Future
import akka.actor.Actor
import akka.actor.PoisonPill
import akka.actor.Props
import akka.event.Logging
import akka.event.LoggingReceive
import akka.pattern.pipe
import se.nimsa.sbx.app.DbProps
import se.nimsa.sbx.storage.PropertiesDAO
import se.nimsa.sbx.util.ExceptionCatching
import se.nimsa.sbx.log.SbxLog
import ScuProtocol._
import java.net.UnknownHostException
import java.net.ConnectException
import se.nimsa.sbx.lang.NotFoundException
import se.nimsa.sbx.lang.BadGatewayException
import java.net.NoRouteToHostException
import se.nimsa.sbx.app.GeneralProtocol.ImagesSent

class ScuServiceActor(dbProps: DbProps, storage: Path) extends Actor with ExceptionCatching {
  val log = Logging(context.system, this)

  val db = dbProps.db
  val dao = new ScuDAO(dbProps.driver)
  val propertiesDao = new PropertiesDAO(dbProps.driver)

  import context.system
  implicit val ec = context.dispatcher

  log.info("SCU service started")

  def receive = LoggingReceive {

    case msg: ScuRequest =>

      catchAndReport {

        msg match {

          case AddScu(scu) =>
            scuForName(scu.name) match {
              case Some(scuData) =>

                sender ! scuData

              case None =>

                val trimmedAeTitle = scu.aeTitle.trim

                if (trimmedAeTitle.isEmpty)
                  throw new IllegalArgumentException("Ae title must not be empty")

                if (trimmedAeTitle.length > 16)
                  throw new IllegalArgumentException("Ae title must not exceed 16 characters, excluding leading and trailing epaces.")

                if (scu.port < 0 || scu.port > 65535)
                  throw new IllegalArgumentException("Port must be a value between 0 and 65535")

                if (scuForHostAndPort(scu.host, scu.port).isDefined)
                  throw new IllegalArgumentException(s"Host ${scu.host} and port ${scu.port} is already in use")

                val scuData = addScu(scu)

                sender ! scuData

            }

          case RemoveScu(scuDataId) =>
            scuForId(scuDataId).foreach(scuData => deleteScuWithId(scuDataId))
            sender ! ScuRemoved(scuDataId)

          case GetScus =>
            val scus = getScus()
            sender ! Scus(scus)

          case SendImagesToScp(imageIds, scuId) =>
            scuForId(scuId).map(scu => {
              val imageFiles = imageFilesForImageIds(imageIds)
              if (imageFiles.isEmpty)
                throw new NotFoundException(s"No files found given ${imageIds.length} image ids")
              SbxLog.info("SCU", s"Sending ${imageFiles.length} images using SCU ${scu.name}")
              Future {
                Scu.sendFiles(scu, imageFiles.map(imageFile => storage.resolve(imageFile.fileName.value)))
              }
                .map(r => {
                  context.system.eventStream.publish(ImagesSent(imageIds))
                  ImagesSentToScp(scuId, imageFiles.map(_.id))
                })
                .recover {
                  case e: UnknownHostException =>
                    throw new BadGatewayException(s"Unable to reach host ${scu.name}@${scu.host}:${scu.port}")
                  case e: ConnectException =>
                    throw new BadGatewayException(s"Connection refused on host ${scu.name}@${scu.host}:${scu.port}")
                  case e: NoRouteToHostException =>
                    throw new BadGatewayException(s"No route found to host ${scu.name}@${scu.host}:${scu.port}")
                }
                .pipeTo(sender)
            }).orElse(throw new NotFoundException(s"SCU with id $scuId not found"))
          // TODO handle errors when sending (do not return 500)
        }
      }

  }

  def addScu(scuData: ScuData) =
    db.withSession { implicit session =>
      dao.insert(scuData)
    }

  def scuForId(id: Long) =
    db.withSession { implicit session =>
      dao.scuDataForId(id)
    }

  def scuForName(name: String) =
    db.withSession { implicit session =>
      dao.scuDataForName(name)
    }

  def scuForHostAndPort(host: String, port: Int) =
    db.withSession { implicit session =>
      dao.scuDataForHostAndPort(host, port)
    }

  def deleteScuWithId(id: Long) =
    db.withSession { implicit session =>
      dao.deleteScuDataWithId(id)
    }

  def getScus() =
    db.withSession { implicit session =>
      dao.allScuDatas
    }

  def imageFilesForImageIds(imageIds: Seq[Long]) =
    db.withSession { implicit session =>
      imageIds.map(propertiesDao.imageFileForImage(_)).flatten
    }

}

object ScuServiceActor {
  def props(dbProps: DbProps, storage: Path): Props = Props(new ScuServiceActor(dbProps, storage))
}
