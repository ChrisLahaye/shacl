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
import java.util.stream.Collectors;

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
		if (ValidationEngine.debug)
			System.out.println("-| -| PropertyConstraintExecutor.executeConstraint(" + constraint.toString() + ", _, "
					+ Arrays.toString(focusNodes.toArray()) + ") with value "
					+ constraint.getParameterValue().toString());

		Model report = engine.getReport().getModel();
		SHShape shape = constraint.getShapeResource();
		SHShape propertyShape = engine.getShapesGraph().getShape(constraint.getParameterValue().asNode()).getShapeResource();
		if(shape.isPropertyShape()) {
			for (RDFNode focusNode : focusNodes) {
				Collection<RDFNode> valueNodes = engine.getValueNodes(constraint, focusNode);

				if (engine.isReporting()) {
					report.add(shape,
							valueNodes.stream()
									.anyMatch(valueNode -> engine.hasNegShapeAssigned(propertyShape, valueNode))
											? RSH.No
											: (valueNodes.stream().allMatch(
													valueNode -> engine.hasShapeAssigned(propertyShape, valueNode))
															? RSH.Yes
															: RSH.Unknown),
							focusNode);
				} else {
					engine.validateNodesAgainstShape(new ArrayList<RDFNode>(
							valueNodes.stream().filter(valueNode -> !engine.hasShapeAssigned(propertyShape, valueNode))
									.collect(Collectors.toList())),
							propertyShape.asNode());
					engine.checkCanceled();
				}
			}
		}
		else {
			if (engine.isReporting()) {
				for (RDFNode focusNode : focusNodes) {
					report.add(shape, engine.hasNegShapeAssigned(propertyShape, focusNode) ? RSH.No : (engine.hasShapeAssigned(propertyShape, focusNode) ? RSH.Yes : RSH.Unknown), focusNode);
				}
			} else {
				List<RDFNode> valueNodes = focusNodes.stream()
						.filter(valueNode -> !engine.hasShapeAssigned(propertyShape, valueNode))
						.collect(Collectors.toList());
				engine.validateNodesAgainstShape(valueNodes, propertyShape.asNode());
			}
		}
	}
}
