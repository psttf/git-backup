package gitbackup

import java.io.File

import scala.sys.process._

object Main {

  private lazy val parser = new scopt.OptionParser[ParsedOptions]("git-backup") {
    opt[String]     ('r', "root").required()
      .action( (x, c) => c.copy(root = Some(x)) )
    opt[Seq[String]]('b', "bases")
      .action( (x, c) => c.copy(bases = x) )
    opt[String]     ('t', "target").required()
      .action( (x, c) => c.copy(target = Some(x)) )
  }

  private def dirs(file: File) = {
    if (!file.exists()) sys.error(s"$file does not exist")
    file.listFiles.toList.filter(f => f.isDirectory)
  }

  private def repoDirs(file: File): List[File] = {
    val subDirs = dirs(file)
    if (subDirs exists (_.getName == ".git")) List(file)
    else subDirs.flatMap(repoDirs)
  }

  private def getRelativePath(file: File, folder: File): String = {
    val filePath = file.getAbsolutePath
    val folderPath = folder.getAbsolutePath
    if (filePath.startsWith(folderPath))
      filePath.substring(folderPath.length + 1)
    else
      sys.error(s"$file is not a in $folder")
  }

  private def zip(
    zipFile: File, contentFiles: Iterable[File], base: File
  ) = {

    import java.io.{BufferedInputStream, FileInputStream, FileOutputStream}
    import java.util.zip.{ZipEntry, ZipOutputStream}

    val zip = new ZipOutputStream(new FileOutputStream(zipFile))

    contentFiles.foreach { contentFile =>
      zip.putNextEntry(new ZipEntry(getRelativePath(contentFile, base)))
      val in = new BufferedInputStream(new FileInputStream(contentFile))
      var b = in.read()
      while (b > -1) {
        zip.write(b)
        b = in.read()
      }
      in.close()
      zip.closeEntry()
    }
    zip.close()

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
          val unversionedZip =
            new File(repoTarget, s"${dir.getName}-unversioned.zip")
          if (unversionedZip.exists()) unversionedZip.delete()
          val quoted = """"(.*)"""".r
          val unversionedFiles = Process(s, dir).lineStream.toList map ( name =>
            new File(
              dir,
              name match {
                case quoted(path) => path
                case other        => other
              }
            )
          )
          zip(unversionedZip, unversionedFiles, dir)
        }
      case None => println("exiting")
    }

}
