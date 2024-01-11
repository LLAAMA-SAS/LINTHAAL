package org.linthaal.helpers

import org.neo4j.driver.types.MapAccessor
import org.neo4j.driver.{ AuthTokens, GraphDatabase }

import scala.jdk.CollectionConverters.*

object Neo4jDatabaseService {
  val authToken = AuthTokens.basic(EnvVariables.getEnvVar("neo4j.username"), EnvVariables.getEnvVar("neo4j.password"))
  val driver = GraphDatabase.driver(EnvVariables.getEnvVar("neo4j.uri"), authToken)

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
