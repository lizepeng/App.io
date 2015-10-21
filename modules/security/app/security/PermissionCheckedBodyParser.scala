package security

import helpers.BasicPlayApi
import models.{AccessControls, User}
import play.api.mvc.BodyParsers.parse
import play.api.mvc._
import security.ModulesAccessControl._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
trait PermissionCheckedBodyParser[A]
  extends AuthenticatedBodyParser[A] {

  def access: Access
  def resource: CheckedModule
  def onPermDenied: RequestHeader => Result

  implicit def basicPlayApi: BasicPlayApi
  implicit def _accessControls: AccessControls

  override def invokeParser(req: RequestHeader)(
    implicit user: User
  ): Future[BodyParser[A]] = {

    (for {
      canAccess <- ModulesAccessControl(user, access, resource).canAccessAsync
      result <- super.invokeParser(req)(user) if canAccess
    } yield result).recover {
      case e: Throwable => parse.error(Future.successful(onPermDenied(req)))
    }
  }
}