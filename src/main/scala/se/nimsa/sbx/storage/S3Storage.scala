package se.nimsa.sbx.storage

import java.io.{ByteArrayOutputStream, InputStream}
import java.nio.file.{Files, Path}
import javax.imageio.ImageIO

import se.nimsa.sbx.dicom.DicomHierarchy.Image
import se.nimsa.sbx.dicom.DicomUtil._
import se.nimsa.sbx.dicom.{DicomData, DicomUtil, ImageAttribute}
import se.nimsa.sbx.storage.StorageProtocol.ImageInformation

import scala.util.control.NonFatal

/**
  * Service that stores DICOM files on AWS S3.
  * @param s3Prefix prefix for keys
  * @param bucket S3 bucket
  */
class S3Storage(val bucket: String, val s3Prefix: String) extends StorageService {

  val s3Client = new S3Facade(bucket)

  private def s3Id(image: Image) =
    s3Prefix + "/" + imageName(image)


  override def storeDicomData(dicomData: DicomData, image: Image): Boolean = {
    val storedId = s3Id(image)
    val overwrite = s3Client.exists(storedId)
    try saveDicomDataToS3(dicomData, storedId) catch {
      case NonFatal(e) =>
        throw new IllegalArgumentException("Dicom data could not be stored", e)
    }
    overwrite
  }

  private def saveDicomDataToS3(dicomData: DicomData, s3Key: String): Unit = {
    val os = new ByteArrayOutputStream()
    try saveDicomData(dicomData, os) catch {
      case NonFatal(e) =>
        throw new IllegalArgumentException("Dicom data could not be stored", e)
    }
    val buffer = os.toByteArray
    s3Client.upload(s3Key, buffer)
  }

  override def deleteFromStorage(image: Image): Unit = s3Client.delete(s3Id(image))

  override def readDicomData(image: Image, withPixelData: Boolean): DicomData = {
    val s3InputStream = s3Client.get(s3Id(image))
    loadDicomData(s3InputStream, withPixelData)
  }

  override def readImageAttributes(image: Image): List[ImageAttribute] = {
    val s3InputStream = s3Client.get(s3Id(image))
    DicomUtil.readImageAttributes(loadDicomData(s3InputStream, withPixelData = false).attributes)
  }

  override def readImageInformation(image: Image): ImageInformation = {
    val s3InputStream = s3Client.get(s3Id(image))
    super.readImageInformation(s3InputStream)
  }

  override def readPngImageData(image: Image, frameNumber: Int, windowMin: Int, windowMax: Int, imageHeight: Int): Array[Byte] = {
    val s3InputStream = s3Client.get(s3Id(image))
    val iis = ImageIO.createImageInputStream(s3InputStream)
    super.readPngImageData(iis, frameNumber, windowMin, windowMax, imageHeight)
  }

  override def imageAsInputStream(image: Image): InputStream = {
    val s3InputStream = s3Client.get(s3Id(image))
    s3InputStream
  }

}
