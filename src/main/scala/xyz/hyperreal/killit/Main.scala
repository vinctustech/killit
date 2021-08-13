package xyz.hyperreal.killit

import scala.scalanative.posix.unistd._
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

import xyz.hyperreal.snutils.Globbing
import xyz.hyperreal.snutils.signal._
import xyz.hyperreal.snutils.unistd._

import scopt.OParser

object Main extends App {
  case class Config(port: Int, verbose: Boolean)

  val builder = OParser.builder[Config]

  val parser = {
    import builder._

    OParser.sequence(
      programName("killit"),
      head("killit", "v0.1.0"),
      help('h', "help").text("prints this usage text"),
      opt[Unit]('v', "verbose")
        .action((_, c) => c.copy(verbose = true))
        .text("print internal actions"),
      version('V', "version").text("prints the version"),
      arg[Int]("<port>")
        .action((p, c) => c.copy(port = p))
        .text("port (tcp6) that the server is listening on")
    )
  }

  OParser.parse(parser, args, Config(-1, false)) match {
    case Some(config) => app(config.port, config.verbose)
    case _            =>
  }

  def app(port: Int, verbose: Boolean): Unit = {
    def info(s: String): Unit =
      if (verbose)
        println(s)

    val hport = port.formatted("%04x").toUpperCase
    val connections =
      io.Source
        .fromFile("/proc/net/tcp6")
        .getLines() map (_ split " +" toVector) filter (_(2) contains s":$hport") toList

    info(s"TCP6 local address connections on port $port ($hport):")
    info(connections map (c => s"  ${c(2)} ${c(4)} ${c(10)}") mkString "\n")

    val linkRegex = "/proc/([0-9]+)/fd/[0-9]+ -> socket:\\[([0-9]+)]".r

    val inodes = Globbing.expand("/proc/*/fd/*") flatMap { l =>
      readLink(l).filter(_ startsWith "socket:").map(s => s"$l -> $s").toList
    } map { case linkRegex(pid, inode) => pid -> inode }

    info(s"processes that are listening on $port:")

    val allpids =
      (for (c <- connections)
        yield {
          info(s"  inode ${c(10)}:")

          val pids = inodes filter { case (_, inode) => c(4) == "0A" && inode == c(10) } map { case (pid, _) => pid }

          info(pids map (pid => s"    $pid") mkString "\n")
          pids
        }) flatten

    if (allpids.isEmpty)
      info("can't find any processes to kill")
    else {
      info(s"killing ${allpids.length} process(es):")

      for (pid <- allpids)
        info(s"  pid $pid... ${if (kill(pid.toInt, SIGKILL) == 0) "ok" else "failed"}")
    }
  }
}
