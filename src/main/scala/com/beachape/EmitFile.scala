package com.beachape

import java.io.{File, FileOutputStream}
import java.nio.charset.StandardCharsets

object EmitFile {
  def main(args: Array[String]): Unit = {
    val file = new File(args(0))
    if (!file.exists()) {
      val _ = file.createNewFile()
    }
    println(s"Writing to ${file.getAbsolutePath}")
    val out = new FileOutputStream(file)
    try {
      out.write("Hello World".getBytes(StandardCharsets.UTF_8))
    } finally {
      out.close()
      println("Done writing.")
    }
  }
}
