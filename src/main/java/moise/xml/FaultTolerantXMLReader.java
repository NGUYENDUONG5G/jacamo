package moise.xml;

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import moise.os.fs.ArgumentSpec;
import moise.os.fs.ConditionSpec;
import moise.os.fs.ErrorSpec;
import moise.os.fs.Failure;
import moise.os.fs.RecoveryAct;


public class FaultTolerantXMLReader {


    public static Map<String, List<Failure>> parseFailuresFromFile(File file) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(file);
        return parseFailuresFromDoc(doc);
    }

 
    public static Map<String, List<Failure>> parseFailuresFromStream(InputStream is) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(is);
        return parseFailuresFromDoc(doc);
    }

 
    public static Map<String, List<Failure>> parseFailuresFromString(String xmlContent) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new InputSource(new StringReader(xmlContent)));
        return parseFailuresFromDoc(doc);
    }

 
    public static Map<String, List<Failure>> parseFailuresFromDoc(Document doc) {
        Map<String, List<Failure>> result = new LinkedHashMap<>();
        NodeList schemeNodes = doc.getElementsByTagName("scheme");
        for (int i = 0; i < schemeNodes.getLength(); i++) {
            Node node = schemeNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element schemeEle = (Element) node;
                String schemeId = schemeEle.getAttribute("id");
                List<Failure> failures = parseFailuresFromSchemeElement(schemeEle);
                if (!failures.isEmpty()) {
                    result.put(schemeId, failures);
                }
            }
        }
        return result;
    }

 
    public static List<Failure> parseFailuresFromSchemeElement(Element schemeEle) {
        List<Failure> list = new ArrayList<>();
        NodeList children = schemeEle.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "failure".equalsIgnoreCase(child.getNodeName())) {
                Failure f = parseFailureElement((Element) child);
                if (f != null) {
                    list.add(f);
                }
            }
        }
        return list;
    }

 
    public static Failure parseFailureElement(Element failureEle) {
        String goal = failureEle.getAttribute("goal");
        String id = failureEle.getAttribute("id");
        if (id == null || id.trim().isEmpty()) {
            id = "f_" + goal;
        }

        Failure failure = new Failure(id, goal);

        NodeList children = failureEle.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "error".equalsIgnoreCase(child.getNodeName())) {
                ErrorSpec err = parseErrorElement((Element) child);
                if (err != null) {
                    failure.addError(err);
                }
            }
        }
        return failure;
    }

 
    public static ErrorSpec parseErrorElement(Element errorEle) {
        String id = errorEle.getAttribute("id");
        ErrorSpec error = new ErrorSpec(id);

        NodeList children = errorEle.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childEle = (Element) child;
            String tagName = childEle.getNodeName().toLowerCase();

            if ("condition".equals(tagName)) {
                error.setCondition(new ConditionSpec(childEle.getTextContent().trim()));
            } else if ("argument".equals(tagName)) {
                String argId = childEle.getAttribute("id");
                if (argId == null || argId.isEmpty()) {
                    argId = childEle.getAttribute("name"); 
                }
                int arity = 1;
                String arityStr = childEle.getAttribute("arity");
                if (arityStr != null && !arityStr.isEmpty()) {
                    try {
                        arity = Integer.parseInt(arityStr.trim());
                    } catch (NumberFormatException ignored) {}
                }
                error.addArgument(new ArgumentSpec(argId, arity));
            } else if ("recoveryact".equals(tagName)) {
                String scheme = childEle.getAttribute("scheme");
                error.setRecoveryAct(new RecoveryAct(scheme));
            }
        }
        return error;
    }

  
    public static Element getAsDOM(Failure failure, Document doc) {
        Element failEle = doc.createElement("failure");
        if (failure.getId() != null) {
            failEle.setAttribute("id", failure.getId());
        }
        if (failure.getGoalId() != null) {
            failEle.setAttribute("goal", failure.getGoalId());
        }

        for (ErrorSpec err : failure.getErrors()) {
            Element errEle = doc.createElement("error");
            errEle.setAttribute("id", err.getId());

            if (err.getCondition() != null && !err.getCondition().isEmpty()) {
                Element condEle = doc.createElement("condition");
                CDATASection cdata = doc.createCDATASection(" " + err.getCondition().getExpression() + " ");
                condEle.appendChild(cdata);
                errEle.appendChild(condEle);
            }

            for (ArgumentSpec arg : err.getArguments()) {
                Element argEle = doc.createElement("argument");
                argEle.setAttribute("id", arg.getId());
                argEle.setAttribute("arity", String.valueOf(arg.getArity()));
                errEle.appendChild(argEle);
            }

            if (err.getRecoveryAct() != null) {
                Element recEle = doc.createElement("recoveryact");
                recEle.setAttribute("scheme", err.getRecoveryAct().getScheme());
                errEle.appendChild(recEle);
            }

            failEle.appendChild(errEle);
        }
        return failEle;
    }
}
