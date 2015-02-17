package nlp.cache;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.CollectionSerializer;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.*;
import edu.stanford.nlp.dcoref.Dictionaries;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.AnnotationSerializer;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.BasicDependenciesAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.TreeCoreAnnotations.HeadTagAnnotation;
import edu.stanford.nlp.trees.TreeCoreAnnotations.HeadWordAnnotation;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import org.objenesis.strategy.SerializingInstantiatorStrategy;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

/**
 * A custom annotator, using the Kryo framework.
 * A fair bit of the code, particularly for serializing Semantic Graphs,
 * was taken from CustomAnnotationSerializer and adapted for the Kryo
 * framework.
 *
 * @author Gabor Angeli
 * @author Keenon Werling - refactored to return a correctly configured Kryo object statically
 */
@SuppressWarnings("unchecked")
public class KryoAnnotationSerializerSupplier {
  private static final boolean DEFAULT_COMPRESS  = true;
  private static final boolean DEFAULT_ROBUST    = false;  // Was false for 2013 evaluation
  private static final boolean DEFAULT_SAVEROOTS = false;  // Was false for 2013 evaluation

  public static Kryo getKryo() {
    return getKryo(DEFAULT_COMPRESS, DEFAULT_ROBUST, DEFAULT_SAVEROOTS);
    // return new Kryo();
  }

  public static Kryo getKryo(boolean compress, boolean robustBackwardsCompatibility, boolean includeDependencyRoots) {
    Kryo kryo = new Kryo();

    // Trees are not really collections, and this causes Kryo to lose
    // its marbles: http://i.imgur.com/FSakhIy.gif.
    // Also, for some reason this has to come before the register() calls
    // below?
    kryo.addDefaultSerializer(Collection.class, new CollectionSerializer() {
      private final FieldSerializer<Tree> treeSerializer = new FieldSerializer<Tree>(kryo, Tree.class);

      @Override
      public void write(Kryo kryo, Output output, Collection collection) {
        assert collection != null;
        for (Object o : collection) { assert o != null; }
        if (collection instanceof LabeledScoredTreeNode) {
          Tree tree = (LabeledScoredTreeNode) collection;
          String treeString = tree.toString();
          assert treeString != null;
          byte[] bytes = treeString.getBytes();
          output.writeInt(bytes.length);
          output.write(bytes);
        } else if (collection instanceof Tree) {
          treeSerializer.write(kryo, output, (Tree) collection);
        } else if (unmodifiableListClass.isAssignableFrom(collection.getClass())) {
          ArrayList copy = new ArrayList();
          copy.addAll(collection);
          super.write(kryo, output, copy);
        } else {
          System.out.println("Writing a regular old "+collection);
          super.write(kryo, output, collection);
        }
      }

      @Override
      public Collection read(Kryo kryo, Input input, Class<Collection> collectionClass) {
        // NOTE: If you're reading a null tree, you're going to have a bad time.
        // The collectionClass will recognize that it should be a tree, but the writer had
        // no way of knowing that you were writing a tree, so it wrote the tree as a default
        // collection.
        // I blame Java for not having real type safety,
        // but if you find that impractical you can go ahead and blame me too.
        if (LabeledScoredTreeNode.class.isAssignableFrom(collectionClass)) {
          int length = input.readInt();
          String treeString = new String(input.readBytes(length));
          try {
            return new PennTreeReader(new StringReader(treeString), new LabeledScoredTreeFactory(CoreLabel.factory())).readTree();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        } else if (Tree.class.isAssignableFrom(collectionClass)) {
          //noinspection unchecked
          return treeSerializer.read(kryo, input, (Class) collectionClass);
        } else if (unmodifiableListClass.isAssignableFrom(collectionClass)) {
          return Collections.unmodifiableList((List) super.read(kryo, input, (Class) ArrayList.class));
        } else {
          return super.read(kryo, input, collectionClass);
        }
      }
    });

    // GrammaticalRelation is a funky class to serialize (multithreaded at least)
    kryo.addDefaultSerializer(SemanticGraph.class, new Serializer<SemanticGraph>(){
      @Override
      public void write(Kryo kryo, Output output, SemanticGraph graph) {
        // Is Null
        if (graph == null) {
          output.writeBoolean(true);
          return;
        } else {
          output.writeBoolean(false);
        }

        // Meta-info
        Set<IndexedWord> nodes = graph.vertexSet();
        output.writeInt(nodes.size());
        if (!nodes.isEmpty()) {
          IndexedWord node = nodes.iterator().next();
          assert node.containsKey(DocIDAnnotation.class);
          output.writeString(node.get(DocIDAnnotation.class));
          assert node.containsKey(SentenceIndexAnnotation.class);
          output.writeInt(node.get(SentenceIndexAnnotation.class));
        }

        // Token Info
        for (IndexedWord node : graph.vertexSet()) {
          output.writeInt(node.index());
          if (node.copyCount() > 0) {
            output.writeBoolean(true);
            output.writeInt(node.copyCount());
          } else {
            output.writeBoolean(false);
          }
        }

        // Edges
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
          assert edge != null && edge.getRelation() != null && edge.getRelation().toString() != null;
          String rel = edge.getRelation().toString();
          int source = edge.getSource().index();
          int target = edge.getTarget().index();
          boolean extra = edge.isExtra();
          output.writeBoolean(true);
          output.writeString(rel);
          output.writeInt(source);
          output.writeInt(target);
          output.writeBoolean(extra); 
          // FIXME: we should write out the copy annotation of the
          // endpoints so that certain rare collapsed annotations are
          // saved correctly
        }
        output.writeBoolean(false);

        if (includeDependencyRoots) { // FIXME: instead write out "root" as a boolean attached to the nodes above
          // Roots (not necessary part of the vertices)
          Collection<IndexedWord> roots = graph.getRoots();
          assert roots != null;
          output.writeInt(roots.size());
          if (nodes.isEmpty()) {
            // there was no DocID or sentenceIndex information!!!
            // Have some here
            if (!roots.isEmpty()) {
              IndexedWord node = roots.iterator().next();
              assert node.containsKey(DocIDAnnotation.class);
              output.writeString(node.get(DocIDAnnotation.class));
              assert node.containsKey(SentenceIndexAnnotation.class);
              output.writeInt(node.get(SentenceIndexAnnotation.class));
            }
          }
          for (IndexedWord node : roots) {
            output.writeInt(node.index());
            if (node.copyCount() > 0) {
              output.writeBoolean(true);
              output.writeInt(node.copyCount());
            } else {
              output.writeBoolean(false);
            }
          }
        }
      }

      @Override
      public SemanticGraph read(Kryo kryo, Input input, Class<SemanticGraph> grammaticalRelationClass) {
        // Is null
        boolean isNull = input.readBoolean();
        if (isNull) return null;

        // Meta-info
        int size = input.readInt();
        String docid = size > 0 ? input.readString() : "";
        int sentenceIndex = size > 0 ? input.readInt() : -1;

        // Token Info
        List<AnnotationSerializer.IntermediateNode> nodes = new ArrayList<AnnotationSerializer.IntermediateNode>(size);
        for ( int i = 0; i < size; ++i) {
          int index = input.readInt();
          Integer copy = null;
          if (input.readBoolean()) { copy = input.readInt(); }
          boolean isRoot = false; // FIXME: labeling the root nodes would make much more sense than saving a separate list
          nodes.add(new AnnotationSerializer.IntermediateNode(docid, sentenceIndex, index, copy == null ? -1 : copy, isRoot));
        }

        // Edges
        List<AnnotationSerializer.IntermediateEdge> edges = new ArrayList<AnnotationSerializer.IntermediateEdge>(size + 1);
        while (input.readBoolean()) {
          String rel = input.readString();
          int source = input.readInt();
          int target = input.readInt();
          boolean isExtra = input.readBoolean();
          int sourceCopy = 0;  // FIXME: we are punting on nodes with copy annotations for now
          int targetCopy = 0;
          edges.add(new AnnotationSerializer.IntermediateEdge(rel, source, sourceCopy, target, targetCopy, isExtra));
        }

        List<AnnotationSerializer.IntermediateNode> roots = null;
        if (includeDependencyRoots) { // FIXME: we should just get rid of this and instead mark the roots
          // Crazy dependency roots - why aren't they part of the vertices
          int nRoots = input.readInt();
          roots = new ArrayList<AnnotationSerializer.IntermediateNode>(nRoots);
          if (nodes.isEmpty()) {
            // there was no DocID or sentenceIndex information!!!
            // Have some here
            if (nRoots > 0) {
              docid = input.readString();
              sentenceIndex = input.readInt();
            }
          }
          for ( int i = 0; i < nRoots; ++i) {
            int index = input.readInt();
            Integer copy = null;
            if (input.readBoolean()) { copy = input.readInt(); }
            roots.add(new AnnotationSerializer.IntermediateNode(docid, sentenceIndex, index, copy == null ? -1 : copy, true));
          }
        }
        // FIXME: we just throw away the roots info, not really needed.  We shouldn't even bother writing it out
        return new SemanticGraphWrapper(nodes, edges);
      }
    });

    // IMPORTANT NOTE: Add new classes to the *END* of this list,
    // and don't change these numbers.
    // otherwise, de-serializing existing serializations won't work.
    // Note that registering classes here is an efficiency tweak, and not
    // strictly necessary
    kryo.register(String.class, 0);
    kryo.register(Short.class, 1);
    kryo.register(Integer.class, 2);
    kryo.register(Long.class, 3);
    kryo.register(Float.class, 4);
    kryo.register(Double.class, 5);
    kryo.register(Boolean.class, 6);
    kryo.register(List.class, 17);
    kryo.register(ArrayList.class, 18);
    kryo.register(LinkedList.class, 19);
    kryo.register(ArrayCoreMap.class, 20);
    kryo.register(CoreMap.class, 21);
    kryo.register(CoreLabel.class, 22);
    kryo.register(Calendar.class, 23);
    kryo.register(Map.class, 24);
    kryo.register(HashMap.class, 25);
    kryo.register(Pair.class, 26);
    kryo.register(Tree.class, 27);
    kryo.register(LabeledScoredTreeNode.class, 28);
    kryo.register(TreeGraphNode.class, 29);
    kryo.register(CorefChain.class, 30);
    kryo.register(CorefChain.CorefMention.class, 32);
    kryo.register(Dictionaries.MentionType.class, 33);
    kryo.register(Dictionaries.Animacy.class, 34);
    kryo.register(Dictionaries.Gender.class, 35);
    kryo.register(Dictionaries.Number.class, 36);
    kryo.register(Dictionaries.Person.class, 37);
    kryo.register(Set.class, 38);
    kryo.register(HashSet.class, 39);
    kryo.register(Label.class, 40);
    kryo.register(SemanticGraph.class, 41);
    kryo.register(SemanticGraphEdge.class, 42);
    kryo.register(IndexedWord.class, 43);
    kryo.register(Timex.class, 44);
    kryo.register(subListClass, 45);
    // The keys (hey, why not)
    kryo.register(LabelWeightAnnotation.class, 101);
    kryo.register(AntecedentAnnotation.class, 102);
    kryo.register(LeftChildrenNodeAnnotation.class, 103);
    kryo.register(MentionTokenAnnotation.class, 104);
    kryo.register(ParagraphAnnotation.class, 105);
    kryo.register(SpeakerAnnotation.class, 106);
    kryo.register(UtteranceAnnotation.class, 107);
    kryo.register(UseMarkedDiscourseAnnotation.class, 108);
    kryo.register(NumerizedTokensAnnotation.class, 109);
    kryo.register(NumericCompositeObjectAnnotation.class, 110);
    kryo.register(NumericCompositeTypeAnnotation.class, 111);
    kryo.register(NumericCompositeValueAnnotation.class, 112);
    kryo.register(NumericObjectAnnotation.class, 113);
    kryo.register(NumericValueAnnotation.class, 114);
    kryo.register(NumericTypeAnnotation.class, 115);
    kryo.register(DocDateAnnotation.class, 116);
    kryo.register(CommonWordsAnnotation.class, 117);
    kryo.register(ProtoAnnotation.class, 118);
    kryo.register(PhraseWordsAnnotation.class, 119);
    kryo.register(PhraseWordsTagAnnotation.class, 120);
    kryo.register(WordnetSynAnnotation.class, 121);
    kryo.register(TopicAnnotation.class, 122);
    kryo.register(XmlContextAnnotation.class, 123);
    kryo.register(XmlElementAnnotation.class, 124);
    //kryo.register(CopyAnnotation.class, 125);
    kryo.register(ArgDescendentAnnotation.class, 126);
    kryo.register(CovertIDAnnotation.class, 127);
    kryo.register(SemanticTagAnnotation.class, 128);
    kryo.register(SemanticWordAnnotation.class, 129);
    kryo.register(PriorAnnotation.class, 130);
    kryo.register(YearAnnotation.class, 131);
    kryo.register(DayAnnotation.class, 132);
    kryo.register(MonthAnnotation.class, 133);
    kryo.register(HeadWordStringAnnotation.class, 134);
    kryo.register(GrandparentAnnotation.class, 135);
    kryo.register(PercentAnnotation.class, 136);
    kryo.register(NotAnnotation.class, 137);
    kryo.register(BeAnnotation.class, 138);
    kryo.register(HaveAnnotation.class, 139);
    kryo.register(DoAnnotation.class, 140);
    kryo.register(UnaryAnnotation.class, 141);
    kryo.register(FirstChildAnnotation.class, 142);
    kryo.register(PrevChildAnnotation.class, 143);
    kryo.register(StateAnnotation.class, 144);
    kryo.register(SpaceBeforeAnnotation.class, 145);
    kryo.register(UBlockAnnotation.class, 146);
    kryo.register(D2_LEndAnnotation.class, 147);
    kryo.register(D2_LMiddleAnnotation.class, 148);
    kryo.register(D2_LBeginAnnotation.class, 149);
    kryo.register(LEndAnnotation.class, 150);
    kryo.register(LMiddleAnnotation.class, 151);
    kryo.register(LBeginAnnotation.class, 152);
    kryo.register(LengthAnnotation.class, 153);
    kryo.register(HeightAnnotation.class, 154);
    kryo.register(BagOfWordsAnnotation.class, 155);
    kryo.register(SubcategorizationAnnotation.class, 156);
    kryo.register(TrueTagAnnotation.class, 157);
    kryo.register(WordFormAnnotation.class, 158);
    kryo.register(DependentsAnnotation.class, 159);
    kryo.register(ContextsAnnotation.class, 160);
    kryo.register(NeighborsAnnotation.class, 161);
    kryo.register(LabelAnnotation.class, 162);
    kryo.register(LastTaggedAnnotation.class, 163);
    kryo.register(BestFullAnnotation.class, 164);
    kryo.register(BestCliquesAnnotation.class, 165);
    kryo.register(AnswerObjectAnnotation.class, 166);
    kryo.register(EntityClassAnnotation.class, 167);
    kryo.register(SentenceIDAnnotation.class, 168);
    kryo.register(SentencePositionAnnotation.class, 169);
    kryo.register(ParaPositionAnnotation.class, 170);
    kryo.register(WordPositionAnnotation.class, 171);
    kryo.register(SectionAnnotation.class, 172);
    kryo.register(EntityRuleAnnotation.class, 173);
    kryo.register(UTypeAnnotation.class, 174);
    kryo.register(OriginalCharAnnotation.class, 175);
    kryo.register(OriginalAnswerAnnotation.class, 176);
    kryo.register(PredictedAnswerAnnotation.class, 177);
    kryo.register(IsDateRangeAnnotation.class, 178);
    kryo.register(EntityTypeAnnotation.class, 179);
    kryo.register(IsURLAnnotation.class, 180);
    kryo.register(LastGazAnnotation.class, 181);
    kryo.register(MaleGazAnnotation.class, 182);
    kryo.register(FemaleGazAnnotation.class, 183);
    kryo.register(WebAnnotation.class, 184);
    kryo.register(DictAnnotation.class, 185);
    kryo.register(FreqAnnotation.class, 186);
    kryo.register(AbstrAnnotation.class, 187);
    kryo.register(GeniaAnnotation.class, 188);
    kryo.register(AbgeneAnnotation.class, 189);
    kryo.register(GovernorAnnotation.class, 190);
    kryo.register(ChunkAnnotation.class, 191);
    kryo.register(AbbrAnnotation.class, 192);
    kryo.register(DistSimAnnotation.class, 193);
    kryo.register(PossibleAnswersAnnotation.class, 194);
    kryo.register(GazAnnotation.class, 195);
    kryo.register(IDAnnotation.class, 196);
    kryo.register(UnknownAnnotation.class, 197);
    kryo.register(CharAnnotation.class, 198);
    kryo.register(PositionAnnotation.class, 199);
    kryo.register(DomainAnnotation.class, 200);
    kryo.register(TagLabelAnnotation.class, 201);
    kryo.register(NumTxtSentencesAnnotation.class, 202);
    kryo.register(SRLInstancesAnnotation.class, 203);
    kryo.register(WordSenseAnnotation.class, 204);
    kryo.register(CostMagnificationAnnotation.class, 205);
    kryo.register(CharacterOffsetEndAnnotation.class, 206);
    kryo.register(CharacterOffsetBeginAnnotation.class, 207);
    kryo.register(ChineseIsSegmentedAnnotation.class, 208);
    kryo.register(ChineseSegAnnotation.class, 209);
    kryo.register(ChineseOrigSegAnnotation.class, 210);
    kryo.register(ChineseCharAnnotation.class, 211);
    kryo.register(MorphoCaseAnnotation.class, 212);
    kryo.register(MorphoGenAnnotation.class, 213);
    kryo.register(MorphoPersAnnotation.class, 214);
    kryo.register(MorphoNumAnnotation.class, 215);
    kryo.register(PolarityAnnotation.class, 216);
    kryo.register(StemAnnotation.class, 217);
    kryo.register(GazetteerAnnotation.class, 218);
    kryo.register(RoleAnnotation.class, 219);
    kryo.register(InterpretationAnnotation.class, 220);
    kryo.register(FeaturesAnnotation.class, 221);
    kryo.register(GoldAnswerAnnotation.class, 222);
    kryo.register(AnswerAnnotation.class, 223);
    kryo.register(SpanAnnotation.class, 224);
    kryo.register(INAnnotation.class, 225);
    kryo.register(ParentAnnotation.class, 226);
    kryo.register(LeftTermAnnotation.class, 227);
    kryo.register(ShapeAnnotation.class, 228);
    kryo.register(SRLIDAnnotation.class, 229);
    kryo.register(SRL_ID.class, 230);
    kryo.register(NormalizedNamedEntityTagAnnotation.class, 231);
    kryo.register(NERIDAnnotation.class, 232);
    kryo.register(CategoryFunctionalTagAnnotation.class, 233);
    kryo.register(VerbSenseAnnotation.class, 234);
    kryo.register(SemanticHeadTagAnnotation.class, 235);
    kryo.register(SemanticHeadWordAnnotation.class, 236);
    kryo.register(MarkingAnnotation.class, 237);
    kryo.register(ArgumentAnnotation.class, 238);
    kryo.register(ProjectedCategoryAnnotation.class, 239);
    kryo.register(IDFAnnotation.class, 240);
    kryo.register(CoNLLDepParentIndexAnnotation.class, 241);
    kryo.register(CoNLLDepTypeAnnotation.class, 242);
    kryo.register(CoNLLSRLAnnotation.class, 243);
    kryo.register(CoNLLPredicateAnnotation.class, 244);
    kryo.register(CoNLLDepAnnotation.class, 245);
    kryo.register(CoarseTagAnnotation.class, 246);
    kryo.register(AfterAnnotation.class, 247);
    kryo.register(BeforeAnnotation.class, 248);
    kryo.register(OriginalTextAnnotation.class, 249);
    kryo.register(CategoryAnnotation.class, 250);
    kryo.register(ValueAnnotation.class, 251);
    kryo.register(LineNumberAnnotation.class, 252);
    kryo.register(SentenceIndexAnnotation.class, 253);
    kryo.register(ForcedSentenceEndAnnotation.class, 254);
    kryo.register(EndIndexAnnotation.class, 255);
    kryo.register(BeginIndexAnnotation.class, 256);
    kryo.register(IndexAnnotation.class, 257);
    kryo.register(DocIDAnnotation.class, 258);
    kryo.register(CalendarAnnotation.class, 259);
    kryo.register(TokenEndAnnotation.class, 260);
    kryo.register(TokenBeginAnnotation.class, 261);
    kryo.register(ParagraphsAnnotation.class, 262);
    kryo.register(SentencesAnnotation.class, 263);
    kryo.register(GenericTokensAnnotation.class, 264);
    kryo.register(TokensAnnotation.class, 265);
    kryo.register(TrueCaseTextAnnotation.class, 266);
    kryo.register(TrueCaseAnnotation.class, 267);
    kryo.register(StackedNamedEntityTagAnnotation.class, 268);
    kryo.register(NamedEntityTagAnnotation.class, 269);
    kryo.register(PartOfSpeechAnnotation.class, 270);
    kryo.register(LemmaAnnotation.class, 271);
    kryo.register(TextAnnotation.class, 272);
    kryo.register(CorefChainAnnotation.class, 273);
    kryo.register(CorefClusterAnnotation.class, 274);
    kryo.register(CorefClusterIdAnnotation.class, 275);
    //noinspection deprecation
    kryo.register(CorefGraphAnnotation.class, 276);
    kryo.register(CorefDestAnnotation.class, 277);
    kryo.register(CorefAnnotation.class, 278);
    kryo.register(HeadTagAnnotation.class, 279);
    kryo.register(HeadWordAnnotation.class, 280);
    kryo.register(TreeAnnotation.class, 281);
    kryo.register(CollapsedDependenciesAnnotation.class, 282);
    kryo.register(CollapsedCCProcessedDependenciesAnnotation.class, 283);
    kryo.register(TimeAnnotations.TimexAnnotation.class, 284);
    kryo.register(TimeAnnotations.TimexAnnotations.class, 285);

    // Handle zero argument constructors gracefully
    kryo.setInstantiatorStrategy(new SerializingInstantiatorStrategy());

    // Robust backwards compatibility.
    // Fields can be added or removed, but their signature
    // can't be changed (this seems fair).
    if (robustBackwardsCompatibility) {
      kryo.setDefaultSerializer(CompatibleFieldSerializer.class);
    }

    return kryo;
  }

  private static final Class unmodifiableListClass;
  private static final Class subListClass;
  private static final Object globalLock = "I'm a lock :-)";

  static {
    ArrayList<Integer> dummy = new ArrayList<Integer>();
    dummy.add(1);
    dummy.add(2);
    subListClass = dummy.subList(0, 1).getClass();
    unmodifiableListClass = Collections.unmodifiableList(dummy).getClass();
  }

  private static void fixAnnotation(Annotation someObject) {
    // Fix up Annotation (e.g., dependency graph)
    if (someObject.get(SentencesAnnotation.class) != null) {
      for (CoreMap sentence : someObject.get(SentencesAnnotation.class)) {
        List<CoreLabel> tokens = sentence.get(TokensAnnotation.class);
        if (sentence.containsKey(CollapsedDependenciesAnnotation.class)) {
          sentence.set(CollapsedDependenciesAnnotation.class,
                       ((SemanticGraphWrapper) sentence.get(CollapsedDependenciesAnnotation.class)).convertIntermediateGraph(tokens));
        }
        if (sentence.containsKey(BasicDependenciesAnnotation.class)) {
          sentence.set(BasicDependenciesAnnotation.class,
                       ((SemanticGraphWrapper) sentence.get(BasicDependenciesAnnotation.class)).convertIntermediateGraph(tokens));
        }
        if (sentence.containsKey(CollapsedCCProcessedDependenciesAnnotation.class)) {
          sentence.set(CollapsedCCProcessedDependenciesAnnotation.class,
                       ((SemanticGraphWrapper) sentence.get(CollapsedCCProcessedDependenciesAnnotation.class)).convertIntermediateGraph(tokens));
        }
      }
    }
  }

  // FIXME: this should not extend SemanticGraph
  private static class SemanticGraphWrapper extends SemanticGraph {
    public List<AnnotationSerializer.IntermediateNode> nodes;
    public List<AnnotationSerializer.IntermediateEdge> edges;

    public SemanticGraphWrapper(List<AnnotationSerializer.IntermediateNode> nodes, List<AnnotationSerializer.IntermediateEdge> edges) {
      this.nodes = new ArrayList<AnnotationSerializer.IntermediateNode>(nodes);
      this.edges = new ArrayList<AnnotationSerializer.IntermediateEdge>(edges);
    }

    SemanticGraph convertIntermediateGraph(List<CoreLabel> tokens) {
      AnnotationSerializer.IntermediateSemanticGraph graph = new AnnotationSerializer.IntermediateSemanticGraph(nodes, edges);
      return graph.convertIntermediateGraph(tokens);
    }
  }

}
