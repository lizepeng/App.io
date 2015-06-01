package controllers

import javax.inject.Inject

import helpers.{AppConfig, ModuleLike}
import models.cfs.Path
import play.api.Play.current
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Controller
import security._
import views._

class Application @Inject()(val messagesApi: MessagesApi)
  extends Controller
  with ModuleLike with ViewMessages with AppConfig with I18nSupport {

  override val moduleName = "app"

  def index = MaybeUserAction { implicit req =>
    Ok(html.welcome.index())
  }

  def about = MaybeUserAction { implicit req =>
    Ok(html.static_pages.about())
  }

  def wiki = MaybeUserAction { implicit req =>
    val videoPath = config.getString("wiki.video").map(fn => Path(filename = Some(fn)))
    Ok(html.static_pages.wiki(videoPath))
  }
}