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

package se.nimsa.sbx.box

import se.nimsa.sbx.model.Entity
import se.nimsa.sbx.dicom.DicomHierarchy.Image
import se.nimsa.sbx.anonymization.AnonymizationProtocol.TagValue
import se.nimsa.sbx.anonymization.AnonymizationProtocol.ImageTagValues
import org.dcm4che3.data.Attributes
import se.nimsa.sbx.app.GeneralProtocol.Destination

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

  sealed trait TransactionStatus {
    override def toString(): String = this match {
      case TransactionStatus.SENDING => "SENDING"
      case TransactionStatus.WAITING => "WAITING"
      case TransactionStatus.FAILED => "FAILED"
      case TransactionStatus.FINISHED => "FINISHED"
    }
  }

  object TransactionStatus {
    case object SENDING extends TransactionStatus
    case object WAITING extends TransactionStatus
    case object FAILED extends TransactionStatus
    case object FINISHED extends TransactionStatus

    def withName(string: String) = string match {
      case "SENDING" => SENDING
      case "WAITING" => WAITING
      case "FAILED" => FAILED
      case "FINISHED" => FINISHED
    }
  }

  case class RemoteBox(name: String, baseUrl: String)

  case class RemoteBoxConnectionData(name: String)

  case class Box(id: Long, name: String, token: String, baseUrl: String, sendMethod: BoxSendMethod, online: Boolean) extends Entity

  case class OutgoingTransaction(id: Long, remoteBoxId: Long, remoteBoxName: String, sentImageCount: Long, totalImageCount: Long, lastUpdated: Long, status: TransactionStatus) extends Entity {
    def incrementSent = copy(sentImageCount = this.sentImageCount + 1)
    def updateTimestamp = copy(lastUpdated = System.currentTimeMillis)
  }

  case class OutgoingImage(id: Long, outgoingTransactionId: Long, imageId: Long, sent: Boolean) extends Entity
  
  case class OutgoingTagValue(id: Long, outgoingImageId: Long, tagValue: TagValue) extends Entity
  
  case class OutgoingTransactionImage(transaction: OutgoingTransaction, image: OutgoingImage)
  
  case class IncomingTransaction(id: Long, remoteBoxId: Long, remoteBoxName: String, remoteTransactionId: Long, receivedImageCount: Long, totalImageCount: Long, lastUpdated: Long, status: TransactionStatus) extends Entity {
    def incrementReceived = copy(receivedImageCount = this.receivedImageCount + 1)
    def updateTimestamp = copy(lastUpdated = System.currentTimeMillis)
  }

  case class IncomingImage(id: Long, incomingTransactionId: Long, imageId: Long) extends Entity
  
  case class FailedOutgoingEntryImage(transactionImage: OutgoingTransactionImage, message: String)
  
  // case class PushImageData(transactionId: Long, imageId: Long, totalImageCount: Long, dataset: Attributes)
  
  
  sealed trait BoxRequest

  case class CreateConnection(remoteBoxConnectionData: RemoteBoxConnectionData) extends BoxRequest

  case class Connect(remoteBox: RemoteBox) extends BoxRequest

  case class RemoveBox(boxId: Long) extends BoxRequest

  case object GetBoxes extends BoxRequest

  case class GetBoxById(boxId: Long) extends BoxRequest
  
  case class GetBoxByToken(token: String) extends BoxRequest

  case class UpdateIncoming(box: Box, transactionId: Long, totalImageCount: Long, imageId: Long) extends BoxRequest

  case class PollOutgoing(box: Box) extends BoxRequest

  case class SendToRemoteBox(box: Box, imageTagValuesSeq: Seq[ImageTagValues]) extends BoxRequest

  case class GetOutgoingEntryImage(box: Box, transactionId: Long, imageId: Long) extends BoxRequest

  case class GetTransactionTagValues(imageId: Long, transactionId: Long) extends BoxRequest

  case class MarkOutgoingImageAsSent(box: Box, transactionImage: OutgoingTransactionImage) extends BoxRequest

  case class MarkOutgoingTransactionAsFailed(box: Box, failedEntryImage: FailedOutgoingEntryImage) extends BoxRequest
  
  case object GetIncoming extends BoxRequest

  case object GetOutgoing extends BoxRequest

  case class RemoveIncomingEntry(incomingEntryId: Long) extends BoxRequest
  
  case class RemoveOutgoingEntry(outgoingEntryId: Long) extends BoxRequest

  case class GetImagesForIncomingEntry(incomingEntryId: Long) extends BoxRequest
  
  case class GetImagesForOutgoingEntry(outgoingEntryId: Long) extends BoxRequest
  
  case class GetIncomingEntryForImageId(imageId: Long) extends BoxRequest
  
  
  case class IncomingEntryRemoved(incomingEntryId: Long)

  case class OutgoingEntryRemoved(outgoingEntryId: Long)

  case class RemoteBoxAdded(box: Box)
  
  case class BoxRemoved(boxId: Long)

  case class Boxes(boxes: Seq[Box])

  case class IncomingUpdated(transaction: IncomingTransaction)

  case object OutgoingEmpty

  case class ImagesAddedToOutgoing(remoteBoxId: Long, imageIds: Seq[Long])

  case object OutgoingImageMarkedAsSent

  case object OutgoingTransactionMarkedAsFailed
  
  case class Incoming(transactions: Seq[IncomingTransaction])

  case class Outgoing(transactions: Seq[OutgoingTransaction])

  // box push actor internal messages

  case object PollOutgoing

  case class FileSent(transactionImage: OutgoingTransactionImage)

  case class FileSendFailed(transactionImage: OutgoingTransactionImage, statusCode: Int, e: Exception)

  // box poll actor internal messages

  case object PollRemoteBox

  case object RemoteOutgoingEmpty

  case class RemoteOutgoingEntryImageFound(transactionImage: OutgoingTransactionImage)

  case class PollRemoteBoxFailed(e: Throwable)

  case class RemoteOutgoingFileFetched(transactionImage: OutgoingTransactionImage, imageId: Long)

  case class FetchFileFailed(transactionImage: OutgoingTransactionImage, e: Throwable)

  case class HandlingFetchedFileFailed(transactionImage: OutgoingTransactionImage, e: Throwable)
}
