package BetterSequenceDecoder

import edu.cmu.lti.nlp.amr.{Node, Span, Graph}
import nlp.experiments.SequenceSystem

import scala.collection.JavaConversions._

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

    val spans : java.util.Set[edu.stanford.nlp.util.Triple[Integer,Integer,String]] = sequenceSystem.getSpans(line)

    for (span : edu.stanford.nlp.util.Triple[Integer,Integer,String] <- spans) {
      graph.addSpan(sentence, span.first, span.second, span.third)
    }

    if (graph.getNodeById.size == 0) {  // no invoked concepts
      graph = Graph.AMREmpty
    }

    graph
  }
}
