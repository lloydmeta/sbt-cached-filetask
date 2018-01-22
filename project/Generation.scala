import java.nio.file.{Files, StandardCopyOption}

import sbt._
import sbt.Keys._
import FileUtils._

object Generation {

  val generateCached =
    TaskKey[Set[File]]("generate-files", "Generates files resource. Uncached.")

  val generateUncached =
    TaskKey[Set[File]]("generate-files-uncached", "Generates files resource. Cached.")

  lazy val generationSettings = Seq(
    generateUncached := Def.taskDyn {
      val fileName = "hello-file"
      val output   = (resourceDirectory in Compile).value / fileName
      (runMain in Compile)
        .toTask(
          s" com.beachape.EmitFile ${output.absolutePath}"
        )
        .map {
          _ =>
            /*
            Since we generally run *after* compile, we need to make sure to replace the
            file that "compile" has already placed into target
             */
            val targetPath = (classDirectory in Compile).value / fileName
            Files.copy(output.toPath, targetPath.toPath, StandardCopyOption.REPLACE_EXISTING)
            Set(output)
        }
    }.value,
    generateCached := Def.taskDyn {
      val cacheDir = target.value / "generate-swagger-cache"
      val files = {
        val mainSrcScalaDir = (scalaSource in Compile).value
        listFiles(mainSrcScalaDir)
          .filter(_.getName.endsWith(".scala"))
      }

      cachedFileTask(
        fileInfoCacheDir = cacheDir,
        filesToWatch = files.toSet,
        taskKey = generateUncached,
        state = state.value
      )
    }.value,
    /*
      The use of "<<==" is not a typo. ":=" is broken when used with triggeredBy until
      we upgrade to sbt 0.13.14 (https://github.com/sbt/sbt/issues/1444)
     */
    generateCached := generateCached.triggeredBy(compile in Compile).value
  )

}
