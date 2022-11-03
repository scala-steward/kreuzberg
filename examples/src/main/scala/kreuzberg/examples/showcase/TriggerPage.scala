package kreuzberg.examples.showcase

import kreuzberg.*
import kreuzberg.imperative.{SimpleContext, SimpleComponentBase}
import scalatags.Text.all.*

case class Label(model: Model[String]) extends SimpleComponentBase {
  override def assemble(implicit c: SimpleContext): Html = {
    val current = subscribe(model)
    span(current)
  }
}

object TriggerPage extends SimpleComponentBase {
  override def assemble(implicit c: SimpleContext): Html = {
    val value  = model("value", "")
    val editor = child("editor", TextInput("field"))
    val shower = child("shower", Label(value))

    add(
      from(editor)(_.inputEvent)
        .withReplacedState(editor)(_.text)
        .toModel(value)((n, _) => n)
    )
    div(
      "Bitte geben sie was ein: ",
      editor,
      "Das hier haben sie eingegeben: ",
      shower
    )
  }
}
