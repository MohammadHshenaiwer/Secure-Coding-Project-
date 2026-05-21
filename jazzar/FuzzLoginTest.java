import com.code_intelligence.jazzer.api.FuzzedDataProvider;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;

public class FuzzLoginTest {
    public static void fuzzerTestOneInput(FuzzedDataProvider data) throws Exception {
        String username = data.consumeString(30);
        String password = data.consumeString(30);

        byte[] inputBytes = (username + "\n" + password + "\n").getBytes(StandardCharsets.UTF_8);
        PrintStream originalOut = System.out;

        try (InputStream inputStream = new ByteArrayInputStream(inputBytes);
             Scanner sc = new Scanner(inputStream, StandardCharsets.UTF_8.name())) {

            System.setOut(new PrintStream(OutputStream.nullOutputStream()));

            FileDB db = new FileDB(
                    "fuzz_login_data/users.txt",
                    "fuzz_login_data/shipments.txt",
                    "fuzz_login_data/tracking.txt",
                    "fuzz_login_data/security_policy.txt");
            Map<String, User> users = db.loadUsers();
            AuthService auth = new AuthService(db);

            auth.login(users, sc);

        } catch (IllegalArgumentException e) {
            // Acceptable input rejection.
        } catch (Exception e) {
            throw e;
        } finally {
            System.setOut(originalOut);
        }
    }
}
