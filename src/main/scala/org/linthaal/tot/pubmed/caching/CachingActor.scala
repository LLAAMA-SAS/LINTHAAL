package org.linthaal.tot.pubmed.caching

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import org.linthaal.tot.pubmed.caching.CachePubMedResults.CachedResults

/**
  *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version. 
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program. If not, see <http://www.gnu.org/licenses/>.
  *
  */

object CachingActor {

  sealed trait CacheCmd

  case class CacheResults(cachedRes : CachedResults) extends CacheCmd

  def apply(): Behavior[CacheCmd] = caching(0L)


  def caching(lastDone: Long): Behavior[CacheCmd] = {
    Behaviors.receive {
      case (ctx, CacheResults(cachedRes)) =>
        if (System.currentTimeMillis() - lastDone > 30000) {
          ctx.log.debug("caching results (pubmed query and processing)")
          CachePubMedResults.flushPubMedResults(cachedRes)
        } else {
          ctx.log.debug("caching done less than 30 seconds ago...")
        }
        caching(System.currentTimeMillis())
    }
  }
}
