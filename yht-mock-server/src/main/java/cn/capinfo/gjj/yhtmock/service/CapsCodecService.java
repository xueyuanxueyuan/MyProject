package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.CapsHeader;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

@Service
public class CapsCodecService {

    private static final int HEADER_LENGTH = 162;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");

    public ParsedFrame parseFrame(String rawMessage) {
        if (rawMessage == null) {
            return new ParsedFrame(new CapsHeader(), "", "");
        }
        int xmlStart = locateXmlStart(rawMessage);
        String headerText;
        String xmlBody;
        if (xmlStart >= 0) {
            headerText = rawMessage.substring(0, xmlStart);
            xmlBody = rawMessage.substring(xmlStart);
        } else {
            headerText = rawMessage.length() >= HEADER_LENGTH ? rawMessage.substring(0, HEADER_LENGTH) : "";
            xmlBody = rawMessage.length() >= HEADER_LENGTH ? rawMessage.substring(HEADER_LENGTH) : rawMessage;
        }
        return new ParsedFrame(parseHeader(headerText), headerText, xmlBody);
    }

    public CapsHeader parseHeader(String headerText) {
        CapsHeader header = new CapsHeader();
        if (headerText == null || headerText.isBlank()) {
            return header;
        }
        String normalizedHeader = headerText.replace("\r", "").replace("\n", "");
        if (normalizedHeader.length() < HEADER_LENGTH) {
            normalizedHeader = rightPad(normalizedHeader, HEADER_LENGTH, ' ');
        }
        header.versionId = read(normalizedHeader, 3, 2);
        header.userName = read(normalizedHeader, 5, 14);
        header.password = read(normalizedHeader, 19, 8);
        header.origSender = read(normalizedHeader, 27, 18);
        header.origSenderSid = read(normalizedHeader, 45, 4);
        header.origReceiver = read(normalizedHeader, 49, 18);
        header.origReceiverSid = read(normalizedHeader, 67, 4);
        header.origSendDate = read(normalizedHeader, 71, 8);
        header.origSendTime = read(normalizedHeader, 79, 6);
        header.structType = read(normalizedHeader, 85, 3);
        header.mesgType = read(normalizedHeader, 88, 20);
        header.mesgId = read(normalizedHeader, 108, 20);
        header.mesgRefId = read(normalizedHeader, 128, 20);
        header.mesgPriority = read(normalizedHeader, 148, 1);
        header.mesgDirection = read(normalizedHeader, 149, 1);
        header.reserve = read(normalizedHeader, 150, 9);
        return header;
    }

    public String buildMessage(String mesgType, String direction, String mesgRefId,
                               String userName, String password, String sender, String receiver,
                               String xmlBody) {
        return buildHeader(mesgType, direction, mesgRefId, userName, password, sender, receiver) + xmlBody;
    }

    public String buildHeader(String mesgType, String direction, String mesgRefId,
                              String userName, String password, String sender, String receiver) {
        LocalDateTime now = LocalDateTime.now();
        StringBuilder builder = new StringBuilder(HEADER_LENGTH);
        builder.append("{H:");
        builder.append(leftPad("02", 2, '0'));
        builder.append(rightPad(userName, 14, ' '));
        builder.append(rightPad(password, 8, ' '));
        builder.append(rightPad(sender, 18, ' '));
        builder.append("CAPS");
        builder.append(rightPad(receiver, 18, ' '));
        builder.append("CAPS");
        builder.append(now.format(DATE_FORMATTER));
        builder.append(now.format(TIME_FORMATTER));
        builder.append("XML");
        builder.append(rightPad(mesgType, 20, ' '));
        builder.append(rightPad(generateMesgId(), 20, ' '));
        builder.append(rightPad(mesgRefId, 20, ' '));
        builder.append("3");
        builder.append(rightPad(direction, 1, ' '));
        builder.append(rightPad("", 9, ' '));
        builder.append("}\r\n");
        return builder.toString();
    }

    public Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = newSecureDocumentBuilderFactory();
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            return null;
        }
    }

    public String text(Document document, String tagName) {
        if (document == null || document.getDocumentElement() == null) {
            return "";
        }
        return text(document.getDocumentElement(), tagName);
    }

    public String text(Element element, String tagName) {
        Element found = findFirst(element, tagName);
        return found == null ? "" : found.getTextContent().trim();
    }

    public Element child(Element element, String tagName) {
        return findFirst(element, tagName);
    }

    public String buildXml(String mesgType, String headXml, String bodyXml) {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        builder.append("<Message xmlns=\"urn:caps:msg:").append(mesgType).append("\">");
        if (headXml != null && !headXml.isBlank()) {
            builder.append("<Head>").append(headXml).append("</Head>");
        }
        if (bodyXml != null && !bodyXml.isBlank()) {
            builder.append("<Body>").append(bodyXml).append("</Body>");
        }
        builder.append("</Message>");
        return builder.toString();
    }

    public String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private Element findFirst(Element element, String tagName) {
        if (element == null) {
            return null;
        }
        if (matches(element, tagName)) {
            return element;
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element childElement) {
                Element found = findFirst(childElement, tagName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean matches(Element element, String tagName) {
        String localName = element.getLocalName();
        String nodeName = element.getNodeName();
        return tagName.equals(localName) || tagName.equals(nodeName);
    }

    private String read(String source, int offset, int length) {
        return source.substring(offset, offset + length).trim();
    }

    private String generateMesgId() {
        String raw = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return raw.length() > 20 ? raw.substring(0, 20) : raw;
    }

    private int locateXmlStart(String rawMessage) {
        int xmlIndex = rawMessage.indexOf("<?xml");
        if (xmlIndex >= 0) {
            return xmlIndex;
        }
        return rawMessage.indexOf("<Message");
    }

    private DocumentBuilderFactory newSecureDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private String rightPad(String value, int length, char padChar) {
        String normalized = value == null ? "" : value;
        if (normalized.length() >= length) {
            return normalized.substring(0, length);
        }
        return normalized + String.valueOf(padChar).repeat(length - normalized.length());
    }

    private String leftPad(String value, int length, char padChar) {
        String normalized = value == null ? "" : value;
        if (normalized.length() >= length) {
            return normalized.substring(normalized.length() - length);
        }
        return String.valueOf(padChar).repeat(length - normalized.length()) + normalized;
    }

    public record ParsedFrame(CapsHeader header, String headerText, String xmlBody) {
    }
}
