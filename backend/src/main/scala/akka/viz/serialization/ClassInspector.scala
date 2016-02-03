package akka.viz.serialization

import scala.collection.mutable

trait ClassInspector {

  def underlyingClass: Class[_]

  def fields: Seq[ClassInspector.ClassField]

  def allFieldNames = fields.map(_.name).toSet

  def inspect(obj: Any, fields: Set[String] = allFieldNames): Map[String, Any]

  def inspect(obj: Any, field: String): Option[Any] = inspect(obj, Set(field)).values.headOption

  override def toString: String = {
    val fieldsStr = fields.map(f => s"${f.name}:${f.signature}").mkString(",")
    s"ClassInspector(underlyingClass=${underlyingClass},fields=${fieldsStr})"
  }

}

object ClassInspector {

  sealed trait FieldType

  case object Val extends FieldType

  case object Var extends FieldType

  case class ClassField(name: String, fullName: String, fieldType: FieldType, resultClass: Class[_], signature: String)


  private val rm = scala.reflect.runtime.currentMirror

  def of(clazz: Class[_]): ClassInspector = {
    val t = rm.classSymbol(clazz).toType

    val reflectedFields = t.members.filter(_.isTerm).map(_.asTerm).filter(t => t.isVal || t.isVar).map {
      s =>
        val fn = s.fullName
        val name = fn.split('.').last
        ClassField(
          name,
          fn,
          if (s.isVal) Val else Var,
          rm.runtimeClass(s.typeSignature),
          s.typeSignature.toString
        )
    }
    new ClassInspector {
      val underlyingClass: Class[_] = clazz
      private val f = reflectedFields.toSeq

      override def inspect(obj: Any, fieldNames: Set[String] = allFieldNames): Map[String, Any] = {
        val result = mutable.Map[String, Any]()
        fieldNames.foreach {
          fieldName =>
            val field = underlyingClass.getDeclaredField(fieldName)
            field.setAccessible(true)
            result += (fieldName -> field.get(obj))
        }

        result.toMap
      }

      override def fields: Seq[ClassField] = f
    }
  }
}