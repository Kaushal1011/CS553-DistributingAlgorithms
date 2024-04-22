package com.distcomp.common

object utils {

  def extractId(nodeId: String): Int = {
    nodeId.stripPrefix("node-").toInt
  }


}
