package fold.parse.idris.para

object ParaLexical {
  import fastparse.NoWhitespace._
  import fastparse._

  // "average is string to double"

  val keywords = Set(
    "data", "is", "as", "of" // of
  )

  val symbols = Set(
    ".(", // for things such as Foo.Bar.(+)
    "@{",
    "[|", "|]",
    "(", ")", "{", "}", "[", "]", ",", ";", "_",
    "`(", "`", "\""
  )

  val reservedSymbols = symbols.union(
    Set("%", "\\", ":", "=", "|", "|||", "<-", "->", "=>", "?", "!",
      "&", "**", "..", "/"))

  def identifier[_: P]: P[ParaGrammar.Identifier] =
    P((letter | "_") ~ (letter | digit | "_").rep).!.filter(!(keywords.union(constants)).contains(_)).map(ParaGrammar.Identifier)

  def letter[_: P] = P(lowercase | uppercase)

  def lowercase[_: P] = P(CharIn("a-z"))

  def uppercase[_: P] = P(CharIn("A-Z"))

  def digit[_: P] = P(CharIn("0-9"))

  def comment[_: P] = P("--" ~ CharsWhile(_ != '\n', 0))


  // ------------------------------------------------------------------------------------------------

  def kw[_: P](s: String) = s ~ !(letter | digit | "_")

  def wscomment[_: P] = P((CharsWhileIn(" \n") | ParaLexical.comment | "\\\n").rep)

  def nonewlinewscomment[_: P] = P((CharsWhileIn(" ") | ParaLexical.comment | "\\\n").rep)

  val constants = Set(
    "Integer", "Int", "Char", "Double", "String", "Bits8", "Bits16", "Bits32", "Bits64"
  )


  def stringliteral[_: P]: P[String] = P(stringprefix.? ~ (longstring | shortstring))

  def stringprefix[_: P]: P[Unit] = P(
    "r" | "u" | "ur" | "R" | "U" | "UR" | "Ur" | "uR" | "b" | "B" | "br" | "Br" | "bR" | "BR"
  )

  def shortstring[_: P]: P[String] = P(shortstring0("'") | shortstring0("\""))

  def shortstring0[_: P](delimiter: String) = P(delimiter ~ shortstringitem(delimiter).rep.! ~ delimiter)

  def shortstringitem[_: P](quote: String): P[Unit] = P(shortstringchar(quote) | escapeseq)

  def shortstringchar[_: P](quote: String): P[Unit] = P(CharsWhile(!s"\\\n${quote(0)}".contains(_)))

  def longstring[_: P]: P[String] = P(longstring0("'''") | longstring0("\"\"\""))

  def longstring0[_: P](delimiter: String) = P(delimiter ~ longstringitem(delimiter).rep.! ~ delimiter)

  def longstringitem[_: P](quote: String): P[Unit] = P(longstringchar(quote) | escapeseq | !quote ~ quote.take(1))

  def longstringchar[_: P](quote: String): P[Unit] = P(CharsWhile(!s"\\${quote(0)}".contains(_)))

  def escapeseq[_: P]: P[Unit] = P("\\" ~ AnyChar)

  def negatable[T, _: P](p: => P[T])(implicit ev: Numeric[T]) = (("+" | "-").?.! ~ p).map {
    case ("-", i) => ev.negate(i)
    case (_, i) => i
  }

  def longinteger[_: P]: P[BigInt] = P(integer ~ ("l" | "L"))

  def integer[_: P]: P[BigInt] = negatable[BigInt, Any](P(octinteger | hexinteger | bininteger | decimalinteger))

  def decimalinteger[_: P]: P[BigInt] = P(nonzerodigit ~ digit.rep | "0").!.map(scala.BigInt(_))

  def octinteger[_: P]: P[BigInt] = P("0" ~ ("o" | "O") ~ octdigit.rep(1).! | "0" ~ octdigit.rep(1).!).map(scala.BigInt(_, 8))

  def hexinteger[_: P]: P[BigInt] = P("0" ~ ("x" | "X") ~ hexdigit.rep(1).!).map(scala.BigInt(_, 16))

  def bininteger[_: P]: P[BigInt] = P("0" ~ ("b" | "B") ~ bindigit.rep(1).!).map(scala.BigInt(_, 2))

  def nonzerodigit[_: P]: P[Unit] = P(CharIn("1-9"))

  def octdigit[_: P]: P[Unit] = P(CharIn("0-7"))

  def bindigit[_: P]: P[Unit] = P("0" | "1")

  def hexdigit[_: P]: P[Unit] = P(digit | CharIn("a-f", "A-F"))


  def floatnumber[_: P]: P[BigDecimal] = negatable[BigDecimal, Any](P(pointfloat | exponentfloat))

  def pointfloat[_: P]: P[BigDecimal] = P(intpart.? ~ fraction | intpart ~ ".").!.map(BigDecimal(_))

  def exponentfloat[_: P]: P[BigDecimal] = P((intpart | pointfloat) ~ exponent).!.map(BigDecimal(_))

  def intpart[_: P]: P[BigDecimal] = P(digit.rep(1)).!.map(BigDecimal(_))

  def fraction[_: P]: P[Unit] = P("." ~ digit.rep(1))

  def exponent[_: P]: P[Unit] = P(("e" | "E") ~ ("+" | "-").? ~ digit.rep(1))


  def imagnumber[_: P] = P((floatnumber | intpart) ~ ("j" | "J"))
}
