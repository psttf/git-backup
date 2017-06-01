package gitbackup

import java.io.File

import scala.sys.process.ProcessBuilder.Source
import scala.sys.process._

object Main {

  val parser = new scopt.OptionParser[ParsedOptions]("git-backup") {
    opt[String]     ('r', "root")  .action( (x, c) => c.copy(root = Some(x)) )
    opt[Seq[String]]('b', "bases") .action( (x, c) => c.copy(bases = x) )
    opt[String]     ('t', "target").action( (x, c) => c.copy(target = Some(x)) )
  }

  private def dirs(file: File) = {
    if (!file.exists()) sys.error(s"$file does not exist")
    file.listFiles.toList.filter(f => f.isDirectory)
  }

  def repoDirs(file: File): List[File] = {
    val subDirs = dirs(file)
    if (subDirs exists (_.getName == ".git")) List(file)
    else subDirs.flatMap(repoDirs)
  }

  def main(args: Array[String]): Unit =
    parser.parse(args, ParsedOptions()) match {
      case Some(options) =>
        val config = options.toConfig
        val root = config.root
        val bases = config.bases
        val target = config.target
        bases map (b => new File(s"$root$b")) flatMap repoDirs foreach { dir =>
          val repoTarget = new File(s"$target${dir.getPath.drop(root.length)}")
          repoTarget.mkdirs()
          Process(
            "git log --decorate=full -p --branches --not --remotes", dir
          ).#>(new File(repoTarget, s"${dir.getName}-log")).run()
          Process(
            "git diff", dir
          ).#>(new File(repoTarget, s"${dir.getName}-diff")).run
          val s = "git ls-files -o --exclude-standard"
          val unversioned = new File(repoTarget, s"${dir.getName}-unversioned")
          if (unversioned.exists()) unversioned.delete()
          val quoted = """"(.*)"""".r
          Process(s, dir).lineStream.toList flatMap ( name =>
            List[Source](
              s"cmd /c echo ----",
              s"cmd /c echo $name",
              s"cmd /c echo ----",
              new File(
                dir,
                name match {
                  case quoted(path) => path
                  case other        => other
                }
              )
            )
          ) map (_.#>>(unversioned).!)
        }
      case None => println("exiting")
    }

}
