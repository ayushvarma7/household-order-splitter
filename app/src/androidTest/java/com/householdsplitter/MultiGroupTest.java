package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.repo.HouseholdRepository;
import com.householdsplitter.ui.setup.SetupMembersViewModel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A second group is a different group.
 *
 * <p>This is the regression test for a defect that was found by hand, fixed in one place,
 * and left everywhere else. The database has a query returning the first household by id,
 * which was the right answer for as long as there could only be one and silently the wrong
 * answer afterwards. The visible symptom was the worst kind: making a second group and
 * typing names into it put the people in the first group, and nothing said so.
 *
 * <p>{@link com.householdsplitter.arch.FirstGroupQueryTest} stops the query being reached
 * for again. This checks the behaviour it was breaking, because an architectural rule and
 * the thing it protects are not the same claim.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class MultiGroupTest {

    private HouseholdRepository repository;
    private long first;
    private long second;

    @Before
    public void setUp() throws Exception {
        TestData.wipe();
        repository = TestData.locator().householdRepository();
        first = create("First Flat");
        second = create("Second Flat");
        assertTrue("the second group must have the higher id for this to mean anything",
                second > first);
    }

    /**
     * The members screen writes into the group it was opened on.
     *
     * <p>Asked of the ViewModel rather than the repository, because the repository was
     * never wrong: it took a household id and used it. The screen was wrong, by asking for
     * a household without saying which.
     */
    @Test
    public void theMembersScreenEditsTheGroupItWasOpenedOn() throws Exception {
        SetupMembersViewModel model = onMainThread(() -> new SetupMembersViewModel(
                repository, new androidx.lifecycle.SavedStateHandle(), second));

        Household shown = awaitValue(model.household());
        assertNotNull("the members screen showed no group at all", shown);
        assertEquals("the members screen is editing the wrong group",
                second, shown.id);
        assertEquals("Second Flat", shown.name);
    }

    /** And a person added there lands there, which is the damage the bug actually did. */
    @Test
    public void somebodyAddedToTheSecondGroupIsInTheSecondGroup() throws Exception {
        CountDownLatch added = new CountDownLatch(1);
        repository.addMember(second, "Beta", result -> added.countDown());
        assertTrue("adding a member timed out", added.await(10, TimeUnit.SECONDS));

        List<Member> inSecond = TestData.database().memberDao().getActiveSync(second);
        List<Member> inFirst = TestData.database().memberDao().getActiveSync(first);
        assertEquals("the person should be in the group they were added to",
                1, inSecond.size());
        assertEquals("Beta", inSecond.get(0).name);
        assertTrue("the other group should not have gained anybody: " + names(inFirst),
                inFirst.isEmpty());
    }

    /**
     * Renaming the second group renames the second group.
     *
     * <p>It used to fetch the first household and rename that, whatever id it had been
     * handed, so this is the one where the id was passed in correctly and then ignored.
     */
    @Test
    public void renamingTheSecondGroupLeavesTheFirstAlone() throws Exception {
        CountDownLatch renamed = new CountDownLatch(1);
        repository.renameHousehold(second, "Renamed Second", result -> renamed.countDown());
        assertTrue("renaming timed out", renamed.await(10, TimeUnit.SECONDS));

        assertEquals("Renamed Second",
                TestData.database().householdDao().getByIdSync(second).name);
        assertEquals("renaming one group renamed another",
                "First Flat", TestData.database().householdDao().getByIdSync(first).name);
    }

    /** An install that has never switched still works, which is every existing one. */
    @Test
    public void notHavingChosenAGroupFallsBackToTheOnlyOne() throws Exception {
        SetupMembersViewModel model = onMainThread(() -> new SetupMembersViewModel(
                repository, new androidx.lifecycle.SavedStateHandle(), 0L));

        Household shown = awaitValue(model.household());
        assertNotNull("zero should fall back to a group, not to nothing", shown);
        assertEquals(first, shown.id);
    }

    private long create(String name) throws Exception {
        AtomicReference<Long> id = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        repository.createHousehold(name, result -> {
            id.set(result.isOk() ? result.value() : 0L);
            done.countDown();
        });
        assertTrue("creating " + name + " timed out", done.await(10, TimeUnit.SECONDS));
        return id.get();
    }

    private static String names(List<Member> members) {
        StringBuilder out = new StringBuilder();
        for (Member member : members) {
            out.append(member.name).append(' ');
        }
        return out.toString().trim();
    }

    private static <T> T onMainThread(java.util.concurrent.Callable<T> work) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                value.set(work.call());
            } catch (Exception thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return value.get();
    }

    /** Observes until the first non-null value, on the main thread as LiveData requires. */
    private static <T> T awaitValue(LiveData<T> source) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Observer<T> observer = new Observer<T>() {
            @Override
            public void onChanged(T item) {
                if (item != null) {
                    value.set(item);
                    latch.countDown();
                }
            }
        };
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> source.observeForever(observer));
        boolean arrived = latch.await(10, TimeUnit.SECONDS);
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> source.removeObserver(observer));
        assertTrue("no value arrived within ten seconds", arrived);
        return value.get();
    }
}
