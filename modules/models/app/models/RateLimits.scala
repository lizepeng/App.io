package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.dsl._
import helpers.Logging
import models.cassandra._
import org.joda.time.DateTime

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
sealed class RateLimits
  extends CassandraTable[RateLimits, UUID]
  with ExtCQL[RateLimits, UUID]
  with Logging {

  override val tableName = "rate_limits"

  object user_id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object resource
    extends StringColumn(this)
    with PrimaryKey[String]

  object datetime
    extends DateTimeColumn(this)
    with PrimaryKey[DateTime]

  object value
    extends CounterColumn(this)

  override def fromRow(r: Row): UUID = user_id(r)
}

object RateLimit extends RateLimits with Cassandra {

  def get(
    resource: String,
    datetime: DateTime
  )(
    implicit user: User
  ): Future[Long] = CQL {
    select(_.value)
      .where(_.user_id eqs user.id)
      .and(_.resource eqs resource)
      .and(_.datetime eqs datetime)
  }.one().map(_.getOrElse(0L))

  def inc(
    resource: String,
    datetime: DateTime,
    value: Long = 1L
  )(
    implicit user: User
  ): Future[ResultSet] = CQL {
    update
      .where(_.user_id eqs user.id)
      .and(_.resource eqs resource)
      .and(_.datetime eqs datetime)
      .modify(_.value increment value)
  }.future()
}