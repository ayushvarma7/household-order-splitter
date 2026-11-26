package com.householdsplitter.core.splitwise;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SplitwiseClientTest {

    /** Answers every call with one canned reply and remembers what was asked. */
    private static final class Fake implements SplitwiseTransport {
        final Reply reply;
        final boolean offline;
        String path;
        Map<String, String> form;

        Fake(int status, String body) {
            this.reply = new Reply(status, body);
            this.offline = false;
        }

        Fake() {
            this.reply = null;
            this.offline = true;
        }

        @Override
        public Reply get(String p) throws IOException {
            path = p;
            if (offline) {
                throw new IOException("down");
            }
            return reply;
        }

        @Override
        public Reply post(String p, Map<String, String> f) throws IOException {
            path = p;
            form = f;
            if (offline) {
                throw new IOException("down");
            }
            return reply;
        }
    }

    private static ExpensePayload payload() {
        return ExpenseBuilder.build("Shop", null, "2026-11-01T00:00:00Z", "USD", 5L, 1L,
                Arrays.asList(new ExpenseBuilder.Share(1L, 100)));
    }

    @Test
    public void readsTheCurrentUser() throws Exception {
        SplitwiseUser me = new SplitwiseClient(new Fake(200,
                "{\"user\":{\"id\":42,\"first_name\":\"Ada\",\"last_name\":null,"
                        + "\"email\":\"a@b.c\",\"default_currency\":\"GBP\"}}")).currentUser();
        assertEquals(42L, me.id());
        assertEquals("Ada", me.displayName());
        assertEquals("GBP", me.defaultCurrency());
    }

    @Test
    public void readsGroupsAndSkipsTheNonGroupBucket() throws Exception {
        List<SplitwiseGroup> groups = new SplitwiseClient(new Fake(200,
                "{\"groups\":[{\"id\":0,\"name\":\"Non-group expenses\",\"members\":[]},"
                        + "{\"id\":7,\"name\":\"Flat\",\"members\":["
                        + "{\"id\":1,\"first_name\":\"A\",\"last_name\":\"B\"},"
                        + "{\"id\":2,\"first_name\":\"C\"}]}]}")).groups();
        assertEquals(1, groups.size());
        assertEquals("Flat", groups.get(0).name());
        assertEquals(2, groups.get(0).members().size());
    }

    @Test
    public void createReturnsTheExpenseId() throws Exception {
        Fake fake = new Fake(200, "{\"expenses\":[{\"id\":555}],\"errors\":{}}");
        assertEquals(555L, new SplitwiseClient(fake).createExpense(payload()));
        assertEquals("create_expense", fake.path);
        assertEquals("1.00", fake.form.get("cost"));
    }

    @Test
    public void updateTargetsTheExpense() throws Exception {
        Fake fake = new Fake(200, "{\"expenses\":[{\"id\":555}],\"errors\":{}}");
        new SplitwiseClient(fake).updateExpense(555L, payload());
        assertEquals("update_expense/555", fake.path);
    }

    @Test
    public void errorsInsideA200AreAFailure() {
        Fake fake = new Fake(200, "{\"expenses\":[],\"errors\":{\"base\":[\"Bad shares\"]}}");
        try {
            new SplitwiseClient(fake).createExpense(payload());
            fail();
        } catch (SplitwiseException e) {
            assertTrue(e.getMessage().contains("Bad shares"));
        }
    }

    @Test
    public void unauthorisedIsFlagged() {
        try {
            new SplitwiseClient(new Fake(401, "{}")).currentUser();
            fail();
        } catch (SplitwiseException e) {
            assertTrue(e.isUnauthorised());
        }
    }

    @Test
    public void offlineGivesAReadableMessage() {
        try {
            new SplitwiseClient(new Fake()).groups();
            fail();
        } catch (SplitwiseException e) {
            assertTrue(e.getMessage().contains("Could not reach"));
        }
    }

    @Test
    public void garbageBodyIsAFailureNotACrash() {
        try {
            new SplitwiseClient(new Fake(200, "<html>")).groups();
            fail();
        } catch (SplitwiseException e) {
            assertTrue(e.getMessage().contains("cannot read"));
        }
    }
}
