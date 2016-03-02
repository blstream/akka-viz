package akkaviz.frontend.components

import akkaviz.frontend.ActorRepository.ActorState
import akkaviz.frontend.DOMGlobalScope.$
import akkaviz.frontend.FrontendUtil.actorComponent
import akkaviz.frontend.{DOMGlobalScope, FrontendUtil, PrettyJson}
import akkaviz.protocol.{ActorFailure, Received}
import org.scalajs.dom.html._
import org.scalajs.dom.raw.MouseEvent
import org.scalajs.dom.{Element => domElement, Node, console}
import rx.{Rx, Var}

import scala.collection.immutable.Queue
import scala.scalajs.js
import scala.scalajs.js.{JSON, ThisFunction0, ThisFunction1}
import scala.util.Try
import scalatags.JsDom.all._
import js.Dynamic.{newInstance => jsNew, literal}

trait Component {
  def render: Element
}

trait OnOffWithLabel {
  lazy val lbl = span().render
  lazy val inp = input(tpe := "checkbox").render

  lazy val stateBtn = div(
    `class` := "togglebutton",
    label(
      inp, lbl
    )
  )
}

class ActorSelector(
    seenActors: Var[Set[String]],
    selectedActors: Var[Set[String]],
    currentActorState: (String) => Var[ActorState],
    actorFailures: Var[Seq[ActorFailure]]
) extends PrettyJson with Component {

  val popoverContent: ThisFunction0[domElement, Node] = (that: domElement) => {
    val actor: String = that.getAttribute("data-actor")
    val content = div().render
    val stateVar = currentActorState(actor)
    stateVar.trigger {
      import DOMGlobalScope.Rickshaw
      val state = stateVar.now
      val graphElem = pre().render
      val graph = jsNew(Rickshaw.Graph)(js.Dynamic.literal(
        element = graphElem,
        height = 150,
        series = js.Array(
          js.Dynamic.literal(
            color = "lightblue",
            data = state.throughputLog
          )
        )
      ))

      val xAxis = jsNew(Rickshaw.Graph.Axis.Time)(literal(
        graph = graph
      ))

      val yAxis = jsNew(Rickshaw.Graph.Axis.Y)(literal(
        graph = graph
      ))

      val renderedState = Seq[Frag](
        div(strong("Class: "), state.className.getOrElse[String]("Unknown class")),
        div(strong("Is dead: "), state.isDead.toString),
        div(strong("Internal state: "), pre(state.internalState.map(prettyPrintJson).getOrElse[String]("Internal state unknown"))),
        div(strong("Is FSM: "), state.fsmState.isDefined.toString),
        state.fsmState.map[Frag] {
          fsm =>
            Seq(
              div(strong("FSM State: "), pre(prettyPrintJson(fsm.currentState))),
              div(strong("FSM Data: "), pre(prettyPrintJson(fsm.currentData)))
            )
        }.getOrElse(()),
        div(strong("Mailbox size: "), state.mailboxSize.map(_.toString).getOrElse[String]("Unknown")),
        div(strong("Last updated: "), state.lastUpdatedAt.toISOString()),
        div(strong("Throughput:"), graphElem)
      ).render

      content.innerHTML = ""
      content.appendChild(renderedState)
      graph.render()
      xAxis.render()
      yAxis.render()
    }

    Seq[Frag](
      h5(actor),
      content
    ).render
  }

  val popoverOptions = js.Dictionary(
    "content" -> popoverContent,
    "trigger" -> "hover",
    "placement" -> "right",
    "html" -> true
  )

  def failureTable(failures: Seq[ActorFailure]) =
    table(
      id := "failures-table",
      `class` := "table",
      thead(
        tr(th("Exception", cls := "col-md-6"), th("Supervisor decision", cls := "col-md-1"), th("Time", cls := "col-md-5"))
      ),
      tbody(
        for (f <- failures)
          yield tr(td(f.cause), td(f.decision), td(f.ts))
      )
    ).render

  def exceptionsButton(actorName: String, failures: Seq[ActorFailure]) =
    span(
      style := "color: red",
      `class` := "glyphicon glyphicon-exclamation-sign",
      "data-toggle".attr := "modal",
      "data-target".attr := "#failures-modal",
      onclick := { () =>
        $("#actor-name").html(actorName)
        $("#actor-failures").html(failureTable(failures))
      }
    )

  def actorExceptionsIndicator(actorName: String, failures: Seq[ActorFailure]): _root_.scalatags.JsDom.Modifier =
    if (failures.isEmpty) ""
    else span(b(s"${failures.length} "), exceptionsButton(actorName, failures))

  val actorsObs = Rx.unsafe {
    (seenActors(), selectedActors(), actorFailures())
  }.trigger {
    val seen = seenActors.now.toList.sorted
    val selected = selectedActors.now

    val content = seen.map {
      actorName =>
        val isSelected = selected.contains(actorName)
        val element = tr(
          td(input(`type` := "checkbox", if (isSelected) checked else (),
            onclick := {
              () => toggleActor(actorName)
            })),
          td(actorComponent(actorName)),
          td(actorExceptionsIndicator(actorName, actorFailures.now.filter(_.actorRef == actorName)))
        )(data("actor") := actorName).render

        DOMGlobalScope.$(element).popover(popoverOptions)
        element
    }

    actorTreeTbody.innerHTML = ""
    actorTreeTbody.appendChild(content.render)
  }

  def toggleActor(actorPath: String): Unit = {
    if (selectedActors.now contains actorPath) {
      console.log(s"Unselected '$actorPath' actor")
      selectedActors() = selectedActors.now - actorPath
    } else {
      console.log(s"Selected '$actorPath' actor")
      selectedActors() = selectedActors.now + actorPath
    }
  }

  lazy val actorTreeTbody = tbody().render

  def clearActorFilters: ThisFunction0[domElement, Unit] = { self: domElement =>
    selectedActors() = Set.empty
  }

  def selectAllActorFilters: ThisFunction0[domElement, Unit] = { self: domElement =>
    selectedActors() = seenActors.now
  }

  def regexActorFilter: ThisFunction0[domElement, Unit] = { self: domElement =>
    val input = self.asInstanceOf[Input].value
    Try(input.r).foreach { r =>
      selectedActors() = seenActors.now.filter(_.matches(r.regex))
    }
  }

  def render = {
    div(cls := "panel-body", id := "actortree",
      table(
        cls := "table table-striped table-hover",
        thead(
          tr(th(), th("Actor", p(
            float.right,
            input(id := "actorfilter-regex", size := 12, tpe := "text", placeholder := "Filter...", marginRight := 1.em, onkeyup := regexActorFilter),
            a(href := "#", id := "actorfilter-select-all", "all", onclick := selectAllActorFilters),
            " | ",
            a(href := "#", id := "actorfilter-select-none", "none", onclick := clearActorFilters)
          )))
        ),
        actorTreeTbody
      )).render
  }
}

class MessageFilter(
    seenMessages: Var[Set[String]],
    selectedMessages: Var[Set[String]],
    selectedActors: Var[Set[String]]
) extends Component {
  val messagesObs = Rx.unsafe {
    (seenMessages(), selectedMessages())
  }.triggerLater {

    val seen = seenMessages.now.toList.sorted
    val selected = selectedMessages.now

    val content = seen.map {
      clazz =>
        val contains = selected(clazz)
        tr(
          td(input(`type` := "checkbox", if (contains) checked else ())),
          td(if (contains) b(clazz) else clazz),
          onclick := {
            () =>
              console.log(s"Toggling ${clazz} now it will be ${!contains}")
              selectedMessages() = if (contains) selected - clazz else selected + clazz
          }
        )
    }

    messagesTbody.innerHTML = ""
    messagesTbody.appendChild(content.render)
  }

  lazy val messagesTbody = tbody().render

  def render = {
    div(cls := "panel-body", id := "messagefilter",
      table(
        cls := "table table-striped table-hover",
        thead(
          tr(th(), th("Class", p(
            float.right,
            input(id := "messagefilter-regex", size := 12, tpe := "text", placeholder := "Filter...", marginRight := 1.em, onkeyup := regexMessageFilter),
            a(href := "#", id := "messagefilter-select-all", "all", onclick := selectAllMessageFilters),
            " | ",
            a(href := "#", id := "messagefilter-select-none", "none", onclick := clearMessageFilters)
          )))
        ),
        messagesTbody
      )).render
  }

  def clearMessageFilters: ThisFunction0[domElement, Unit] = { _: domElement =>
    selectedMessages() = Set.empty
  }

  def selectAllMessageFilters: ThisFunction0[domElement, Unit] = { _: domElement =>
    selectedMessages() = seenMessages.now
  }

  def regexMessageFilter(): ThisFunction0[Input, Unit] = { self: Input =>
    val input = self.value
    Try(input.r).foreach { r =>
      selectedMessages() = seenMessages.now.filter(_.matches(r.regex))
    }
  }

}

class MessagesPanel(selectedActors: Var[Set[String]]) extends Component with PrettyJson {
  val ShowMoreLength = 200

  private val msgQueue = Var[Queue[Received]](Queue.empty)

  private var lastDisplayed = 0L

  def messageReceived(rcv: Received): Unit = {
    val selected = selectedActors.now
    if (selected.contains(rcv.sender) || selected.contains(rcv.receiver)) {
      if (messagesTbody.childNodes.length < 50) {
        messagesTbody.appendChild(messageRow(rcv).render)
        lastDisplayed = rcv.eventId
      } else {
        msgQueue() = msgQueue.now.enqueue(rcv)
      }
    }
  }

  val showMoreRow = tr(cell, onclick := displayMoreMessages).render
  lazy val cell = td(colspan := 3, fontStyle.italic).render

  msgQueue.foreach { q =>
    if (q.headOption.exists(_.eventId > lastDisplayed))
      cell.innerHTML = s"${q.length} messages not shown, click to display more"
    else cell.innerHTML = ""
  }

  def displayMoreMessages: ThisFunction1[TableRow, MouseEvent, Unit] = { (row: TableRow, ev: MouseEvent) =>
    ev.preventDefault()
    val (portion, q) = (msgQueue.now.take(ShowMoreLength), msgQueue.now.drop(ShowMoreLength))
    msgQueue() = q
    portion.foreach { rcv =>
      messagesTbody.appendChild(messageRow(rcv).render)
      lastDisplayed = rcv.eventId
    }

    ()
  }

  def toggleVisibility(e: domElement): Unit = {
    val elem = e.asInstanceOf[Element]
    if (elem.style.display == "none")
      elem.style.display = ""
    else elem.style.display = "none"
  }

  val toggleMessageDetails = (mouseEvent: MouseEvent) => {
    mouseEvent.preventDefault()
    console.log(mouseEvent.srcElement)
    FrontendUtil.findParentWithAttribute(mouseEvent.srcElement, "data-message").foreach {
      row =>
        val nextRow = row.nextElementSibling
        if (nextRow == null || nextRow.hasAttribute("data-message")) {
          val payload = row.getAttribute("data-message")
          val detailsRow = tr(
            td(
              colspan := 3,
              div(pre(prettyPrintJson(payload)))
            )
          ).render
          row.parentNode.insertBefore(detailsRow, nextRow)
        } else {
          toggleVisibility(nextRow)
        }
    }
  }

  def messageRow(rcv: Received) = {
    tr(
      "data-message".attr := rcv.payload.getOrElse(""),
      `class` := "tgl",
      td(actorComponent(rcv.sender)),
      td(actorComponent(rcv.receiver)),
      td(rcv.payloadClass, if (!rcv.handled) unhandledIndicator else ""),
      onclick := toggleMessageDetails
    )
  }

  val unhandledIndicator = span(
    style := "color: orange; vertical-align: middle",
    `class` := "glyphicon glyphicon-exclamation-sign",
    title := "Unhandled message"
  )

  selectedActors.trigger {
    if (selectedActors.now.isEmpty) {
      messagePanelTitle.innerHTML = s"Select actor to show its messages"
    } else {
      messagePanelTitle.innerHTML = s"Messages"
    }
  }
  lazy val messagePanelTitle = span("Messages").render
  lazy val messagePanelHeader = div(
    cls := "panel-heading", id := "messagespaneltitle",
    messagePanelTitle,
    a(href := "#", float.right, onclick := clearMessages, i(`class` := "material-icons", "delete"))
  ).render
  lazy val messagesTbody = tbody().render

  val clearMessages = () => {
    messagesTbody.innerHTML = ""
    msgQueue() = Queue.empty
  }

  override def render: Element = {
    div(
      cls := "panel panel-default",
      messagePanelHeader,
      div(cls := "panel-body", id := "messagespanelbody",
        table(
          cls := "table table-striped table-hover",
          thead(
            tr(th("From"), th("To"), th("Class"))
          ), messagesTbody, tfoot(showMoreRow)
        ))
    ).render
  }
}

