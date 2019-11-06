/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  See the NOTICE file distributed with this work for additional
 *  information regarding copyright ownership.
 */
package org.topbraid.shacl.validation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.topbraid.shacl.engine.Constraint;
import org.topbraid.shacl.model.SHShape;
import org.topbraid.shacl.util.RecursionGuard;
import org.topbraid.shacl.vocabulary.RSH;

/**
 * Implements the special handling of sh:property by recursively calling the validator
 * against the provided property shape.
 * 
 * @author Holger Knublauch
 */
class PropertyConstraintExecutor implements ConstraintExecutor {

	@Override
	public void executeConstraint(Constraint constraint, ValidationEngine engine, Collection<RDFNode> focusNodes) {
		System.out.println("-| -| PropertyConstraintExecutor.executeConstraint(" + constraint.toString() + ", _, " + Arrays.toString(focusNodes.toArray()) + ") with value " + constraint.getParameterValue().toString());

		SHShape shape = constraint.getShapeResource();
		SHShape propertyShape = engine.getShapesGraph().getShape(constraint.getParameterValue().asNode()).getShapeResource();
		if(shape.isPropertyShape()) {
			for (RDFNode focusNode : focusNodes) {
				Collection<RDFNode> valueNodes = engine.getValueNodes(constraint, focusNode);
				executeHelper(engine, valueNodes, propertyShape.asNode());
				engine.checkCanceled();

				if (engine.getAssignment() != null && !engine.isReporting()) {
					Model report = engine.getReport().getModel();

					boolean valueNodeFailed = false;
					boolean valueNodeUnknown = false;

					for (RDFNode valueNode : valueNodes) {
						if (report.contains(propertyShape, RSH.No, valueNode)) {
							valueNodeFailed = true;
							break;
						}
						if (report.contains(propertyShape, RSH.Unknown, valueNode)) {
							valueNodeUnknown = true;
						}
					}

					report.add(shape, valueNodeFailed ? RSH.No : (valueNodeUnknown ? RSH.Unknown : RSH.Yes), focusNode);
				}
			}
		}
		else {
			executeHelper(engine, focusNodes, propertyShape.asNode());

			if (engine.getAssignment() != null && !engine.isReporting()) {
				Model report = engine.getReport().getModel();

				for (RDFNode focusNode : focusNodes) {
					for (Statement it : report.listStatements(propertyShape, null, focusNode).toList()) {
						if (it.getPredicate().getNameSpace().equals(RSH.NS)) {
							report.add(shape, it.getPredicate(), focusNode);
						}
					}
				}
			}
		}
	}


	private void executeHelper(ValidationEngine engine, Collection<RDFNode> valueNodes, Node propertyShape) {
		System.out.println("-| -| -> executeHelper(_, " + Arrays.toString(valueNodes.toArray()) + ", " + (propertyShape.isBlank() ? propertyShape.getBlankNodeLabel() : propertyShape.toString()) +")");
		System.out.println("-| -| -| -> engine.validateNodesAgainstShape()");

		engine.validateNodesAgainstShape(new ArrayList<RDFNode>(valueNodes), propertyShape);
	}
}
