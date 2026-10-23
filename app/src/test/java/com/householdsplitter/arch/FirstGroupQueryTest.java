package com.householdsplitter.arch;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Nothing may ask for "the first group" except the places that genuinely mean it.
 *
 * <p>This exists because the same defect was fixed twice and reintroduced in between. The
 * database has a query that returns the first household by id, written when there could
 * only be one, and it stayed the correct answer right up until groups became switchable.
 * Afterwards it was silently the wrong answer everywhere, and silently is the problem: the
 * home screen showed the wrong name, the summary shared the wrong group, renaming the
 * second group renamed the first, and the members screen, which writes, added people to a
 * group the user was not looking at.
 *
 * <p>It was found by hand twice. The first time it was fixed in one place, the balances
 * widget, and the other six were left. A sweep is a thing somebody did on a Tuesday; this
 * is the thing that notices on every build.
 *
 * <p>A source scan rather than an API restriction because the honest fix, deleting the
 * query, is not available: two callers really do want "is there any group at all", which is
 * a different question with the same answer when there is one group. They are named below
 * with the reason, and a new caller has to come here and justify itself.
 */
public class FirstGroupQueryTest {

    /** The two queries that answer "a group" rather than "the group I am in". */
    private static final List<String> FIRST_GROUP_CALLS =
            Arrays.asList("observeAnyHousehold()", "getHouseholdSync()");

    /**
     * Files allowed to ask, and why.
     *
     * <p>Each of these is answering "does any group exist yet", which is a question about
     * the install rather than about the user's current choice.
     */
    private static final List<String> ALLOWED = Arrays.asList(
            // Decides the start destination: setup on a fresh install, home otherwise.
            "ui/MainActivity.java",
            // Falls back when the chosen group has been deleted out from under a widget.
            "widget/BalancesWidgetProvider.java",
            // Declares the queries.
            "data/dao/HouseholdDao.java",
            // Offers both, and is where the distinction is documented.
            "data/repo/HouseholdRepository.java",
            // Writes a backup of the install; multi-group backup is a separate change.
            "backup/BackupService.java");

    @Test
    public void onlyTheFilesThatMeanItAskForTheFirstGroup() throws IOException {
        Path root = Paths.get("src/main/java");
        assertTrue("run from the app module, where src/main/java is", Files.isDirectory(root));

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))
                    ::iterator) {
                String relative = root.relativize(file).toString()
                        .replace('\\', '/')
                        .replace("com/householdsplitter/", "");
                if (ALLOWED.contains(relative)) {
                    continue;
                }
                String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                for (String call : FIRST_GROUP_CALLS) {
                    if (source.contains(call)) {
                        offenders.add(relative + " calls " + call);
                    }
                }
            }
        }

        if (!offenders.isEmpty()) {
            fail("These ask for the first group, which is not the group the user is in.\n"
                    + "Use HouseholdRepository.observeCurrent(householdId), or add the file to\n"
                    + "ALLOWED here with the reason it genuinely means \"any group\":\n  "
                    + String.join("\n  ", offenders));
        }
    }

    /**
     * And the allowlist stays honest: an entry for a file that no longer asks is an entry
     * nobody will question the next time it is read.
     */
    @Test
    public void theAllowlistHasNoStaleEntries() throws IOException {
        Path root = Paths.get("src/main/java/com/householdsplitter");
        List<String> stale = new ArrayList<>();
        for (String allowed : ALLOWED) {
            Path file = root.resolve(allowed);
            if (!Files.exists(file)) {
                stale.add(allowed + " (no such file)");
                continue;
            }
            String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            boolean asks = FIRST_GROUP_CALLS.stream().anyMatch(source::contains);
            if (!asks) {
                stale.add(allowed + " (no longer asks, so remove it from ALLOWED)");
            }
        }
        assertTrue("stale allowlist entries: " + stale, stale.isEmpty());
    }
}
