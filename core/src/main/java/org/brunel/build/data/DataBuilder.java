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

package org.brunel.build.data;

import org.brunel.action.Param;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.model.VisSingle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Created by graham on 12/16/15.
 */
public class DataBuilder {

    private final DataModifier modifier;
    private final VisSingle vis;

    public DataBuilder(VisSingle vis, DataModifier modifier) {
        this.vis = vis;
        this.modifier = modifier;
    }

    /**
     * This builds the data and reports the built data to the builder
     *
     * @return built dataset
     */
    public Dataset build() {
        String constantsCommand = makeConstantsCommand();
        String filterCommand = makeFilterCommands();
        String binCommand = makeTransformCommands();
        String summaryCommand = buildSummaryCommands();
        String sortCommand = makeFieldCommands();
        String seriesYFields = makeSeriesCommand();
        String usedFields = required();

        DataTransformParameters params = new DataTransformParameters(constantsCommand,
                filterCommand, binCommand, summaryCommand, "", sortCommand, "", seriesYFields,
                usedFields);

        // Call the engine to see if it has any special needs
        params = modifier.modifyParameters(params, vis);

        Dataset data = vis.getDataset();                                                // The data to use
        data = data.addConstants(params.constantsCommand);                              // add constant fields
        data = data.filter(params.filterCommand);                                       // filter data
        data = data.bin(params.transformCommand);                                       // bin data
        data = data.summarize(params.summaryCommand);                                   // summarize data
        data = data.series(params.seriesCommand);                                       // convert series
        data = data.sort(params.sortCommand);                                           // sort data
        data = data.sortRows(params.sortRowsCommand);                                   // sort rows only
        data = data.stack(params.stackCommand);                                         // stack data
        data.set("parameters", params);                                                 // Params used to build this
        return data;
    }

    protected int getParameterIntValue(Param param, int defaultValue) {
        if (param == null) return defaultValue;
        if (param.isField()) {
            // The parameter is a field, so we examine the modifier for the int value
            return getParameterIntValue(param.firstModifier(), defaultValue);
        } else {
            // The parameter is a value
            return (int) param.asDouble();
        }
    }

    private String buildSummaryCommands() {
        Map<String, String> spec = new HashMap<String, String>();

        // We must account for all of these except for the special fields series and values
        // As they will be handled later
        HashSet<String> fields = new HashSet<String>(Arrays.asList(vis.usedFields(false)));
        fields.remove("#series");
        fields.remove("#values");

        // Add the summary measures
        for (Map.Entry<Param, String> e : vis.fSummarize.entrySet()) {
            Param p = e.getKey();
            String name = p.asField();
            String measure = e.getValue();
            if (p.hasModifiers()) measure += ":" + p.firstModifier().asString();
            spec.put(name, name + ":" + measure);
            fields.remove(name);
        }

        // Add all color used fields in as dimensions (factors)
        for (String s : fields) {
            // Count is an implicit summary
            if (s.equals("#count"))
                spec.put(s, s + ":sum");
            else
                spec.put(s, s);
        }

        // X fields are used for the percentage bases
        for (Param s : vis.fX) spec.put(s.asField(), s.asField() + ":base");

        // Return null if summary is not called for
        if (spec.containsKey("#count") || vis.fSummarize.size() > 0) {
            String[] result = new String[spec.size()];
            int n = 0;
            for (Map.Entry<String, String> e : spec.entrySet())
                result[n++] = e.getKey() + "=" + e.getValue();
            return Data.join(result, "; ");
        } else
            return "";
    }

    private String getParameterFieldValue(Param param) {

        if (param != null && param.isField()) {
            // Usual case of a field specified
            return param.asField();
        } else {
            // Try Y fields then aesthetic fields
            if (vis.fY.size() == 1) {
                String s = vis.fY.get(0).asField();
                if (!s.startsWith("'") && !s.startsWith("#")) return s;       // If it's a real field
            }
            if (vis.aestheticFields().length > 0) return vis.aestheticFields()[0];
            return "#row";      // If all else fails
        }
    }

    private String makeConstantsCommand() {
        List<String> toAdd = new ArrayList<String>();
        for (String f : vis.usedFields(false)) {
            if (!f.startsWith("#") && vis.getDataset().field(f) == null) {
                // Field does not exist -- assume it is a constant and add it
                toAdd.add(f);
            }
        }
        return Data.join(toAdd, "; ");
    }

    private String makeFieldCommands() {
        List<Param> params = vis.fSort;
        String[] commands = new String[params.size()];
        for (int i = 0; i < params.size(); i++) {
            Param p = params.get(i);
            String s = p.asField();
            if (p.hasModifiers())
                commands[i] = s + ":" + p.firstModifier().asString();
            else
                commands[i] = s;
        }
        return Data.join(commands, "; ");
    }

    private String makeFilterCommands() {
        List<String> commands = new ArrayList<String>();

        // All position fields must be valid -- filter if not
        String[] pos = vis.positionFields();
        for (String s : pos) {
            Field f = vis.getDataset().field(s);
            if (f == null) continue;        // May have been added as a constant -- no need to filter
            if (f.numericProperty("valid") < f.rowCount())
                commands.add(s + " valid");
        }

        for (Map.Entry<Param, String> e : vis.fTransform.entrySet()) {
            String operation = e.getValue();
            Param key = e.getKey();
            String name = getParameterFieldValue(key);
            Field f = vis.getDataset().field(name);
            int N;
            if (f == null) {
                // The field must be a constant or created field -- get length from data set
                N = vis.getDataset().rowCount();
            } else {
                name = f.name;              // Make sure we use the canonical (not lax) name
                N = f.valid();              // And we can use the valid ones
            }

            if (name.equals("#row")) {
                // Invert 'top' and 'bottom' as row #1 is the top one, not the bottom
                if (operation.equals("top")) operation = "bottom";
                else if (operation.equals("bottom")) operation = "top";
            }

            int n = getParameterIntValue(key, 10);
            if (operation.equals("top"))
                commands.add(name + " ranked 1," + n);
            else if (operation.equals("bottom")) {
                commands.add(name + " ranked " + (N - n) + "," + N);
            } else if (operation.equals("inner")) {
                commands.add(name + " ranked " + n + "," + (N - n));
            } else if (operation.equals("outer")) {
                commands.add(name + " !ranked " + n + "," + (N - n));
            }
        }

        return Data.join(commands, "; ");
    }

    private String makeSeriesCommand() {
        // Only have a series for 2+ y fields

        if (vis.fY.size() < 2) return "";
        /*
            The command is of the form:
                    y1, y2, y3; a1, a2
            Where the fields y1 ... are the fields to makes the series
            and the additional fields a1... are ones required to be kept as-is.
            #series and #values are always generated, so need to retain them additionally
        */

        LinkedHashSet<String> keep = new LinkedHashSet<String>();
        for (Param p : vis.fX) keep.add(p.asString());
        Collections.addAll(keep, vis.nonPositionFields());
        keep.remove("#series");
        keep.remove("#values");
        return Data.join(vis.fY) + ";" + Data.join(keep);
    }

    /*
        Builds the command to transform fields without summarizing -- ranks, bin, inside and outside
        The commands look like this:
            salary=bin:10; age=rank; education=outside:90
     */
    private String makeTransformCommands() {
        if (vis.fTransform.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (Map.Entry<Param, String> e : vis.fTransform.entrySet()) {
            Param p = e.getKey();
            String name = p.asField();
            String measure = e.getValue();
            if (measure.equals("bin") || measure.equals("rank")) {
                if (p.hasModifiers()) measure += ":" + p.firstModifier().asString();
                if (b.length() > 0) b.append("; ");
                b.append(name).append("=").append(measure);
            }
        }
        return b.toString();
    }

    private String required() {
        String[] fields = vis.usedFields(true);
        List<String> result = new ArrayList<String>();
        Collections.addAll(result, fields);

        // ensure we always have #row and #count
        if (!result.contains("#row")) result.add("#row");
        if (!result.contains("#count")) result.add("#count");
        return Data.join(result, "; ");
    }
}
