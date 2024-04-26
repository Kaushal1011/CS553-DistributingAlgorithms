package com.distcomp.common

object utils {

  // extract the node id from the node actor reference path name
  def extractId(nodeId: String): Int = {
    nodeId.stripPrefix("node-").toInt
  }


}
