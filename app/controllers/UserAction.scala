package controllers

import helpers.BasicPlayApi
import models._
import play.api.mvc._
import security.ModulesAccessControl._
import security._

/**
 * @author zepeng.li@gmail.com
 */
object MaybeUserAction {

  import UsersComponents._

  def apply()(
    implicit
    basicPlayApi: BasicPlayApi,
    _groups: Groups
  ): ActionBuilder[UserOptRequest] = {
    MaybeUser().Action() andThen LayoutLoader()
  }
}

trait MaybeUserActionComponents {

  implicit lazy val userActionExceptionHandler = DefaultUserActionExceptionHandler
  implicit lazy val bodyParserExceptionHandler = new BodyParserExceptionHandler with DefaultExceptionHandler
}

case class UserActionRequired(
  _groups: Groups,
  _accessControls: AccessControls
)

trait UserActionComponents[T <: BasicAccessDef] extends MaybeUserActionComponents {
  self: T =>

  import UsersComponents._

  def userActionRequired: UserActionRequired

  implicit def _accessControls: AccessControls = userActionRequired._accessControls

  implicit def _groups: Groups = userActionRequired._groups

  def UserAction(specifiers: (T => Access.Pos)*)(
    implicit
    resource: CheckedModule,
    basicPlayApi: BasicPlayApi,
    userActionRequired: UserActionRequired
  ): ActionBuilder[UserRequest] = {
    val access = Access.union(specifiers.map(_ (this).toAccess))
    MaybeUser().Action() andThen
      LayoutLoader() andThen
      AuthChecker() andThen
      PermissionChecker(access)
  }
}

object DefaultUserActionExceptionHandler extends UserActionExceptionHandler {

  def onUnauthorized = _ => Results.Redirect(routes.SessionsCtrl.nnew())
  def onPermissionDenied = _ => Results.Redirect(routes.Application.index())
  def onFilePermissionDenied = _ => Results.Redirect(routes.Application.index())
  def onPathNotFound = _ => Results.Redirect(routes.Application.index())
  def onThrowable = _ => Results.Redirect(routes.Application.index())
}