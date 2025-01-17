package hmda.api.http.filing

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.event.LoggingAdapter
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes
import hmda.api.http.codec.institution.InstitutionCodec._
import hmda.api.http.codec.filing.FilingStatusCodec._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import hmda.api.http.directives.HmdaTimeDirectives
import hmda.util.http.FilingResponseUtils._
import hmda.messages.institution.InstitutionCommands.GetInstitutionDetails
import hmda.model.institution.InstitutionDetail
import hmda.persistence.institution.InstitutionPersistence

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import hmda.api.http.model.ErrorResponse
import io.circe.generic.auto._
import hmda.api.http.codec.ErrorResponseCodec._
import hmda.auth.OAuth2Authorization
import hmda.utils.YearUtils._

trait InstitutionHttpApi extends HmdaTimeDirectives {

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  val log: LoggingAdapter
  implicit val ec: ExecutionContext
  implicit val timeout: Timeout
  val sharding: ClusterSharding

  //institutions/<lei>
  def institutionReadPath(oAuth2Authorization: OAuth2Authorization): Route =
    oAuth2Authorization.authorizeToken { _ =>
      path("institutions" / Segment / "year" / Segment) { (lei, period) =>
        val institutionPersistence = {
          if (period == "2018") {
            sharding.entityRefFor(InstitutionPersistence.typeKey,
                                  s"${InstitutionPersistence.name}-$lei")
          } else {
            sharding.entityRefFor(
              InstitutionPersistence.typeKey,
              s"${InstitutionPersistence.name}-$lei-$period")
          }
        }

        val iDetails
          : Future[Option[InstitutionDetail]] = institutionPersistence ? (ref =>
          GetInstitutionDetails(ref))

        val filingDetailsF = for {
          i <- iDetails
        } yield i

        timedGet { uri =>
          if (!isValidYear(period.toInt)) {
            complete(
              ErrorResponse(500, s"Invalid Year Provided: $period", uri.path))
          } else {
            onComplete(filingDetailsF) {
              case Success(Some(institutionDetails)) =>
                complete(ToResponseMarshallable(institutionDetails))
              case Success(None) =>
                val errorResponse =
                  ErrorResponse(404,
                                s"Institution: $lei does not exist",
                                uri.path)
                complete(
                  ToResponseMarshallable(StatusCodes.NotFound -> errorResponse)
                )
              case Failure(error) =>
                failedResponse(StatusCodes.InternalServerError, uri, error)
            }

          }
        }
      }
    }

  def institutionRoutes(oAuth2Authorization: OAuth2Authorization): Route = {
    handleRejections(corsRejectionHandler) {
      cors() {
        encodeResponse {
          institutionReadPath(oAuth2Authorization)
        }
      }
    }
  }
}
