package com.householdsplitter.ui.settings;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.householdsplitter.R;
import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.core.quality.MissDiagnosis;
import com.householdsplitter.core.quality.ParserScorecard;
import com.householdsplitter.data.entity.CorrectionEvent;
import com.householdsplitter.data.entity.DiscardedRow;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.databinding.SheetParserReportBinding;
import com.householdsplitter.quality.ParserQualityService;

import java.util.Locale;
import java.util.Map;

/**
 * How the reader has been doing, shown only when asked for.
 *
 * <p>Deliberately not a screen anyone lands on. The numbers here are about the parser, not
 * about the household's money, and a housemate opening the app to see who owes what does
 * not need to be told the reader is running at 94 percent. It is reached by a deliberate
 * gesture on the About line, the same way Android hides its own developer options, which
 * keeps it out of the way without inventing a password for an app that has no accounts and
 * no server to protect.
 *
 * <p>The report is plain selectable text with a share action, so it can be pulled off the
 * device and read next to the code. That is the whole point of it: it exists to be acted
 * on by whoever is fixing the vocabulary, not to be admired in the app.
 */
public class ParserReportSheet extends BottomSheetDialogFragment {

    private ParserQualityService.Report report;
    private String currencySymbol = "$";

    public static ParserReportSheet of(ParserQualityService.Report report, String currencySymbol) {
        ParserReportSheet sheet = new ParserReportSheet();
        sheet.report = report;
        sheet.currencySymbol = currencySymbol == null ? "$" : currencySymbol;
        return sheet;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        SheetParserReportBinding binding = SheetParserReportBinding.inflate(getLayoutInflater());
        dialog.setContentView(binding.getRoot());

        if (report == null) {
            dismiss();
            return dialog;
        }
        final String text = render(report);
        binding.reportText.setText(text);
        binding.shareReportButton.setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.parser_report_title));
            send.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(send, getString(R.string.parser_report_share)));
        });
        return dialog;
    }

    private String render(ParserQualityService.Report value) {
        StringBuilder out = new StringBuilder();
        // Said once rather than under every store. Repeating it wrapped each line twice
        // over and made a short report look like a wall.
        out.append("invented = a charge nobody bought, visible\n");
        out.append("missed   = a charge never found, quiet\n\n");
        appendScorecard(out, "ALL STORES", value.overall);

        for (Map.Entry<StoreKind, ParserScorecard> entry : value.byStore.entrySet()) {
            out.append('\n');
            appendScorecard(out, entry.getKey().displayName().toUpperCase(Locale.US),
                    entry.getValue());
        }

        appendList(out, "MISSED, typed in by hand", value.addedByHand.size());
        for (LineItem item : value.addedByHand) {
            out.append("  ").append(money(item.lineTotalCents)).append("  ")
                    .append(item.name).append('\n');
            // The verdict is the whole point: not that a row was missed, but which stage
            // lost it, because each one points at a single pattern list or constant.
            out.append("       ").append(verdictOf(item)).append('\n');
        }

        appendList(out, "INVENTED, deleted by a person", value.discarded.size());
        for (DiscardedRow row : value.discarded) {
            out.append("  ").append(money(row.lineTotalCents)).append("  ")
                    .append(row.name).append('\n');
        }

        appendList(out, "CORRECTED, found but wrong", value.corrected.size());
        for (LineItem item : value.corrected) {
            out.append("  ").append(money(item.parsedCents)).append(" -> ")
                    .append(money(item.lineTotalCents)).append("  ")
                    .append(item.parsedName == null ? "" : item.parsedName);
            if (item.parsedName != null && !item.parsedName.equals(item.name)) {
                out.append("\n       renamed to: ").append(item.name);
            }
            out.append('\n');
        }

        appendCorrectionLog(out, value.log);
        return out.toString();
    }

    /**
     * The correction log, which is the part of this report that can be acted on.
     *
     * <p>Everything above is a count: how often the reader was right, and which rows it was
     * wrong about. A count has never once suggested a fix. Every improvement to this parser
     * came from one receipt somebody looked at, and these are the entries that say which
     * receipt, which page of it, and what the person who was there thought had gone wrong.
     *
     * <p>Printed newest first and capped, because a log nobody can read is the same as no
     * log. The whole of it is in the database for anyone who wants to query it.
     */
    private void appendCorrectionLog(StringBuilder out,
                                     java.util.List<CorrectionEvent> log) {
        appendList(out, "LOG, what went wrong and where", log == null ? 0 : log.size());
        if (log == null || log.isEmpty()) {
            return;
        }
        java.text.DateFormat when =
                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,
                        java.text.DateFormat.SHORT);
        for (CorrectionEvent event : log) {
            out.append("  ").append(when.format(new java.util.Date(event.createdAt)))
                    .append("  ").append(event.store == null ? "?" : event.store)
                    .append("  ").append(event.kind).append('\n');
            if (event.parsedName != null && !event.parsedName.isEmpty()) {
                out.append("       read:  ").append(money(event.parsedCents)).append("  ")
                        .append(event.parsedName).append('\n');
            }
            out.append("       kept:  ").append(money(event.finalCents)).append("  ")
                    .append(event.finalName == null ? "" : event.finalName).append('\n');
            if (event.imageIndex >= 0) {
                out.append("       photo: ").append(event.imageIndex + 1).append('\n');
            }
            if (event.reason != null && !event.reason.isEmpty()) {
                out.append("       said:  ").append(event.reason).append('\n');
            }
            if (event.missVerdict != null && !event.missVerdict.isEmpty()) {
                out.append("       stage: ").append(event.missVerdict).append('\n');
            }
        }
    }

    private void appendScorecard(StringBuilder out, String heading, ParserScorecard card) {
        out.append(heading).append('\n');
        if (card.judged() == 0) {
            out.append("  nothing judged yet\n");
            return;
        }
        out.append("  left as read  ").append(percent(card.accuracyPermille()))
                .append("  (").append(card.keptAsRead()).append(" of ")
                .append(card.judged()).append(" rows)\n");
        out.append("  by value      ").append(percent(card.valueAccuracyPermille())).append('\n');
        out.append("  corrected     ").append(card.corrected()).append('\n');
        out.append("  invented      ").append(card.removed()).append('\n');
        out.append("  missed        ").append(card.addedByHand()).append('\n');
    }

    private void appendList(StringBuilder out, String heading, int count) {
        out.append('\n').append(heading).append(": ").append(count).append('\n');
    }

    /**
     * What re-reading the screenshots concluded about this row.
     *
     * <p>"Not on any screenshot" is not a failure and says so plainly, because a cash item
     * or a page nobody captured is not the reader's fault and should not read like one.
     */
    private String verdictOf(LineItem item) {
        if (item.missVerdict == null) {
            return "not diagnosed (the screenshots could not be re-read)";
        }
        try {
            MissDiagnosis.Verdict verdict = MissDiagnosis.Verdict.valueOf(item.missVerdict);
            return (verdict.isParserFault() ? "FAULT: " : "not a fault: ") + verdict.message();
        } catch (IllegalArgumentException unknown) {
            return item.missVerdict;
        }
    }

    /** Permille to one decimal place, without ever holding the rate as a double. */
    private static String percent(int permille) {
        if (permille == ParserScorecard.UNKNOWN) {
            return "n/a";
        }
        return String.format(Locale.US, "%d.%d%%", permille / 10, permille % 10);
    }

    private String money(long cents) {
        long absolute = Math.abs(cents);
        return String.format(Locale.US, "%s%s%d.%02d", cents < 0 ? "-" : "",
                currencySymbol, absolute / 100, absolute % 100);
    }
}
