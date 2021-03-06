/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and openDb the template in the editor.
 */
package fr.inria.corese.rdftograph.driver;

import fr.inria.corese.rdftograph.RdfToGraph;
import static fr.inria.corese.rdftograph.RdfToGraph.BNODE;
import static fr.inria.corese.rdftograph.RdfToGraph.IRI;
import static fr.inria.corese.rdftograph.RdfToGraph.KIND;
import static fr.inria.corese.rdftograph.RdfToGraph.LANG;
import static fr.inria.corese.rdftograph.RdfToGraph.LITERAL;
import static fr.inria.corese.rdftograph.RdfToGraph.TYPE;
import static fr.inria.corese.rdftograph.RdfToGraph.EDGE_VALUE;
import static fr.inria.corese.rdftograph.RdfToGraph.RDF_EDGE_LABEL;
import static fr.inria.corese.rdftograph.RdfToGraph.RDF_VERTEX_LABEL;
import static fr.inria.corese.rdftograph.RdfToGraph.VERTEX_VALUE;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.tinkerpop.gremlin.neo4j.structure.Neo4jGraph;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.openrdf.model.Literal;
import org.openrdf.model.Value;

/**
 *
 * @author edemairy
 */
public class Neo4jDriver extends GdbDriver {

	Neo4jGraph graph;
	private static final Logger LOGGER = Logger.getLogger(Neo4jDriver.class.getName());

	@Override
	public void openDb(String dbPath) {
		try {
			File dbDir = new File(dbPath);
			if (getWipeOnOpen()) {
				delete(dbPath);
			}
			graph = Neo4jGraph.open(dbPath);
			graph.cypher("CREATE INDEX ON :rdf_edge(e_value)");
			graph.cypher("CREATE INDEX ON :rdf_vertex(v_value)");
			graph.tx().commit();
		} catch (Exception e) {
			LOGGER.severe(e.toString());
			e.printStackTrace();
		}
	}

	@Override
	public void closeDb() {
		try {
			graph.tx().commit();
			graph.close();
		} catch (Exception ex) {
			Logger.getLogger(Neo4jDriver.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	protected boolean nodeEquals(Node endNode, Value object) {
		boolean result = true;
		result &= endNode.getProperty(KIND).equals(RdfToGraph.getKind(object));
		if (result) {
			switch (RdfToGraph.getKind(object)) {
				case BNODE:
				case IRI:
					result &= endNode.getProperty(EDGE_VALUE).equals(object.stringValue());
					break;
				case LITERAL:
					Literal l = (Literal) object;
					result &= endNode.getProperty(EDGE_VALUE).equals(l.getLabel());
					result &= endNode.getProperty(TYPE).equals(l.getDatatype().stringValue());
					if (l.getLanguage().isPresent()) {
						result &= endNode.hasProperty(LANG) && endNode.getProperty(LANG).equals(l.getLanguage().get());
					} else {
						result &= !endNode.hasProperty(LANG);
					}
			}
		}
		return result;
	}

	private static enum RelTypes implements RelationshipType {
		CONTEXT
	}
	Map<String, Object> alreadySeen = new HashMap<>();

	/**
	 * Returns a unique id to store as the key for alreadySeen, to prevent
	 * creation of duplicates.
	 *
	 * @param v
	 * @return
	 */
	String nodeId(Value v) {
		StringBuilder result = new StringBuilder();
		String kind = RdfToGraph.getKind(v);
		switch (kind) {
			case IRI:
			case BNODE:
				result.append("label=" + v.stringValue() + ";");
				result.append("value=" + v.stringValue() + ";");
				result.append("kind=" + kind);
				break;
			case LITERAL:
				Literal l = (Literal) v;
				result.append("label=" + l.getLabel() + ";");
				result.append("value=" + l.getLabel() + ";");
				result.append("type=" + l.getDatatype().toString() + ";");
				result.append("kind=" + kind);
				if (l.getLanguage().isPresent()) {
					result.append("lang=" + l.getLanguage().get() + ";");
				}
				break;
		}
		return result.toString();
	}

	/**
	 * Returns a new node if v does not exist yet.
	 *
	 * @param v
	 * @param context
	 * @return
	 */
	@Override
	public Object createNode(Value v) {
//		Graph g = graph.getTx();
		Object result = null;
		String nodeId = nodeId(v);
		if (alreadySeen.containsKey(nodeId)) {
			return alreadySeen.get(nodeId);
		}
		switch (RdfToGraph.getKind(v)) {
			case IRI:
			case BNODE: {
				Vertex newVertex = graph.addVertex(RDF_VERTEX_LABEL);
				newVertex.property(VERTEX_VALUE, v.stringValue());
				newVertex.property(KIND, RdfToGraph.getKind(v));
				result = newVertex.id();
				break;
			}
			case LITERAL: {
				Literal l = (Literal) v;
				Vertex newVertex = graph.addVertex(RDF_VERTEX_LABEL);
				newVertex.property(VERTEX_VALUE, l.getLabel());
				newVertex.property(TYPE, l.getDatatype().toString());
				newVertex.property(KIND, RdfToGraph.getKind(v));
				if (l.getLanguage().isPresent()) {
					newVertex.property(LANG, l.getLanguage().get());
				}
				result = newVertex.id();
				break;
			}
		}
		alreadySeen.put(nodeId, result);
		return result;
	}

	@Override
	public Object createRelationship(Object source, Object object, String predicate, Map<String, Object> properties
	) {
		Object result = null;
//		OrientGraph g = graph.getTx();
		Vertex vSource = graph.vertices(source).next();
		Vertex vObject = graph.vertices(object).next();
		ArrayList<Object> p = new ArrayList<>();
		properties.keySet().stream().forEach((key) -> {
			p.add(key);
			p.add(properties.get(key));
		});
		p.add(EDGE_VALUE);
		p.add(predicate);
		Edge e = vSource.addEdge(RDF_EDGE_LABEL, vObject, p.toArray());
		result = e.id();
		return result;
		//properties.put(EDGE_VALUE, predicate);
		//return g.createRelationship((Long) source, (Long) object, rdfEdge, properties);
	}

	@Override
	public void commit() {
		graph.tx().commit();
	}
}
