package org.linthaal.helpers

import org.neo4j.driver.types.MapAccessor
import org.neo4j.driver.{ AuthTokens, GraphDatabase }

import scala.jdk.CollectionConverters.*

/** This program is free software: you can redistribute it and/or modify it
  * under the terms of the GNU General Public License as published by the Free
  * Software Foundation, either version 3 of the License, or (at your option)
  * any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT
  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
  * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
  * more details.
  *
  * You should have received a copy of the GNU General Public License along with
  * this program. If not, see <http://www.gnu.org/licenses/>.
  */
object Neo4jDatabaseService {
  val authToken = AuthTokens.basic("neo4j", "password")
  val driver = GraphDatabase.driver(sys.env.getOrElse("NEO4J_URI", "bolt://localhost:7687"), authToken)

  val schema: String = {
    val nodeProperties = executeQuery(nodePropertiesQuery)
    val relProperties = executeQuery(relPropertiesQuery)
    val rels = executeQuery(relsQuery)

    schemaTemplate(nodeProperties, relProperties, rels)
  }

  def executeQuery(query: String): String =
    driver
      .executableQuery(query)
      .execute()
      .records()
      .asScala
      .map(_.asMap().asScala.map {
        case (k, mapAccessor: MapAccessor) =>
          (k, mapAccessor.asMap.toString)
        case kv => kv
      })
      .mkString("\n")

  private lazy val nodePropertiesQuery =
    """CALL apoc.meta.data()
      |YIELD label, other, elementType, type, property
      |WHERE NOT type = "RELATIONSHIP" AND elementType = "node"
      |WITH label AS nodeLabels, collect(property) AS properties
      |RETURN {labels: "`" + nodeLabels + "`", properties: properties} AS output""".stripMargin('|')

  private lazy val relPropertiesQuery =
    """CALL apoc.meta.data()
      |YIELD label, other, elementType, type, property
      |WHERE NOT type = "RELATIONSHIP" AND elementType = "relationship"
      |WITH label AS nodeLabels, collect(property) AS properties
      |RETURN {type: nodeLabels, properties: properties} AS output""".stripMargin('|')

  private lazy val relsQuery =
    """CALL apoc.meta.data()
      |YIELD label, other, elementType, type, property
      |WHERE type = "RELATIONSHIP" AND elementType = "node"
      |RETURN {source: label, relationship: property, target: other} AS output""".stripMargin('|')

  private def schemaTemplate(nodeProperties: String, relProperties: String, rels: String): String =
    s"""This is the schema representation of the Neo4j database.
       |Node properties are the following:
       |$nodeProperties
       |Relationship properties are the following:
       |$relProperties
       |Relationship point from source to target nodes
       |$rels
       |Make sure to respect relationship types and directions""".stripMargin('|')
}
