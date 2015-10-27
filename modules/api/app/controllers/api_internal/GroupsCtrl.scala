package controllers.api_internal

import java.util.UUID

import controllers.RateLimitConfigComponents
import elasticsearch._
import helpers._
import models._
import models.sys.SysConfigs
import play.api.i18n._
import play.api.libs.json._
import play.api.mvc.Controller
import protocols.JsonProtocol._
import protocols._
import security._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class GroupsCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val userActionRequired: UserActionRequired,
  val es: ElasticSearch,
  val _sysConfig: SysConfigs,
  val _groups: Groups
)
  extends Secured(GroupsCtrl)
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with UserActionComponents
  with DefaultPlayExecutor
  with RateLimitConfigComponents
  with I18nSupport
  with Logging {

  ESIndexCleaner(_groups).dropIndexIfEmpty

  case class GroupInfo(name: Name, description: Option[String])
  object GroupInfo {implicit val jsonFormat = Json.format[GroupInfo]}

  def index(ids: Seq[UUID], q: Option[String], p: Pager, sort: Seq[String]) =
    UserAction(_.Index).async { implicit req =>
      if (ids.nonEmpty)
        _groups.find(ids).map { grps =>
          Ok(Json.toJson(grps))
        }
      else
        (es.Search(q, p, sort) in _groups future()).map { page =>
          Ok(page).withHeaders(
            linkHeader(page, routes.GroupsCtrl.index(Nil, q, _, sort))
          )
        }
    }

  def show(id: UUID) =
    UserAction(_.Show).async { implicit req =>
      _groups.find(id).map {
        HttpCaching { grp =>
          Ok(Json.toJson(grp))
        }
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def create =
    UserAction(_.Save).async { implicit req =>
      BindJson().as[GroupInfo] { success =>
        for {
          saved <- _groups.save(Group(name = success.name, description = success.description))
          _resp <- es.Index(saved) into _groups
        } yield {
          Created(_resp._1)
            .withHeaders(
              LOCATION -> routes.GroupsCtrl.show(saved.id).url
            )
        }
      }
    }

  def destroy(id: UUID) =
    UserAction(_.Destroy).async { implicit req =>
      (for {
        grp <- _groups.find(id)
        ___ <- _groups.remove(id)
        ___ <- es.Delete(id) from _groups
      } yield grp).map { _ =>
        NoContent
      }.recover {
        case e: Group.NotWritable =>
          MethodNotAllowed(JsonMessage(e))
        case e: Group.NotEmpty    =>
          MethodNotAllowed(JsonMessage(e))
        case e: Group.NotFound    =>
          NotFound
      }
    }

  def checkName =
    UserAction(_.Show).async { implicit req =>
      BodyIsJsObject { obj =>
        Future.successful {
          (obj \ "name").validate[Name].fold(
            failure => UnprocessableEntity(JsonClientErrors(failure)),
            success => Ok
          )
        }
      }
    }

  def save(id: UUID) =
    UserAction(_.Save).async { implicit req =>
      BindJson().as[GroupInfo] { grp =>
        (for {
          group <- _groups.find(id)
          saved <- _groups.save(group.copy(name = grp.name, description = grp.description))
          _resp <- es.Update(saved) in _groups
        } yield _resp._1).map {
          Ok(_)
        }.recover {
          case e: BaseException => NotFound
        }
      }
    }

  def users(id: UUID, pager: Pager) =
    UserAction(_.Show).async { implicit req =>
      (for {
        page <- _groups.children(id, pager)
        usrs <- _users.find(page)
      } yield (page, usrs)).map { case (page, usrs) =>
        Ok(Json.toJson(usrs))
          .withHeaders(linkHeader(page, routes.GroupsCtrl.users(id, _)))
      }.recover {
        case e: BaseException => NotFound
      }
    }

  def addUser(id: UUID) =
    UserAction(_.Save).async { implicit req =>
      BodyIsJsObject { obj =>
        def u1 = (obj \ "id").validate[UUID].map(_users.find)
        def u2 = (obj \ "email").validate[EmailAddress].map(_users.find)
        (u1 orElse u2).fold(
          failure => Future.successful {
            UnprocessableEntity(JsonClientErrors(failure))
          },
          success => success.flatMap { user =>
            if (user.groups.contains(id)) Future.successful {
              Ok(Json.toJson(user))
            }
            else _groups.addChild(id, user.id).map { _ =>
              Created(Json.toJson(user))
            }
          }
        ).recover {
          case e: User.NotFound => NotFound(JsonMessage(e))
        }
      }
    }

  def delUser(id: UUID, uid: UUID) =
    UserAction(_.Destroy).async { implicit req =>
      _groups.delChild(id, uid).map { _ => NoContent }
    }

  def layouts(ids: Seq[UUID]) =
    UserAction(_.Index).async { implicit req =>
      _groups.findLayouts(ids).map { layouts =>
        val map = layouts.collect {
          case (gid, layout) if layout.isDefined =>
            gid.toString -> layout.get
        }.toMap
        Ok(Json.toJson(map))
      }
    }

  def setLayout(gid: UUID) =
    UserAction(_.Save).async { implicit req =>
      BindJson().as[Layout] { success =>
        _groups.setLayout(gid, success).map { saved =>
          Ok(Json.toJson(saved))
        }
      }.recover {
        case e: BaseException => NotFound(JsonMessage(e))
      }
    }
}

object GroupsCtrl extends Secured(Group)