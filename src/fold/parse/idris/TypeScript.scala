package fold.parse.idris

import java.nio.file.{Files, Path}

import fastparse.Parsed
import fold.parse.idris.Grammar.{ArrayIdentifier, Bracketed, Extraction, Identifier, Method}

object TypeScript {

  case class BracketedBinding(origional: Seq[String], // Assume ["S", "k"] which is (S k)
                             name: String,            // Assume "k" as any place this name is found we are referring to this backeted binding
                             substitution: String     // Assume "k-1" to substitute into this place
                             )
  case class Binding(localName: String, origionalName: Option[String], typeOf: String, bracketedBinding: Option[BracketedBinding]) // @todo bracketedBinding set to None
  case class ParameterBinding(bindings: Seq[Binding])

  case class CodeGenerationPreferences(usePreludeTs: Boolean = true,
                                       usePreludeTsVectorForList: Boolean = true,
                                       usePreludeTsListForList: Boolean = false,
                                       placeFunctionsIntoClasses: Boolean = false,
                                       codeGenerationDebugComments: Boolean = false) {
    def listType() = if (usePreludeTsVectorForList) "Vector" else if (usePreludeTsListForList) "LinkedList" else ""
  }

  case class LocalVariable(methodName: String, variableName: String, typeOf: String, indexInMethod: Int, variableAlias: Option[String])

  case class CodeEnvironment(scopedVariablePrefix: Option[String] = None,
                             localVariablesFromMethodParameterScope: Seq[LocalVariable] = Seq.empty,
                             generationPreferences: CodeGenerationPreferences = CodeGenerationPreferences())
  case class CodeLine(line: String, indentLevel: Int = 0)

  // Use linked list
  // https://github.com/immutable-js/immutable-js

  def basicTypeToTypescript(code: CodeGenerationPreferences, t: String, ft: String): String = {
    t match {
      case "List" => {
        s"${code.listType()}<${ft}>"
      }
      case "Bool" => "boolean"
      case "Nat" => "number"
      case "True" => "true"
      case "False" => "false"
      case _ => t
    }
  }

  def functionTypeParameters(value: Grammar.Method) = {
    if (value.methodDefinition.parameters.head.rest.isEmpty) "" else
    s"${value.methodDefinition.parameters.head.rest.head.name}"
  }

  def emptyList(c: CodeGenerationPreferences) = if (c.usePreludeTs) {
    if (c.usePreludeTsVectorForList)
      "Vector.of()"
    else if (c.usePreludeTsListForList)
      "LinkedList.of()"
  } else {
    "@todo ERROR"
  }

  def isCapitalized(name: String): Boolean = {
    name.head.isUpper
  }

  def methodCall(ce: CodeEnvironment, code: CodeGenerationPreferences, methodCall: Grammar.MethodCall) = {
    if (methodCall.isReferenceNotMethodCall)
      s"  return ${localVariable(ce, methodCall.method.name).get}"
    else {
      val params = for (p <- methodCall.parameter) yield {
        p match {
          case i: Identifier => {
            localVariable(ce, i.name).get
          }
          case a: ArrayIdentifier => {
            if (a.isEmpty)
              emptyList(code)
            else {
              throw new Exception("Error")
              ""
            }
          }
        }
      }
      if (isCapitalized(methodCall.method.name))
        s"  return ${basicTypeToTypescript(code, methodCall.method.name, "")}"
      else
        s"  return ${methodCall.method.name}(${params.mkString(", ")})"
    }
  }

  def prefix(codeEnvironment: CodeEnvironment, name: String) = {
    if (codeEnvironment.scopedVariablePrefix.isDefined)
      s"${codeEnvironment.scopedVariablePrefix.get}${name.capitalize}"
    else
      name
  }

  def buildExtractor(codeEnvironment: CodeEnvironment, patternMatch: Grammar.MethodLine): Seq[CodeLine] = {
    (for (r <- patternMatch.left.rest.zipWithIndex) yield {
      if (r._1.extractionForm.isDefined) {
        val e = r._1.extractionForm.get

        Seq(CodeLine(s"const ${prefix(codeEnvironment, e.first.name)} = head${codeEnvironment.generationPreferences.listType()}(${patternMatch.left.methodName.name}Param${r._2 + 1})"),
          CodeLine(s"const ${prefix(codeEnvironment, e.second.name)} = tail${codeEnvironment.generationPreferences.listType()}(${patternMatch.left.methodName.name}Param${r._2 + 1})"))
      } else if (r._1.bracketedForm.isDefined) {
        val b: Bracketed = r._1.bracketedForm.get
        if (b.first.name == "S") { // Assume (S k)
          Seq(CodeLine(s"const k = ${patternMatch.left.methodName.name}Param${r._2+1} - 1"))
        } else
          Seq.empty[CodeLine]
      } else {
        Seq.empty[CodeLine]
      }

      /*r._1.extractionForm match {
        case e: Extraction => {
          Seq(CodeLine(s"const ${prefix(codeEnvironment, e.first.name)} = head${codeEnvironment.generationPreferences.listType()}(${patternMatch.left.methodName.name}Param${r._2 + 1})"),
            CodeLine(s"const ${prefix(codeEnvironment, e.second.name)} = tail${codeEnvironment.generationPreferences.listType()}(${patternMatch.left.methodName.name}Param${r._2 + 1})"))
        }
        case i: Identifier => {
          // What is current method name?
          // What is current index?
          // What is identifier name?
          val currentMethodName = patternMatch.left.methodName.name // @todo not sure if this is true
          val index = r._2
          val identifierName = i.name

          // Find in codeEnvironment
          val found = codeEnvironment.localVariablesFromMethodParameterScope.find(f => {
            (f.variableAlias.contains(identifierName))
          })
          //(f.methodName == currentMethodName) &&
          if (found.isDefined) {
            Seq(CodeLine(s"const ${identifierName} = ${found.get.variableName}"))
          } else
            Seq(CodeLine("?notFound"))
        }
        case _ => Seq.empty
      }*/
    }).flatten
  }

  def codeLinesToString(indent: Int, lines: Seq[CodeLine]): String = {
    val i = if (lines.size == 1) indent -1 else indent
    (for (l <- lines) yield {
      "  ".repeat(i + l.indentLevel).mkString + l.line
    }).mkString("\n")
  }

  def emptyCodeLine() = CodeLine("")

  def buildPatternMatchCondition(codeEnvironment: CodeEnvironment, patternMatch: Grammar.MethodLine): Seq[CodeLine] = {


    val lines: Seq[Option[String]] = for (rr <- patternMatch.left.rest.zipWithIndex) yield {
      val r = rr._1.name
      if (r.isDefined) {
        val param = s"${patternMatch.left.methodName.name}Param${rr._2 + 1}"
        if (r.get == "[]")
          Some(s"(${param}.isEmpty())")
        else
        if (r.get == "Z")
          Some(s"(${param} == 0)")
        else
          None
      } else None
    }


    val conditions: Seq[String] = lines.flatten
    if (conditions.isEmpty) Seq.empty //Seq(CodeLine("{"))
    else Seq(CodeLine(s"if ${
      if (conditions.size == 1) {
        conditions.head
      } else {
        conditions.mkString("(", " && ", ")")
      }
    } {"))


    /*if (patternMatch.methodImplWhere.isDefined) {
      val lines: Seq[Option[String]] = for (r <- patternMatch.methodImplWhere.get.patternMatch.zipWithIndex) yield {
        val i: Option[String] = r._1 match {
          case a: ArrayIdentifier => {
            if (a.isEmpty)
              Some(s"(param${r._2 + 1}.isEmpty())")
            else {
              // @todo This one in cases of non empty array
              None
            }
          }
          case _ => None
        }
        i
      }

      val conditions: Seq[String] = lines.flatten
      if (conditions.isEmpty) Seq(CodeLine("{"))
      else Seq(CodeLine(s"if ${
        if (conditions.size == 1) {
          conditions.head
        } else {
          conditions.mkString("(", " && ", ")")
        }
      } {"))
    } else Seq.empty*/
  }

  def indented(code: Seq[CodeLine], indentLevel: Int): Seq[CodeLine] = {
    for (c <- code) yield {
      c.copy(indentLevel = indentLevel)
    }
  }

  def declaredIdentifier(codeEnvironment: CodeEnvironment, name: String): String = {
    val found = codeEnvironment.localVariablesFromMethodParameterScope.find(_.variableAlias.contains(name))
    if (found.isDefined)
      name
    else
      "?notFoundIdentifier"
  }

  def localVariable(code: CodeEnvironment, name: String): Option[String] = {
    val found = code.localVariablesFromMethodParameterScope.find(_.variableAlias.contains(name))
    if (found.isDefined)
      Some(found.get.variableName)
    else
      None
  }

  def buildCode(code: CodeGenerationPreferences, codeEnvironment: CodeEnvironment, patternMatch: Grammar.MethodLine): Seq[CodeLine] = {

    val c2 = updateCodeEnvironment(codeEnvironment, patternMatch)

    def la(i: Identifier) = {
      if (isCapitalized(i.name)) {
        i.name
      } else {
        localVariable(c2, declaredIdentifier(c2, i.name)).getOrElse(i.name)
      }
    }

    if (patternMatch.methodCall.isReferenceNotMethodCall)
      Seq(CodeLine(s"return ${localVariable(c2, patternMatch.methodCall.method.name).get}"))
    else {
      val p = for (p <- patternMatch.methodCall.parameter) yield {
        p match {
          case i: Identifier => {
            la(i)
          }
          case e: Extraction => {
            // Do prepend
            s"${localVariable(c2, e.second.name).get}.prepend(${e.first.name})"
          }
          case a: ArrayIdentifier => {
            if (a.isEmpty)
              emptyList(code)
            else {
              throw new Exception("Error")
              ""
            }
          }
          case _ => ""
        }
      }

      val parameters = p.mkString(", ")
      if (isCapitalized(patternMatch.methodCall.method.name))
        Seq(CodeLine(s"return ${basicTypeToTypescript(code, patternMatch.methodCall.method.name, ")")}"))
      else
        Seq(CodeLine(s"return ${patternMatch.methodCall.method.name}(${parameters})"))
    }
  }

  def updateCodeEnvironment(codeEnvironment: CodeEnvironment, patternMatch: Grammar.MethodLine): CodeEnvironment = {
    // @todo Add the aliases
    val methodName = patternMatch.left.methodName.name
    // Add variables to the codeEnvironment
    if (patternMatch.methodImplWhere.isDefined) {
      val add: Seq[LocalVariable] = (for (p <- patternMatch.methodImplWhere.get.patternMatch.zipWithIndex) yield {
        p._1 match {
          case i: Identifier => {
            Some(LocalVariable(methodName, i.name, "?unknownTypeOf", p._2, Some(s"param${p._2 + 1}"))) // @todo fix variable alias param
          }
          case _ => None
        }
      }).flatten

      val l: Seq[LocalVariable] = codeEnvironment.localVariablesFromMethodParameterScope ++ add
      codeEnvironment.copy(localVariablesFromMethodParameterScope = l)
    } else {
      val params = for (p <- patternMatch.left.rest.zipWithIndex) yield {
        if (p._1.name.isDefined) {
          if (p._1.name == "[]") {
            None
          } else {
            Some(p._1.name.get)
          }
        } else {
          None
        }
      }

      val addLocalVariables = (for (p <- params.zipWithIndex) yield {
        val prefix = s"${patternMatch.left.methodName.name}Param${p._2+1}"
        if (p._1.isDefined)
          Some(LocalVariable(methodName = patternMatch.left.methodName.name, variableName = prefix, typeOf = "unknown", indexInMethod = p._2, variableAlias = p._1))
        else
          None
      }).flatten

      codeEnvironment.copy(localVariablesFromMethodParameterScope = addLocalVariables ++ codeEnvironment.localVariablesFromMethodParameterScope)
    }
  }


  def buildExtractorLocalVariables(codeEnvironmentNested: CodeEnvironment, methodLine: Grammar.MethodLine): CodeEnvironment = {
    println("local varaibles...")
    val found: Seq[Option[Seq[LocalVariable]]] = for (r <- methodLine.left.rest) yield {

      if (r.extractionForm.isDefined) {
        // methodName: String, variableName: String, typeOf: String, indexInMethod: Int, variableAlias: Option[String]
        val s = Seq(LocalVariable(methodName = methodLine.left.methodName.name, variableName = r.extractionForm.get.first.name, typeOf = "", indexInMethod = 0, variableAlias = Some(r.extractionForm.get.first.name)),
          LocalVariable(methodName = methodLine.left.methodName.name, variableName = r.extractionForm.get.second.name, typeOf = "", indexInMethod = 0, variableAlias = Some(r.extractionForm.get.second.name)))
        Some(s)
      } else None
    }

    val flat : Seq[LocalVariable] = found.flatten.flatten

    codeEnvironmentNested.copy(localVariablesFromMethodParameterScope = flat ++ codeEnvironmentNested.localVariablesFromMethodParameterScope)
  }

  // @todo Merge the next two functions
  def patternMatchesToCode(code: CodeGenerationPreferences, codeEnvironment: CodeEnvironment, method: Grammar.Method): Seq[CodeLine] = {

    val codeLines: Seq[Seq[CodeLine]] = for (m <- method.patternMatch.zipWithIndex) yield {

      val codeEnvironmentNested: CodeEnvironment = updateCodeEnvironment(codeEnvironment, m._1)

      val p = buildPatternMatchCondition(codeEnvironmentNested, m._1)
      val b = buildExtractor(codeEnvironmentNested, m._1)

      val codeEnvironmentExtractor = buildExtractorLocalVariables(codeEnvironmentNested, m._1)

      // @todo At this point at buildExtractor above we need to copy new codeEnvironmentNested variables into it
      val c = buildCode(code, codeEnvironmentExtractor, m._1)

      val joined: Seq[CodeLine] = p ++ indented(b ++ c, 1) ++ {
        if ((m._2 == (method.patternMatch.size-1))) {
          if (m._2 == 0) Seq.empty else
          Seq(CodeLine("}"))
        } else
          Seq(CodeLine("} else { "))
      }
      if (codeEnvironmentExtractor.generationPreferences.codeGenerationDebugComments) {
        CodeLine(s"// Pattern matching function ${m._2+1}") +: joined
      } else joined
    }

    // Insert an empty line between each group
    val deGrouped: Seq[CodeLine] = codeLines.filter(_.nonEmpty).zipWithIndex.flatMap(f => {
      if (f._2 == 0) {
        f._1
      } else {
        f._1
        //CodeLine("") +: f._1
      }
    })

    deGrouped
  }

  def patternMatchesToCodeForMainFunction(codeEnvironment: CodeEnvironment, methodImplWhere: Method): Seq[CodeLine] = {
/*
    val codeLines: Seq[Seq[CodeLine]] = for (m <- methodImplWhere.patterns.zipWithIndex) yield {

      val codeEnvironmentNested: CodeEnvironment = updateCodeEnvironment(codeEnvironment, m._1)

      val p = buildPatternMatchCondition(codeEnvironmentNested, m._1)
      val b = buildExtractor(codeEnvironmentNested, m._1)
      val c = buildCode(codeEnvironmentNested, m._1)

      val joined: Seq[CodeLine] = p ++ indented(b ++ c, 1) ++ Seq({
        if (m._2 == (methodImplWhere.patterns.size-1))
          CodeLine("}")
        else
          CodeLine("} else")
      })
      if (codeEnvironmentNested.generationPreferences.codeGenerationDebugComments) {
        CodeLine(s"// Pattern matching function ${m._2+1}") +: joined
      } else joined
    }

    // Insert an empty line between each group
    val deGrouped: Seq[CodeLine] = codeLines.filter(_.nonEmpty).zipWithIndex.flatMap(f => {
      if (f._2 == 0) {
        f._1
      } else {
        f._1
        //CodeLine("") +: f._1
      }
    })

    deGrouped*/
    null
  }
















  def methodDefinition(methodImplWhere: Option[Grammar.Method], code: CodeGenerationPreferences, c: CodeEnvironment): Option[String] = {
    if (methodImplWhere.isDefined) {
      val last = methodImplWhere.get.methodDefinition.parameters.last
      val r = methodImplWhere.get.methodDefinition.parameters.dropRight(1)
      val paramTypes = for (p <- r) yield p.firstParam.name
      val ft = "a"
      val paramNames = paramTypes.zipWithIndex.map((t: (String, Int)) => s"${methodImplWhere.get.methodDefinition.name}Param${t._2 + 1}")
      val param = paramTypes.zipWithIndex.map((t: (String, Int)) => s"${paramNames(t._2)}: ${basicTypeToTypescript(code, t._1, ft)}").mkString(", ")

      val methodBody: String = (for (p <- methodImplWhere.get.patternMatch) yield {
        p.toString
      }).mkString("\n")

//      val what = methodImplWhere.get.patternMatch(1).rest.toString

      val what2 = codeLinesToString(2, patternMatchesToCode(code, c, methodImplWhere.get))

      // @todo The function is parameterized by <${ft}> but I deleted that to make it work
      Some(s"""  function ${methodImplWhere.get.methodDefinition.name}($param): ${basicTypeToTypescript(code, last.firstParam.name, ft)} {
         |${what2}
         |  }""".stripMargin)
    } else None
  }

  def docComments(code: CodeGenerationPreferences, params: Seq[(String, String)]): String = {
    val codeLines = (for (p <- params.zipWithIndex) yield {
      CodeLine(s"* @param ${p._1._1} ${basicTypeToTypescript(code, p._1._2, "a")} ?")
    }) :+ CodeLine(s"* @returns ?")

    val parameterJsDoc = codeLinesToString(0, codeLines)

    s"""/**
     |* ?
     |${parameterJsDoc}
     |*/""".stripMargin
  }


  def generateLocalVariables(methodName: String, parameterNames: Seq[String], parameterTypes: Seq[String]): Seq[LocalVariable] = {

    for (n <- parameterNames.zip(parameterTypes).zipWithIndex) yield {
      LocalVariable(methodName, n._1._1, n._1._2, n._2, None)
    }
  }

  def generateLocalVariables(methodName: String, parameterNames: Seq[String], parameterTypes: Seq[String], parameterBinding: ParameterBinding): Seq[LocalVariable] = {
    assert(parameterNames.size == parameterBinding.bindings.size)
    for (n <- parameterNames.zip(parameterTypes).zip(parameterBinding.bindings).zipWithIndex) yield {
      LocalVariable(methodName, n._1._1._1, n._1._1._2, n._2, n._1._2.asInstanceOf[Binding].origionalName)
    }
  }

  def isDataType(name: String) = {
    name.head.isUpper
  }

  //ParameterBinding
  def parameterBinding(method: Grammar.Method, methodLine: Grammar.MethodLine): ParameterBinding = {
    val totalParameters = method.methodDefinition.parameters.size
    val methodDef = method.methodDefinition.parameters
    val methodLineParameters = methodLine.left.rest


    val parameterNames = for (p <- (0 until (totalParameters-1))) yield {
      s"${method.methodDefinition.name}Param${p+1}"
    }

    val bindings = for (p <- parameterNames.zip(methodLineParameters).zip(methodDef)) yield {
      // @todo Bracketed binding
      Binding(p._1._1, if (p._1._2.name.isEmpty) None else if (isDataType(p._1._2.name.get)) None else Some(p._1._2.name.get), p._2.firstParam.name, None)
    }
    ParameterBinding(bindings)
  }

  def toTypescriptAST(fileName: String, idrisAst: Parsed[Grammar.Method], code: CodeGenerationPreferences) = {
    idrisAst match {
      case Parsed.Success(value, index) => {
        if (value!=null) {
          val methodLineParameterBindings = for (m <- value.patternMatch)
            yield parameterBinding(value, m)

          val parameterTypes = for (p <- value.methodDefinition.parameters) yield (p.firstParam.name)

          val methodLines = value.patternMatch.size
          println(methodLines)

          // In the case of multiple method lines then the variable names maybe different so we need to use
          // some common name

          val parameterNames = methodLineParameterBindings.head.bindings.map(b => {
            b.localName
          }) //for (p <- value.methodLine.head.left.rest) yield (p.name.getOrElse(""))

          val parameterNamesFiltered: Seq[Option[String]] = parameterNames.map((f) => {
            if (f.size == 0) None else if (f.head.isUpper == false) Some(f) else None
          })

          val codeEnvironment = CodeEnvironment(localVariablesFromMethodParameterScope = generateLocalVariables(value.methodDefinition.name, parameterNames, parameterTypes, methodLineParameterBindings(0)),
            generationPreferences = code)

          val ft = functionTypeParameters(value)

          val params = parameterNamesFiltered.zip(parameterTypes).map(f => {
            if (f._1.isDefined) Some((f._1.get, f._2)) else None
          }).flatten

          val parametersStr = for (p <- params) yield (p._1 + ": " + basicTypeToTypescript(code, p._2, ft))

          val header =
            s"""// npm install --save prelude-ts
              |import { Vector, LinkedList } from "prelude-ts";
              |
              |function head${code.listType()}<a>(param: ${code.listType()}<a>): a {
              |  return param.head().getOrThrow()
              |}
              |
              |function tail${code.listType()}<a>(param: ${code.listType()}<a>): ${code.listType()}<a> {
              |  return param.tail().getOrElse(${emptyList(code)})
              |}
              |
              |""".stripMargin

          val methodChoices = patternMatchesToCodeForMainFunction(codeEnvironment, value)

          val test = patternMatchesToCode(code, codeEnvironment, value)
          val methodImpl = codeLinesToString(1, test)

          // value
          //val methodImpl = methodCall(codeEnvironment, code, value.patternMatch.head.methodCall)

          // Find all method definitions
          val methodDefs = for (p <- value.patternMatch) yield methodDefinition(p.methodImplWhere, code, codeEnvironment)
          val methodDef = methodDefs.flatten.mkString("\n")

          val functionDoc = docComments(code, params)

          val parameterized = if (ft.trim.isEmpty) "" else s"<$ft>"

          val function = s"""${functionDoc}
                            |export function ${value.methodDefinition.name}${parameterized}(${parametersStr.mkString(", ")}): ${basicTypeToTypescript(code, parameterTypes.last, ft)}
             |{
             |${methodDef}
             |${methodImpl}
             |}""".stripMargin

          val output = header + function

          Files.writeString(Path.of("typescript/src/test/" + fileName), output)

          output
        } else {
          Files.writeString(Path.of("typescript/src/test/" + fileName), "failure case")
          ""
        }
      }


      case _ => {
        ""
      }
    }
  }

}
