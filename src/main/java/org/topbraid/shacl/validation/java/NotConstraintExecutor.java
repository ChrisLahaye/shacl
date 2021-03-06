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
					if (engine.hasShapeAssigned(shape, valueNode)) {
						valueNodeFailed = true;
						break;
					} else if (!engine.hasNegShapeAssigned(shape, valueNode)) {
						valueNodeUnknown = true;
					}
				} else if (engine.hasShapeAssigned(shape, valueNode)
						|| (!engine.hasAssignment()
								&& hasShape(engine, constraint, focusNode, valueNode, shape, false) == null)) {
					engine.createValidationResult(constraint, focusNode, valueNode, () -> "Value has shape " + engine.getLabelFunction().apply(shape));
				}
			}
			engine.checkCanceled();

			if (engine.isReporting()) {
				engine.getReport().getModel().add(constraint.getShapeResource(),
						valueNodeFailed ? RSH.No : (valueNodeUnknown ? RSH.Unknown : RSH.Yes), focusNode);
			}
		}
		addStatistics(constraint, startTime);
	}
}
