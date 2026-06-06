import com.code_intelligence.jazzer.api.FuzzedDataProvider;

import java.io.FileReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

public class FuzzTrackPackageTest {
    private static int attempt = 0;

    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        String shipmentId = data.consumeString(30);

        attempt++;
        System.out.println("Attempt " + attempt
                + " | shipmentId=" + clean(shipmentId));

        try (Scanner sc = new Scanner(shipmentId + "\n");
             FileReader reader = new FileReader(".env")) {
            Properties env = new Properties();
            env.load(reader);

            Map<String, Shipment> shipments = new LinkedHashMap<>();
            Map<String, List<TrackingRecord>> trackingHistory = new LinkedHashMap<>();

            FileDB db = new FileDB(
                    env.getProperty("USERS_FILE_PATH"),
                    env.getProperty("SHIPMENTS_FILE_PATH"),
                    env.getProperty("TRACKING_FILE_PATH"),
                    env.getProperty("SECURITY_POLICY_FILE_PATH"));
            ShipmentService service = new ShipmentService(db);

            service.trackPackage(sc, shipments, trackingHistory);

        } catch (IllegalArgumentException e) {
            // Acceptable input rejection.
        } catch (Exception e) {
            throw new RuntimeException("Track package function crashed with fuzzed input", e);
        }
    }

    private static String clean(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
