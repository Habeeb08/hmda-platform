package hmda.validation.engine

import org.scalatest.{MustMatchers, PropSpec}
import org.scalatest.prop.PropertyChecks
import hmda.model.filing.ts.TsGenerators._
import TsEngine2018._
import hmda.model.validation.{
  SyntacticalValidationError,
  TsValidationError,
  ValidityValidationError
}
import hmda.validation.context.ValidationContext

class TsEngine2018Spec extends PropSpec with PropertyChecks with MustMatchers {

  property("Ts Validation Engine must pass all checks") {
    forAll(tsGen) { ts =>
      whenever(
        ts.contact.name != "" &&
          ts.contact.email != "" &&
          ts.contact.address.street != "" &&
          ts.contact.address.city != "" &&
          ts.institutionName != "") {
        val testContext = ValidationContext(None, Some(ts.year))
        val validation = checkAll(ts, ts.LEI, testContext, TsValidationError)
        validation.leftMap(errors => errors.toList.size mustBe 0)
      }
    }
  }

  property(
    "Ts Validation Engine must capture S300 (wrong id) and V602 (wrong quarter)") {
    forAll(tsGen) { ts =>
      whenever(
        ts.contact.name != "" &&
          ts.contact.email != "" &&
          ts.contact.address.street != "" &&
          ts.contact.address.city != "" &&
          ts.institutionName != "") {
        val testContext = ValidationContext(None, Some(ts.year))
        val validation =
          checkAll(ts.copy(id = 2, quarter = 2),
                   ts.LEI,
                   testContext,
                   TsValidationError)
        val errors =
          validation.leftMap(errors => errors.toList).toEither.left.get
        errors mustBe
          List(SyntacticalValidationError(ts.LEI, "S300", TsValidationError),
               ValidityValidationError(ts.LEI, "V602", TsValidationError))
      }
    }
  }

}
