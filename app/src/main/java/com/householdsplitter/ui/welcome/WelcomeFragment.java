package com.householdsplitter.ui.welcome;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.viewpager2.widget.ViewPager2;

import com.householdsplitter.R;
import com.householdsplitter.databinding.FragmentWelcomeBinding;
import com.householdsplitter.ui.common.BaseFragment;
import com.householdsplitter.ui.common.Insets;
import com.householdsplitter.ui.theme.WelcomeGradient;

import java.util.Arrays;
import java.util.List;

/**
 * The introduction, shown once on a fresh install.
 *
 * <p>SPEC 7.1.2 puts one line of explanation on the setup screen, which is enough for
 * somebody who already knows what the app is for and not enough for anybody else. The app
 * asks for a group name before it has said what a group is for, so this says it first.
 *
 * <p>Shown exactly once, tracked by its own setting rather than by whether a household
 * exists. Those are different questions: somebody who reads the introduction and then closes
 * the app should land on the setup screen next time, not read it again.
 *
 * <p>It replaces itself on the back stack when it finishes, so Back from setup leaves the
 * app rather than reopening an introduction the user has already dismissed. That also keeps
 * SPEC 7.1.5's rule that S1 is never returned to intact for the screen before it.
 */
public class WelcomeFragment extends BaseFragment {

    private static final List<WelcomePage> PAGES = Arrays.asList(
            WelcomePage.of(R.drawable.ic_welcome_capture,
                    R.string.welcome_1_title, R.string.welcome_1_body),
            WelcomePage.of(R.drawable.ic_welcome_people,
                    R.string.welcome_2_title, R.string.welcome_2_body),
            WelcomePage.of(R.drawable.ic_welcome_done,
                    R.string.welcome_3_title, R.string.welcome_3_body));

    private FragmentWelcomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWelcomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Insets.marginTop(binding.skipButton);
        Insets.padBottom(binding.footer);
        paintBackdrop();

        binding.pager.setAdapter(new WelcomeAdapter(PAGES));
        buildIndicator();

        binding.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                renderControls(position);
            }
        });
        renderControls(0);

        binding.nextButton.setOnClickListener(v -> {
            int current = binding.pager.getCurrentItem();
            if (current < PAGES.size() - 1) {
                binding.pager.setCurrentItem(current + 1, true);
            } else {
                finish();
            }
        });
        binding.backButton.setOnClickListener(v -> {
            int current = binding.pager.getCurrentItem();
            if (current > 0) {
                binding.pager.setCurrentItem(current - 1, true);
            }
        });
        binding.skipButton.setOnClickListener(v -> finish());
    }

    /** Dots, one per page, built from the adapter so the two can never disagree. */
    private void buildIndicator() {
        binding.indicator.removeAllViews();
        int size = getResources().getDimensionPixelSize(R.dimen.welcome_dot);
        int gap = getResources().getDimensionPixelSize(R.dimen.space_xs);
        for (int i = 0; i < PAGES.size(); i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMarginStart(i == 0 ? 0 : gap);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(R.drawable.bg_welcome_dot);
            binding.indicator.addView(dot);
        }
    }

    private void renderControls(int position) {
        if (binding == null) {
            return;
        }
        boolean last = position == PAGES.size() - 1;
        binding.nextButton.setText(last ? R.string.welcome_start : R.string.welcome_next);
        // Kept in the layout rather than removed, so the button below does not jump.
        binding.backButton.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
        binding.skipButton.setVisibility(last ? View.INVISIBLE : View.VISIBLE);

        // White on the wash, rather than the palette accent that was here before. The
        // backdrop is now the palette accent, so an accent dot on it would be invisible.
        int active = android.graphics.Color.WHITE;
        int inactive = android.graphics.Color.argb(80, 255, 255, 255);
        for (int i = 0; i < binding.indicator.getChildCount(); i++) {
            View dot = binding.indicator.getChildAt(i);
            boolean on = i == position;
            dot.setBackgroundTintList(ColorStateList.valueOf(on ? active : inactive));
            dot.setContentDescription(getString(R.string.welcome_page_of,
                    i + 1, PAGES.size()));
        }
        binding.pager.setContentDescription(getString(R.string.welcome_page_of,
                position + 1, PAGES.size()));
    }

    /**
     * Paints the wash and puts the controls on top of it in white.
     *
     * <p>Done here rather than in the layout because the colour is derived from whichever
     * palette is switched on, and because the derivation is a measurement rather than a
     * value: {@link WelcomeGradient} darkens the palette colour until white text clears
     * contrast on it, which no theme attribute can express.
     *
     * <p>The primary button inverts: white fill, wash-coloured text. On a coloured
     * background a white button is the strongest thing on the screen, which is what the
     * one action on this screen should be.
     */
    private void paintBackdrop() {
        binding.backdrop.setBackground(WelcomeGradient.forTheme(requireContext()));

        int wash = WelcomeGradient.head(com.google.android.material.color.MaterialColors.getColor(
                requireContext(), androidx.appcompat.R.attr.colorPrimary,
                android.graphics.Color.BLACK));

        binding.nextButton.setBackgroundTintList(
                ColorStateList.valueOf(android.graphics.Color.WHITE));
        binding.nextButton.setTextColor(wash);
        binding.nextButton.setIconTint(ColorStateList.valueOf(wash));

        binding.backButton.setTextColor(android.graphics.Color.WHITE);
        binding.skipButton.setTextColor(android.graphics.Color.WHITE);

        // Light status bar icons: the bar now sits over a dark wash rather than a pale
        // surface, and the system has no way to know that on its own.
        android.view.Window window = requireActivity().getWindow();
        new androidx.core.view.WindowInsetsControllerCompat(window, window.getDecorView())
                .setAppearanceLightStatusBars(false);
    }

    /** Hands the status bar back to the theme, so the next screen is not left dark-on-dark. */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (getActivity() == null) {
            return;
        }
        boolean lightTheme = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                != android.content.res.Configuration.UI_MODE_NIGHT_YES;
        android.view.Window window = requireActivity().getWindow();
        new androidx.core.view.WindowInsetsControllerCompat(window, window.getDecorView())
                .setAppearanceLightStatusBars(lightTheme);
    }

    /**
     * Records that the introduction has been read and moves on to setup, replacing itself so
     * Back does not bring it back.
     */
    private void finish() {
        locator().settings().welcomeSeen(true);
        NavHostFragment.findNavController(this).navigate(R.id.setupGroupFragment,
                null,
                new NavOptions.Builder()
                        .setPopUpTo(R.id.welcomeFragment, true)
                        .build());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
