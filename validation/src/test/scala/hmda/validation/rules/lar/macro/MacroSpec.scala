package hmda.validation.rules.lar.`macro`

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import hmda.model.fi.lar.LoanApplicationRegister
import hmda.parser.fi.lar.LarGenerators
import hmda.validation.rules.lar.SummaryEditCheckSpec
import hmda.validation.rules.lar.`macro`.MacroEditTypes._
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.ExecutionContext

abstract class MacroSpec extends SummaryEditCheckSpec with BeforeAndAfterAll with LarGenerators {

  implicit val system: ActorSystem = ActorSystem("macro-edits-test")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher

  val lars = MacroTestData.lars

  val larSource: LoanApplicationRegisterSource = {
    Source.fromIterator(() => lars.toIterator)
  }

  override def afterAll(): Unit = {
    system.terminate()
  }

  protected def newLarSource(
    lars: List[LoanApplicationRegister],
    numOfRelevantLars: Int,
    relevantDef: LoanApplicationRegister => LoanApplicationRegister,
    irrelevantDef: LoanApplicationRegister => LoanApplicationRegister
  ) = {
    val relevantLars = lars
      .map(lar => relevantDef(lar))
      .take(numOfRelevantLars)
    val irrelevantLars = lars
      .map(lar => irrelevantDef(lar))
      .drop(numOfRelevantLars)
    val newLars = relevantLars ::: irrelevantLars
    Source.fromIterator(() => newLars.toIterator)
  }

  protected def setRelevantAmount(base: Int, numOfRelevantLars: Int, numOfLars: Int, setLarValue: (LoanApplicationRegister, Int) => LoanApplicationRegister, relevantMultiplier: Double)(lar: LoanApplicationRegister): LoanApplicationRegister = {
    val amount = ((numOfLars.toDouble / numOfRelevantLars.toDouble) * base * relevantMultiplier).toInt
    setLarValue(lar, amount)
  }

  protected def setIrrelevantAmount(base: Int, setLarAmount: (LoanApplicationRegister, Int) => LoanApplicationRegister)(lar: LoanApplicationRegister): LoanApplicationRegister = {
    setLarAmount(lar, base)
  }
}
