package gitbackup

case class ParsedOptions(
  root: Option[String] = None,
  bases: Seq[String] = Seq(),
  target: Option[String] = None
) {
  def toConfig = Config(
    root   getOrElse sys.error("root is required"),
    bases,
    target getOrElse sys.error("target is required")
  )
}

case class Config(
  root: String,
  bases: Seq[String],
  target: String
)
