package com.gecemmo.testing

import akka.actor._
import scala.concurrent.duration._
import concurrent.Await
import akka.actor.SupervisorStrategy._
import akka.event.Logging
import akka.util.Timeout
import akka.pattern.ask

// Actor messages
case class Start()
case class DoWork()
case class Started()
case class Stop()
case class StartFetch(count: Int)

/**
 *  Child Actor
 *
 *  Responsible for fetching data from specific company
 */
class SampleAgentChildActor(company: String) extends Actor with ActorLogging {

  override def preStart() = {
    log.info(self.path + " :: " + company + " started")
  }

  def receive = {
    case Start() =>
      sender ! Started()

    case Stop() =>
      context.children.foreach(context.stop _)

    case StartFetch(count: Int) =>
      (1 to count).foreach(i => println("[" + company + "] Fetching item " + i))
  }
}

/**
 *  Parent Actor
 *
 *  Responsible for:
 *   - Supervision
 *   - Start of children
 *   - Managing data fetching
 */
class SampleAgentParentActor(childConfig: List[String]) extends Actor with ActorLogging {

  def childName: String = "samplechildagent"
  implicit val timeout = Timeout(30000)

  override def preStart() = {
    log.info(self.path + " started")
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
    case _: ArithmeticException => Resume
    case _: NullPointerException => Restart
    case _: Exception => Escalate
  }

  def childActorName(str: String) =
    // Replaces unwanted characters (must be valid actor name)
    """[åäö\ ]""".r.replaceAllIn(childName + "_" + str.toLowerCase, x => x.group(0) match {case "å" | "ä" => "a" case "ö" => "o" case _ => ""})

  def startChild(name: String) = {
      val childActor = context.actorOf(Props(new SampleAgentChildActor(name)), childActorName(name))
       Await.ready(childActor ? Start(), timeout.duration)
    }

  def receive = {
    case Start() =>
      childConfig.foreach(name => startChild(name))      
      sender ! Started()

    case StartFetch(count: Int) =>
      // Just for test, send nr of items to fetch
      context.children.foreach(_ ! StartFetch(count))

    case Stop() =>
      context.children.foreach(context.stop _)
  }
}

object ScalaCalc extends App {
  println("API :: SampleAgent system")

  val system = ActorSystem("SampleAgent")
  implicit val timeout = Timeout(30000)

  // List of companies
  val childConfig: List[String] = List("CompanyA", "CompanyB", "CompanyC", "CompanyD")

  val application = system.actorOf(
    props = Props(new SampleAgentParentActor(childConfig)),
    name = "sampleparentagent"
  )

  Await.ready(application ? Start(), timeout.duration)
  println("All children started...")

  println("Will start fetch in 5 seconds...")

  // Wait 5 seconds  
  Thread.sleep(5000);

  // Test system, ask to fetch 10 items each
  application ! StartFetch(10)
}