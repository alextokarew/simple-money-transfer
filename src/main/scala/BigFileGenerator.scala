//Usage: scala BigFileGenerator.scala {number of lines} {line length}

import java.io.{BufferedWriter, File, FileWriter, PrintWriter}

import scala.util.Random

object BigFileGenerator {

  def main(args: Array[String]): Unit = {
    val nLines = Integer.parseInt(args(0))
    val lineLength = Integer.parseInt(args(1))

    val file = new File("bigFile.txt")
    val fw = new FileWriter(file)
    val bw = new BufferedWriter(fw)
    val pw = new PrintWriter(bw)
    var randomCharGenerator: Stream[Char] = Random.alphanumeric

    (0 until nLines).foreach {_ =>
      val line = randomCharGenerator.take(lineLength).toArray
      pw.println(line)
      randomCharGenerator = randomCharGenerator.drop(lineLength)

    }
    pw.flush()
    pw.close()
    fw.close()
  }
}
