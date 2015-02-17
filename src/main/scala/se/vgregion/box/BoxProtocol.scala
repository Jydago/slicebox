package se.vgregion.box

import se.vgregion.model.Entity
import org.dcm4che3.data.Attributes

object BoxProtocol {

  sealed trait BoxSendMethod {
    override def toString(): String = this match {
      case BoxSendMethod.PUSH => "PUSH"
      case BoxSendMethod.POLL => "POLL"
    }
  }

  object BoxSendMethod {
    case object PUSH extends BoxSendMethod
    case object POLL extends BoxSendMethod

    def withName(string: String) = string match {
      case "PUSH" => PUSH
      case "POLL" => POLL
    }    
  }

  
  case class RemoteBox(name: String, baseUrl: String)

  case class RemoteBoxName(value: String)

  case class BoxBaseUrl(value: String)
  
  case class ImageId(value: Long)

  case class Box(id: Long, name: String, token: String, baseUrl: String, sendMethod: BoxSendMethod) extends Entity

  case class OutboxEntry(id: Long, remoteBoxId: Long, transactionId: Long, sequenceNumber: Long, totalImageCount: Long, imageId: Long, failed: Boolean) extends Entity

  case class InboxEntry(id: Long, remoteBoxId: Long, transactionId: Long, receivedImageCount: Long, totalImageCount: Long) extends Entity
  
  case class PushImageData(transactionId: Long, sequenceNumber: Long, totalImageCount: Long, dataset: Attributes)
  
  sealed trait BoxRequest

  case class GenerateBoxBaseUrl(remoteBoxName: String) extends BoxRequest

  case class AddRemoteBox(remoteBox: RemoteBox) extends BoxRequest

  case class RemoveBox(boxId: Long) extends BoxRequest

  case object GetBoxes extends BoxRequest

  case class ValidateToken(token: String) extends BoxRequest
  
  case class UpdateInbox(token: String, transactionId: Long, sequenceNumber: Long, totalImageCount: Long) extends BoxRequest
  
  case class PollOutbox(token: String) extends BoxRequest
  
  case class SendImageToRemoteBox(remoteBoxId: Long, imageId: Long) extends BoxRequest
  
  case class GetOutboxEntry(token: String, transactionId: Long, sequenceNumber: Long) extends BoxRequest
  
  case class DeleteOutboxEntry(token: String, transactionId: Long, sequenceNumber: Long) extends BoxRequest

  case class RemoteBoxAdded(box: Box)

  case class BoxRemoved(boxId: Long)

  case class Boxes(boxes: Seq[Box])

  case class BoxBaseUrlGenerated(baseUrl: String)

  case class ValidToken(token: String)

  case class InvalidToken(token: String)
  
  case class InboxUpdated(token: String, transactionId: Long, sequenceNumber: Long, totalImageCount: Long)
  
  case object OutboxEmpty
  
  case class ImageSent(remoteBoxId: Long, imageId: Long)
  
  case object OutboxEntryNotFound
  
  case object OutboxEntryDeleted

}