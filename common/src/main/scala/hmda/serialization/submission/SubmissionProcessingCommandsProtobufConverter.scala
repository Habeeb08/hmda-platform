package hmda.serialization.submission

import akka.actor.typed.ActorRefResolver
import hmda.messages.submission.SubmissionProcessingCommands._
import hmda.model.validation.MacroValidationError
import hmda.persistence.serialization.submission.SubmissionIdMessage
import hmda.persistence.serialization.submission.processing.commands._
import hmda.persistence.serialization.validation.ValidationErrorMessage
import hmda.serialization.submission.SubmissionProtobufConverter._
import hmda.serialization.validation.ValidationProtobufConverter._

object SubmissionProcessingCommandsProtobufConverter {

  def startUploadToProtobuf(cmd: StartUpload): StartUploadMessage = {
    StartUploadMessage(
      submissionIdToProtobuf(cmd.submissionId)
    )
  }

  def startUploadFromProtobuf(msg: StartUploadMessage): StartUpload = {
    StartUpload(
      submissionIdFromProtobuf(
        msg.submissionId.getOrElse(SubmissionIdMessage()))
    )
  }

  def completeUploadToProtobuf(cmd: CompleteUpload): CompleteUploadMessage = {
    CompleteUploadMessage(
      submissionIdToProtobuf(cmd.submissionId)
    )
  }

  def completeUploadFromProtobuf(msg: CompleteUploadMessage): CompleteUpload = {
    CompleteUpload(
      submissionIdFromProtobuf(
        msg.submissionId.getOrElse(SubmissionIdMessage()))
    )
  }

  def startParsingToProtobuf(cmd: StartParsing): StartParsingMessage = {
    StartParsingMessage(
      submissionIdToProtobuf(cmd.submissionId)
    )
  }

  def startParsingFromProtobuf(msg: StartParsingMessage): StartParsing = {
    StartParsing(
      submissionIdFromProtobuf(
        msg.submissionId.getOrElse(SubmissionIdMessage()))
    )
  }

  def persistHmdaRowParsedErrorToProtobuf(
      cmd: PersistHmdaRowParsedError,
      refResolver: ActorRefResolver): PersistHmdaRowParsedErrorMessage = {
    PersistHmdaRowParsedErrorMessage(
      cmd.rowNumber,
      cmd.estimatedULI,
      cmd.errors,
      cmd.maybeReplyTo match {
        case None      => ""
        case Some(ref) => refResolver.toSerializationFormat(ref)
      }
    )
  }

  def persistHmdaRowParsedErrorFromProtobuf(
      msg: PersistHmdaRowParsedErrorMessage,
      refResolver: ActorRefResolver): PersistHmdaRowParsedError = {
    PersistHmdaRowParsedError(
      msg.rowNumber,
      msg.estimatedULI,
      msg.errors.toList,
      if (msg.maybeReplyTo == "") None
      else Some(refResolver.resolveActorRef(msg.maybeReplyTo))
    )
  }

  def persisteMacroErrorToProtobuf(
      cmd: PersistMacroError,
      refResolver: ActorRefResolver): PersistMacroErrorMessage = {
    PersistMacroErrorMessage(
      submissionIdToProtobuf(cmd.submissionId),
      Some(validationErrorToProtobuf(cmd.validationError)),
      cmd.maybeReplyTo match {
        case None      => ""
        case Some(ref) => refResolver.toSerializationFormat(ref)
      }
    )
  }

  def persistMacroErrorFromProtobuf(
      msg: PersistMacroErrorMessage,
      refResolver: ActorRefResolver): PersistMacroError = {
    PersistMacroError(
      submissionIdFromProtobuf(
        msg.submissionId.getOrElse(SubmissionIdMessage())),
      validationErrorFromProtobuf(
        msg.validationError.getOrElse(ValidationErrorMessage()))
        .asInstanceOf[MacroValidationError],
      if (msg.maybeReplyTo == "") None
      else Some(refResolver.resolveActorRef(msg.maybeReplyTo))
    )
  }

  def getParsedWithErrorCountToProtobuf(
      cmd: GetParsedWithErrorCount,
      resolver: ActorRefResolver): GetParsedWithErrorCountMessage = {
    GetParsedWithErrorCountMessage(
      resolver.toSerializationFormat(cmd.replyTo)
    )
  }

  def getParsedWithErrorCountFromProtobuf(
      msg: GetParsedWithErrorCountMessage,
      resolver: ActorRefResolver): GetParsedWithErrorCount = {
    GetParsedWithErrorCount(
      resolver.resolveActorRef(msg.replyTo)
    )
  }

  def getParsingErrorsToProtobuf(
      cmd: GetParsingErrors,
      resolver: ActorRefResolver): GetParsingErrorsMessage = {
    GetParsingErrorsMessage(cmd.page,
                            resolver.toSerializationFormat(cmd.replyTo))
  }

  def getParsingErrorsFromProtobuf(
      msg: GetParsingErrorsMessage,
      resolver: ActorRefResolver): GetParsingErrors = {
    GetParsingErrors(msg.page, resolver.resolveActorRef(msg.replyTo))
  }

  def completeParsingToProtobuf(
      cmd: CompleteParsing): CompleteParsingMessage = {
    CompleteParsingMessage(
      submissionIdToProtobuf(cmd.submissionId)
    )
  }

  def completeParsingFromProtobuf(
      msg: CompleteParsingMessage): CompleteParsing = {
    CompleteParsing(
      submissionIdFromProtobuf(
        msg.submissionId.getOrElse(SubmissionIdMessage()))
    )
  }

  def completeParsingWithErrorsToProtobuf(
      cmd: CompleteParsingWithErrors): CompleteParsingWithErrorsMessage = {
    CompleteParsingWithErrorsMessage(
      submissionIdToProtobuf(cmd.submissionId)
    )
  }

  def completeParsingWithErrorsFromProtobuf(
      msg: CompleteParsingWithErrorsMessage): CompleteParsingWithErrors = {
    CompleteParsingWithErrors(
      submissionIdFromProtobuf(
        msg.submissionId.getOrElse(SubmissionIdMessage()))
    )
  }

  def startSyntacticalValidityToProtobuf(
      cmd: StartSyntacticalValidity): StartSyntacticalValidityMessage = {
    StartSyntacticalValidityMessage(
      submissionIdToProtobuf(cmd.submissionId)
    )
  }

  def startSyntacticalValidityFromProtobuf(
      msg: StartSyntacticalValidityMessage): StartSyntacticalValidity = {
    StartSyntacticalValidity(
      submissionIdFromProtobuf(
        msg.submissionId.getOrElse(SubmissionIdMessage()))
    )
  }

  def persistHmdaRowValidatedErrorToProtobuf(
      cmd: PersistHmdaRowValidatedError,
      refResolver: ActorRefResolver): PersistHmdaRowValidatedErrorMessage = {
    PersistHmdaRowValidatedErrorMessage(
      submissionIdToProtobuf(cmd.submissionId),
      cmd.rowNumber,
      cmd.validationErrors.map(error => validationErrorToProtobuf(error)),
      cmd.replyTo match {
        case None      => ""
        case Some(ref) => refResolver.toSerializationFormat(ref)
      }
    )
  }

  def persistHmdaRowValidatedErrorFromProtobuf(
      msg: PersistHmdaRowValidatedErrorMessage,
      refResolver: ActorRefResolver): PersistHmdaRowValidatedError = {
    PersistHmdaRowValidatedError(
      submissionIdFromProtobuf(
        msg.submissionId.getOrElse(SubmissionIdMessage())),
      msg.rowNumber,
      msg.validationErrors
        .map(error => validationErrorFromProtobuf(error))
        .toList,
      if (msg.replyTo == "") None
      else Some(refResolver.resolveActorRef(msg.replyTo))
    )
  }

  def getHmdaValidationErrorStateToProtobuf(cmd: GetHmdaValidationErrorState,
                                            actorRefResolver: ActorRefResolver)
    : GetHmdaValidationErrorStateMessage = {
    GetHmdaValidationErrorStateMessage(
      submissionIdToProtobuf(cmd.submissionId),
      actorRefResolver.toSerializationFormat(cmd.replyTo)
    )
  }

  def getHmdaValidationErrorStateFromProtobuf(
      msg: GetHmdaValidationErrorStateMessage,
      actorRefResolver: ActorRefResolver): GetHmdaValidationErrorState = {
    GetHmdaValidationErrorState(
      submissionIdFromProtobuf(
        msg.submissionId.getOrElse(SubmissionIdMessage())),
      actorRefResolver.resolveActorRef(msg.replyTo)
    )
  }

  def completeSyntacticalValidityToProtobuf(
      cmd: CompleteSyntacticalValidity): CompleteSyntacticalValidityMessage = {
    CompleteSyntacticalValidityMessage(
      submissionIdToProtobuf(cmd.submissionId)
    )
  }

  def completeSyntacticalValidityFromProtobuf(
      msg: CompleteSyntacticalValidityMessage): CompleteSyntacticalValidity = {
    CompleteSyntacticalValidity(
      submissionIdFromProtobuf(
        msg.submissionId.getOrElse(SubmissionIdMessage()))
    )
  }

  def startQualityToProtobuf(cmd: StartQuality): StartQualityMessage = {
    StartQualityMessage(
      submissionIdToProtobuf(cmd.submissionId)
    )
  }

  def startQualituFromProtobuf(msg: StartQualityMessage): StartQuality = {
    StartQuality(
      submissionIdFromProtobuf(
        msg.submissionId.getOrElse(SubmissionIdMessage()))
    )
  }

  def startMacroToProtobuf(cmd: StartMacro): StartMacroMessage = {
    StartMacroMessage(
      submissionIdToProtobuf(cmd.submissionId)
    )
  }

  def startMacroFromProtobuf(msg: StartMacroMessage): StartMacro = {
    StartMacro(
      submissionIdFromProtobuf(
        msg.submissionId.getOrElse(SubmissionIdMessage()))
    )
  }

  def completeQualityToProtobuf(
      cmd: CompleteQuality): CompleteQualityMessage = {
    CompleteQualityMessage(
      submissionIdToProtobuf(cmd.submissionId)
    )
  }

  def completeQualityFromProtobuf(
      msg: CompleteQualityMessage): CompleteQuality = {
    CompleteQuality(
      submissionIdFromProtobuf(
        msg.submissionId.getOrElse(SubmissionIdMessage()))
    )
  }

  def completeMacroToProtobuf(cmd: CompleteMacro): CompleteMacroMessage = {
    CompleteMacroMessage(
      submissionIdToProtobuf(cmd.submissionId)
    )
  }

  def completeMacroFromProtobuf(msg: CompleteMacroMessage): CompleteMacro = {
    CompleteMacro(
      submissionIdFromProtobuf(
        msg.submissionId.getOrElse(SubmissionIdMessage()))
    )
  }

  def verifyQualityToProtobuf(
      cmd: VerifyQuality,
      refResolver: ActorRefResolver): VerifyQualityMessage = {
    VerifyQualityMessage(
      submissionIdToProtobuf(cmd.submissionId),
      cmd.verified,
      refResolver.toSerializationFormat(cmd.replyTo)
    )
  }

  def verifyQualityFromProtobuf(
      msg: VerifyQualityMessage,
      refResolver: ActorRefResolver): VerifyQuality = {
    VerifyQuality(
      submissionIdFromProtobuf(
        msg.submissionId.getOrElse(SubmissionIdMessage())),
      msg.verified,
      refResolver.resolveActorRef(msg.replyTo)
    )
  }

  def verifyMacroToProtobuf(
      cmd: VerifyMacro,
      refResolver: ActorRefResolver): VerifyMacroMessage = {
    VerifyMacroMessage(
      submissionIdToProtobuf(cmd.submissionId),
      cmd.verified,
      refResolver.toSerializationFormat(cmd.replyTo)
    )
  }

  def verifyMacroFromProtobuf(msg: VerifyMacroMessage,
                              refResolver: ActorRefResolver): VerifyMacro = {
    VerifyMacro(
      submissionIdFromProtobuf(
        msg.submissionId.getOrElse(SubmissionIdMessage())),
      msg.verified,
      refResolver.resolveActorRef(msg.replyTo)
    )
  }

  def signSubmissionToProtobuf(
      cmd: SignSubmission,
      refResolver: ActorRefResolver): SignSubmissionMessage = {
    SignSubmissionMessage(
      submissionIdToProtobuf(cmd.submissionId),
      refResolver.toSerializationFormat(cmd.replyTo)
    )
  }

  def signSubmissionFromProtobuf(
      msg: SignSubmissionMessage,
      refResolver: ActorRefResolver): SignSubmission = {
    SignSubmission(
      submissionIdFromProtobuf(
        msg.submissionId.getOrElse(SubmissionIdMessage())),
      refResolver.resolveActorRef(msg.replyTo)
    )
  }

}
