package nlp.experiments;

import nlp.keenonutils.JaroWinklerDistance;
import nlp.stamr.AMR;
import nlp.stamr.AMRSlurp;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by keenon on 2/8/15.
 */
public class FrameManager {
    List<Frame> frames;

    public FrameManager(String path) throws IOException {
        frames = loadFrames(path);
    }

    /*
    public static void main(String[] args) throws IOException {
        AMR[] bank = AMRSlurp.slurp("data/amr-release-1.0-training-proxy.txt", AMRSlurp.Format.LDC);
        for (AMR amr : bank) {
            for (AMR.Node node : amr.nodes) {
                Pattern p = Pattern.compile("[0-9]+");
                if (node.title.contains("-") && !node.title.equals("-")) {
                    String[] components = node.title.split("-");
                    if (components.length == 2) {
                        if (p.matcher(components[1]).matches()) {
                            String lemma = amr.sourceText[node.alignment].toLowerCase();
                            writeFrame("data/frames", lemma, node.title);
                        }
                    }
                }
            }
        }
    }
    */

    public static void writeFrame(String path, String lemma, String name) {
        try {
            File dir = new File(path);
            if (!dir.isDirectory()) {
                throw new IllegalArgumentException("Must pass a folder to loadFrames()");
            }

            if (!name.contains("-") || name.equals("-")) return;

            String verbName = name.split("-")[0];

            String xmlPath = path+"/"+verbName+".xml";
            File xmlFile = new File(xmlPath);
            if (!xmlFile.exists()) {
                System.out.println("Newly discovered verb \""+lemma+"\" -> "+name);
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setValidating(false);
                dbf.setNamespaceAware(true);
                dbf.setFeature("http://xml.org/sax/features/namespaces", false);
                dbf.setFeature("http://xml.org/sax/features/validation", false);
                dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
                dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

                DocumentBuilder db = dbf.newDocumentBuilder();

                Document doc = db.newDocument();

                Element rootElement = doc.createElement("frameset");
                doc.appendChild(rootElement);

                Element predicate = doc.createElement("predicate");
                predicate.setAttribute("lemma", lemma);
                rootElement.appendChild(predicate);

                Element roleset = doc.createElement("roleset");
                roleset.setAttribute("id", name.replaceAll("-", "."));
                predicate.appendChild(roleset);

                // write the content into xml file
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                DOMSource source = new DOMSource(doc);
                StreamResult result = new StreamResult(xmlFile);

                // Output to console for testing
                // StreamResult result = new StreamResult(System.out);

                transformer.transform(source, result);
            }
        }
        catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
        }
    }

    public static List<Frame> loadFrames(String path) throws IOException {
        List<Frame> frames = new ArrayList<>();
        try {
            File dir = new File(path);
            if (!dir.isDirectory()) {
                throw new IllegalArgumentException("Must pass a folder to loadFrames()");
            }

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(false);
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://xml.org/sax/features/namespaces", false);
            dbf.setFeature("http://xml.org/sax/features/validation", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            DocumentBuilder db = dbf.newDocumentBuilder();

            for (File frame : dir.listFiles()) {
                Document doc = db.parse(frame);
                NodeList nl = doc.getElementsByTagName("predicate");
                for (int i = 0; i < nl.getLength(); i++) {
                    Node n = nl.item(i);
                    String lemma = n.getAttributes().getNamedItem("lemma").getNodeValue();
                    NodeList rl = n.getChildNodes();
                    for (int j = 0; j < rl.getLength(); j++) {
                        Node c = rl.item(j);
                        if (c.getNodeName().equals("roleset")) {
                            String sense = c.getAttributes().getNamedItem("id").getNodeValue();
                            sense = sense.replaceAll("\\.","-");

                            Frame f = new Frame(lemma, sense);
                            frames.add(f);
                        }
                    }
                }
            }
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
        return frames;
    }

    public double getMaxSimilarity(String token) {
        double maxSimilarity = 0;
        for (Frame f : frames) {
            double dist = JaroWinklerDistance.distance(token.toLowerCase(), f.lemma.toLowerCase());
            if (dist > maxSimilarity) {
                maxSimilarity = dist;
            }
        }
        return maxSimilarity;
    }

    public String getClosestFrame(String token) {
        return getClosestFrame(token, frames);
    }

    public static String getClosestFrame(String token, List<Frame> frames) {
        double maxSimilarity = 0;
        Frame closestFrame = null;
        for (Frame f : frames) {
            double dist = JaroWinklerDistance.distance(token.toLowerCase(), f.lemma.toLowerCase());
            if (dist > maxSimilarity) {
                maxSimilarity = dist;
                closestFrame = f;
            }
        }
        if (closestFrame != null) {
            return closestFrame.sense;
        }
        return token.toLowerCase()+"-01";
    }
}
