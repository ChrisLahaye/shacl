package org.topbraid.shacl.validation.java;

import java.util.Collection;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.topbraid.shacl.engine.Constraint;
import org.topbraid.shacl.validation.ValidationEngine;
import org.topbraid.shacl.vocabulary.RSH;

class AndConstraintExecutor extends AbstractShapeListConstraintExecutor {

	AndConstraintExecutor(Constraint constraint) {
		super(constraint);
	}

	
	@Override
	public void executeConstraint(Constraint constraint, ValidationEngine engine, Collection<RDFNode> focusNodes) {

		long startTime = System.currentTimeMillis();

		for(RDFNode focusNode : focusNodes) {
			boolean valueNodeFailed = false;
			boolean valueNodeUnknown = false;

			value:
			for(RDFNode valueNode : engine.getValueNodes(constraint, focusNode)) {
				for (Resource shape : shapes) {
					Model nestedResults = hasShape(engine, constraint, focusNode, valueNode, shape, true);

					if (engine.getAssignment() != null) {
						if (nestedResults.contains(shape.asResource(), RSH.No, valueNode)) {
							valueNodeFailed = true;
							continue value;
						}
						if (nestedResults.contains(shape.asResource(), RSH.Unknown, valueNode)) {
							valueNodeUnknown = true;
						}
					} else if (nestedResults != null) {
						engine.createValidationResult(constraint, focusNode, valueNode,
								() -> "Value does not have all the shapes in the sh:and enumeration");
						continue value;
					}
				}
			}

			if (engine.getAssignment() != null) {
				engine.getReport().getModel().add(constraint.getShapeResource(),
						valueNodeFailed ? RSH.No : (valueNodeUnknown ? RSH.Unknown : RSH.Yes), focusNode);
			}
		}

		addStatistics(constraint, startTime);
	}
}
