import models.Schemas
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import play.api.{Logger, _}

/**
 * @author zepeng.li@gmail.com
 */
object Global extends GlobalSettings {

  override def onStart(app: Application) = {
    Schemas.create
  }

  override def doFilter(next: EssentialAction): EssentialAction = {
    Filters(super.doFilter(next), loggingFilter)
  }

  val loggingFilter = Filter {(nextFilter, header) =>
    val startTime = System.currentTimeMillis
    nextFilter(header).map {result =>
      val endTime = System.currentTimeMillis
      val requestTime = endTime - startTime
      if (!header.uri.contains("assets")) {
        Logger.trace(
          f"${result.header.status}, took $requestTime%4d ms, ${header.method} ${header.uri}"
        )
      }
      result.withHeaders("Request-Time" -> requestTime.toString)
    }
  }
}