/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.brunel.build.element;

/**
 * Stores the functionality needed to build an element.
 * This is a struct-like object that is constructed using the scales and then used to write out
 * the required definitions. Any field that is defined may be used
 */
public class ElementDefinition {

    /* Definitions for x and y fields */
    public final ElementDimensionDefinition x = new ElementDimensionDefinition();
    public final ElementDimensionDefinition y = new ElementDimensionDefinition();
    public String overallSize;                         // A general size for the whole item
    public String refLocation;                         // Defines the location using a reference to another element

    public static class ElementDimensionDefinition {
        public String center;                          // Where the center is to be (always defined)
        public String left;                            // Where the left is to be (right will also be defined)
        public String right;                           // Where the right is to be (left will also be defined)
        public String size;                            // What the size is to be
        public String clusterSize;                     // The size of a cluster
    }
}
