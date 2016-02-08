package akka.viz.events

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import akka.viz.events.types._

import scala.collection.immutable

class EventPublisherActor extends Actor with ActorLogging {

  var subscribers = immutable.Set[ActorRef]()
  var availableTypes = immutable.Set[Class[_ <: Any]]()
  var eventCounter = 0L
  var replayables: Vector[ReplayableEvent] = Vector.empty

  override def receive = storeIfReplayable andThen {
    case r: Received =>
      trackMsgType(r.message)
      broadcast(ReceivedWithId(nextEventNumber(), r.sender, r.receiver, r.message))

    case be: BackendEvent =>
      broadcast(be)

    case EventPublisherActor.Subscribe =>
      val s = sender()
      subscribers += s
      context.watch(s)
      s ! (if (EventSystem.isEnabled()) ReportingEnabled else ReportingDisabled)
      s ! AvailableMessageTypes(availableTypes.toList)
      replayables.foreach(s ! _)

    case EventPublisherActor.Unsubscribe =>
      unsubscribe(sender())

    case Terminated(s) =>
      unsubscribe(s)
  }

  def broadcast(backendEvent: BackendEvent): Unit = {
    subscribers.foreach(_ ! backendEvent)
  }

  def storeIfReplayable: PartialFunction[Any, Any] = {
    case r: ReplayableEvent =>
      replayables = replayables :+ r
      r
    case other => other
  }

  @inline
  private def nextEventNumber(): Long = {
    eventCounter += 1
    eventCounter
  }

  private def unsubscribe(s: ActorRef): Unit = {
    subscribers -= s
  }

  private def trackMsgType(msg: Any): Unit = {
    if (!availableTypes.contains(msg.getClass)) {
      availableTypes += msg.getClass
      subscribers.foreach(_ ! AvailableMessageTypes(availableTypes.toList))
    }
  }
}

object EventPublisherActor {

  case object Subscribe

  case object Unsubscribe

}
