package org.topbraid.shacl.validation.java;

import java.util.Collection;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.topbraid.shacl.engine.Constraint;
import org.topbraid.shacl.validation.AbstractNativeConstraintExecutor;
import org.topbraid.shacl.validation.ValidationEngine;
import org.topbraid.shacl.validation.sparql.AbstractSPARQLExecutor;
import org.topbraid.shacl.vocabulary.RSH;

class NodeConstraintExecutor extends AbstractNativeConstraintExecutor {

	@Override
	public void executeConstraint(Constraint constraint, ValidationEngine engine, Collection<RDFNode> focusNodes) {
		long startTime = System.currentTimeMillis();
		RDFNode shape = constraint.getParameterValue();

		for(RDFNode focusNode : focusNodes) {
			boolean valueNodeFailed = false;
			boolean valueNodeUnknown = false;

			for(RDFNode valueNode : engine.getValueNodes(constraint, focusNode)) {
				Model nestedResults = hasShape(engine, constraint, focusNode, valueNode, shape, false);

				if (engine.getAssignment() != null) {
					if (nestedResults.contains(shape.asResource(), RSH.No, valueNode)) {
						valueNodeFailed = true;
						break;
					}
					if (nestedResults.contains(shape.asResource(), RSH.Unknown, valueNode)) {
						valueNodeUnknown = true;
					}
				} else if(nestedResults != null) {
					Resource result = engine.createValidationResult(constraint, focusNode, valueNode, () -> "Value does not have shape " + engine.getLabelFunction().apply(shape));
					if(engine.getConfiguration().getReportDetails()) {
						AbstractSPARQLExecutor.addDetails(result, nestedResults);
					}
				}
			}
			engine.checkCanceled();


			if (engine.getAssignment() != null) {
				engine.getReport().getModel().add(constraint.getShapeResource(),
						valueNodeFailed ? RSH.No : (valueNodeUnknown ? RSH.Unknown : RSH.Yes), focusNode);
			}
		}
		addStatistics(constraint, startTime);
	}
}
