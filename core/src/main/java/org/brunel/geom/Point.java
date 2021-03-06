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

package org.brunel.geom;

/**
 * An X,Y location
 */
public class Point implements Comparable<Point> {

    /**
     * Determines if the points a - b - c turns clockwise or counterclockwise
     *
     * @param a first point
     * @param b second point
     * @param c third point
     * @return +1,-1, 0 for counterclockwise, clockwise and collinear
     */
    public static int ccw(Point a, Point b, Point c) {
        double area2 = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
        return area2 < 0 ? -1 : (area2 > 0 ? 1 : 0);
    }

    /* Coordinates */
    public final double x, y;

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Compare points -- used to sort by y location, then by x
     * The first point will be the LOWEST point. If there are ties for Y, it will be the LEFTMOST of those
     *
     * @param p other point
     * @return -1 if to be sorted lower, +1 higher, 0 if identical
     */
    public int compareTo(Point p) {
        int d = Double.compare(y, p.y);
        return d == 0 ? Double.compare(x, p.x) : d;
    }

    public int hashCode() {
        long temp = Double.doubleToLongBits(x);
        int result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(y);
        return 31 * result + (int) (temp ^ (temp >>> 32));
    }

    @SuppressWarnings("SimplifiableIfStatement")
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return ((Point) o).x == x && ((Point) o).y == y;

    }

    public String toString() {
        return String.format("(%.2f,%.2f)",x,y);
    }

    /**
     * Create a new point offset form the orginal
     * @param dx x offset
     * @param dy y offset
     * @return new point
     */
    public Point translate(double dx, double dy) {
        return new Point(x + dx, y + dy);
    }
}
