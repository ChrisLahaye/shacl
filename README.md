# Recursive SHACL API

**An open source implementation of the W3C Shapes Constraint Language (SHACL) based on Apache Jena.**

Can be used to perform SHACL constraint checking and rule inferencing in any Jena-based Java application.

The set of shapes must be strictly stratified.

## Command Line Usage

Download the latest release from:

`https://github.com/ChrisLahaye/shacl/releases`

The binary to perform SHACL constraint checking can be executed as follows:

`java -cp shacl.jar org.topbraid.shacl.tools.Validate -datafile myfile.ttl -shapesfile myshapes.ttl`

where `-shapesfile` is optional and falls back to using the data graph as shapes graph.

Currently only Turtle (.ttl) files and SHACL Compact Syntax (.shaclc) files are supported.

The tools print the validation report or the inferences graph to the output screen.
