package xyz.hyperreal.killit

import xyz.hyperreal.snutils.Globbing
import xyz.hyperreal.snutils.signal._
import xyz.hyperreal.snutils.unistd._

import scopt.OParser

object Main extends App {
  case class Config(port: Int, verbose: Boolean, test: Boolean)

  val builder = OParser.builder[Config]

  val parser = {
    import builder._

    OParser.sequence(
      programName("killit"),
      head("killit", "v0.1.0"),
      help('h', "help").text("prints this usage text"),
      opt[Unit]('t', "test")
        .action((_, c) => c.copy(test = true))
        .text("same as verbose but don't kill any processes"),
      opt[Unit]('v', "verbose")
        .action((_, c) => c.copy(verbose = true))
        .text("print internal actions"),
      version('V', "version").text("prints the version"),
      arg[Int]("<port>")
        .action((p, c) => c.copy(port = p))
        .text("port (tcp6) that the server is listening on")
    )
  }

  OParser.parse(parser, args, Config(-1, verbose = false, test = false)) match {
    case Some(config) => app(config)
    case _            =>
  }

  def app(conf: Config): Unit = {
    def info(s: String): Unit =
      if (conf.verbose || conf.test)
        println(s)

    val port  = conf.port
    val hport = port.formatted("%04x").toUpperCase
    val connectionsTCP =
      util
        .Using(
          io.Source
            .fromFile("/proc/net/tcp"))(
          _.getLines() map (_ split " +" toVector) filter (_(2) contains s":$hport") toList) get
    val connectionsTCP6 =
      util
        .Using(
          io.Source
            .fromFile("/proc/net/tcp6"))(
          _.getLines() map (_ split " +" toVector) filter (_(2) contains s":$hport") toList) get
    val connections = connectionsTCP ++ connectionsTCP6

    info(s"TCP6 local address connections on port $port ($hport):")
    info(connections map (c => s"  ${c(2)} ${c(4)} ${c(10)}") mkString "\n")

    val linkRegex = "/proc/([^/]+)/fd/[0-9]+ socket:\\[([0-9]+)]".r

    val inodes = Globbing.expand("/proc/*/fd/*") flatMap { l =>
      readLink(l).filter(_ startsWith "socket:").map(s => s"$l $s").toList
    } map { case linkRegex(pid, inode) => pid -> inode } filter { case (pid, _) => pid forall (_.isDigit) }

    info(s"processes that are listening on $port:")

    val allpids =
      (for (c <- connections)
        yield {
          info(s"  inode ${c(10)}:")

          val pids = inodes filter {
            case (_, inode)     => c(4) == "0A" && inode == c(10)
          } map { case (pid, _) => pid }

          info(pids map (pid => s"    $pid") mkString "\n")
          pids
        }) flatten

    if (allpids.isEmpty)
      info("can't find any processes to kill")
    else {
      if (conf.test)
        info(s"would kill ${allpids.length} process(es) with pid(s): ${allpids mkString ", "}")
      else
        info(s"killing ${allpids.length} process(es):")

      if (!conf.test)
        for (pid <- allpids)
          info(s"  pid $pid... ${if (kill(pid.toInt, SIGKILL) == 0) "ok" else "failed"}")
    }
  }
}
