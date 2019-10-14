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

		if (shape.isNodeShape() && engine.getAssignment() != null) {
			Model nestedResults = JenaUtil.createMemoryModel();

			Property predicate = (engine.getAssignment().get(valueNode).containsKey(shapeNode)
					? (engine.getAssignment().get(valueNode).get(shapeNode) ? RSH.Yes : RSH.No)
					: RSH.Unknown);

			nestedResults.add(shapeNode.asResource(), predicate, valueNode);

			System.out.println("!! -| " + shapeNode + " " + predicate + " " + valueNode);

			return nestedResults;
		}

		if (shape.isNodeShape() && engine.getShapesGraph().isShapeCyclic(shape)) {
			HashMap<RDFNode, HashMap<RDFNode, Boolean>> assignment = new HashMap<RDFNode, HashMap<RDFNode, Boolean>>();

			List<Shape> fpShapes = engine.getShapesGraph().getShapeDependencies(shape);
			List<RDFNode> fpNodes = engine.getDataset().getUnionModel().listObjects()
					.filterKeep(f -> f.isURIResource() && f.asNode().getURI().startsWith("http://example.org#v"))
					.toList();

			fpNodes.forEach(fpNode -> {
				if (!assignment.containsKey(fpNode)) {
					assignment.put(fpNode, new HashMap<RDFNode, Boolean>());
				}
			});

			fp: while (true) {
				HashMap<RDFNode, HashMap<RDFNode, Boolean>> prevAssignment = new HashMap<RDFNode, HashMap<RDFNode, Boolean>>();

				assignment.forEach((key, value) -> {
					prevAssignment.put(key, (HashMap<RDFNode, Boolean>) value.clone());
				});

				System.out.println("!! > Iteration");

				for (Shape fpShape : fpShapes) {
					for (RDFNode fpNode : fpNodes) {
						System.out.println("!! >> " + fpShape.getShapeResource() + " " + fpNode);

						ValidationEngine newEngine = ValidationEngineFactory.get().create(engine.getDataset(),
								engine.getShapesGraphURI(), engine.getShapesGraph(), null);
						newEngine.setAssignment(prevAssignment);
						newEngine.setConfiguration(ValidationEngine.getCurrent().getConfiguration());
						Model results = newEngine.validateNodesAgainstShape(Collections.singletonList(fpNode),
								fpShape.getShapeResource().asNode()).getModel();

						System.out.println(ModelPrinter.get().print(results));
						System.out.println("!! << " + fpShape + " " + fpNode);

						// Check non-reference constraints
						Boolean result = true;
						for (Resource r : results.listSubjectsWithProperty(RDF.type, SH.ValidationResult).toList()) {
							if (!results.contains(null, SH.detail, r)) {
								result = false;
								break;
							}
						}

						if (result) {
							// Check reference constraints
							StmtIterator failed = results.listStatements(fpShape.getShapeResource(),
									RSH.No, fpNode);

							if (failed.hasNext()) {
								System.out.println("!! :( Reference constraint violated: " + failed.next());

								assignment.get(fpNode).put(fpShape.getShapeResource(), false);
							} else {
								StmtIterator unknown = results.listStatements(fpShape.getShapeResource(), RSH.Unknown,
										fpNode);

								if (unknown.hasNext()) {
									System.out.println("!! :( Reference constraint unknown: " + unknown.next());
								} else {
									System.out.println("!! :) Success");
									assignment.get(fpNode).put(fpShape.getShapeResource(), true);
								}
							}
						} else {
							System.out.println("!! :( Non-reference constraint violated");
							assignment.get(fpNode).put(fpShape.getShapeResource(), false);
						}

						System.out.println();
					}
				}

				System.out.println("!! < Iteration");

				for (Map.Entry<RDFNode, HashMap<RDFNode, Boolean>> entry : assignment.entrySet()) {
					System.out.println("!! - " + entry.getKey() + ": " + entry.getValue());
				}

				for (Map.Entry<RDFNode, HashMap<RDFNode, Boolean>> entry : assignment.entrySet()) {
					if (!entry.getValue().equals(prevAssignment.get(entry.getKey()))) {
						continue fp;
					}
				}

				break fp;
			}

			if (assignment.get(valueNode).containsKey(shapeNode) && !assignment.get(valueNode).get(shapeNode)) {
				return JenaUtil.createMemoryModel();
			}

			return null;
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
