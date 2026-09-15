package com.householdsplitter.core.quality;

import com.householdsplitter.core.parse.layout.ParseTrace;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Why a row the user typed in was not found on the screenshots.
 *
 * <p>The manual add is the one moment the app is handed ground truth for free: the user has
 * just written down the correct name and the correct amount for a row the reader did not
 * produce. Paired with a {@link ParseTrace} of the same screenshots, that is enough to say
 * not merely that a charge was missed, but at which stage it was lost, and every stage
 * points at one pattern list or one named constant.
 *
 * <p>The first question is the one that decides whether there is a bug at all. A hand-typed
 * row might be a cash item, a page nobody captured, or a row split up by hand, and none of
 * those are the reader's fault. If the text is nowhere in the recognised output, the answer
 * is {@link Verdict#NOT_ON_ANY_PAGE} and the count of "missed charges" should not include
 * it. Without that check, the measurement mostly records how people use the app.
 *
 * <p>Pure, so it is unit tested against traced fixtures with no device and no network.
 */
public final class MissDiagnosis {

    /** Where a row was lost, from not being there at all to being there and ignored. */
    public enum Verdict {

        /** The text is not in the recognised output. Not the reader's failure. */
        NOT_ON_ANY_PAGE("Not on any screenshot, so the reader had nothing to find"),

        /** The margin crop threw it away: it sat under the status bar or the nav bar. */
        CROPPED_AT_THE_MARGIN("Cropped off at the edge of the capture"),

        /** It was inside the no-scan header zone. */
        INSIDE_THE_HEADER("Inside the header zone, which is never scanned"),

        /** A chrome pattern matched it, so a real product was treated as furniture. */
        FILTERED_AS_CHROME("A chrome pattern matched it and discarded it"),

        /** Read as a section header, so a real product became a heading. */
        READ_AS_A_SECTION_HEADER("Read as a section header rather than as a product"),

        /** Read as an order-level figure, so a real product became a total. */
        READ_AS_A_SUMMARY_LINE("Read as an order-level figure rather than as a product"),

        /** It sat below the point where purchased rows stop. */
        PAST_THE_ITEM_REGION("Below where the item list was judged to end"),

        /** Found in the name column, with no amount in the price column beside it. */
        NO_PRICE_IN_THE_COLUMN("Found, but no price was read in the right-hand column"),

        /** Present, eligible, and still absent from the order. */
        UNEXPLAINED("Present and eligible, so this one needs looking at by hand");

        private final String message;

        Verdict(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }

        /** True when this points at a defect rather than at how the app was used. */
        public boolean isParserFault() {
            return this != NOT_ON_ANY_PAGE;
        }
    }

    /** What was decided, and the evidence for it. */
    public static final class Result {

        public final Verdict verdict;
        /** The band the row was matched to, or null when nothing matched. */
        public final String matchedText;
        public final int imageIndex;
        public final int topPermille;

        Result(Verdict verdict, String matchedText, int imageIndex, int topPermille) {
            this.verdict = verdict;
            this.matchedText = matchedText;
            this.imageIndex = imageIndex;
            this.topPermille = topPermille;
        }
    }

    /**
     * How much of a band, or of the typed name, the other has to account for.
     *
     * <p>Measured in both directions and the better one taken, because the two realistic
     * ways of typing a row fail opposite tests. Someone who types the catalogue title in
     * full is matched by how much of the band they cover, which works even though Amazon
     * wraps a long title over four bands and no single one holds all of it. Someone who
     * types "broccoli florets" is matched by how much of what they typed the band covers,
     * which is everything, even though those two words are barely half of the printed
     * title. Requiring both would reject the second; requiring only the first did.
     */
    private static final int MATCH_PERMILLE = 600;

    /**
     * Two distinct words, or it is a coincidence.
     *
     * <p>This is what stops the looser of the two directions from matching everything.
     * "Amazon Grocery" prefixes most of the catalogue, so a single shared word scores a
     * perfect thousand against whichever row happens to come first, and every hand-typed
     * item would be blamed on it.
     */
    private static final int MIN_MATCHED_TOKENS = 2;

    private MissDiagnosis() {
    }

    public static Result diagnose(ParseTrace trace, String typedName) {
        if (trace == null || trace.isEmpty() || typedName == null || typedName.trim().isEmpty()) {
            return new Result(Verdict.NOT_ON_ANY_PAGE, null, -1, -1);
        }
        List<String> queryTokens = tokens(typedName);
        if (queryTokens.isEmpty()) {
            return new Result(Verdict.NOT_ON_ANY_PAGE, null, -1, -1);
        }

        ParseTrace.BandNote best = null;
        int bestScore = -1;
        for (ParseTrace.BandNote note : trace.notes()) {
            // The name column, not the whole band. The band also holds the price, and its
            // digits tokenise into words the typed name will never contain, so scoring the
            // whole band penalises exactly the band that carried the price: the one that
            // would have become the row. Left unfixed, a wrapped title was diagnosed
            // against its own second line and reported as having no price beside it.
            int score = score(note.nameZoneText.isEmpty() ? note.text : note.nameZoneText,
                    queryTokens);
            // Ties go to the band that got furthest, because a row whose name wrapped over
            // several lines is only ever going to be found through whichever of its lines
            // carried the price.
            if (score > bestScore
                    || (score == bestScore && best != null && note.stage.ordinal() > best.stage.ordinal())) {
                bestScore = score;
                best = note;
            }
        }
        if (best == null || bestScore < MATCH_PERMILLE) {
            return new Result(Verdict.NOT_ON_ANY_PAGE, null, -1, -1);
        }
        return new Result(verdictFor(best.stage), best.text, best.imageIndex, best.topPermille);
    }

    private static Verdict verdictFor(ParseTrace.Stage stage) {
        switch (stage) {
            case CROPPED_AT_THE_MARGIN:
                return Verdict.CROPPED_AT_THE_MARGIN;
            case INSIDE_THE_HEADER:
                return Verdict.INSIDE_THE_HEADER;
            case FILTERED_AS_CHROME:
                return Verdict.FILTERED_AS_CHROME;
            case READ_AS_A_SECTION_HEADER:
                return Verdict.READ_AS_A_SECTION_HEADER;
            case READ_AS_A_SUMMARY_LINE:
                return Verdict.READ_AS_A_SUMMARY_LINE;
            case PAST_THE_ITEM_REGION:
                return Verdict.PAST_THE_ITEM_REGION;
            case NO_PRICE_IN_THE_COLUMN:
                return Verdict.NO_PRICE_IN_THE_COLUMN;
            case ELIGIBLE:
            default:
                return Verdict.UNEXPLAINED;
        }
    }

    /**
     * How well a band and the typed name agree, in permille, or zero for no agreement.
     *
     * <p>The better of the two directions, guarded by a minimum of two distinct matched
     * words. See {@link #MATCH_PERMILLE} for why one direction alone is not enough.
     */
    private static int score(String bandText, List<String> queryTokens) {
        List<String> bandTokens = tokens(bandText);
        if (bandTokens.isEmpty()) {
            return 0;
        }
        java.util.Set<String> matchedWords = new java.util.HashSet<>();
        int bandTotal = 0;
        int bandMatched = 0;
        for (String token : bandTokens) {
            bandTotal += token.length();
            if (queryTokens.contains(token)) {
                bandMatched += token.length();
                matchedWords.add(token);
            }
        }
        if (matchedWords.size() < MIN_MATCHED_TOKENS) {
            return 0;
        }
        int queryTotal = 0;
        int queryMatched = 0;
        for (String token : queryTokens) {
            queryTotal += token.length();
            if (bandTokens.contains(token)) {
                queryMatched += token.length();
            }
        }
        int bandSide = bandTotal == 0 ? 0 : (bandMatched * 1000) / bandTotal;
        int querySide = queryTotal == 0 ? 0 : (queryMatched * 1000) / queryTotal;
        return Math.max(bandSide, querySide);
    }

    /** Lowercase words of two characters or more, with punctuation and digits stripped. */
    private static List<String> tokens(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        for (String raw : text.toLowerCase(Locale.US).split("[^a-z0-9]+")) {
            if (raw.length() >= 2) {
                result.add(raw);
            }
        }
        return result;
    }
}
