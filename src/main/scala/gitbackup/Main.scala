package gitbackup

import java.io.File

import scala.sys.process.ProcessBuilder.Source
import scala.sys.process._

object Main {

  val root = "d:\\Users\\shapkin\\Dropbox\\"
  val bases = List("prog", "pubs")
  val target = "d:\\Users\\shapkin\\GoogleDrive\\MEPhI\\backup\\"

  private def dirs(file: File) =
    file.listFiles.toList.filter(f => f.isDirectory)

  def repoDirs(file: File): List[File] = {
    val subDirs = dirs(file)
    if (subDirs exists (_.getName == ".git")) List(file)
    else subDirs.flatMap(repoDirs)
  }

  def main(args: Array[String]): Unit =
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
      Process(
        s, dir
      ).lineStream.toList flatMap ( name =>
        List[Source](
          "echo ----",
          s"echo $name",
          "echo ----",
          new File(dir, name)
        )
      ) map (_.#>>(unversioned).!)
    }

}
