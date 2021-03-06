package akkaviz

import java.nio.ByteBuffer

import scala.collection.immutable.{List, Set}
import scala.concurrent.duration.{Duration, FiniteDuration}

package object protocol {
  sealed trait ApiServerMessage

  case class Received(eventId: Long, sender: String, receiver: String, payloadClass: String, payload: Option[String], handled: Boolean) extends ApiServerMessage

  case class AvailableClasses(availableClasses: Set[String]) extends ApiServerMessage

  case class Spawned(ref: String) extends ApiServerMessage

  case class ActorSystemCreated(systemName: String) extends ApiServerMessage

  case class Instantiated(ref: String, clazz: String) extends ApiServerMessage

  case class MailboxStatus(owner: String, size: Int) extends ApiServerMessage

  case class FSMTransition(
    ref: String,
    currentState: String,
    currentStateClass: String,
    currentData: String,
    currentDataClass: String,
    nextState: String,
    nextStateClass: String,
    nextData: String,
    nextDataClass: String
  ) extends ApiServerMessage

  case class CurrentActorState(ref: String, state: String) extends ApiServerMessage

  case class ReceiveDelaySet(current: Duration) extends ApiServerMessage

  case class Killed(ref: String) extends ApiServerMessage

  case class ActorFailure(
    actorRef: String,
    cause: String,
    decision: String,
    ts: Long
  ) extends ApiServerMessage

  case class Question(
    id: Long,
    sender: Option[String],
    actorRef: String,
    message: String
  ) extends ApiServerMessage

  sealed trait AskResult

  case class Answer(questionId: Long, message: String) extends ApiServerMessage with AskResult

  case class AnswerFailed(questionId: Long, ex: String) extends ApiServerMessage with AskResult

  case object ReportingEnabled extends ApiServerMessage

  case object ReportingDisabled extends ApiServerMessage

  case object Ping extends ApiServerMessage

  case class SnapshotAvailable(
    live: Map[String, Option[String]],
    dead: Map[String, Option[String]],
    receivedFrom: Set[(String, String)]
  ) extends ApiServerMessage

  case class ThroughputMeasurement(actorRef: String, msgPerSecond: Double, timestamp: Long) extends ApiServerMessage

  case class Restarted(ref: String) extends ApiServerMessage

  sealed trait ApiClientMessage

  case class SetAllowedMessages(allowedClasses: Set[String]) extends ApiClientMessage

  case class SetReceiveDelay(duration: FiniteDuration) extends ApiClientMessage

  case class SetEnabled(isEnabled: Boolean) extends ApiClientMessage

  case class ObserveActors(actors: Set[String]) extends ApiClientMessage

  case class RefreshInternalState(ref: String) extends ApiClientMessage

  case class PoisonPillActor(ref: String) extends ApiClientMessage

  case class KillActor(ref: String) extends ApiClientMessage

  object IO {

    import boopickle.Default._

    private[this] implicit val serverPickler: Pickler[ApiServerMessage] = generatePickler[ApiServerMessage]
    private[this] implicit val clientPickler: Pickler[ApiClientMessage] = generatePickler[ApiClientMessage]

    def readServer(bytes: ByteBuffer): ApiServerMessage = {
      Unpickle[ApiServerMessage].fromBytes(bytes)
    }

    def readClient(bytes: ByteBuffer): ApiClientMessage = {
      Unpickle[ApiClientMessage].fromBytes(bytes)
    }

    def write(msg: ApiServerMessage): ByteBuffer = {
      Pickle.intoBytes(msg)
    }

    def write(msg: ApiClientMessage): ByteBuffer = {
      Pickle.intoBytes(msg)
    }

  }

}

package object rest {

  case class Received(timestamp: Long, direction: String, from: String, to: String, payload: String)

}