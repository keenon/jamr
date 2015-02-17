package BetterSequenceDecoder

import edu.cmu.lti.nlp.amr.{Node, Span, Graph}
import nlp.experiments.SequenceSystem

import scala.collection.mutable.ArrayBuffer

/**
 * Created by keenon on 2/16/15.
 */
object Decoder {
  val sequenceSystem = new SequenceSystem();

  def decode(line : String) : Graph = {
    var graph = Graph.Null
    graph.getNodeById.clear
    graph.getNodeByName.clear

    val sentence = line.split(" ")

    val start = 0
    val end = 1
    val amrStr = "(e / empty)"

    graph.addSpan(sentence, start, end, amrStr)

    if (graph.getNodeById.size == 0) {  // no invoked concepts
      graph = Graph.AMREmpty
    }

    graph
  }
}
