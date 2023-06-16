package kreuzberg.examples.showcase.todo

import kreuzberg.*
import kreuzberg.examples.showcase.todo.{TodoItemShower, TodoList}
import kreuzberg.scalatags.*
import kreuzberg.scalatags.all.*

case class TodoItemShower(item: String) extends SimpleComponentBase {
  override def assemble(using context: SimpleContext): Html = {
    span(item)
  }
}

case class TodoShower(todoList: Subscribeable[TodoList]) extends SimpleComponentBase {

  def assemble(implicit c: SimpleContext): Html = {
    val value = subscribe(todoList)
    val parts = value.elements.map { element =>
      li(
        TodoItemShower(element).wrap
      )
    }
    ul(parts)
  }

  override def update(before: ModelValueProvider)(using context: AssemblerContext): UpdateResult = {
    val valueBefore = before.value(todoList)
    val valueAfter  = read(todoList)
    (for {
      last <- valueAfter.elements.lastOption
      if valueAfter == valueBefore.append(last)
    } yield {
      // Just one element added, lets patch it
      val itemShower = li(TodoItemShower(last).wrap)
      UpdateResult.Append(
        Assembly(
          html = itemShower
        )
      )
    }).getOrElse {
      super.update(before)
    }
  }
}
