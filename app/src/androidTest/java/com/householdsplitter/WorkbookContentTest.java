package com.householdsplitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.householdsplitter.core.calc.Scope;
import com.householdsplitter.data.db.AppDatabase;
import com.householdsplitter.data.entity.Household;
import com.householdsplitter.data.entity.ItemAssignment;
import com.householdsplitter.data.entity.LineItem;
import com.householdsplitter.data.entity.Member;
import com.householdsplitter.data.entity.Order;
import com.householdsplitter.data.entity.OrderStatus;
import com.householdsplitter.export.WorkbookService;
import com.householdsplitter.util.Result;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

/**
 * What the workbook actually says about an order.
 *
 * <p>Written because the on-device workbook recorded a settled order as a draft, and the
 * database plainly said SETTLED. Reading the file back is the only way to know which half
 * was wrong.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class WorkbookContentTest {

    private AppDatabase database;
    private long householdId;
    private long alpha;
    private long orderId;

    @Before
    public void seedASettledOrder() {
        TestData.wipe();
        database = TestData.database();
        householdId = database.householdDao().insert(new Household("Fixture Group", 1L));
        alpha = database.memberDao().insert(new Member(householdId, "Alpha", "#1F6FB2", 0));
        long beta = database.memberDao().insert(new Member(householdId, "Beta", "#B3261E", 1));
        TestData.locator().currentHouseholdId(householdId);

        Order order = new Order();
        order.householdId = householdId;
        order.label = "Fixture order";
        order.orderDate = 1_756_000_000_000L;
        order.createdAt = order.orderDate;
        order.statedTotalCents = 1000L;
        orderId = database.orderDao().insert(order);
        database.participantDao().replaceForOrder(orderId, Arrays.asList(alpha, beta));

        LineItem shared = new LineItem();
        shared.orderId = orderId;
        shared.name = "Shared thing";
        shared.rawOcrText = "Shared thing";
        shared.lineTotalCents = 600L;
        shared.scope = Scope.COMMON;
        database.lineItemDao().insert(shared);

        LineItem mine = new LineItem();
        mine.orderId = orderId;
        mine.name = "Alpha's thing";
        mine.rawOcrText = "Alpha's thing";
        mine.lineTotalCents = 400L;
        mine.scope = Scope.PERSONAL;
        mine.position = 1;
        long mineId = database.lineItemDao().insert(mine);
        database.assignmentDao().insertAll(
                Arrays.asList(new ItemAssignment(mineId, alpha, 1)));

        // The two facts the workbook was getting wrong.
        database.orderDao().updatePayer(orderId, alpha);
        database.orderDao().updateStatus(orderId, OrderStatus.SETTLED);
    }

    @Test
    public void theDatabaseReallyDoesSaySettledWithAPayer() {
        Order order = database.orderDao().getByIdSync(orderId);
        assertEquals(OrderStatus.SETTLED, order.status);
        assertEquals(Long.valueOf(alpha), order.payerMemberId);
    }

    @Test
    public void theWorkbookRecordsTheStatusAndThePayer() throws Exception {
        String workbook = writeAndRead();
        assertTrue("the overview must not call a settled order a draft",
                workbook.contains("Settled"));
        assertTrue("who fronted the money belongs on the sheet",
                workbook.contains("Paid by"));
        assertTrue(workbook.contains("Alpha"));
    }

    @Test
    public void theWorkbookCarriesTheFiguresPeopleWereAskedToPay() throws Exception {
        String workbook = writeAndRead();
        assertTrue(workbook.contains("<v>10.00</v>"));   // computed total
        assertTrue(workbook.contains("<v>6.00</v>"));    // the shared row
        assertTrue(workbook.contains("<v>4.00</v>"));    // Alpha's own item
    }

    /** Writes the workbook to a real file and returns every sheet's XML, concatenated. */
    private String writeAndRead() throws Exception {
        File file = new File(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                        .getTargetContext().getCacheDir(), "test-workbook.xlsx");
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("could not clear the previous workbook");
        }
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(new byte[0]);
        }

        WorkbookService service = TestData.locator().workbookService();
        final StringBuilder error = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        service.writeTo(householdId, "Fixture Group", android.net.Uri.fromFile(file),
                (Result<WorkbookService.Written> result) -> {
                    if (!result.isOk()) {
                        error.append(result.error());
                    }
                    latch.countDown();
                });
        assertTrue("the write should finish promptly", latch.await(20, TimeUnit.SECONDS));
        assertEquals("", error.toString());

        StringBuilder all = new StringBuilder();
        try (ZipFile zip = new ZipFile(file)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                if (!entry.getName().startsWith("xl/worksheets/")) {
                    continue;
                }
                try (java.io.InputStream in = zip.getInputStream(entry)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    while ((count = in.read(buffer)) > 0) {
                        out.write(buffer, 0, count);
                    }
                    all.append(out.toString("UTF-8"));
                }
            }
        }
        assertNotNull(all.toString());
        return all.toString();
    }
}
