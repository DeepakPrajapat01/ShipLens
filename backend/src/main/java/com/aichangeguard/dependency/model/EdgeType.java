package com.aichangeguard.dependency.model;

/**
 * Directed relationship type between code elements.
 */
public enum EdgeType {
    /** Method M1 calls method M2 */
    CALLS,

    /** Class C1 extends class/interface C2 */
    EXTENDS,

    /** Class C1 implements interface I1 */
    IMPLEMENTS,

    /** Method M instantiates/constructs type T */
    CONSTRUCTS,

    /** Code element references a type */
    REFERENCES_TYPE
}
