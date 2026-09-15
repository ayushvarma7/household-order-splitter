package com.householdsplitter.di;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.savedstate.SavedStateRegistryOwner;

import com.householdsplitter.ui.home.HomeViewModel;
import com.householdsplitter.ui.importer.ImportViewModel;
import com.householdsplitter.core.parse.StoreKind;
import com.householdsplitter.ui.parsing.ParsingArgs;
import com.householdsplitter.ui.parsing.ParsingViewModel;
import com.householdsplitter.ui.participants.ParticipantsViewModel;
import com.householdsplitter.ui.review.ReviewItemsViewModel;
import com.householdsplitter.ui.rules.RulesViewModel;
import com.householdsplitter.ui.details.OrderDetailsViewModel;
import com.householdsplitter.ui.assign.AssignViewModel;
import com.householdsplitter.ui.assign.BulkAssignViewModel;
import com.householdsplitter.ui.summary.SummaryViewModel;
import com.householdsplitter.ui.analytics.AnalyticsViewModel;
import com.householdsplitter.ui.setup.SetupGroupViewModel;
import com.householdsplitter.ui.setup.SetupMembersViewModel;

/**
 * Builds every ViewModel by hand, in keeping with the ServiceLocator choice of SPEC 4.8.
 *
 * <p>Extends the saved-state factory so each ViewModel gets a {@link SavedStateHandle}.
 * That is what keeps typed input alive across rotation (SPEC 7.1.6) and across process
 * death (SPEC 11.14) without any screen having to reimplement it.
 */
public class ViewModelFactory extends AbstractSavedStateViewModelFactory {

    private final ServiceLocator locator;

    public ViewModelFactory(SavedStateRegistryOwner owner, Bundle defaultArgs,
                            ServiceLocator locator) {
        super(owner, defaultArgs);
        this.locator = locator;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    protected <T extends ViewModel> T create(@NonNull String key, @NonNull Class<T> modelClass,
                                             @NonNull SavedStateHandle handle) {
        if (modelClass == SetupGroupViewModel.class) {
            return (T) new SetupGroupViewModel(locator.householdRepository(), handle);
        }
        if (modelClass == SetupMembersViewModel.class) {
            return (T) new SetupMembersViewModel(locator.householdRepository(), handle);
        }
        if (modelClass == HomeViewModel.class) {
            return (T) new HomeViewModel(locator.householdRepository(), locator.orderRepository());
        }
        if (modelClass == AnalyticsViewModel.class) {
            return (T) new AnalyticsViewModel(locator.workbookService(),
                    locator.settlementRepository(), locator.currentHouseholdId());
        }
        if (modelClass == RulesViewModel.class) {
            return (T) new RulesViewModel(locator.ruleService(), locator.householdRepository(),
                    locator.currentHouseholdId());
        }
        if (modelClass == ImportViewModel.class) {
            return (T) new ImportViewModel(handle);
        }
        if (modelClass == ParsingViewModel.class) {
            // The store arrives as a nav argument from the picker, so the parser is built
            // for this import rather than shared across every order in the session.
            StoreKind store = StoreKind.fromName(handle.get(ParsingArgs.ARG_STORE));
            return (T) new ParsingViewModel(
                    locator.orderRepository(),
                    locator.householdRepository(),
                    locator.receiptParser(store),
                    store,
                    locator.executors(),
                    locator.currentHouseholdId(),
                    handle.contains(ParsingArgs.ARG_ORDER_ID)
                            ? handle.<Long>get(ParsingArgs.ARG_ORDER_ID) : 0L);
        }
        // Every screen below works on one order, whose id arrives as a nav argument.
        long orderId = handle.contains(ParsingArgs.ARG_ORDER_ID)
                ? handle.<Long>get(ParsingArgs.ARG_ORDER_ID) : 0L;

        if (modelClass == ReviewItemsViewModel.class) {
            return (T) new ReviewItemsViewModel(locator.orderRepository(), orderId);
        }
        if (modelClass == OrderDetailsViewModel.class) {
            return (T) new OrderDetailsViewModel(locator.orderRepository(), orderId);
        }
        if (modelClass == ParticipantsViewModel.class) {
            return (T) new ParticipantsViewModel(locator.orderRepository(),
                    locator.householdRepository(), orderId, locator.currentHouseholdId());
        }
        if (modelClass == AssignViewModel.class) {
            return (T) new AssignViewModel(locator.orderRepository(),
                    locator.assignmentMemory(), locator.ruleService(), orderId,
                    locator.currentHouseholdId());
        }
        if (modelClass == BulkAssignViewModel.class) {
            return (T) new BulkAssignViewModel(locator.orderRepository(), orderId);
        }
        if (modelClass == SummaryViewModel.class) {
            return (T) new SummaryViewModel(locator.orderRepository(),
                    locator.settings(), orderId);
        }
        throw new IllegalArgumentException("No ViewModel wired for " + modelClass.getName());
    }
}
