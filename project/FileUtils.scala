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
      output: Set[File],
      taskKey: TaskKey[Set[File]],
      inStyle: FileInfo.Style = FilesInfo.lastModified): Def.Initialize[Task[Set[File]]] = {
    var shouldRun             = false
    val fileCachedTaskRunTime = System.currentTimeMillis()
    val originalLastModifiedTimes = output.map { f =>
      val maybeLastModified =
        if (f.exists())
          Some(math.max(f.lastModified(), 0L)) // JDK-6791812
        else
          None
      f -> maybeLastModified
    }
    val runIfChanged = FileFunction.cached(cacheBaseDirectory = fileInfoCacheDir,
                                           inStyle = inStyle,
                                           outStyle = FilesInfo.lastModified) { _ =>
      shouldRun = true
      /*
       * Set the lastModified time on output files so the cache function can track them
       *
       * Ensure FileFunction.cache can track lastModified timestamps of files that are yet
       * to be created by our task by creating them ahead of time.
       */
      output.foreach { outFile =>
        if (!outFile.exists()) {
          IO.touch(outFile)
        }
        outFile.setLastModified(fileCachedTaskRunTime)
      }
      output
    }
    runIfChanged(filesToWatch)
    if (shouldRun ||
        sys.props.getOrElse("sbt.runTaskCache.ignore", "false") != "false") {
      Def.task {
        taskKey.result.value.toEither match {
          case Left(incomplete) => {
            /*
             * If the underlying task does not finish properly, reset the last modified times on the output files
             * so that at the next invocation, they will not match the ones that were recorded after the closure
             * passed to FileFunction.cached exited, therefore busting the cache the next time the wrapping task is
             * invoked. We want this to happen so that failures aren't cached.
             */
            originalLastModifiedTimes.foreach {
              case (f, Some(originalLastMod)) =>
                f.setLastModified(originalLastMod)
              case (f, None) =>
                // Delete files that did not exist to begin with and were created by IO.touch
                f.delete()
            }
            throw incomplete
          }
          case Right(r) => {
            /*
             * Override the output files' last modified times once the task finishes running successfully
             * so that the cache function can find the right timestamp (the last modified timestamp of the output
             * files that it found after the closure it was passed exited) on the output files the next time
             * this wrapping task is invoked, so that the cache can function properly.
             */
            output.foreach(_.setLastModified(fileCachedTaskRunTime))
            r
          }
        }
      }
    } else {
      Def.task {
        Set.empty
      }
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
