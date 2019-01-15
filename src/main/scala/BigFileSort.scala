//Usage: scala BigFileSort.scala {file to sort} {max lines in partition, default is 300000}

import java.io.{BufferedWriter, File, FileWriter, PrintWriter}
import java.nio.file.{Files, Paths}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.io.{BufferedSource, Source}

object BigFileSort {

  def main(args: Array[String]): Unit = {
    val fileName: String = args(0)

    val maxLinesPerPartition: Int = if (args.length > 1) Integer.parseInt(args(1)) else 300000

    val fileSource: BufferedSource = Source.fromFile(fileName)
    val linesIterator: Iterator[String] = fileSource.getLines()

    //Recursively reading next lines into RAM, sorting it and saving them into next partition
    @tailrec def createSortedPartition(partitionIndex: Int): Int =
      if (linesIterator.hasNext) {
        val lines: Seq[String] = linesIterator.take(maxLinesPerPartition).toVector
        val sortedLines: Seq[String] = lines.sorted
        savePartition(fileName, partitionIndex, sortedLines)
        createSortedPartition(partitionIndex + 1)
      } else {
        partitionIndex
      }

    val nPartitions = createSortedPartition(0)
    fileSource.close()

    val partitionFileSources = new Array[BufferedSource](nPartitions)
    val partitionIterators = new Array[Iterator[String]](nPartitions)

    //Priority queue ordered by line. Each entry contains a line and partition index
    val queue: mutable.PriorityQueue[(String, Int)] = new mutable.PriorityQueue()

    //Initializing priority queue by reading first lines from each partitions
    (0 until nPartitions).foreach {p =>
      partitionFileSources(p) = Source.fromFile(partitionFileName(fileName, p))
      partitionIterators(p) = partitionFileSources(p).getLines()

      if (partitionIterators(p).hasNext) {
        queue.enqueue(partitionIterators(p).next() -> p)
      }
    }

    val outFile = new File(f"$fileName%s.sorted")
    val fw = new FileWriter(outFile)
    val bw = new BufferedWriter(fw)
    val pw = new PrintWriter(bw)

    //Sequentially writing to result file by dequeueing the top element and pulling the next line from corresponding partition
    while (queue.nonEmpty) {
      val (line, partition) = queue.dequeue()
      pw.println(line)

      if (partitionIterators(partition).hasNext) {
        queue.enqueue(partitionIterators(partition).next() -> partition)
      }
    }

    pw.flush()
    pw.close()
    fw.close()

    //Closing all partition sources and deleting intermediate partition files
    (0 until nPartitions).foreach { p =>
      partitionFileSources(p).close()
      Files.delete(Paths.get(partitionFileName(fileName, p)))
    }
  }

  def savePartition(fileName: String, partitionIndex: Int, sortedLines: Seq[String]): Unit = {
    val file = new File(partitionFileName(fileName, partitionIndex))
    val fw = new FileWriter(file)
    val bw = new BufferedWriter(fw)
    val pw = new PrintWriter(bw)

    sortedLines.foreach { line =>
      pw.println(line)
    }

    pw.flush()
    pw.close()
    fw.close()
  }

  def partitionFileName(fileName: String, partitionIndex: Int): String =
    f"$fileName%s.sorted.$partitionIndex%05d"

}
