package com.aichangeguard.repository.service;

import com.aichangeguard.repository.model.ProjectMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Securely extracts project metadata from pom.xml with XXE prevention.
 */
@Component
public class MavenPomExtractor {

    private static final Logger log = LoggerFactory.getLogger(MavenPomExtractor.class);

    public ProjectMetadata extractMetadata(Path pomPath) {
        if (pomPath == null || !Files.exists(pomPath) || !Files.isRegularFile(pomPath)) {
            return ProjectMetadata.empty();
        }

        try (InputStream is = Files.newInputStream(pomPath)) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // Secure XML processing to prevent XXE injection
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document doc = builder.parse(is);
            doc.getDocumentElement().normalize();

            Element projectElem = doc.getDocumentElement();

            String artifactId = getDirectChildText(projectElem, "artifactId");
            String name = getDirectChildText(projectElem, "name");
            if (name == null || name.isBlank()) {
                name = artifactId;
            }

            String groupId = getDirectChildText(projectElem, "groupId");
            String version = getDirectChildText(projectElem, "version");
            String packaging = getDirectChildText(projectElem, "packaging");
            if (packaging == null || packaging.isBlank()) {
                packaging = "jar";
            }

            // Fallback to parent groupId / version if not declared on root
            Element parentElem = getDirectChildElement(projectElem, "parent");
            if (parentElem != null) {
                if (groupId == null || groupId.isBlank()) {
                    groupId = getDirectChildText(parentElem, "groupId");
                }
                if (version == null || version.isBlank()) {
                    version = getDirectChildText(parentElem, "version");
                }
            }

            // Extract Java version from properties
            String javaVersion = null;
            Element propertiesElem = getDirectChildElement(projectElem, "properties");
            if (propertiesElem != null) {
                javaVersion = getDirectChildText(propertiesElem, "java.version");
                if (javaVersion == null) {
                    javaVersion = getDirectChildText(propertiesElem, "maven.compiler.release");
                }
                if (javaVersion == null) {
                    javaVersion = getDirectChildText(propertiesElem, "maven.compiler.target");
                }
            }

            return new ProjectMetadata(name, groupId, artifactId, version, packaging, javaVersion);
        } catch (Exception e) {
            log.warn("Failed to parse pom.xml at {}: {}", pomPath, e.getMessage());
            return new ProjectMetadata(pomPath.getParent().getFileName().toString(), null, null, null, null, null);
        }
    }

    private String getDirectChildText(Element parent, String tagName) {
        Element child = getDirectChildElement(parent, tagName);
        return child != null ? child.getTextContent().trim() : null;
    }

    private Element getDirectChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }
}
