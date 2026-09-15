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
import com.householdsplitter.core.quality.ParserScorecard;
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
        return out.toString();
    }

    private void appendScorecard(StringBuilder out, String heading, ParserScorecard card) {
        out.append(heading).append('\n');
        if (card.judged() == 0) {
            out.append("  nothing judged yet\n");
            return;
        }
        out.append("  left as read   ").append(percent(card.accuracyPermille()))
                .append("   (").append(card.keptAsRead()).append(" of ")
                .append(card.judged()).append(" rows)\n");
        out.append("  by value       ").append(percent(card.valueAccuracyPermille())).append('\n');
        out.append("  corrected      ").append(card.corrected()).append('\n');
        out.append("  invented       ").append(card.removed())
                .append("   (visible: a charge nobody bought)\n");
        out.append("  missed         ").append(card.addedByHand())
                .append("   (quiet: the order just comes up short)\n");
    }

    private void appendList(StringBuilder out, String heading, int count) {
        out.append('\n').append(heading).append(": ").append(count).append('\n');
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
