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

package org.brunel.build.d3;

import org.brunel.build.d3.diagrams.D3Diagram;
import org.brunel.build.element.ElementDefinition;
import org.brunel.build.element.ElementDetails;
import org.brunel.build.element.ElementStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Field;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

class D3ElementBuilder {

    private static final String BAR_SPACING = "0.9";            // Spacing between categorical bars
    private static final String CLUSTER_SPACING = "0.75";       // Spacing between clusters

    private final ScriptWriter out;                             // To write code out to
    private final VisSingle vis;                                // Element definition

    private final D3ScaleBuilder scales;                        // Helper to build scales
    private final D3LabelBuilder labelBuilder;                  // Helper to build labels
    private final D3Diagram diagram;                            // Helper to build diagrams
    private final ElementStructure structure;

    public D3ElementBuilder(ElementStructure structure, ScriptWriter out, D3ScaleBuilder scales) {
        this.structure = structure;
        this.vis = structure.vis;
        this.out = out;
        this.scales = scales;
        this.labelBuilder = new D3LabelBuilder(vis, out, structure.data);
        this.diagram = D3Diagram.make(structure, out);
    }

    public void generate(int elementIndex) {
        out.add("element = elements[" + elementIndex + "]").endStatement();

        if (diagram != null) out.onNewLine().comment("Data structures for a", vis.tDiagram, "diagram");

        ElementDetails details = makeDetails();                     // Create the details of what the element should be
        ElementDefinition elementDef = buildElementDefinition();    // And the coordinate definitions

        // Define paths needed in the element, and make data splits
        if (details.producesPath) definePathsAndSplits(elementDef);

        labelBuilder.defineLabeling(details, vis.itemsLabel, false);   // Labels

        modifyGroupStyleName();             // Diagrams change the name so CSS style sheets will work well

        // Set the values of things known to this element
        out.add("d3Data =", details.dataSource).endStatement();
        out.add("selection = main.selectAll('*').data(d3Data,", getKeyFunction(), ")").endStatement();

        // Define what happens when data is added ('enter')
        out.add("selection.enter().append('" + details.elementType + "')");
        out.add(".attr('class', ", details.classes, ")");

        if (diagram != null) diagram.writeDiagramEnter();
        else writeCoordEnter();

        // When data changes (including being added) update the items
        // These fire for both 'enter' and 'update' data

        if (diagram != null) {
            diagram.writePreDefinition(details, elementDef);
            out.add("BrunelD3.trans(selection,transitionMillis)");
            diagram.writeDefinition(details, elementDef);
        } else {
            writeCoordinateDefinition(details, elementDef);
            writeCoordinateLabelingAndAesthetics(details);
        }

        // This fires when items leave the system
        // It removes the item and any associated labels
        out.onNewLine().ln().add("BrunelD3.trans(selection.exit(),transitionMillis/3)");
        out.addChained("style('opacity', 0.5).each( function() {")
                .indentMore().indentMore()
                .add("this.remove(); if (this.__label__) this.__label__.remove()")
                .indentLess().indentLess()
                .add("})").endStatement();
    }

    public boolean needsDiagramExtras() {
        return diagram != null && diagram.needsDiagramExtras();
    }

    public boolean needsDiagramLabels() {
        return diagram != null && diagram.needsDiagramLabels();
    }

    public void writeBuildCommands() {
        if (diagram != null) diagram.writeBuildCommands();
    }

    public void writePerChartDefinitions() {
        if (diagram != null) diagram.writePerChartDefinitions();
    }

    private ElementDetails makeDetails() {
        // When we create diagrams this has the side effect of writing the data calls needed
        if (structure.isGraphEdge()) {
            return ElementDetails.makeForDiagram(vis, "graph.links", "line", "edge", "box", false);
        } else if (diagram == null)
            return ElementDetails.makeForCoordinates(vis, getSymbol());
        else
            return diagram.initializeDiagram();
    }

    private ElementDefinition buildElementDefinition() {
        ElementDefinition e = new ElementDefinition();
        Field[] x = structure.chart.coordinates.getX(vis);
        Field[] y = structure.chart.coordinates.getY(vis);
        Field[] keys = new Field[vis.fKeys.size()];
        for (int i = 0; i < keys.length; i++) keys[i] = structure.data.field(vis.fKeys.get(i).asField());
        ModelUtil.Size sizeWidth = ModelUtil.getElementSize(vis, "width");
        ModelUtil.Size sizeHeight = ModelUtil.getElementSize(vis, "height");

        if (structure.chart.geo != null) {
            if (structure.dependent) {
                if (keys.length == 1) {
                    e.refLocation = "proj(" + structure.referredLocation(keys[0]) + ")";
                } else {
                    e.refLocation = "[ proj(" + structure.referredLocation(keys[0]) + "), proj(" + structure.referredLocation(keys[1]) + ") ]";
                }
            }

            // Maps with feature data do not need the geo coordinates set
            if (vis.tDiagram != VisTypes.Diagram.map)
                setGeoLocations(e, x, y, keys);
            // Just use the default point size
            e.x.size = getSize(getSizeCall(0), sizeWidth, new Field[0], "geom.default_point_size", null);
            e.y.size = getSize(getSizeCall(1), sizeHeight, new Field[0], "geom.default_point_size", null);
        } else {
            if (structure.dependent && !structure.isGraphEdge()) {
                if (keys.length == 1) {
                    e.refLocation = structure.referredLocation(keys[0]);
                } else {
                    e.refLocation = "[" + structure.referredLocation(keys[0]) + ", " + structure.referredLocation(keys[1]) + "]";
                }
            }
            setLocations(e.x, "x", x, keys, structure.chart.coordinates.xCategorical);
            setLocations(e.y, "y", y, keys, structure.chart.coordinates.yCategorical);
            e.x.size = getSize(getSizeCall(0), sizeWidth, x, "geom.inner_width", ScalePurpose.x);
            e.y.size = getSize(getSizeCall(1), sizeHeight, y, "geom.inner_height", ScalePurpose.y);
            if (x.length > 1)
                e.x.clusterSize = getSize(null, sizeWidth, x, "geom.inner_width", ScalePurpose.inner);
        }
        e.overallSize = getOverallSize(vis, e);
        return e;
    }

    private void definePathsAndSplits(ElementDefinition elementDef) {

        // Define y or (y0, y1)
        defineVerticalExtentFunctions(elementDef, false);

        // First deal with the case of wedges (polar intervals)
        if (vis.tElement == VisTypes.Element.bar && vis.coords == VisTypes.Coordinates.polar) {
            out.add("var path = d3.svg.arc().outerRadius(geom.inner_radius).innerRadius(0)").ln();
            out.addChained("outerRadius(geom.inner_radius).innerRadius(0)").ln();
            if (vis.fRange == null && !vis.stacked)
                out.addChained("startAngle(0).endAngle(y)");
            else
                out.addChained("startAngle(y0).endAngle(y1)");
            out.endStatement();
            return;
        }

        // Add definition for the internal width of a cluster category
        if (elementDef.x.clusterSize != null)
            out.add("var w1 =", elementDef.x.clusterSize).endStatement();

        // Define the x function
        out.add("var x =", elementDef.x.center).endStatement();

        // Now actual paths
        if (vis.tElement == VisTypes.Element.area) {
            if (vis.fRange == null && !vis.stacked)
                out.add("var path = d3.svg.area().x(x).y1(y).y0(function(d) { return scale_y(0) })");
            else
                out.add("var path = d3.svg.area().x(x).y1(y1).y0(y0)");
        } else if (vis.tElement.producesSingleShape) {
            // Choose the top line if there is a range (say for stacking)
            String yDef = elementDef.y.right == null ? "y" : "y1";
            if (vis.fSize.size() == 1) {
                out.add("var path = BrunelD3.sizedPath().x(x).y(" + yDef + ")");
                String size = elementDef.y.size != null ? elementDef.y.size : elementDef.overallSize;
                out.addChained("r(" + size + ")");
            } else {
                out.add("var path = d3.svg.line().x(x).y(" + yDef + ")");
            }
        }
        if (vis.tUsing == VisTypes.Using.interpolate) {
            out.add(".interpolate('basis')");
        }
        out.endStatement();
        constructSplitPath();
    }

    private void modifyGroupStyleName() {
        // Define the main element class
        if (diagram != null)
            out.add("main.attr('class',", diagram.getStyleClasses(), ")").endStatement();
    }

    /* The key function ensure we have object constancy when animating */
    private String getKeyFunction() {
        String content = diagram != null ? diagram.getRowKey() : "d.key";
        return "function(d) { return " + content + "}";
    }

    private void writeCoordEnter() {
        // Added rounded styling if needed
        ModelUtil.Size size = ModelUtil.getRoundRectangleRadius(vis);
        if (size != null)
            out.addChained("attr('rx'," + size.valueInPixels(8) + ").attr('ry', " + size.valueInPixels(8) + ")").ln();
        out.endStatement().onNewLine().ln();
    }

    private void writeCoordinateDefinition(ElementDetails details, ElementDefinition elementDef) {

        // This starts the transition or update going
        String basicDef = "BrunelD3.trans(selection,transitionMillis)";

        if (details.splitIntoShapes)
            out.add(basicDef).addChained("attr('d', function(d) { return d.path })");     // Split path -- get it from the split
        else if (details.producesPath)
            out.add(basicDef).addChained("attr('d', path)");                              // Simple path -- just util it
        else {
            // Add definition for the internal width of a cluster category
            if (elementDef.x.clusterSize != null) {
                out.add("var w1 =", elementDef.x.clusterSize).endStatement();
            }

            if (vis.tElement == VisTypes.Element.bar)
                defineBar(basicDef, elementDef);
            else if (vis.tElement == VisTypes.Element.edge)
                defineEdge(basicDef, elementDef);
            else {
                // Handles points (as circles, rects, etc.) and text
                D3PointBuilder pointBuilder = new D3PointBuilder(out);
                if (pointBuilder.needsExtentFunctions(details)) {
                    defineVerticalExtentFunctions(elementDef, true);
                    defineHorizontalExtentFunctions(elementDef);
                }

                out.add(basicDef);
                pointBuilder.defineShapeGeometry(vis, elementDef, details);
            }
        }
    }

    private void defineHorizontalExtentFunctions(ElementDefinition elementDef) {
        if (elementDef.x.left != null) {
            // Use the left and right values
            out.add("var x0 =", elementDef.x.left).endStatement();
            out.add("var x1 =", elementDef.x.right).endStatement();
        } else {
            out.add("var x =", elementDef.x.center).endStatement();
            out.add("var w =", elementDef.x.size).endStatement();
        }
    }

    private void defineVerticalExtentFunctions(ElementDefinition elementDef, boolean withHeight) {
        if (elementDef.y.left != null) {
            // Use the left and right values
            out.add("var y0 =", elementDef.y.left).endStatement();
            out.add("var y1 =", elementDef.y.right).endStatement();
        } else {
            out.add("var y =", elementDef.y.center).endStatement();
            if (withHeight) out.add("var h =", elementDef.y.size).endStatement();
        }
    }

    private void writeCoordinateLabelingAndAesthetics(ElementDetails details) {
        // Define colors using the color function
        if (!vis.fColor.isEmpty()) out.addChained("style('" + details.colorAttribute + "', color)");

        // Define line width if needed
        if (details.needsStrokeSize)
            out.addChained("style('stroke-width', size)");

        // Define opacity
        if (!vis.fOpacity.isEmpty()) {
            out.addChained("style('fill-opacity', opacity)").addChained("style('stroke-opacity', opacity)");
        }

        out.endStatement();

        labelBuilder.addElementLabeling();

        labelBuilder.addTooltips(details);

    }

    private String getSymbol() {
        String result = ModelUtil.getElementSymbol(vis);
        if (result != null) return result;
        // We default to a rectangle if all the scales are categorical or binned, otherwise we return a point
        boolean cat = allShowExtent(structure.chart.coordinates.allXFields) && allShowExtent(structure.chart.coordinates.allYFields);
        return cat ? "rect" : "point";
    }

    private void setGeoLocations(ElementDefinition def, Field[] x, Field[] y, Field[] keys) {

        int n = x.length;
        if (y.length != n)
            throw new IllegalStateException("X and Y dimensions do not match in geographic maps");
        if (structure.isGraphEdge()) {
            throw new IllegalStateException("Cannot handle edged dependencies in geographic maps");
        }

        if (structure.dependent) {
            setDependentLocations(def.x, "x", keys, "");
            setDependentLocations(def.y, "y", keys, "");
        } else if (n == 0) {
            def.x.center = "null";
            def.y.center = "null";
        } else if (n == 1) {
            String xFunction = D3Util.writeCall(x[0]);
            String yFunction = D3Util.writeCall(y[0]);
            def.x.center = "function(d) { return proj([" + xFunction + "," + yFunction + "])[0] }";
            def.y.center = "function(d) { return proj([" + xFunction + "," + yFunction + "])[1] }";
        } else if (n == 2) {
            String xLow = D3Util.writeCall(x[0]);          // A call to the low field using the datum 'd'
            String xHigh = D3Util.writeCall(x[1]);         // A call to the high field using the datum 'd'

            // When one of the fields is a range, use the outermost value of that
            if (isRange(x[0])) xLow += ".low";
            if (isRange(x[1])) xHigh += ".high";

            String yLow = D3Util.writeCall(y[0]);          // A call to the low field using the datum 'd'
            String yHigh = D3Util.writeCall(y[1]);         // A call to the high field using the datum 'd'

            // When one of the fields is a range, use the outermost value of that
            if (isRange(y[0])) yLow += ".low";
            if (isRange(y[1])) yHigh += ".high";

            def.x.left = "function(d) { return proj([" + xLow + "," + yLow + "])[0] }";
            def.x.right = "function(d) { return proj([" + xHigh + "," + yHigh + "])[0] }";
            def.y.left = "function(d) { return proj([" + xLow + "," + yLow + "])[1] }";
            def.y.right = "function(d) { return proj([" + xHigh + "," + yHigh + "])[1] }";
        }

    }

    private String getSize(String aestheticFunctionCall, ModelUtil.Size size, Field[] fields,
                           String extent, ScalePurpose purpose) {

        boolean needsFunction = aestheticFunctionCall != null;
        String baseAmount;
        if (size != null && !size.isPercent()) {
            // Absolute size overrides everything
            baseAmount = "" + size.value();
        } else if (fields.length == 0) {
            if (vis.tDiagram != null) {
                // Default point size for diagrams
                baseAmount = "geom.default_point_size";
            } else {
                // If there are no fields, then fill the extent completely
                baseAmount = extent;
            }
        } else {
            // Use size of categories
            Field[] baseFields = fields;
            if (purpose == ScalePurpose.x || purpose == ScalePurpose.inner) {
                // Do not count the other fields
                baseFields = new Field[]{fields[0]};
            }
            int categories = scales.getCategories(baseFields).size();
            if (purpose == ScalePurpose.x && fields.length > 1) {
                // We want the size of the bars for a clustered chart
                // Each major cluster is divided into subclusters so we multiply to find the number
                // of paired categories
                Object[] cats = fields[1].categories();
                if (cats != null) categories *= cats.length;
            }
            Double granularity = scales.getGranularitySuitableForSizing(baseFields);
            if (vis.tDiagram != null) {
                // Diagrams do not define these things
                granularity = null;
                categories = 0;
            }
            // Use the categories to define the size to fill if thre are any categories
            if (categories > 0) {
                // divide up the space by the number of categories
                baseAmount = (categories == 1) ? extent : extent + "/" + categories;

                // Create some spacing between categories -- ONLY if we have all categorical data,
                // or if we are clustering (in which case a larger gap is better)

                if (purpose == ScalePurpose.inner || purpose == ScalePurpose.x && fields.length > 1)
                    baseAmount = CLUSTER_SPACING + " * " + baseAmount;
                else if ((size == null || !size.isPercent()) && !scales.allNumeric(baseFields))
                    baseAmount = BAR_SPACING + " * " + baseAmount;

            } else if (granularity != null) {
                String scaleName = "scale_" + purpose.name();
                baseAmount = "Math.abs( " + scaleName + "(" + granularity + ") - " + scaleName + "(0) )";
            } else {
                baseAmount = "geom.default_point_size";
            }
        }

        // If the size definition is a percent, use that to scale by
        if (size != null && size.isPercent())
            baseAmount = size.value() + " * " + baseAmount;

        // If we need a function, wrap it up as required
        if (needsFunction) {
            return "function(d) { return " + aestheticFunctionCall + " * " + baseAmount + "}";
        } else {
            return baseAmount;
        }

    }

    private String getSizeCall(int dim) {
        if (vis.fSize.isEmpty()) return null;                   // No sizing
        if (vis.fSize.size() == 1) return "size(d)";            // Use this for both
        return dim == 0 ? "width(d)" : "height(d)";            // Different for x and y dimensions
    }

    private void setLocations(ElementDefinition.ElementDimensionDefinition dim, String dimName, Field[] fields, Field[] keys, boolean categorical) {

        String scaleName = "scale_" + dimName;

        if (structure.isGraphEdge()) {
            // These are edges in a network layout
            dim.left = "function(d) { return d.source." + dimName + " }";
            dim.right = "function(d) { return d.target." + dimName + " }";
            return;
        }

        if (structure.dependent) {
            // Positions are dependent on other elements
            setDependentLocations(dim, dimName, keys, scaleName);
            return;
        }

        if (fields.length == 0) {
            // There are no fields -- we have a notional [0,1] extent, so use the center of that
            dim.center = "function() { return " + scaleName + "(0.5) }";
            dim.left = "function() { return " + scaleName + "(0) }";
            dim.right = "function() { return " + scaleName + "(1) }";
            return;
        }

        Field main = fields[0];
        boolean numericBins = main.isBinned() && !categorical;

        // X axis only ever has one main field at most -- rest are clustered
        boolean oneMainField = fields.length == 1 || dimName.equals("x");

        if (oneMainField) {

            // If defined, this is the cluster field on the X dimension
            Field cluster = fields.length > 1 ? fields[1] : null;

            String dataFunction = D3Util.writeCall(main);          // A call to that field using the datum 'd'

            if (numericBins) {
                // A Binned value on a non-categorical axes
                if (cluster == null) {
                    dim.center = "function(d) { return " + scaleName + "(" + dataFunction + ".mid) }";
                    dim.left = "function(d) { return " + scaleName + "(" + dataFunction + ".low) }";
                    dim.right = "function(d) { return " + scaleName + "(" + dataFunction + ".high) }";
                } else {
                    // Left of the cluster bar, right of the cluster bar, and distance along it
                    String L = scaleName + "(" + dataFunction + ".low)";
                    String R = scaleName + "(" + dataFunction + ".high)";
                    String D;
                    if (isRange(cluster))
                        D = "scale_inner(" + D3Util.writeCall(cluster) + ".mid)";
                    else
                        D = "scale_inner(" + D3Util.writeCall(cluster) + ")";

                    dim.center = "function(d) { var L=" + L + ", R=" + R + "; return (L+R)/2 + (L-R) * "
                            + CLUSTER_SPACING + " * " + D + " }";
                }
            } else if (isRange(main)) {
                // This is a range field, but we have not been asked to show both ends,
                // so we use the midpoint
                dim.center = "function(d) { return " + scaleName + "(" + dataFunction + ".mid)";
                if (cluster != null) dim.center += addClusterMultiplier(cluster);
                dim.center += " }";
            } else {
                // Nothing unusual -- just define the center
                dim.center = "function(d) { return " + scaleName + "(" + dataFunction + ")";
                if (cluster != null) dim.center += addClusterMultiplier(cluster);
                dim.center += " }";
            }

        } else {
            // The dimension contains two fields: a range
            String lowDataFunc = D3Util.writeCall(main);          // A call to the low field using the datum 'd'
            String highDataFunc = D3Util.writeCall(fields[1]);         // A call to the high field using the datum 'd'

            // When one of the fields is a range, use the outermost value of that
            if (isRange(main)) lowDataFunc += ".low";
            if (isRange(fields[1])) highDataFunc += ".high";

            dim.left = "function(d) { return " + scaleName + "(" + lowDataFunc + ") }";
            dim.right = "function(d) { return " + scaleName + "(" + highDataFunc + ") }";
            dim.center = "function(d) { return " + scaleName + "( (" + highDataFunc + " + " + lowDataFunc + " )/2) }";
        }

    }

    private String addClusterMultiplier(Field cluster) {
        if (isRange(cluster))
            return " + w1 * scale_inner(" + D3Util.writeCall(cluster) + ".mid)";
        else
            return " + w1 * scale_inner(" + D3Util.writeCall(cluster) + ")";
    }

    private void setDependentLocations(ElementDefinition.ElementDimensionDefinition dim, String dimName, Field[] keys, String scaleName) {
        // Use the keys to get the X and Y locations from other items
        if (keys.length == 1) {
            // One key gives the center
            dim.center = "function(d) { return " + scaleName + "(" + structure.keyedLocation(dimName, keys[0]) + ") }";
        } else {
            // Two keys give ends
            dim.left = "function(d) { return " + scaleName + "(" + structure.keyedLocation(dimName, keys[0]) + ") }";
            dim.right = "function(d) { return " + scaleName + "(" + structure.keyedLocation(dimName, keys[1]) + ") }";
            dim.center = "function() { return " + scaleName + "(0.5) }";        // Not sure what is best here -- should not get used
        }
    }

    public static String getOverallSize(VisSingle vis, ElementDefinition def) {
        ModelUtil.Size size = ModelUtil.getElementSize(vis, "size");
        boolean needsFunction = vis.fSize.size() == 1;

        if (size != null && !size.isPercent()) {
            // Just multiply by the aesthetic if needed
            if (needsFunction)
                return "function(d) { return size(d) * " + size.value() + " }";
            else
                return "" + size.value();
        }

        // Use the X and Y extents to define the overall one

        String x = def.x.size;
        String y = def.y.size;
        if (x.equals(y)) return x;          // If they are both the same, use that

        String xBody = D3Util.stripFunction(x);
        String yBody = D3Util.stripFunction(y);

        // This will already have the size function factored in if defined
        String content = "Math.min(" + xBody + ", " + yBody + ")";

        // if the body is different from the whole item for x or y, then we have a function and must return a function
        if (!xBody.equals(x) || !yBody.equals(y)) {
            return "function(d) { return " + content + " }";
        } else {
            return content;
        }
    }

    private void constructSplitPath() {
        // We add the x function to signal we need the paths sorted
        String params = "data, path";
        if (vis.tElement == VisTypes.Element.line || vis.tElement == VisTypes.Element.area)
            params += ", x";
        out.add("var splits = BrunelD3.makePathSplits(" + params + ");").ln();
    }

    private void defineBar(String basicDef, ElementDefinition elementDef) {
        if (vis.fRange != null || vis.stacked) {
            // Stacked or range element goes from higher of the pair of values to the lower
            out.add("var y0 =", elementDef.y.left).endStatement();
            out.add("var y1 =", elementDef.y.right).endStatement();
            defineHorizontalExtentFunctions(elementDef);
            out.add(basicDef);
            out.addChained("attr('y', function(d) { return Math.min(y0(d), y1(d)) } )");
            out.addChained("attr('height', function(d) {return Math.max(0.001, Math.abs(y0(d) - y1(d))) })");
        } else {
            // Simple element; drop from the upper value to the baseline
            out.add("var y =", elementDef.y.center).endStatement();
            defineHorizontalExtentFunctions(elementDef);
            out.add(basicDef);
            if (vis.coords == VisTypes.Coordinates.transposed) {
                out.addChained("attr('y', 0)")
                        .addChained("attr('height', function(d) { return Math.max(0,y(d)) })");
            } else {
                out.addChained("attr('y', y)")
                        .addChained("attr('height', function(d) {return Math.max(0,geom.inner_height - y(d)) }) ");
            }
        }
        new D3PointBuilder(out).defineHorizontalExtent(elementDef.x);
    }

    private void defineEdge(String basicDef, ElementDefinition elementDef) {
        out.add(basicDef);
        if (elementDef.refLocation != null) {
            out.addChained("each(function(d) { this.__edge = " + elementDef.refLocation + "})");
            if (structure.chart.geo != null) {
                // geo does not need scales
                out.addChained("attr('x1', function() { return this.__edge[0][0]})");
                out.addChained("attr('y1', function() { return this.__edge[0][1]})");
                out.addChained("attr('x2', function() { return this.__edge[1][0]})");
                out.addChained("attr('y2', function() { return this.__edge[1][1]})");
            } else {
                out.addChained("attr('x1', function() { return this.__edge[0] ? scale_x(this.__edge[0][0]) : null })");
                out.addChained("attr('y1', function() { return this.__edge[0] ? scale_y(this.__edge[0][1]) : null })");
                out.addChained("attr('x2', function() { return this.__edge[1] ? scale_x(this.__edge[1][0]) : null })");
                out.addChained("attr('y2', function() { return this.__edge[1] ? scale_y(this.__edge[1][1]) : null })");
            }
            out.addChained("each(function() { if (!this.__edge[0][0] || !this.__edge[1][0]) this.style.visibility = 'hidden'})");
        } else {
            out.addChained("attr('x1'," + elementDef.x.left + ")");
            out.addChained("attr('y1'," + elementDef.y.left + ")");
            out.addChained("attr('x2'," + elementDef.x.right + ")");
            out.addChained("attr('y2'," + elementDef.y.right + ")");
        }
    }

    private boolean allShowExtent(Field[] fields) {
        // Categorical and numeric fields both show elements as extents on the axis
        for (Field field : fields) {
            if (field.isNumeric() && !field.isBinned()) return false;
        }
        return true;
    }

    private boolean isRange(Field field) {
        if (field.isBinned() && field.isNumeric()) return true;
        String s = field.stringProperty("summary");
        return s != null && (s.equals("iqr") || s.equals("range"));
    }

    public void preBuildDefinitions() {
        if (diagram != null) diagram.preBuildDefinitions();
    }

}
