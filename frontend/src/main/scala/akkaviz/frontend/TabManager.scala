package akkaviz.frontend

import akkaviz.frontend.components.{ClosableTab, Tab, ActorStateTab}
import org.scalajs.dom._
import org.scalajs.dom.raw.HTMLElement
import rx.{Var, Ctx}

import scala.scalajs.js

class TabManager(repo: ActorRepository, upstreamConnection: ApiConnection.Upstream)(implicit ctx: Ctx.Owner) {

  val openedTabs: js.Dictionary[Tab] = js.Dictionary.empty

  def attachTab[T <: Tab](tab: T): T = {
    tab match {
      case ct: ClosableTab =>
        handleClose(ct)
      case _ => ()
    }
    attachDom(tab)
    tab
  }

  def openActorDetails(actorRef: String): Unit = {
    activate(openedTabs.getOrElseUpdate(ActorStateTab.stateTabId(actorRef), createDetailTab(actorRef)))
  }

  def createDetailTab(actorRef: String): ActorStateTab = {
    val stateVar = repo.state(actorRef)
    val tab: ActorStateTab = new ActorStateTab(stateVar, upstreamConnection.send)
    handleClose(tab)
    attachDom(tab)
    tab
  }

  def handleClose(tab: ClosableTab): ClosableTab = {
    attachDom(tab)
    tab.tab.querySelector("a.close-tab").onClick({ () => close(tab) })
    tab.tab.querySelector("a[data-toggle]").addEventListener("click", handleMiddleClick(tab) _)
    tab
  }

  private[this] def attachDom(tab: Tab): Unit = {
    tab.attach(document.querySelector("#right-pane"))
  }

  def activate(tab: Tab): Unit = {
    document.querySelector(s"""a[href*="${tab.tabId}"]""").asInstanceOf[HTMLElement].click()
  }

  def close(target: ClosableTab): Unit = {
    if (target.isActive) activateSiblingOf(target)
    target.tab.parentNode.removeChild(target.tab)
    target.tabBody.parentNode.removeChild(target.tabBody)
    target.onClose()
    openedTabs.delete(target.tabId)
  }

  private[this] def activateSiblingOf(ct: ClosableTab): Unit = {
    Option(ct.tab.nextElementSibling).orElse(Option(ct.tab.previousElementSibling)).map { s =>
      s.querySelector("a[data-toggle]").asInstanceOf[HTMLElement]
    }.foreach { _.click() }

  }

  private[this] def handleMiddleClick(tab: ClosableTab)(e: MouseEvent): Unit = {
    if (e.button == 1) {
      e.preventDefault()
      close(tab)
    }
  }
}
