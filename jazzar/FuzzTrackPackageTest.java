import com.code_intelligence.jazzer.api.FuzzedDataProvider;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class FuzzTrackPackageTest {
    public static void fuzzerTestOneInput(FuzzedDataProvider data) throws Exception {
        String shipmentId = data.consumeString(30);
        byte[] inputBytes = (shipmentId + "\n").getBytes(StandardCharsets.UTF_8);
        PrintStream originalOut = System.out;

        try (InputStream inputStream = new ByteArrayInputStream(inputBytes);
             Scanner sc = new Scanner(inputStream, StandardCharsets.UTF_8.name())) {

            System.setOut(new PrintStream(OutputStream.nullOutputStream()));

            FileDB db = new FileDB(
                    "fuzz_track_data/users.txt",
                    "fuzz_track_data/shipments.txt",
                    "fuzz_track_data/tracking.txt",
                    "fuzz_track_data/security_policy.txt");

            Map<String, Shipment> shipments = db.loadShipments();
            Map<String, List<TrackingRecord>> trackingHistory = db.loadTrackingHistory();
            ShipmentService service = new ShipmentService(db);

            service.trackPackage(sc, shipments, trackingHistory);

        } catch (IllegalArgumentException e) {
            // Acceptable input rejection.
        } catch (Exception e) {
            throw e;
        } finally {
            System.setOut(originalOut);
        }
    }
}
