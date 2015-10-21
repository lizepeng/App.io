package controllers.api_internal

import java.util.UUID

import controllers.RateLimitConfig
import elasticsearch._
import helpers._
import models._
import play.api.i18n._
import play.api.libs.json._
import play.api.mvc.Controller
import protocols.JsonProtocol._
import protocols._
import security._

/**
 * @author zepeng.li@gmail.com
 */
class AccessControlsCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val userActionRequired: UserActionRequired,
  val es: ElasticSearch,
  val _groups: Groups
)
  extends Secured(AccessControlsCtrl)
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with UserActionComponents
  with DefaultPlayExecutor
  with RateLimitConfig
  with I18nSupport
  with Logging {

  def index(q: Option[String], p: Pager) =
    UserAction(_.Index).async { implicit req =>
      _accessControls.list(p).map { page =>
        Ok(page).withHeaders(
          linkHeader(page, routes.AccessControlsCtrl.index(q, _))
        )
      }
    }

  def show(principal_id: UUID, resource: String) =
    UserAction(_.Show).async { implicit req =>
      _accessControls.find(principal_id, resource).map { ace =>
        Ok(Json.toJson(ace))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def create =
    UserAction(_.Create).async { implicit req =>
      println(req.body)
      BindJson().as[AccessControlEntry] { success =>
        _accessControls.find(success).map { found =>
          Ok(Json.toJson(found))
        }.recoverWith {
          case e: AccessControlEntry.NotFound =>
            (for {
              exists <-
              if (!success.is_group) _users.exists(success.principal)
              else _groups.exists(success.principal)

              saved <- success.copy(permission = 0L).save
              _resp <- es.Index(saved) into _accessControls
            } yield (saved, _resp)).map { case (saved, _resp) =>

              Created(_resp._1)
                .withHeaders(
                  LOCATION -> routes.AccessControlsCtrl.show(
                    saved.principal, saved.resource
                  ).url
                )
            }.recover {
              case e: User.NotFound  => BadRequest(JsonMessage(e))
              case e: Group.NotFound => BadRequest(JsonMessage(e))
            }
        }
      }
    }

  def destroy(principal_id: UUID, resource: String) =
    UserAction(_.Destroy).async { implicit req =>
      (for {
        ___ <- es.Delete(AccessControlEntry.genId(resource, principal_id)) from _accessControls
        ace <- _accessControls.find(principal_id, resource)
        ___ <- _accessControls.remove(principal_id, resource)
      } yield ace).map { _ =>
        NoContent
      }.recover {
        case e: AccessControlEntry.NotFound => NotFound
      }
    }

  def toggle(principal_id: UUID, resource: String, bit: Long) =
    UserAction(_.Save).async { implicit req =>
      (for {
        found <- _accessControls.find(principal_id, resource)
        saved <- found.copy(permission = found.permission ^ bit).save
        _resp <- es.Update(saved) in _accessControls
      } yield _resp._1).map {
        Ok(_)
      }.recover {
        case e: BaseException => NotFound
      }
    }
}

object AccessControlsCtrl extends Secured(AccessControl)