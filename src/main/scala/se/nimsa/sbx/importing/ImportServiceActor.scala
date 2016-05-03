package se.nimsa.sbx.importing

import akka.actor.Actor
import se.nimsa.sbx.app.DbProps
import akka.event.Logging
import akka.actor.Props
import akka.event.LoggingReceive
import se.nimsa.sbx.importing.ImportProtocol._
import se.nimsa.sbx.lang.NotFoundException
import se.nimsa.sbx.util.ExceptionCatching

class ImportServiceActor(dbProps: DbProps) extends Actor with ExceptionCatching {
  val log = Logging(context.system, this)

  val db = dbProps.db
  val dao = new ImportDAO(dbProps.driver)

  log.info("Import service started")

  override def receive = LoggingReceive {

    case msg: ImportSessionRequest => catchAndReport {
      msg match {

        case AddImportSession(importSession) =>
          db.withSession { implicit session =>
            val newImportSession = importSession.copy(filesImported = 0, filesAdded = 0, filesRejected = 0, created = now, lastUpdated = now)
            sender ! dao.addImportSession(newImportSession)
          }

        case GetImportSessions(startIndex, count) =>
          db.withSession { implicit session =>
            sender ! ImportSessions(dao.getImportSessions(startIndex, count))
          }

        case GetImportSession(id) =>
          db.withSession { implicit session =>
            sender ! dao.getImportSession(id)
          }

        case DeleteImportSession(id) =>
          db.withSession { implicit session =>
            sender ! dao.removeImportSession(id)
          }

        case GetImportSessionImages(id) =>
          db.withSession { implicit session =>
            sender ! ImportSessionImages(dao.listImagesForImportSesstionId(id))
          }

        case AddImageToSession(importSession, image, overwrite) =>
          db.withSession { implicit session =>
            val importSessionImage =
              if (overwrite) {
                val updatedImportSession = importSession.copy(filesImported = importSession.filesImported + 1, lastUpdated = now)
                dao.updateImportSession(updatedImportSession)
                dao.importSessionImageForImportSessionIdAndImageId(importSession.id, image.id)
                  .getOrElse(throw new NotFoundException(s"No import session image found for import session id ${importSession.id} and image id ${image.id}"))
              } else {
                val updatedImportSession = importSession.copy(filesImported = importSession.filesImported + 1, filesAdded = importSession.filesAdded + 1, lastUpdated = now)
                dao.updateImportSession(updatedImportSession)
                dao.insertImportSessionImage(ImportSessionImage(-1, updatedImportSession.id, image.id))
              }
            sender ! ImageAddedToSession(importSessionImage)
          }

        case UpdateSessionWithRejection(importSession) =>
          db.withSession { implicit session =>
            val updatedImportSession = importSession.copy(filesRejected = importSession.filesRejected + 1, lastUpdated = now)
            sender ! dao.updateImportSession(updatedImportSession)
          }

      }
    }

  }

  def now = System.currentTimeMillis

}

object ImportServiceActor {
  def props(dbProps: DbProps): Props = Props(new ImportServiceActor(dbProps))
}