package nlp.cache;

import com.esotericsoftware.kryo.Kryo;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;

/**
 * Created by keenon on 12/28/14.
 *
 * Common interface for cacheing CoreNLP annotations. Uses Protobuf, because I can't seem to get Gabor's Kryo serializer
 * to work.
 */
public abstract class CoreNLPCache {
    public abstract Annotation getAnnotation(int index);
    public abstract void close();
    static final boolean useProtobuf = false;
    static final boolean useKryo = false;
    ProtobufAnnotationSerializer protobufAnnotationSerializer = new ProtobufAnnotationSerializer();
    Kryo kryo = KryoAnnotationSerializerSupplier.getKryo();
}
