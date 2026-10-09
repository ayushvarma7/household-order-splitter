package com.householdsplitter.ui.review;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.householdsplitter.R;
import com.householdsplitter.data.entity.CorrectionEvent;

/**
 * Asks what the reader got wrong, after the correction has already been saved.
 *
 * <p>Two questions, and both are optional. Which image it was on, because a row the reader
 * never produced has no position on any page to infer one from, and knowing it turns
 * "something was missed" into a page that can be re-read and a stage that can be named.
 * And what kind of mistake it was, in the user's words, because the categories a parser
 * would invent for itself are the ones it already knows how to look for.
 *
 * <p>Offered rather than imposed. It arrives behind a snackbar action, so the common case
 * of correcting a row and moving on costs nothing, and anybody who wants to leave a note
 * can. A log nobody fills in is better than a flow nobody finishes.
 */
public class CorrectionNoteSheet extends BottomSheetDialogFragment {

    public interface Listener {
        void onAnnotated(long eventId, int imageIndex, String reason);
    }

    /** The short answers, in the order they are offered. */
    private static final int[] REASONS_FOR_MISSED = {
            R.string.note_reason_not_read_at_all,
            R.string.note_reason_cut_off,
            R.string.note_reason_blurry,
            R.string.note_reason_merged_with_another
    };

    private static final int[] REASONS_FOR_CORRECTED = {
            R.string.note_reason_wrong_name,
            R.string.note_reason_wrong_price,
            R.string.note_reason_wrong_quantity,
            R.string.note_reason_name_truncated,
            R.string.note_reason_merged_with_another
    };

    private long eventId;
    private String kind;
    private String itemName;
    private String whatItRead;
    private int imageCount;
    private Listener listener;

    private com.householdsplitter.databinding.SheetCorrectionNoteBinding binding;
    private int chosenImage = -1;

    public static CorrectionNoteSheet forEvent(long eventId, String kind, String itemName,
                                               String whatItRead, int imageCount,
                                               Listener listener) {
        CorrectionNoteSheet sheet = new CorrectionNoteSheet();
        sheet.eventId = eventId;
        sheet.kind = kind;
        sheet.itemName = itemName;
        sheet.whatItRead = whatItRead;
        sheet.imageCount = imageCount;
        sheet.listener = listener;
        return sheet;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        binding = com.householdsplitter.databinding.SheetCorrectionNoteBinding
                .inflate(getLayoutInflater());
        dialog.setContentView(binding.getRoot());

        boolean missed = CorrectionEvent.ADDED_BY_HAND.equals(kind);
        binding.noteTitle.setText(missed
                ? R.string.note_title_missed : R.string.note_title_corrected);
        binding.noteBody.setText(getString(
                missed ? R.string.note_body_missed : R.string.note_body_corrected, itemName));

        // What the reader said, shown back, because a question about a mistake is easier to
        // answer next to the mistake. A row that was never read has nothing to show.
        boolean hasPrediction = whatItRead != null && !whatItRead.trim().isEmpty();
        binding.predictionLabel.setVisibility(hasPrediction ? View.VISIBLE : View.GONE);
        binding.predictionText.setVisibility(hasPrediction ? View.VISIBLE : View.GONE);
        if (hasPrediction) {
            binding.predictionText.setText(whatItRead);
        }

        buildPhotoChips();
        buildReasonChips(missed ? REASONS_FOR_MISSED : REASONS_FOR_CORRECTED);

        binding.noteSaveButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAnnotated(eventId, chosenImage, chosenReason());
            }
            dismiss();
        });
        binding.noteSkipButton.setOnClickListener(v -> dismiss());
        return dialog;
    }

    /**
     * One chip per image, plus "not sure".
     *
     * <p>Numbered rather than shown as thumbnails. The user has just been looking at these
     * pictures and knows them by their order; a strip of four near-identical receipt
     * thumbnails at chip size would be harder to tell apart than the numbers are.
     */
    private void buildPhotoChips() {
        binding.photoChips.removeAllViews();
        if (imageCount <= 0) {
            binding.photoScroll.setVisibility(View.GONE);
            return;
        }
        for (int i = 0; i < imageCount; i++) {
            final int index = i;
            Chip chip = new Chip(requireContext());
            chip.setText(getString(R.string.note_photo_number, i + 1));
            chip.setCheckable(true);
            chip.setOnClickListener(v -> chosenImage = chip.isChecked() ? index : -1);
            binding.photoChips.addView(chip);
        }
        Chip unsure = new Chip(requireContext());
        unsure.setText(R.string.note_photo_unsure);
        unsure.setCheckable(true);
        unsure.setOnClickListener(v -> chosenImage = -1);
        binding.photoChips.addView(unsure);
    }

    private void buildReasonChips(int[] reasons) {
        binding.reasonChips.removeAllViews();
        for (int reason : reasons) {
            Chip chip = new Chip(requireContext());
            chip.setText(reason);
            chip.setCheckable(true);
            binding.reasonChips.addView(chip);
        }
    }

    /** The chosen chip, plus anything typed, as one line. */
    private String chosenReason() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < binding.reasonChips.getChildCount(); i++) {
            Chip chip = (Chip) binding.reasonChips.getChildAt(i);
            if (chip.isChecked()) {
                out.append(chip.getText());
                break;
            }
        }
        CharSequence typed = binding.noteInput.getText();
        if (typed != null && typed.toString().trim().length() > 0) {
            if (out.length() > 0) {
                out.append(": ");
            }
            out.append(typed.toString().trim());
        }
        return out.length() == 0 ? null : out.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
