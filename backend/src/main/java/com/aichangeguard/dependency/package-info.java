/**
 * Method-level and type-level dependency graph representation and impact traversal.
 * Boundary Rule: Manages directed graph nodes and edges (CALLS, IMPLEMENTS, REFERENCES).
 * Traverses reverse caller paths from changed symbols within bounded depth limits.
 */
package com.aichangeguard.dependency;
