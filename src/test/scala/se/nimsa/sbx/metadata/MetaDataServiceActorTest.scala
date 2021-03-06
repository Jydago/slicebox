package se.nimsa.sbx.metadata

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.dcm4che3.data.{Attributes, Tag, VR}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, WordSpecLike}
import se.nimsa.sbx.app.DbProps
import se.nimsa.sbx.app.GeneralProtocol.{Source, SourceType}
import se.nimsa.sbx.dicom.DicomHierarchy._
import se.nimsa.sbx.metadata.MetaDataProtocol._
import se.nimsa.sbx.seriestype.SeriesTypeDAO
import se.nimsa.sbx.util.TestUtil

import scala.collection.mutable.ListBuffer
import scala.slick.driver.H2Driver
import scala.slick.jdbc.JdbcBackend.Database

class MetaDataServiceActorTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  def this() = this(ActorSystem("MetaDataTestSystem"))

  val db = Database.forURL("jdbc:h2:mem:metadataserviceactortest;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
  val dbProps = DbProps(db, H2Driver)

  val seriesTypeDao = new SeriesTypeDAO(dbProps.driver)
  val metaDataDao = new MetaDataDAO(dbProps.driver)
  val propertiesDao = new PropertiesDAO(dbProps.driver)

  db.withSession { implicit session =>
    seriesTypeDao.create
    metaDataDao.create
    propertiesDao.create
  }

  val dicomData = TestUtil.testImageDicomData()

  val metaDataActorRef = TestActorRef(new MetaDataServiceActor(dbProps))
  val metaDataActor = metaDataActorRef.underlyingActor

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val patientEvents = new ListBuffer[Patient]()
  val studyEvents = new ListBuffer[Study]()
  val seriesEvents = new ListBuffer[Series]()
  val imageEvents = new ListBuffer[Image]()

  override def afterEach {
    db.withSession { implicit session => seriesTypeDao.clear; metaDataDao.clear; propertiesDao.clear }
    patientEvents.clear
    studyEvents.clear
    seriesEvents.clear
    imageEvents.clear
  }

  val listeningService = system.actorOf(Props(new Actor {

    override def preStart = {
      context.system.eventStream.subscribe(context.self, classOf[MetaDataAdded])
      context.system.eventStream.subscribe(context.self, classOf[MetaDataDeleted])
    }

    def receive = {
      case MetaDataAdded(patient, study, series, image, patientAdded, studyAdded, seriesAdded, imageAdded, source) =>
        if (patientAdded) patientEvents += patient
        if (studyAdded) studyEvents += study
        if (seriesAdded) seriesEvents += series
        if (imageAdded) imageEvents += image
      case MetaDataDeleted(patientMaybe, studyMaybe, seriesMaybe, imageMaybe) =>
        patientMaybe.foreach(patient => patientEvents.find(_.id == patient.id).foreach(patientEvents -= _))
        studyMaybe.foreach(study => studyEvents.find(_.id == study.id).foreach(studyEvents -= _))
        seriesMaybe.foreach(series => seriesEvents.find(_.id == series.id).foreach(seriesEvents -= _))
        imageMaybe.foreach(image => imageEvents.find(_.id == image.id).foreach(imageEvents -= _))
    }

  }))

  "The meta data service" should {

    "return an empty list of patients when no metadata exists" in {
      metaDataActorRef ! GetPatients(0, 10000, None, orderAscending = true, None, Array.empty, Array.empty, Array.empty)
      expectMsg(Patients(Seq()))
    }

    "return a list of one object when asking for all patients" in {
      val source = Source(SourceType.UNKNOWN, "unknown", -1)
      metaDataActorRef ! AddMetaData(dicomData.attributes, source)
      expectMsgType[MetaDataAdded]

      metaDataActorRef ! GetPatients(0, 10000, None, orderAscending = true, None, Array.empty, Array.empty, Array.empty)
      expectMsgPF() {
        case Patients(list) if list.size == 1 => true
      }
    }

    "emit the approprite xxxAdded events when adding meta data" in {
      val source = Source(SourceType.UNKNOWN, "unknown", -1)

      patientEvents shouldBe empty
      studyEvents shouldBe empty
      seriesEvents shouldBe empty
      imageEvents shouldBe empty

      metaDataActorRef ! AddMetaData(dicomData.attributes, source)
      expectMsgType[MetaDataAdded]

      Thread.sleep(500)

      patientEvents should have length 1
      studyEvents should have length 1
      seriesEvents should have length 1
      imageEvents should have length 1

      // changing series level

      val attributes2 = new Attributes(dicomData.attributes)
      attributes2.setString(Tag.SeriesInstanceUID, VR.UI, "seuid2")
      metaDataActorRef ! AddMetaData(attributes2, source)
      expectMsgType[MetaDataAdded]

      Thread.sleep(500)

      patientEvents should have length 1
      studyEvents should have length 1
      seriesEvents should have length 2
      imageEvents should have length 2

      // changing patient level

      val attributes3 = new Attributes(dicomData.attributes)
      attributes3.setString(Tag.PatientName, VR.PN, "pat2")
      metaDataActorRef ! AddMetaData(attributes3, source)
      expectMsgType[MetaDataAdded]

      Thread.sleep(500)

      patientEvents should have length 2
      studyEvents should have length 2
      seriesEvents should have length 3
      imageEvents should have length 3

      // duplicate, changing nothing

      metaDataActorRef ! AddMetaData(attributes3, source)
      expectMsgType[MetaDataAdded]

      Thread.sleep(500)

      patientEvents should have length 2
      studyEvents should have length 2
      seriesEvents should have length 3
      imageEvents should have length 3
    }

    "emit the approprite xxxDeleted events when deleting meta data" in {
      val source = Source(SourceType.UNKNOWN, "unknown", -1)

      metaDataActorRef ! AddMetaData(dicomData.attributes, source)
      val image1 = expectMsgPF() { case MetaDataAdded(_, _, _, im, _, _, _, _, _) => im }

      val attributes2 = new Attributes(dicomData.attributes)
      attributes2.setString(Tag.PatientName, VR.PN, "pat2")
      metaDataActorRef ! AddMetaData(attributes2, source)
      val image2 = expectMsgPF() { case MetaDataAdded(_, _, _, im, _, _, _, _, _) => im }

      val attributes3 = new Attributes(dicomData.attributes)
      attributes3.setString(Tag.StudyInstanceUID, VR.UI, "stuid2")
      metaDataActorRef ! AddMetaData(attributes3, source)
      val image3 = expectMsgPF() { case MetaDataAdded(_, _, _, im, _, _, _, _, _) => im }

      val attributes4 = new Attributes(dicomData.attributes)
      attributes4.setString(Tag.SeriesInstanceUID, VR.UI, "seuid2")
      metaDataActorRef ! AddMetaData(attributes4, source)
      val image4 = expectMsgPF() { case MetaDataAdded(_, _, _, im, _, _, _, _, _) => im }

      val attributes5 = new Attributes(dicomData.attributes)
      attributes5.setString(Tag.SOPInstanceUID, VR.UI, "sopuid2")
      metaDataActorRef ! AddMetaData(attributes5, source)
      val image5 = expectMsgPF() { case MetaDataAdded(_, _, _, im, _, _, _, _, _) => im }

      Thread.sleep(500)

      patientEvents should have length 2
      studyEvents should have length 3
      seriesEvents should have length 4
      imageEvents should have length 5

      metaDataActorRef ! DeleteMetaData(image5.id)
      expectMsgType[MetaDataDeleted]

      Thread.sleep(500)

      patientEvents should have length 2
      studyEvents should have length 3
      seriesEvents should have length 4
      imageEvents should have length 4

      metaDataActorRef ! DeleteMetaData(image4.id)
      expectMsgType[MetaDataDeleted]

      Thread.sleep(500)

      patientEvents should have length 2
      studyEvents should have length 3
      seriesEvents should have length 3
      imageEvents should have length 3

      metaDataActorRef ! DeleteMetaData(image3.id)
      expectMsgType[MetaDataDeleted]

      Thread.sleep(500)

      patientEvents should have length 2
      studyEvents should have length 2
      seriesEvents should have length 2
      imageEvents should have length 2

      metaDataActorRef ! DeleteMetaData(image2.id)
      expectMsgType[MetaDataDeleted]

      Thread.sleep(500)

      patientEvents should have length 1
      studyEvents should have length 1
      seriesEvents should have length 1
      imageEvents should have length 1

      metaDataActorRef ! DeleteMetaData(image1.id)
      expectMsgType[MetaDataDeleted]

      Thread.sleep(500)

      patientEvents shouldBe empty
      studyEvents shouldBe empty
      seriesEvents shouldBe empty
      imageEvents shouldBe empty
    }

    "support updating metadata without creating new metadata instances if key attributes are unchanged" in {
      val source = Source(SourceType.UNKNOWN, "unknown", -1)

      metaDataActorRef ! AddMetaData(dicomData.attributes, source)
      expectMsgType[MetaDataAdded]

      val attributes2 = new Attributes(dicomData.attributes)
      attributes2.setString(Tag.PatientBirthDate, VR.DA, "new date")
      attributes2.setString(Tag.StudyID, VR.LO, "new id")
      attributes2.setString(Tag.Modality, VR.CS, "new modality")
      attributes2.setString(Tag.InstanceNumber, VR.SS, "666")

      metaDataActorRef ! AddMetaData(attributes2, source)
      expectMsgType[MetaDataAdded]

      db.withSession { implicit session =>
        metaDataDao.patients should have length 1
        metaDataDao.studies should have length 1
        metaDataDao.series should have length 1
        metaDataDao.images should have length 1

        metaDataDao.patients.head.patientBirthDate.value shouldBe "new date"
        metaDataDao.studies.head.studyID.value shouldBe "new id"
        metaDataDao.series.head.modality.value shouldBe "new modality"
        metaDataDao.images.head.instanceNumber.value shouldBe "666"
      }
    }

    "support updating metadata and creating new metadata instances if key attributes are changed" in {
      val source1 = Source(SourceType.UNKNOWN, "unknown", -1)
      val source2 = Source(SourceType.SCP, "scp", -1)

      metaDataActorRef ! AddMetaData(dicomData.attributes, source1)
      expectMsgType[MetaDataAdded]

      val attributes2 = new Attributes(dicomData.attributes)
      attributes2.setString(Tag.SeriesInstanceUID, VR.UI, "new ui")

      metaDataActorRef ! AddMetaData(attributes2, source2)
      expectMsgType[MetaDataAdded]

      db.withSession { implicit session =>
        metaDataDao.patients should have length 1
        metaDataDao.studies should have length 1
        metaDataDao.series should have length 2
        metaDataDao.images should have length 2

        val seriesSources = propertiesDao.seriesSources
        seriesSources should have length 2
        seriesSources.head.source shouldBe source1
        seriesSources(1).source shouldBe source2
      }
    }

  }

}