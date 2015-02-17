package edu.cmu.lti.nlp.amr.StanfordDecoder

import edu.cmu.lti.nlp.amr.Graph
import nlp.experiments.SequenceSystem

import scala.collection.JavaConversions._

/**
 * Created by keenon on 2/16/15.
 *
 * Use option --stanford-chunk-gen on the AMRParser to use this chunk generator instead of the standard one.
 */
object Decoder {
  var sequenceSystem : SequenceSystem = null

  def decode(line : String) : Graph = {
    var graph = Graph.Null
    graph.getNodeById.clear
    graph.getNodeByName.clear

    val sentence = line.split(" ")

    if (sequenceSystem == null) sequenceSystem = new SequenceSystem()
    val spans : java.util.Set[edu.stanford.nlp.util.Triple[Integer,Integer,String]] = sequenceSystem.getSpans(line)

    for (span : edu.stanford.nlp.util.Triple[Integer,Integer,String] <- spans) {
      graph.addSpan(sentence, span.first, span.second, span.third + 1)
    }

    if (graph.getNodeById.size == 0) {  // no invoked concepts
      graph = Graph.AMREmpty
    }

    graph
  }
}
