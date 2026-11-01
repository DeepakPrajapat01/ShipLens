/**
 * Dependency-aware test selection and test-to-symbol mapping.
 * Boundary Rule: Discovers Maven/JUnit 5 tests, maps tests to production symbols, and selects candidates.
 * Enforces fallback policies when graph coverage is incomplete; does not run tests directly.
 */
package com.aichangeguard.testing;
