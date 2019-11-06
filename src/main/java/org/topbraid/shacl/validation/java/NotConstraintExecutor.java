package org.topbraid.shacl.validation.java;

import java.util.Collection;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.topbraid.shacl.engine.Constraint;
import org.topbraid.shacl.validation.AbstractNativeConstraintExecutor;
import org.topbraid.shacl.validation.ValidationEngine;
import org.topbraid.shacl.vocabulary.RSH;

class NotConstraintExecutor extends AbstractNativeConstraintExecutor {

	@Override
	public void executeConstraint(Constraint constraint, ValidationEngine engine, Collection<RDFNode> focusNodes) {
		long startTime = System.currentTimeMillis();
		RDFNode shape = constraint.getParameterValue();
		for(RDFNode focusNode : focusNodes) {
			boolean valueNodeFailed = false;
			boolean valueNodeUnknown = false;

			for(RDFNode valueNode : engine.getValueNodes(constraint, focusNode)) {
				if (engine.isReporting()) {
					if (engine.getAssignment().get(valueNode).containsKey(shape)
							&& engine.getAssignment().get(valueNode).get(shape)) {
						engine.createValidationResult(constraint, focusNode, valueNode,
								() -> "Value has shape " + engine.getLabelFunction().apply(shape));
					}
					continue;
				}

				Model nestedResults = hasShape(engine, constraint, focusNode, valueNode, shape, false);

				if (engine.getAssignment() != null) {
					if (!valueNodeFailed && nestedResults.contains(shape.asResource(), RSH.Yes, valueNode)) {
						valueNodeFailed = true;
						break;
					}
					if (!valueNodeUnknown && nestedResults.contains(shape.asResource(), RSH.Unknown, valueNode)) {
						valueNodeUnknown = true;
					}
				} else if(nestedResults == null) {
					engine.createValidationResult(constraint, focusNode, valueNode, () -> "Value has shape " + engine.getLabelFunction().apply(shape));
				}
			}
			engine.checkCanceled();

			if (engine.getAssignment() != null && !engine.isReporting()) {
				engine.getReport().getModel().add(constraint.getShapeResource(),
						valueNodeFailed ? RSH.No : (valueNodeUnknown ? RSH.Unknown : RSH.Yes), focusNode);
			}
		}
		addStatistics(constraint, startTime);
	}
}
