package models

import java.util.UUID

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
object JsonReads {

  val nameConstraint  = minLength[String](2)
  val emailConstraint = minLength[String](1) <~ maxLength[String](40) <~ email

  def idReads(child: Symbol = 'id) =
    (__ \ child).read[UUID]

  def optionalIdReads(child: Symbol = 'id) =
    (__ \ child).readNullable[UUID]

  def nameReads(child: Symbol = 'name) =
    (__ \ child).read[String](nameConstraint)

  def optionalNameReads(child: Symbol = 'name) =
    (__ \ child).readNullable[String](nameConstraint)

  def emailReads(child: Symbol = 'email) =
    (__ \ child).read[String](emailConstraint)
}