package uk.gov.hmrc.agentmapping.repository

import reactivemongo.api.indexes.Index
import reactivemongo.core.errors.GenericDatabaseException
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

trait StrictlyEnsureIndexes[A <: Any, ID <: Any] {

  self: ReactiveRepository[A,ID] =>

  private def ensureIndexOrFail(index: Index)(implicit ec: ExecutionContext): Future[Boolean] = {
    val indexInfo = s"""${index.eventualName}, key=${index.key.map{case (k,_) => k}.mkString("+")}, unique=${index.unique}, background=${index.background}, sparse=${index.sparse}"""
    collection.indexesManager.create(index).map(wr => {
      if(wr.ok) {
        logger.info(s"Successfully Created Index ${collection.name}.$indexInfo")
        true
      } else {
        val msg = wr.writeErrors.mkString(", ")
        if (msg.contains("E11000")) {
          // this is for backwards compatibility to mongodb 2.6.x
          throw GenericDatabaseException(msg, wr.code)
        } else {
          throw new IllegalStateException(s"Failed to ensure index $indexInfo, error=$msg")
        }
      }
    }).recover {
      case t =>
        logger.error(message, t)
        false
    }
  }

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    Future.sequence(indexes.map(ensureIndexOrFail))
  }

}
