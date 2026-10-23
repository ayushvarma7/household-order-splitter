package com.householdsplitter.arch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The dark theme is a difference, not a second copy.
 *
 * <p>It used to be a copy. Both folders declared Theme.HouseholdSplitter from the Material
 * parent and restated every attribute, and the two had drifted: the night file was missing
 * the typography block, all fifteen textAppearance attributes plus the font family, so in
 * dark mode every Material component drew itself in the system typeface while every
 * hand written style stayed on Inter. The app is used in dark mode, so that was the normal
 * case and nothing was there to compare it against.
 *
 * <p>Drift like that is invisible in review, because each file reads correctly on its own.
 * What catches it is the shape: the night theme must inherit, and must declare only the
 * handful of things a colour resource genuinely cannot express.
 */
public class ThemeParityTest {

    private static final File DAY = new File("src/main/res/values/themes.xml");
    private static final File NIGHT = new File("src/main/res/values-night/themes.xml");

    /**
     * What the night theme is allowed to say for itself.
     *
     * <p>The system bars take a boolean for icon polarity, and a boolean has no
     * values-night variant to resolve from. Everything else is a colour, a dimension or a
     * style, and all three already vary by folder on their own.
     */
    private static final List<String> ALLOWED_IN_NIGHT = Arrays.asList(
            "android:windowLightStatusBar",
            "android:windowLightNavigationBar");

    private static final Pattern STYLE =
            Pattern.compile("<style\\s+name=\"([^\"]+)\"\\s+parent=\"([^\"]*)\"");
    private static final Pattern ITEM = Pattern.compile("<item\\s+name=\"([^\"]+)\"");

    @Test
    public void theNightThemeInheritsTheSharedOne() throws IOException {
        String night = read(NIGHT);
        Matcher matcher = STYLE.matcher(night);
        boolean found = false;
        while (matcher.find()) {
            if ("Theme.HouseholdSplitter".equals(matcher.group(1))) {
                assertEquals("the night theme must inherit the shared base, not restate it",
                        "Base.Theme.HouseholdSplitter", matcher.group(2));
                found = true;
            }
        }
        assertTrue("values-night declares no Theme.HouseholdSplitter at all", found);
    }

    @Test
    public void theNightThemeOverridesOnlyWhatAColourResourceCannot() throws IOException {
        List<String> unexpected = new ArrayList<>();
        Matcher matcher = ITEM.matcher(read(NIGHT));
        while (matcher.find()) {
            if (!ALLOWED_IN_NIGHT.contains(matcher.group(1))) {
                unexpected.add(matcher.group(1));
            }
        }
        assertTrue("values-night/themes.xml restates " + unexpected + ". Anything that can "
                        + "be a @color, @dimen or @style belongs in the shared base, where "
                        + "the night folder overrides the resource rather than the theme.",
                unexpected.isEmpty());
    }

    /** And the shared base is where the shared attributes actually live. */
    @Test
    public void theSharedBaseCarriesTheTypography() throws IOException {
        String day = read(DAY);
        int base = day.indexOf("name=\"Base.Theme.HouseholdSplitter\"");
        assertTrue("values/themes.xml declares no Base.Theme.HouseholdSplitter", base >= 0);
        String body = day.substring(base, endOfStyle(day, base));
        for (String needed : new String[] {
                "textAppearanceBodyLarge", "textAppearanceLabelLarge",
                "textAppearanceTitleLarge", "android:fontFamily", "fontFamily"}) {
            assertTrue("the shared base is missing " + needed
                            + ", which is what makes both modes use Inter",
                    body.contains("name=\"" + needed + "\""));
        }
    }

    private static int endOfStyle(String xml, int from) {
        int end = xml.indexOf("</style>", from);
        return end < 0 ? xml.length() : end;
    }

    private static String read(File file) throws IOException {
        assertTrue(file + " is missing; this test runs from the :app module directory",
                file.isFile());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
