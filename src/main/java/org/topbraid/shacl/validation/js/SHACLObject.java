package org.topbraid.shacl.validation.js;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.expr.ExprNotComparableException;
import org.apache.jena.sparql.expr.NodeValue;
import org.topbraid.shacl.engine.ShapesGraph;
import org.topbraid.shacl.js.model.JSTerm;
import org.topbraid.shacl.validation.ValidationEngineFactory;
import org.topbraid.shacl.vocabulary.SH;

public class SHACLObject {
	
	private Dataset dataset;
	
	private URI shapesGraphURI;
	
	
	public SHACLObject(URI shapesGraphURI, Dataset dataset) {
		this.shapesGraphURI = shapesGraphURI;
		this.dataset = dataset;
	}
	
	
	public Integer compareNodes(JSTerm node1, JSTerm node2) {
		try {
			return NodeValue.compare(NodeValue.makeNode(node1.getNode()), NodeValue.makeNode(node2.getNode()));
		}
		catch(ExprNotComparableException ex) {
			return null;
		}
	}
	
	
	public boolean nodeConformsToShape(JSTerm node, JSTerm shape) {
		Model shapesModel = dataset.getNamedModel(shapesGraphURI.toString());
		ShapesGraph shapesGraph = new ShapesGraph(shapesModel);
		List<RDFNode> focusNodes = Collections.singletonList(dataset.getDefaultModel().asRDFNode(node.getNode()));
		Resource report = ValidationEngineFactory.get().create(dataset, shapesGraphURI, shapesGraph, null).
				validateNodesAgainstShape(focusNodes, shape.getNode());
		return !report.hasProperty(SH.result);
	}
}