package akkaviz.frontend.components

import akkaviz.frontend.{Router, FrontendUtil, ActorLink, ActorPath}
import akkaviz.frontend.ActorRepository.ActorState
import akkaviz.{rest, protocol}
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.{Element => domElement, console}
import rx.{Ctx, Rx, Var}

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.{Date, JSON}
import scalatags.JsDom.all._
import scala.concurrent.ExecutionContext.Implicits.global

class LinkStateTab(link: ActorLink)(implicit co: Ctx.Owner) extends ClosableTab {

  import LinkStateTab._
  import akkaviz.frontend.PrettyJson._

  import scalatags.rx.all._

  val tabId = stateTabId(link)
  val name = s"${FrontendUtil.shortActorName(link.from)} → ${FrontendUtil.shortActorName(link.to)}"

  private[this] val url = Router.messagesBetween(link.from, link.to)
  console.log(url)
  private[this] var loading: js.UndefOr[Future[dom.XMLHttpRequest]] = js.undefined

  private[this] def loadBetween(): Unit = {
    if (loading.isEmpty) {
      val f = Ajax.get(url)
      f.onComplete(_ => loading = js.undefined)
      f.onSuccess {
        case req: dom.XMLHttpRequest =>
          val content = upickle.default.read[List[rest.Received]](req.responseText)
          messagesTbody.innerHTML = ""
          content.foreach { rcv =>
            messagesTbody.appendChild(messageRow(rcv).render)
          }
      }
      f.onFailure {
        case _ =>
          messagesTbody.innerHTML = ""
          messagesTbody.appendChild(
            tr(td(colspan := 3, "Unable to download archive, please check server is connected to Cassandra.")).render
          )
      }
      loading = f
    }
  }

  private[this] def messageRow(rcv: rest.Received): Seq[Frag] = Seq(
    tr(
      td(FrontendUtil.shortActorName(rcv.from)),
      td(FrontendUtil.shortActorName(rcv.to)),
      td(new Date(rcv.timestamp.toDouble).toLocaleDateString())
    ),
    tr(
      td(colspan := 3)(pre(prettyPrintJson(rcv.payload)))
    )
  )

  private[this] val refreshButton = a(
    cls := "btn btn-default", href := "#", role := "button", float.right,
    span(
      `class` := "imgbtn glyphicon glyphicon-refresh", " "
    ),
    onclick := { () =>
      loadBetween()
    },
    "Refresh view"
  )

  private[this] val messagesTbody = tbody.render
  private[this] val rendered = div(
    cls := "panel-body",
    div(
      refreshButton,
      clear.both
    ),
    div(cls := "panel-body", id := "messagespanelbody",
      table(
        cls := "table table-striped table-hover",
        thead(
          tr(th("From"), th("To"), th("Time"))
        ), messagesTbody
      ))
  ).render

  tabBody.appendChild(rendered)

  loadBetween()

}

object LinkStateTab {
  def stateTabId(link: ActorLink): String = {
    s"actor-state-${(link.from + link.to).replaceAll("[\\/|\\.|\\\\|\\$]", "-").filterNot(_ == ':')}"
  }
}
