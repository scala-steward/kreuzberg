package kreuzberg.imperative

import kreuzberg._
import kreuzberg.util.Stateful

/** Base class for components, which assemble imperative. Also see [[SimpleComponentBase]] for an even reduced version.
  */
abstract class ImperativeComponentBase extends ImperativeDsl {

  def assemble(implicit c: AssemblyContext): Assembly

}

object ImperativeComponentBase {
  implicit def assembler[T <: ImperativeComponentBase]: Assembler[T] = { value =>
    Stateful.apply { state =>
      implicit val context = new AssemblyContext(state)
      val assembled        = value.assemble
      (context.state, assembled)
    }
  }
}

/** Imperative variant if [[AssemblyState]] */
class AssemblyContext(private var _state: AssemblyState) {
  def transform[T](f: Stateful[AssemblyState, T]): T = {
    val (nextState, value) = f(_state)
    _state = nextState
    value
  }

  def transformFn[T](f: AssemblyState => (AssemblyState, T)): T = {
    val (nextState, value) = f(_state)
    _state = nextState
    value
  }

  def get[T](f: AssemblyState => T): T = {
    f(_state)
  }

  def state: AssemblyState = _state
}

trait ImperativeDsl {
  protected def model[M](name: String, defaultValue: M)(implicit c: AssemblyContext): Model[M] =
    c.transformFn(_.withModel(name, defaultValue))

  protected def child[C](
      name: String,
      value: C
  )(implicit c: AssemblyContext, assembler: Assembler[C]): ComponentNode[C] = {
    c.transform(assembler.assembleNamedChild(name, value))
  }

  protected def anonymousChild[C](value: C)(implicit c: AssemblyContext, assembler: Assembler[C]): ComponentNode[C] = {
    c.transform(assembler.assembleWithNewId(value))
  }

  protected def subscribe[M](model: Model[M])(implicit c: AssemblyContext): M = {
    c.transformFn(_.subscribe(model))
  }

  protected def read[M](model: Model[M])(implicit c: AssemblyContext): M = {
    c.get(_.readValue(model))
  }

  case class RepBuilder[T](rep: ComponentNode[T]) {
    def apply[E](f: T => Event[E]): EventSource[E] = EventSource.RepEvent(rep, f(rep.value))
  }

  def from[T](rep: ComponentNode[T]): RepBuilder[T] = RepBuilder(rep)
}
