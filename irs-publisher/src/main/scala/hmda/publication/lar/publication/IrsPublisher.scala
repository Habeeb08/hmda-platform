package hmda.publication.lar.publication

import akka.NotUsed
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.alpakka.s3.ApiVersion.ListBucketVersion2
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.alpakka.s3.{
  MemoryBufferType,
  MultipartUploadResult,
  S3Attributes,
  S3Settings
}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.util.ByteString
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.regions.AwsRegionProvider
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import hmda.model.census.Census
import hmda.model.filing.lar.LoanApplicationRegister
import hmda.model.filing.submission.SubmissionId
import hmda.parser.filing.lar.LarCsvParser
import hmda.publication.lar.model.{Msa, MsaMap, MsaSummary}
import hmda.query.HmdaQuery._
import hmda.util.streams.FlowUtils.framing
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.util.{Failure, Success}

sealed trait IrsPublisherCommand
case class PublishIrs(submissionId: SubmissionId) extends IrsPublisherCommand

object IrsPublisher {

  final val name: String = "IrsPublisher"

  val config = ConfigFactory.load()
  val bankFilter =
    ConfigFactory.load("application.conf").getConfig("filter")
  val accessKeyId = config.getString("aws.access-key-id")
  val secretAccess = config.getString("aws.secret-access-key ")
  val region = config.getString("aws.region")
  val bucket = config.getString("aws.public-bucket")
  val environment = config.getString("aws.environment")
  val year = config.getInt("hmda.lar.irs.year")

  val censusHost = config.getString("hmda.census.http.host")
  val censusPort = config.getInt("hmda.census.http.port")

  val awsCredentialsProvider = new AWSStaticCredentialsProvider(
    new BasicAWSCredentials(accessKeyId, secretAccess))

  val awsRegionProvider = new AwsRegionProvider {
    override def getRegion: String = region
  }

  val behavior: Behavior[IrsPublisherCommand] =
    Behaviors.setup { ctx =>
      val log = ctx.log
      val decider: Supervision.Decider = {
        case e: Throwable =>
          log.error(e.getLocalizedMessage)
          Supervision.Resume
      }
      implicit val ec = ctx.system.executionContext
      implicit val system = ctx.system.toUntyped
      implicit val materializer = ActorMaterializer(
        ActorMaterializerSettings(system).withSupervisionStrategy(decider))

      log.info(s"Started $name")

      val s3Settings = S3Settings(
        MemoryBufferType,
        None,
        awsCredentialsProvider,
        awsRegionProvider,
        false,
        None,
        ListBucketVersion2
      )

      def getCensus(hmdaCensus: String): Future[Msa] = {
        val request = HttpRequest(
          uri = s"http://$censusHost:$censusPort/census/tract/$hmdaCensus")

        for {
          r <- Http().singleRequest(request)
          census <- Unmarshal(r.entity).to[Census]
        } yield {
          val censusID =
            if (census.msaMd == 0) "-----" else census.msaMd.toString
          val censusName =
            if (census.name.isEmpty) "MSA/MD NOT AVAILABLE" else census.name
          Msa(censusID, censusName)
        }
      }

      Behaviors.receiveMessage {

        case PublishIrs(submissionId) =>
          log.info(s"Publishing IRS for $submissionId")

          val s3Sink: Sink[ByteString, Future[MultipartUploadResult]] =
            S3.multipartUpload(
                bucket,
                s"$environment/reports/disclosure/$year/${submissionId.lei}/nationwide/IRS.csv")
              .withAttributes(S3Attributes.settings(s3Settings))

          val msaSummarySource: Source[ByteString, NotUsed] = {
            val msaSummaryHeader: Source[ByteString, NotUsed] = {
              val header =
                "MSA/MD, MSA/MD Name, Total Lars, Total Amount ($000's), CONV, FHA, VA, FSA, Site Built, Manufactured, 1-4 units, 5+ units, Home Purchase, Home Improvement, Refinancing, Cash-out Refinancing, Other Purpose, Purpose N/A\n"
              Source.single(ByteString(header))
            }

            val msaSummaryContent: Source[ByteString, NotUsed] =
              readRawData(submissionId)
                .drop(1)
                .map(l => l.data)
                .map(ByteString(_))
                .via(framing("\n"))
                .map(_.utf8String)
                .map(_.trim)
                .map(s => LarCsvParser(s, true).getOrElse(LoanApplicationRegister()))
                .mapAsyncUnordered(1)(lar =>
                  getCensus(lar.geography.tract)
                    .map(msa => (lar, msa))) // order does not matter
                .fold(MsaMap()) {
                  case (map, (lar, msa)) => map.addLar(lar, msa)
                }
                .flatMapConcat { msaMap =>
                  val msas = msaMap.msas.values.toList
                  val msasByteContent: List[ByteString] =
                    msas.map(msa => ByteString(msa.toCsv + "\n"))
                  val msaSummaryByteContent: ByteString =
                    ByteString(MsaSummary.fromMsaCollection(msas).toCsv)
                  // first display the contents of all the msas followed by the msa summary (this will be the last line)
                  Source(msasByteContent) ++ Source.single(
                    msaSummaryByteContent)
                }

            msaSummaryHeader ++ msaSummaryContent
          }

          log.info(s"Uploading IRS summary to S3 for $submissionId")
          val result = msaSummarySource.runWith(s3Sink)
          result.onComplete {
            case Failure(e) =>
              log.error("Reading Cassandra journal failed", e)
            case Success(_) => log.info(s"Upload complete for $submissionId")
          }

          Behaviors.same
        case _ =>
          Behaviors.ignore
      }
    }
}
