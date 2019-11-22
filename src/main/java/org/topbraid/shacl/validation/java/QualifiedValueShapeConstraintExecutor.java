package org.topbraid.shacl.validation.java;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.topbraid.jenax.util.JenaDatatypes;
import org.topbraid.jenax.util.JenaUtil;
import org.topbraid.shacl.engine.Constraint;
import org.topbraid.shacl.validation.AbstractNativeConstraintExecutor;
import org.topbraid.shacl.validation.ValidationEngine;
import org.topbraid.shacl.vocabulary.RSH;
import org.topbraid.shacl.vocabulary.SH;

// Note this is used for both min and max, but min is skipped if max also exists (avoids doing the count twice)
class QualifiedValueShapeConstraintExecutor extends AbstractNativeConstraintExecutor {
	
	private boolean disjoint;
	
	private Integer maxCount;
	
	private Integer minCount;
	
	private Set<Resource> siblings = new HashSet<>();
	
	private Resource valueShape;

	
	QualifiedValueShapeConstraintExecutor(Constraint constraint) {
		valueShape = constraint.getShapeResource().getPropertyResourceValue(SH.qualifiedValueShape);
		disjoint = constraint.getShapeResource().hasProperty(SH.qualifiedValueShapesDisjoint, JenaDatatypes.TRUE);
		if(disjoint) {
			for(Resource parent : constraint.getShapeResource().getModel().listSubjectsWithProperty(SH.property, constraint.getShapeResource()).toList()) {
				for(Resource ps : JenaUtil.getResourceProperties(parent, SH.property)) {
					siblings.addAll(JenaUtil.getResourceProperties(ps, SH.qualifiedValueShape));
				}
			}
	        siblings.remove(valueShape);
		}
		maxCount = JenaUtil.getIntegerProperty(constraint.getShapeResource(), SH.qualifiedMaxCount);
		minCount = JenaUtil.getIntegerProperty(constraint.getShapeResource(), SH.qualifiedMinCount);
	}

	
    private boolean hasAnySiblingShape(ValidationEngine engine, Constraint constraint, RDFNode focusNode, RDFNode valueNode) {
        for(Resource sibling : siblings) {
        	Model results = hasShape(engine, constraint, focusNode, valueNode, sibling, true);
        	if(results == null) {
        		return true;
        	}
        }
        return false;
    }

	
	@Override
	public void executeConstraint(Constraint constraint, ValidationEngine engine, Collection<RDFNode> focusNodes) {
		long startTime = System.currentTimeMillis();
		
		if(minCount != null && maxCount != null && SH.QualifiedMinCountConstraintComponent.equals(constraint.getComponent())) {
			// Skip minCount constraint if there is also a maxCount constraint at the same shape
			return;
		}
		
		for(RDFNode focusNode : focusNodes) {
			int valueNodePositive = 0;
			int valueNodeNegative = 0;

			Collection<RDFNode> valueNodes = engine.getValueNodes(constraint, focusNode);
	
			for (RDFNode valueNode : valueNodes) {
				if (engine.isReporting()) {
					if (engine.hasShapeAssigned(valueShape, valueNode)) {
						valueNodePositive++;
					} else if (engine.hasNegShapeAssigned(valueShape, valueNode)) {
						valueNodeNegative++;
					}
				} else if (engine.hasShapeAssigned(valueShape, valueNode) || (!engine.hasAssignment() && hasShape(engine, constraint, focusNode, valueNode, valueShape, true) == null)) {
					valueNodePositive++;
				}
			}
			
			if (engine.isReporting()) {
				if (minCount != null) {
					engine.getReport().getModel().add(constraint.getShapeResource(),
							valueNodePositive >= minCount ? RSH.Yes
									: (valueNodes.size() - valueNodeNegative < minCount ? RSH.No : RSH.Unknown),
									focusNode);
				}
				if (maxCount != null) {
					engine.getReport().getModel().add(constraint.getShapeResource(),
							valueNodePositive >= maxCount + 1 ? RSH.No
									: (valueNodes.size() - valueNodeNegative < maxCount + 1 ? RSH.Yes : RSH.Unknown),
							focusNode);
				}
			} else {
				if (maxCount != null && valueNodePositive > maxCount) {
					Resource result = engine.createValidationResult(constraint, focusNode, null, () -> "More than "
							+ maxCount + " values have shape " + engine.getLabelFunction().apply(valueShape));
					result.removeAll(SH.sourceConstraintComponent);
					result.addProperty(SH.sourceConstraintComponent, SH.QualifiedMaxCountConstraintComponent);
				}
				if (minCount != null && valueNodePositive < minCount) {
					Resource result = engine.createValidationResult(constraint, focusNode, null, () -> "Less than "
							+ minCount + " values have shape " + engine.getLabelFunction().apply(valueShape));
					result.removeAll(SH.sourceConstraintComponent);
					result.addProperty(SH.sourceConstraintComponent, SH.QualifiedMinCountConstraintComponent);
				}
			}

			engine.checkCanceled();
		}
		addStatistics(constraint, startTime);
	}
}
