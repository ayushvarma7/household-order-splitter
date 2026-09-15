package com.householdsplitter.core.quality;

/**
 * Where a row came from, which is what decides whether it can judge the reader.
 *
 * <p>Only a {@link #PARSED} row is evidence about the parser. The distinction matters most
 * for {@link #SPLIT}: breaking a quantity of three into three rows changes the name and the
 * amount of what is stored, but the reader got that row exactly right and the user was
 * reorganising, not correcting. Counting those as corrections would make the parser look
 * worse every time someone used a feature that has nothing to do with it.
 */
public enum ItemOrigin {

    /** The reader produced this row from a screenshot. */
    PARSED,

    /** The user typed this row. If it was on a screenshot, the reader missed a charge. */
    MANUAL,

    /** Derived from a parsed row by splitting its quantity. Judges nothing. */
    SPLIT
}
