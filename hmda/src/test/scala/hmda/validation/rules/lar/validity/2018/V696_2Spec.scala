package hmda.validation.rules.lar.validity.eighteen

import hmda.model.filing.lar.LarGenerators._
import hmda.model.filing.lar.LoanApplicationRegister
import hmda.model.filing.lar.enums._
import hmda.validation.rules.EditCheck
import hmda.validation.rules.lar.LarEditCheckSpec

class V696_2Spec extends LarEditCheckSpec {
  override def check: EditCheck[LoanApplicationRegister] = V696_2

  property("AUS Result fields must be valid") {
    forAll(larGen) { lar =>
      val validLar = lar.copy(
        ausResult = lar.ausResult.copy(
          ausResult1 = ApproveEligible,
          ausResult2 = EmptyAUSResultValue,
          ausResult3 = EmptyAUSResultValue,
          ausResult4 = EmptyAUSResultValue,
          ausResult5 = EmptyAUSResultValue
        ))
      validLar.mustPass

      val invalidLar1 = validLar.copy(
        ausResult = validLar.ausResult.copy(ausResult1 = EmptyAUSResultValue))
      invalidLar1.mustFail

      val invalidLar2 = validLar.copy(
        ausResult = validLar.ausResult.copy(
          ausResult2 = AutomatedUnderwritingResultNotApplicable))
      invalidLar2.mustFail

      val invalidLar3 = validLar.copy(
        ausResult = validLar.ausResult.copy(
          ausResult3 = InvalidAutomatedUnderwritingResultCode))
      invalidLar3.mustFail
    }
  }
}
