package com.householdsplitter.core.splitwise;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The few Splitwise v3.0 calls this app makes. Blocking: call it off the main thread. */
public final class SplitwiseClient {

    private final SplitwiseTransport transport;

    public SplitwiseClient(SplitwiseTransport transport) {
        this.transport = transport;
    }

    /** Whoever the key belongs to. Also the cheapest way to check a pasted key works. */
    public SplitwiseUser currentUser() throws SplitwiseException {
        JsonObject root = parse(send(false, "get_current_user", null));
        JsonObject user = root.getAsJsonObject("user");
        if (user == null) {
            throw new SplitwiseException("Splitwise did not say who you are.");
        }
        return user(user);
    }

    public List<SplitwiseGroup> groups() throws SplitwiseException {
        JsonObject root = parse(send(false, "get_groups", null));
        List<SplitwiseGroup> out = new ArrayList<>();
        JsonArray groups = root.getAsJsonArray("groups");
        if (groups == null) {
            return out;
        }
        for (JsonElement element : groups) {
            JsonObject group = element.getAsJsonObject();
            long id = group.get("id").getAsLong();
            // Group 0 is Splitwise's bucket for expenses outside any group, not a real one.
            if (id == 0L) {
                continue;
            }
            List<SplitwiseUser> members = new ArrayList<>();
            JsonArray raw = group.getAsJsonArray("members");
            if (raw != null) {
                for (JsonElement member : raw) {
                    members.add(user(member.getAsJsonObject()));
                }
            }
            out.add(new SplitwiseGroup(id, string(group, "name"), members));
        }
        return out;
    }

    /** Creates the expense and returns its Splitwise id. */
    public long createExpense(ExpensePayload payload) throws SplitwiseException {
        return expenseId(parse(send(true, "create_expense", payload.form())));
    }

    /** Replaces an earlier expense's details and shares, and returns its id. */
    public long updateExpense(long expenseId, ExpensePayload payload) throws SplitwiseException {
        return expenseId(parse(send(true, "update_expense/" + expenseId, payload.form())));
    }

    private String send(boolean post, String path, Map<String, String> form)
            throws SplitwiseException {
        SplitwiseTransport.Reply reply;
        try {
            reply = post ? transport.post(path, form) : transport.get(path);
        } catch (IOException offline) {
            throw new SplitwiseException("Could not reach Splitwise. Check the connection.");
        }
        if (reply.status == 401) {
            throw new SplitwiseException("Splitwise rejected the API key. Reconnect in Settings.", 401);
        }
        if (reply.status == 429) {
            throw new SplitwiseException("Splitwise says too many requests. Try again shortly.", 429);
        }
        if (reply.status < 200 || reply.status >= 300) {
            String detail = errorText(reply.body);
            throw new SplitwiseException("Splitwise refused the request"
                    + (detail.isEmpty() ? " (HTTP " + reply.status + ")." : ": " + detail),
                    reply.status);
        }
        return reply.body;
    }

    private static JsonObject parse(String body) throws SplitwiseException {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (root.isJsonObject()) {
                return root.getAsJsonObject();
            }
        } catch (RuntimeException malformed) {
            // Falls through to the shared message.
        }
        throw new SplitwiseException("Splitwise sent a reply this app cannot read.");
    }

    /**
     * Splitwise answers 200 even when it refused an expense; only an empty "errors" means
     * success. The shape of a populated one is undocumented, so it is flattened to text.
     */
    private static long expenseId(JsonObject root) throws SplitwiseException {
        String detail = errorText(root);
        if (!detail.isEmpty()) {
            throw new SplitwiseException("Splitwise refused the expense: " + detail);
        }
        JsonArray expenses = root.getAsJsonArray("expenses");
        if (expenses == null || expenses.size() == 0) {
            throw new SplitwiseException("Splitwise did not return the expense.");
        }
        return expenses.get(0).getAsJsonObject().get("id").getAsLong();
    }

    private static String errorText(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            return root.isJsonObject() ? errorText(root.getAsJsonObject()) : "";
        } catch (RuntimeException notJson) {
            return "";
        }
    }

    private static String errorText(JsonObject root) {
        JsonElement errors = root.get("errors");
        if (errors == null || errors.isJsonNull()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        flatten(errors, out);
        return out.toString().trim();
    }

    private static void flatten(JsonElement element, StringBuilder out) {
        if (element.isJsonPrimitive()) {
            if (out.length() > 0) {
                out.append("; ");
            }
            out.append(element.getAsString());
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                flatten(child, out);
            }
        } else if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> child : element.getAsJsonObject().entrySet()) {
                flatten(child.getValue(), out);
            }
        }
    }

    private static SplitwiseUser user(JsonObject json) {
        return new SplitwiseUser(json.get("id").getAsLong(), string(json, "first_name"),
                string(json, "last_name"), string(json, "email"), string(json, "default_currency"));
    }

    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
