package com.householdsplitter.parse;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.core.money.Cents;
import com.householdsplitter.core.parse.model.OrderField;
import com.householdsplitter.core.parse.model.ParsedAdjustments;
import com.householdsplitter.core.parse.model.ParsedItem;
import com.householdsplitter.core.parse.model.ParsedOrder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The optional cloud reader. SPEC 8.10.
 *
 * <p>Implements the same {@link ReceiptParser} interface as the on-device one, and its
 * output goes through the same review screen: the cloud is never trusted more than the
 * device (SPEC 8.10.4).
 *
 * <p>Only compiled into the `cloud` flavour, which is the only flavour that declares the
 * INTERNET permission (SPEC 4.10).
 */
public class LlmReceiptParser implements ReceiptParser {

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-5";
    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_EDGE_PX = 1568;
    private static final int TIMEOUT_MS = 60_000;

    /**
     * SPEC 8.10.2: strict JSON matching ParsedOrder, no prose and no markdown fences. The
     * client strips fences defensively anyway, because models add them.
     */
    private static final String PROMPT = ""
            + "You are reading screenshots of a single Walmart order page, in order.\n"
            + "Return ONLY a JSON object, with no prose and no markdown fences, shaped:\n"
            + "{\"label\":string|null,\"externalOrderNo\":string|null,"
            + "\"items\":[{\"name\":string,\"quantity\":int,\"price\":string,"
            + "\"unitPrice\":string|null,\"section\":string|null,\"excluded\":boolean}],"
            + "\"subtotal\":string,\"tax\":string,\"deliveryFee\":string,\"tip\":string,"
            + "\"otherFees\":string,\"discount\":string,\"total\":string}\n"
            + "Rules:\n"
            + "- Amounts are plain decimals such as \"12.34\". Use \"0.00\" when absent.\n"
            + "- A product name wraps over several lines. Join it into one name.\n"
            + "- Ignore the status bar, the blue app bar and its cart total, buttons such as"
            + " '+ Add' and 'Review item', the payment card, and the barcode.\n"
            + "- Ignore the rating carousel near the top: cards with star ratings and no"
            + " price are suggestions, not purchases.\n"
            + "- 'N items delivered' counts units, not rows. Do not pad the list to match it.\n"
            + "- Where a row prints two amounts, the right-most one is the charged amount.\n"
            + "- Mark rows from unavailable, cancelled or refunded sections excluded:true.\n"
            + "- Do not invent rows. Report only what is visible.";

    private final Context context;
    private final String apiKey;
    private final Gson gson = new Gson();

    public LlmReceiptParser(Context context, String apiKey) {
        this.context = context.getApplicationContext();
        this.apiKey = apiKey;
    }

    @Override
    public String displayName() {
        return "Cloud";
    }

    @Override
    public ParsedOrder parse(List<Uri> images, ProgressListener listener) throws ParseException {
        if (images == null || images.isEmpty()) {
            throw new ParseException("No screenshots to read");
        }
        try {
            JsonObject request = buildRequest(images, listener);
            if (listener != null && listener.isCancelled()) {
                throw new ParseException("Cancelled");
            }
            String body = post(gson.toJson(request));
            return toParsedOrder(stripFences(extractText(body)));
        } catch (ParseException rethrow) {
            throw rethrow;
        } catch (Exception failed) {
            throw new ParseException("The cloud reader could not read those screenshots: "
                    + failed.getMessage(), failed);
        }
    }

    private JsonObject buildRequest(List<Uri> images, ProgressListener listener) throws Exception {
        com.google.gson.JsonArray content = new com.google.gson.JsonArray();
        for (int i = 0; i < images.size(); i++) {
            if (listener != null) {
                listener.onProgress(i, images.size());
                if (listener.isCancelled()) {
                    throw new ParseException("Cancelled");
                }
            }
            JsonObject source = new JsonObject();
            source.addProperty("type", "base64");
            source.addProperty("media_type", "image/jpeg");
            source.addProperty("data", encode(images.get(i)));

            JsonObject image = new JsonObject();
            image.addProperty("type", "image");
            image.add("source", source);
            content.add(image);
        }
        JsonObject instruction = new JsonObject();
        instruction.addProperty("type", "text");
        instruction.addProperty("text", PROMPT);
        content.add(instruction);

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.add("content", content);

        com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
        messages.add(message);

        JsonObject request = new JsonObject();
        request.addProperty("model", MODEL);
        request.addProperty("max_tokens", 8192);
        request.add("messages", messages);
        return request;
    }

    private String encode(Uri uri) throws Exception {
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap == null) {
                throw new ParseException("Could not open that image");
            }
            int longEdge = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (longEdge > MAX_EDGE_PX) {
                int width = bitmap.getWidth() * MAX_EDGE_PX / longEdge;
                int height = bitmap.getHeight() * MAX_EDGE_PX / longEdge;
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
                bitmap.recycle();
                bitmap = scaled;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
            bitmap.recycle();
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        }
    }

    private String post(String payload) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("content-type", "application/json");
            connection.setRequestProperty("x-api-key", apiKey);
            connection.setRequestProperty("anthropic-version", API_VERSION);

            try (OutputStream stream = connection.getOutputStream()) {
                stream.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400
                    ? connection.getErrorStream() : connection.getInputStream();
            String body = read(stream);
            if (status >= 400) {
                throw new ParseException("The service returned " + status);
            }
            return body;
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) > 0) {
            out.write(buffer, 0, count);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static String extractText(String responseBody) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        com.google.gson.JsonArray content = root.getAsJsonArray("content");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < content.size(); i++) {
            JsonObject block = content.get(i).getAsJsonObject();
            if (block.has("text")) {
                out.append(block.get("text").getAsString());
            }
        }
        return out.toString();
    }

    /** SPEC 8.10.2: fences are stripped defensively even though the prompt forbids them. */
    static String stripFences(String text) {
        String value = text == null ? "" : text.trim();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            if (firstNewline > 0) {
                value = value.substring(firstNewline + 1);
            }
            int closing = value.lastIndexOf("```");
            if (closing >= 0) {
                value = value.substring(0, closing);
            }
        }
        return value.trim();
    }

    /** The wire shape. Deliberately separate from ParsedOrder so a bad reply cannot corrupt it. */
    private static final class Dto {
        String label;
        String externalOrderNo;
        List<Item> items;
        String subtotal;
        String tax;
        String deliveryFee;
        String tip;
        String otherFees;
        String discount;
        String total;

        static final class Item {
            String name;
            int quantity;
            String price;
            String unitPrice;
            String section;
            boolean excluded;
        }
    }

    private ParsedOrder toParsedOrder(String json) throws ParseException {
        Dto dto = gson.fromJson(json, Dto.class);
        if (dto == null) {
            throw new ParseException("The cloud reader returned nothing usable");
        }
        List<ParsedItem> items = new ArrayList<>();
        if (dto.items != null) {
            for (Dto.Item item : dto.items) {
                if (item == null || item.name == null || item.name.trim().isEmpty()) {
                    continue;
                }
                items.add(ParsedItem.builder()
                        .name(item.name.trim())
                        .rawOcrText(item.name.trim())
                        .quantity(Math.max(1, item.quantity))
                        .lineTotalCents(cents(item.price))
                        .unitPriceText(item.unitPrice)
                        .sourceSection(item.section)
                        .scope(item.excluded ? Scope.EXCLUDED : Scope.UNASSIGNED)
                        .build());
            }
        }

        ParsedAdjustments adjustments = new ParsedAdjustments(
                cents(dto.subtotal), cents(dto.tax), cents(dto.deliveryFee),
                cents(dto.tip), cents(dto.otherFees), Math.abs(cents(dto.discount)),
                cents(dto.total));

        ParsedOrder.Builder builder = ParsedOrder.builder()
                .items(items)
                .adjustments(adjustments)
                .label(dto.label)
                .externalOrderNo(dto.externalOrderNo)
                .warn("Read in the cloud. Check the rows before splitting.");
        for (OrderField field : OrderField.values()) {
            if (field != OrderField.ORDER_DATE) {
                builder.markParsed(field);
            }
        }
        return builder.build();
    }

    /** Never throws: a malformed amount becomes zero and the review screen catches it. */
    private static long cents(String text) {
        try {
            return Cents.parse(text);
        } catch (NumberFormatException notAnAmount) {
            return 0L;
        }
    }
}
