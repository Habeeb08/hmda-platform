package hmda.publisher.scheduler

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.stream.ActorMaterializer
import akka.stream.alpakka.s3.ApiVersion.ListBucketVersion2
import akka.stream.alpakka.s3._
import akka.stream.scaladsl._
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.regions.AwsRegionProvider
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import com.typesafe.config.ConfigFactory
import hmda.actor.HmdaActor
import hmda.query.DbConfiguration.dbConfig
import hmda.query.ts._
import hmda.publisher.query.component.{
  PublisherComponent2018,
  PublisherComponent2019
}
import hmda.publisher.scheduler.schedules.Schedules.{
  TsScheduler2018,
  TsScheduler2019
}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class TsScheduler
    extends HmdaActor
    with PublisherComponent2018
    with PublisherComponent2019 {

  implicit val ec = context.system.dispatcher
  implicit val materializer = ActorMaterializer()
  private val fullDate = DateTimeFormatter.ofPattern("yyyy-MM-dd-")
  def tsRepository2018 = new TransmittalSheetRepository2018(dbConfig)
  def tsRepository2019 = new TransmittalSheetRepository2019(dbConfig)

  val awsConfig =
    ConfigFactory.load("application.conf").getConfig("private-aws")
  val accessKeyId = awsConfig.getString("private-access-key-id")
  val secretAccess = awsConfig.getString("private-secret-access-key ")
  val region = awsConfig.getString("private-region")
  val bucket = awsConfig.getString("private-s3-bucket")
  val environment = awsConfig.getString("private-environment")
  val year = awsConfig.getString("private-year")
  val bankFilter =
    ConfigFactory.load("application.conf").getConfig("filter")
  val bankFilterList =
    bankFilter.getString("bank-filter-list").toUpperCase.split(",")
  val awsCredentialsProvider = new AWSStaticCredentialsProvider(
    new BasicAWSCredentials(accessKeyId, secretAccess))

  val awsRegionProvider = new AwsRegionProvider {
    override def getRegion: String = region
  }

  val s3Settings = S3Settings(
    MemoryBufferType,
    None,
    awsCredentialsProvider,
    awsRegionProvider,
    false,
    None,
    ListBucketVersion2
  )

  override def preStart() = {
    QuartzSchedulerExtension(context.system)
      .schedule("TsScheduler2018", self, TsScheduler2018)

    QuartzSchedulerExtension(context.system)
      .schedule("TsScheduler2019", self, TsScheduler2019)

  }

  override def postStop() = {
    QuartzSchedulerExtension(context.system).cancelJob("TsScheduler2018")
    QuartzSchedulerExtension(context.system).cancelJob("TsScheduler2019")

  }

  override def receive: Receive = {

    case TsScheduler2018 =>
      val now = LocalDateTime.now().minusDays(1)
      val formattedDate = fullDate.format(now)
      val fileName = s"$formattedDate" + "2018_ts.txt"
      val s3Sink =
        S3.multipartUpload(bucket, s"$environment/ts/$fileName")
          .withAttributes(S3Attributes.settings(s3Settings))

      val allResults: Future[Seq[TransmittalSheetEntity]] =
        tsRepository2018.getAllSheets(bankFilterList)

      val results: Future[MultipartUploadResult] = Source
        .fromFuture(allResults)
        .map(seek => seek.toList)
        .mapConcat(identity)
        .map(transmittalSheet => transmittalSheet.toRegulatorPSV + "\n")
        .map(s => ByteString(s))
        .runWith(s3Sink)

      results onComplete {
        case Success(result) => {
          log.info(
            "Pushing to S3: " + s"$bucket/$environment/ts/$fileName" + ".")
        }
        case Failure(t) =>
          println("An error has occurred getting TS Data 2018: " + t.getMessage)
      }

    case TsScheduler2019 =>
      val now = LocalDateTime.now().minusDays(1)
      val formattedDate = fullDate.format(now)
      val fileName = s"$formattedDate" + "2019_ts.txt"
      val s3Sink =
        S3.multipartUpload(bucket, s"$environment/ts/$fileName")
          .withAttributes(S3Attributes.settings(s3Settings))

      val allResults: Future[Seq[TransmittalSheetEntity]] =
        tsRepository2019.getAllSheets(bankFilterList)

      val results: Future[MultipartUploadResult] = Source
        .fromFuture(allResults)
        .map(seek => seek.toList)
        .mapConcat(identity)
        .map(transmittalSheet => transmittalSheet.toRegulatorPSV + "\n")
        .map(s => ByteString(s))
        .runWith(s3Sink)

      results onComplete {
        case Success(result) => {
          log.info(
            "Pushing to S3: " + s"$bucket/$environment/ts/$fileName" + ".")
        }
        case Failure(t) =>
          println("An error has occurred getting TS Data 2019: " + t.getMessage)
      }
  }
}
