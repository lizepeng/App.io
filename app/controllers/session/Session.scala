package controllers.session

import java.util.UUID

import controllers.UserRequest
import helpers.BaseException
import helpers.syntax._
import models.User.Credentials
import models._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Try

/**
 * @author zepeng.li@gmail.com
 */
trait Session {
  private val user_id_key   = "usr_id"
  private val user_salt_key = "usr_salt"

  def transform[A](request: Request[A]): Future[UserRequest[A]] = {
    request.user.map(Some(_)).recover {
      case e: BaseException => None
    }.map {new UserRequest[A](_, request)}
  }

  implicit def retrieve(implicit request: RequestHeader): Option[Credentials] = {
    val cookie = request.cookies
    for (u <- cookie.get(user_id_key).map(_.value).flatMap(toUUID);
         s <- cookie.get(user_salt_key).map(_.value)
    ) yield Credentials(u, s)
  } orElse {
    val session = request.session
    for (u <- session.get(user_id_key).flatMap(toUUID);
         s <- session.get(user_salt_key)
    ) yield Credentials(u, s)
  }

  private def toUUID(str: String) = Try(UUID.fromString(str)).toOption

  implicit class RequestWithUser(request: RequestHeader) {

    def user: Future[User] = retrieve(request) match {
      case None       => Future.failed(User.NoCredentials())
      case Some(cred) => User.auth(cred)
    }

  }

  implicit class ResultWithSession(result: Result) {

    def createSession(rememberMe: Boolean)(implicit user: User): Result = {
      val maxAge = Some(365.days.toStandardSeconds.getSeconds)

      val resultWithSession = result
        .withNewSession
        .withSession(
          user_id_key -> user.id.toString,
          user_salt_key -> user.salt
        )
        .flashing("success" -> s"Logged in")

      if (!user.remember_me) resultWithSession
      else {
        resultWithSession.withCookies(
          Cookie(user_id_key, user.id.toString, maxAge = maxAge),
          Cookie(user_salt_key, user.salt, maxAge = maxAge)
        )
      }
    }

    def destroySession: Result = {
      result.withNewSession
        .discardingCookies(
          DiscardingCookie(user_id_key),
          DiscardingCookie(user_salt_key)
        )
    }
  }

}