package org.topbraid.shacl.validation;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.jenax.statistics.ExecStatistics;
import org.topbraid.jenax.statistics.ExecStatisticsManager;
import org.topbraid.jenax.util.JenaDatatypes;
import org.topbraid.jenax.util.JenaUtil;
import org.topbraid.shacl.arq.functions.HasShapeFunction;
import org.topbraid.shacl.engine.Constraint;
import org.topbraid.shacl.engine.Shape;
import org.topbraid.shacl.engine.ShapesGraph;
import org.topbraid.shacl.model.SHShape;
import org.topbraid.shacl.util.FailureLog;
import org.topbraid.shacl.util.ModelPrinter;
import org.topbraid.shacl.vocabulary.DASH;
import org.topbraid.shacl.vocabulary.RSH;
import org.topbraid.shacl.vocabulary.SH;

public abstract class AbstractNativeConstraintExecutor implements ConstraintExecutor {

	protected void addStatistics(Constraint constraint, long startTime) {
		if(ExecStatisticsManager.get().isRecording()) {
			long endTime = System.currentTimeMillis();
			long duration = endTime - startTime;
			ExecStatistics stats = new ExecStatistics(constraint.getComponent().getLocalName() + " (Native constraint executor)", null, duration, startTime, constraint.getComponent().asNode());
			ExecStatisticsManager.get().add(Collections.singletonList(stats));
		}
	}
	
	protected Model hasShape(ValidationEngine engine, Constraint constraint, RDFNode focusNode, RDFNode valueNode,
			RDFNode shapeNode, boolean recursionIsError) {
		Shape shape = engine.getShapesGraph().getShape(shapeNode.asNode());

		if (engine.getAssignment() != null) {
			Model nestedResults = JenaUtil.createMemoryModel();

			Property predicate = (engine.getAssignment().get(valueNode).containsKey(shapeNode)
					? (engine.getAssignment().get(valueNode).get(shapeNode) ? RSH.Yes : RSH.No)
					: RSH.Unknown);

			nestedResults.add(shapeNode.asResource(), predicate, valueNode);

			System.out.println("!! -| " + shapeNode + " " + predicate + " " + valueNode);

			return nestedResults;
		}

		URI oldShapesGraphURI = HasShapeFunction.getShapesGraphURI();
		ShapesGraph oldShapesGraph = HasShapeFunction.getShapesGraph();		
		Model oldNestedResults = HasShapeFunction.getResultsModel();
		try {
			if(!engine.getShapesGraphURI().equals(oldShapesGraphURI)) {
				HasShapeFunction.setShapesGraph(engine.getShapesGraph(), engine.getShapesGraphURI());
			}
			Model nestedResults = JenaUtil.createMemoryModel();
			HasShapeFunction.setResultsModel(nestedResults);				
			try {
				NodeValue result = HasShapeFunction.exec(valueNode.asNode(), shapeNode.asNode(), recursionIsError ? JenaDatatypes.TRUE.asNode() : null, engine.getDataset().getDefaultModel().getGraph(), engine.getDataset());
				if(NodeValue.TRUE.equals(result)) {
					return null;
				}
				else {
					return nestedResults;
				}
			}
			catch(ExprEvalException ex) {
				String message = constraint + " has produced a failure for focus node " + engine.getLabelFunction().apply(focusNode);
				FailureLog.get().logFailure(message);
				Resource result = nestedResults.createResource(DASH.FailureResult);
				result.addProperty(SH.resultSeverity, constraint.getShape().getSeverity());
				result.addProperty(SH.sourceConstraintComponent, constraint.getComponent());
				result.addProperty(SH.sourceShape, constraint.getShapeResource());
				result.addProperty(SH.focusNode, focusNode);
				result.addProperty(SH.value, valueNode);
				result.addProperty(SH.resultMessage, message);
				return nestedResults;
			}
		}
		finally {
			HasShapeFunction.setShapesGraph(oldShapesGraph, oldShapesGraphURI);
			HasShapeFunction.setResultsModel(oldNestedResults);
		}
	}
}
