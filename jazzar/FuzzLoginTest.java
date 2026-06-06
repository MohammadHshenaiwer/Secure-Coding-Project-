import com.code_intelligence.jazzer.api.FuzzedDataProvider;

import java.io.FileReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

public class FuzzLoginTest {
    private static int attempt = 0;

    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        String username = data.consumeString(30);
        String password = data.consumeString(30);

        attempt++;
        System.out.println("Attempt " + attempt
                + " | username=" + clean(username)
                + " | password=" + clean(password));

        try (Scanner sc = new Scanner(username + "\n" + password + "\n");
             FileReader reader = new FileReader(".env")) {
            Properties env = new Properties();
            env.load(reader);

            Map<String, User> users = new LinkedHashMap<>();

            FileDB db = new FileDB(
                    env.getProperty("USERS_FILE_PATH"),
                    env.getProperty("SHIPMENTS_FILE_PATH"),
                    env.getProperty("TRACKING_FILE_PATH"),
                    env.getProperty("SECURITY_POLICY_FILE_PATH"));
            AuthService auth = new AuthService(db);

            User loggedIn = auth.login(users, sc);

            // The users map is empty, so no login should succeed.
            if (loggedIn != null) {
                throw new RuntimeException("Login bypass detected with fuzzed input");
            }

        } catch (IllegalArgumentException e) {
            // Acceptable input rejection.
        } catch (Exception e) {
            throw new RuntimeException("Login function crashed with fuzzed input", e);
        }
    }

    private static String clean(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
