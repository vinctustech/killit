package xyz.hyperreal.killit

import scala.scalanative.posix.unistd._
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

import xyz.hyperreal.snutils.Globbing
import xyz.hyperreal.snutils.signal._
import xyz.hyperreal.snutils.unistd._

import scopt.OParser

object Main extends App {
  case class Config(port: Int)

  val builder = OParser.builder[Config]

  val parser = {
    import builder._

    OParser.sequence(
      programName("killit"),
      head("killit", "0.1.0"),
      help("help").text("prints this usage text"),
      arg[Int]("<port>")
        .action((p, c) => c.copy(port = p))
        .text("port (tcp6) that the server is listening on")
    )
  }

  OParser.parse(parser, args, Config(-1)) match {
    case Some(config) => app(config.port)
    case _            =>
  }

  def app(port: Int): Unit = {
    val hport = port.formatted("%04x").toUpperCase

    val connections =
      //    util.Using(io.Source.fromFile("/proc/net/tcp6"))(_.getLines filter (_ contains hport) map (_.split(" +")(9))) get
      io.Source
        .fromFile("/proc/net/tcp6")
        .getLines() map (_ split " +" toVector) filter (_(2) contains s":$hport") toList

    println(s"TCP6 local address connections on port $port ($hport):")
    println(connections map (c => s"  ${c(2)} ${c(4)} ${c(10)}") mkString "\n")

    val linkRegex = "/proc/([0-9]+)/fd/[0-9]+ -> socket:\\[([0-9]+)]".r

    val inodes = Globbing.expand("/proc/*/fd/*") flatMap { l =>
      readLink(l).filter(_ startsWith "socket:").map(s => s"$l -> $s").toList
    } map { case linkRegex(pid, inode) => pid -> inode }

    println(s"processes that are listening on $port:")

    val allpids =
      (for (c <- connections)
        yield {
          println(s"  inode ${c(10)}:")

          val pids = inodes filter { case (_, inode) => c(4) == "0A" && inode == c(10) } map { case (pid, _) => pid }

          println(pids map (pid => s"    $pid") mkString "\n")
          pids
        }) flatten

    if (allpids.isEmpty)
      println("can't find any processes to kill")
    else {
      println(s"killing ${allpids.length} process(es):")

      for (pid <- allpids)
        println(s"  pid $pid... ${if (kill(pid.toInt, SIGKILL) == 0) "ok" else "failed"}")
    }
  }
}
