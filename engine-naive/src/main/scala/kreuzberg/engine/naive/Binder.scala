package kreuzberg.engine.naive

import kreuzberg.*
import kreuzberg.engine.naive.utils.MutableMultimap
import kreuzberg.dom.*
import kreuzberg.engine.common.UpdatePath.Change
import kreuzberg.engine.common.{
  Assembler,
  UpdatePath,
  BrowserDrawer,
  ComponentNode,
  ModelValues,
  SimpleServiceRepository,
  TreeNode
}

import scala.collection.mutable
import scala.util.control.NonFatal

object Binder {

  /** Activate a Node on a root element, starting the whole magic. */
  def runOnLoaded(component: Component, rootId: String): Unit = {
    org.scalajs.dom.document.addEventListener(
      "DOMContentLoaded",
      { (e: ScalaJsEvent) =>
        Logger.info("Initializing naive Kreuzberg engine")
        val rootElement = org.scalajs.dom.document.getElementById(rootId)
        val binder      = Binder(rootElement, component)
        binder.run()
      }
    )
  }
}

/** Binds a root element to a Node. */
class Binder(rootElement: ScalaJsElement, main: Component) extends EventManagerDelegate {
  private var _modelValues: ModelValues       = ModelValues()
  private val _serviceRepo: ServiceRepository = new SimpleServiceRepository()
  private var _tree: TreeNode                 = TreeNode.empty

  override def modelValues: ModelValues = _modelValues

  override def onIterationEnd(modelValues: ModelValues, changedModels: Set[Identifier]): Unit = {
    val before = _modelValues
    _modelValues = modelValues
    redrawChanged(changedModels, before)
  }

  override def locate(componentId: Identifier): ScalaJsElement = {
    browser.findElement(componentId)
  }

  private val browser      = new BrowserDrawer(rootElement)
  private val eventManager = new EventManager(this)

  def run(): Unit = {
    redraw()
  }

  private def redraw(): Unit = {
    Logger.debug("Starting Redraw")
    val tree = Assembler.tree(main)
    browser.drawRoot(tree)
    _tree = tree
    eventManager.clear()
    eventManager.activateEvents(tree)
    garbageCollect()
    Logger.debug("End Redraw")
  }

  private given assemblerContext: AssemblerContext = new AssemblerContext {
    override def value[M](model: Subscribeable[M]): M = _modelValues.value(model)

    override def service[S](using provider: Provider[S]): S = _serviceRepo.service
  }

  private def redrawChanged(changedModels: Set[Identifier], before: ModelValueProvider): Unit = {
    val path = UpdatePath.build(_tree, changedModels, before)
    if (path.isEmpty) {
      return
    }

    _tree = path.tree

    // Rendering
    browser.drawUpdatePath(path)

    // Activating Events
    for {
      change <- path.changes
      node   <- change.nodes
    } {
      Logger.trace(s"Activating events on node ${node.id}")
      eventManager.activateEvents(node)
    }

    garbageCollect()
  }

  private def garbageCollect(): Unit = {
    val referencedComponents = (_tree.allReferencedComponentIds ++ Iterator(Identifier.RootComponent)).toSet

    val referencedModels = _tree.allSubscriptions.map(_._1).toSet

    val stateUpdated = _modelValues.copy(
      modelValues = _modelValues.modelValues.view.filterKeys(referencedModels.contains).toMap
    )

    eventManager.garbageCollect(referencedComponents, referencedModels)

    Logger.debug(
      s"Garbage Collecting Referenced: ${referencedComponents.size} Components/ ${referencedModels.size} Models"
    )
    Logger.debug(s"  Values:   ${_modelValues.modelValues.size} -> ${stateUpdated.modelValues.size}")

    _modelValues = stateUpdated
  }
}
