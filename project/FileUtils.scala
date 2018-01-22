import sbt._

object FileUtils {

  /**
    * A somewhat convoluted way of watching a given set of files for changes in order to decide
    * whether or not to run a given SBT task.
    *
    * Note that depending on where `cacheBaseDirectory`, the cache might be blown away when
    * running clean, such as when it is inside the `target` folder.
    */
  def cachedFileTask[T](
      fileInfoCacheDir: File,
      filesToWatch: Set[File],
      taskKey: TaskKey[Set[File]],
      state: State,
      inStyle: FileInfo.Style = FilesInfo.lastModified,
      outStyle: FileInfo.Style = FilesInfo.lastModified): Def.Initialize[Task[Set[File]]] =
    Def.taskDyn {
      val runIfChanged = FileFunction.cached(cacheBaseDirectory = fileInfoCacheDir,
                                             inStyle = inStyle,
                                             outStyle = outStyle) { _ =>
        Project.extract(state).runTask(taskKey, state)._2
      }
      Def.task {
        runIfChanged(filesToWatch)
      }
    }

  /**
    * Given a file
    *   * Returns a recursively gathered lazy Stream of files if the initial file is directory
    *   * Returns the file itself if the file is just a file.
    */
  def listFiles(f: File): Stream[File] = {
    Stream(f).flatMap { x =>
      if (x.isDirectory) {
        Option(x.listFiles())
          .fold(Stream.empty[File])(_.toStream)
          .flatMap(listFiles)
      } else {
        Stream(x)
      }
    }
  }
}
