/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher

import org.neo4j.cypher.internal.compiler.v3_0.CypherCompilerConfiguration
import org.neo4j.cypher.internal.compiler.v3_0.spi.PlanContext
import org.neo4j.cypher.internal.frontend.v3_0.test_helpers.{CypherFunSuite, CypherTestSupport}
import org.neo4j.cypher.internal.helpers.GraphIcing
import org.neo4j.cypher.internal.spi.v3_0.TransactionBoundPlanContext
import org.neo4j.cypher.internal.spi.v3_0.TransactionBoundQueryContext.IndexSearchMonitor
import org.neo4j.graphdb._
import org.neo4j.graphdb.config.Setting
import org.neo4j.kernel.api.{DataWriteOperations, KernelAPI}
import org.neo4j.kernel.impl.core.ThreadToStatementContextBridge
import org.neo4j.kernel.{GraphDatabaseAPI, monitoring}
import org.neo4j.test.TestGraphDatabaseFactory

import scala.collection.JavaConverters._
import scala.collection.Map

trait GraphDatabaseTestSupport extends CypherTestSupport with GraphIcing {
  self: CypherFunSuite  =>

  var graph: GraphDatabaseAPI = null
  var nodes: List[Node] = null

  def databaseConfig(): Map[Setting[_],String] = Map()

  override protected def initTest() {
    super.initTest()
    graph = createGraphDatabase()
  }

  protected def createGraphDatabase(): GraphDatabaseAPI = {
    new TestGraphDatabaseFactory().newImpermanentDatabase(databaseConfig().asJava).asInstanceOf[GraphDatabaseAPI]
  }

  override protected def stopTest() {
    try {
      super.stopTest()
    }
    finally {
      if (graph != null) graph.shutdown()
    }
  }

  def assertInTx(f: => Option[String]) {
    graph.inTx { f match {
        case Some(error) => fail(error)
        case _           =>
      }
    }
  }

  def indexNode(n: Node, idxName: String, key: String, value: String) {
    graph.inTx(n.getGraphDatabase.index.forNodes(idxName).add(n, key, value))
  }

  def indexRel(r: Relationship, idxName: String, key: String, value: String) {
    graph.inTx(r.getGraphDatabase.index.forRelationships(idxName).add(r, key, value))
  }

  def nodeId(n: Node) = graph.inTx {
    n.getId
  }

  def relationshipId(r: Relationship) = graph.inTx {
    r.getId
  }

  def labels(n: Node) = graph.inTx {
    n.getLabels.iterator().asScala.map(_.toString).toSet
  }

  def countNodes() = graph.inTx {
    graph.getAllNodes.asScala.size
  }

  def countRelationships() = graph.inTx {
    graph.getAllRelationships.asScala.size
  }

  def createNode(): Node = createNode(Map[String, Any]())

  def createNode(name: String): Node = createNode(Map[String, Any]("name" -> name))

  def createNode(props: Map[String, Any]): Node = {
    graph.inTx {
      val node = graph.createNode()

      props.foreach((kv) => node.setProperty(kv._1, kv._2))
      node
    }
  }

  def createLabeledNode(props: Map[String, Any], labels: String*): Node = {
    val n = createNode()

    graph.inTx {
      labels.foreach {
        name => n.addLabel(Label.label(name))
      }

      props.foreach {
        case (k, v) => n.setProperty(k, v)
      }
    }

    n
  }

  def createLabeledNode(labels: String*): Node = createLabeledNode(Map[String, Any](), labels: _*)

  def createNode(values: (String, Any)*): Node = createNode(values.toMap)

  def deleteAllEntities() = graph.inTx {
    val relIterator = graph.getAllRelationships.iterator()

    while (relIterator.hasNext) {
      relIterator.next().delete()
    }

    val nodeIterator = graph.getAllNodes.iterator()
    while (nodeIterator.hasNext) {
      nodeIterator.next().delete()
    }
  }

  def execStatement[T](f: (DataWriteOperations => T)): T = {
    val tx = graph.beginTx
    val result = f(statement.dataWriteOperations())
    tx.success()
    tx.close()
    result
  }

  def nodeIds = nodes.map(_.getId).toArray

  val REL = RelationshipType.withName("REL")

  def relate(a: Node, b: Node): Relationship = relate(a, b, "REL")

  def relate(a: Node, b: Node, pk: (String, Any)*): Relationship = relate(a, b, "REL", pk.toMap)

  def relate(n1: Node, n2: Node, relType: String, name: String): Relationship = relate(n1, n2, relType, Map("name" -> name))

  def relate(a: Node, b: Node, c: Node*) {
    (Seq(a, b) ++ c).reduce((n1, n2) => {
      relate(n1, n2)
      n2
    })
  }

  def relate(n1: Node, n2: Node, relType: String, props: Map[String, Any] = Map()): Relationship = graph.inTx {
    val r = n1.createRelationshipTo(n2, RelationshipType.withName(relType))

    props.foreach((kv) => r.setProperty(kv._1, kv._2))
    r
  }

  def relate(x: ((String, String), String)): Relationship = graph.inTx {
    x match {
      case ((from, relType), to) => {
        val f = node(from)
        val t = node(to)
        f.createRelationshipTo(t, RelationshipType.withName(relType))
      }
    }
  }

  def node(name: String): Node = graph.inTx {
    nodes.find(_.getProperty("name") == name).get
  }

  def relType(name: String): RelationshipType = graph.getAllRelationshipTypes.asScala.find(_.name() == name).get

  def createNodes(names: String*): List[Node] = {
    nodes = names.map(x => createNode(Map("name" -> x))).toList
    nodes
  }

  def createDiamond(): (Node, Node, Node, Node) = {
    //    Graph:
    //             (a)
    //             / \
    //            v   v
    //          (b)  (c)
    //           \   /
    //            v v
    //            (d)

    val a = createNode("a")
    val b = createNode("b")
    val c = createNode("c")
    val d = createNode("d")

    relate(a, b)
    relate(b, d)
    relate(a, c)
    relate(c, d)
    (a, b, c, d)
  }

  def statement = graph.getDependencyResolver.resolveDependency(classOf[ThreadToStatementContextBridge]).get()

  def kernelMonitors = graph.getDependencyResolver.resolveDependency(classOf[monitoring.Monitors])

  def kernelAPI = graph.getDependencyResolver.resolveDependency(classOf[KernelAPI])

  def planContext: PlanContext = new TransactionBoundPlanContext(statement, graph)

  def indexSearchMonitor = kernelMonitors.newMonitor(classOf[IndexSearchMonitor])

  val config = CypherCompilerConfiguration(
    queryCacheSize = 100,
    statsDivergenceThreshold = 0.5,
    queryPlanTTL = 1000,
    useErrorsOverWarnings = false,
    nonIndexedLabelWarningThreshold = 10000
  )
}
