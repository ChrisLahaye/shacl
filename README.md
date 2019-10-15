# Recursive SHACL API

**An open source implementation of the W3C Shapes Constraint Language (SHACL) based on Apache Jena.**

Can be used to perform SHACL constraint checking and rule inferencing in any Jena-based Java application.

## Assumptions
Recursion is supported under the following assumptions:
1. Strictly stratified shapes
2. Node URI prefix of `http://example.org#v`

An open question is how to select nodes that will be assigned recursive shapes in the fixed-point algorithm. Currently it only considers nodes with the prefix `http://example.org#v`.

## Command Line Usage

Download the latest release from:

`https://github.com/ChrisLahaye/shacl/releases`

The binary to perform SHACL constraint checking can be executed as follows:

`java -cp shacl.jar org.topbraid.shacl.tools.Validate -datafile myfile.ttl -shapesfile myshapes.ttl`

where `-shapesfile` is optional and falls back to using the data graph as shapes graph.

Currently only Turtle (.ttl) files and SHACL Compact Syntax (.shaclc) files are supported.

The tools print the validation report or the inferences graph to the output screen.
