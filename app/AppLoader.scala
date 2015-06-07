import elasticsearch.ElasticSearch
import helpers.BasicPlayApi
import messages.ChatActor
import models._
import models.cfs._
import models.sys.SysConfigs
import play.api.ApplicationLoader.Context
import play.api.i18n._
import router.Routes
import services.{BandwidthService, MailService}

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class AppLoader extends play.api.ApplicationLoader {

  def load(context: Context) = {
    new Components(context).application
  }
}

class Components(context: Context)
  extends play.api.BuiltInComponentsFromContext(context)
  with I18nComponents {

  play.api.Logger.configure(context.environment)

  implicit val _basicPlayApi = BasicPlayApi(langs, messagesApi, configuration)

  // Services
  implicit val _bandwidth   = new BandwidthService(_basicPlayApi, actorSystem)
  implicit val _es          = new ElasticSearch(_basicPlayApi)
  implicit val _mailService = new MailService(_basicPlayApi, actorSystem)

  // Models
  implicit val _sysConfig              = new SysConfigs
  implicit val _internalGroups         = new InternalGroups
  implicit val _users                  = new Users
  implicit val _accessControls         = new AccessControls
  implicit val _sessionData            = new SessionData
  implicit val _expirableLinks         = new ExpirableLinks
  implicit val _rateLimits             = new RateLimits
  implicit val _cfs                    = new CassandraFileSystem
  implicit val _groups                 = new Groups
  implicit val _persons                = new Persons
  implicit val _emailTemplates         = new EmailTemplates
  implicit val _emailTemplateHistories = new EmailTemplateHistories

  // Api Permission Checking
  implicit val _apiPermCheckRequired =
    controllers.api.PermCheckRequired(_users, _accessControls, _rateLimits)

  // Api Controllers
  val apiSearchCtrl         = new controllers.api.SearchCtrl
  val apiGroupsCtrl         = new controllers.api.GroupsCtrl
  val apiUsersCtrl          = new controllers.api.UsersCtrl
  val apiAccessControlsCtrl = new controllers.api.AccessControlsCtrl
  val apiFileSystemCtrl     = new controllers.api.FileSystemCtrl

  // Api Router
  val apiRouter = new api.Routes(
    httpErrorHandler,
    apiSearchCtrl,
    apiGroupsCtrl,
    apiUsersCtrl,
    apiAccessControlsCtrl,
    apiFileSystemCtrl
  )

  // Sockets Controllers
  val socketsChatCtrl = new controllers.sockets.ChatCtrl

  // Sockets Router
  val socketsRouter = new sockets.Routes(
    httpErrorHandler,
    socketsChatCtrl
  )

  // Register permission checkable controllers
  implicit val _secured = new controllers.RegisteredSecured(
    messagesApi,
    Seq(
      controllers.FileSystemCtrl,
      controllers.GroupsCtrl,
      controllers.UsersCtrl,
      controllers.EmailTemplatesCtrl,
      controllers.AccessControlsCtrl,
      controllers.api.GroupsCtrl,
      controllers.api.UsersCtrl,
      controllers.api.SearchCtrl,
      controllers.api.FileSystemCtrl,
      controllers.api.AccessControlsCtrl
    )
  )

  // Permission Checking
  implicit val _permCheckRequired =
    controllers.PermCheckRequired(_users, _accessControls)

  // Root Controllers
  val applicationCtrl    = new controllers.Application()
  val chatCtrl           = new controllers.ChatCtrl()
  val fileSystemCtrl     = new controllers.FileSystemCtrl()
  val sessionsCtrl       = new controllers.SessionsCtrl()
  val usersCtrl          = new controllers.UsersCtrl()
  val myCtrl             = new controllers.MyCtrl()
  val groupsCtrl         = new controllers.GroupsCtrl()
  val passwordResetCtrl  = new controllers.PasswordResetCtrl()
  val emailTemplatesCtrl = new controllers.EmailTemplatesCtrl()
  val accessControlsCtrl = new controllers.AccessControlsCtrl()

  // Root Router
  lazy val router = new Routes(
    httpErrorHandler,
    apiRouter,
    socketsRouter,
    applicationCtrl,
    chatCtrl,
    new controllers.Assets(httpErrorHandler),
    fileSystemCtrl,
    sessionsCtrl,
    usersCtrl,
    myCtrl,
    groupsCtrl,
    passwordResetCtrl,
    emailTemplatesCtrl,
    accessControlsCtrl
  )

  implicit val ec = actorSystem.dispatcher

  ChatActor.startRegion(actorSystem)

  //Start System
  Future.sequence(
    Seq(
      Schemas.create,
      //TODO
      apiUsersCtrl.dropIndexIfEmpty,
      apiGroupsCtrl.dropIndexIfEmpty,
      apiAccessControlsCtrl.dropIndexIfEmpty,
      _internalGroups.loadOrInit.flatMap {
        init =>
          if (init) apiGroupsCtrl.reindex
          else Future.successful(false)
      },
      controllers.GroupsCtrl.initialize,
      controllers.AccessControlsCtrl.initialize
    )
  ).onSuccess {
    case _ => play.api.Logger.info("System has started")
  }
}