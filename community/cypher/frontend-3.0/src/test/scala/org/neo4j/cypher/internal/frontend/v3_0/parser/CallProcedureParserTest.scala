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
package org.neo4j.cypher.internal.frontend.v3_0.parser

import org.neo4j.cypher.internal.frontend.v3_0.{DummyPosition, ast}

class CallProcedureParserTest extends ParserAstTest[ast.Command] with Command {

  implicit val parser = Command

  test("CALL foo") {
    yields(ast.CallProcedure(List.empty, ast.ProcName("foo")(pos), IndexedSeq.empty))
  }

  test("CALL foo()") {
    yields(ast.CallProcedure(List.empty, ast.ProcName("foo")(pos), IndexedSeq.empty))
  }

  test("CALL foo('Test', 1+2)") {
    yields(ast.CallProcedure(List.empty, ast.ProcName("foo")(pos),
      Vector(
        ast.StringLiteral("Test")(pos),
        ast.Add(
          ast.SignedDecimalIntegerLiteral("1")(pos),
          ast.SignedDecimalIntegerLiteral("2")(pos))(pos)
      ))
    )
  }

  test("CALL foo.bar.baz('Test', 1+2)") {
    yields(ast.CallProcedure(List("foo", "bar"), ast.ProcName("baz")(pos),
      Vector(
        ast.StringLiteral("Test")(pos),
        ast.Add(
          ast.SignedDecimalIntegerLiteral("1")(pos),
          ast.SignedDecimalIntegerLiteral("2")(pos))(pos)
      ))
    )
  }
  private val pos = DummyPosition(-1)

  implicit class StringToVariable(string: String) {

    def asVar = id(string)(pos)
  }
}
