package com.distcomp.utils

object LoggingTestUtils {


  // Helper function to extract node name from log entry
  private def extractNodeName(log: String): String = {
    log.split(" - ")(1).split(" ")(0)
  }

  def getInitiatorCounts(logs: List[String]): Int = {
    val initiatorCounts = logs
      .filter(_.contains("starting critical section request"))

//    println(s"${initiatorCounts}")

//    println(s"Initiator counts: ${initiatorCounts.length}")

    initiatorCounts.length
  }

  def getInitiatorCountsElection(logs: List[String]): Int = {
    val initiatorCountsElection = logs
      .filter(_.contains("started election"))

//        println(s"Initiaitos eefbebfebf ${initiatorCountsElection}")

//        println(s"Initiator counts: ${initiatorCountsElection.length}")

    initiatorCountsElection.length
  }

  def extractInitiatorsEntersAndExits(logs: List[String]): (Set[String], Set[String], Set[String]) = {
    val initiators = logs
      .filter(_.contains("starting critical section request"))
      .map(extractNodeName).toSet

//    println(s"Initiators: ${initiators}")

    val enters = logs
      .filter(_.contains("entering critical section"))
      .map(extractNodeName).toSet

//    println(s"Enters: ${enters}")

    val exits = logs
      .filter(_.contains("exiting critical section"))
      .map(extractNodeName).toSet

//    println(s"Exits: ${exits}")

    (initiators, enters, exits)
  }

  def verifyExitFollowedByEnterSameNode(logs: List[String]): Boolean = {
    val enters = logs
      .filter(_.contains("entering critical section"))
      .map(extractNodeName)


//    println(s"Enters: ${enters}")

    val exits = logs
      .filter(_.contains("exiting critical section"))
      .map(extractNodeName)

//    println(s"Exits: ${exits}")

    val enterExitPairs = enters.zip(exits)

//    println(s"Enter exit pairs: ${enterExitPairs}")

    enterExitPairs.forall{ case (enter, exit) => enter == exit }
  }

  def verifyElectionLeader(logs: List[String]): Int= {
    val leaders = logs
      .filter(_.contains("I am the leader"))

//    println("The leaders var " + leaders)
        (leaders.length)

  }

}
