
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static org.junit.Assert.*;

public class ShipTrackAppTest {
    private static final String STRONG_PASSWORD = "StrongPass1!";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path tempDir;
    private PrintStream originalOut;
    private ByteArrayOutputStream output;

    @Before
    public void setUp() {
        tempDir = temporaryFolder.getRoot().toPath();
        originalOut = System.out;
        output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output));
    }

    @After
    public void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    public void login_WithValidCustomerCredentials_ShouldReturnCustomerUser() {
        // Arrange
        TestContext context = newContext();
        context.createCustomer("customer1", "1001", STRONG_PASSWORD);

        // Act
        User loggedIn = context.login("customer1", STRONG_PASSWORD);

        // Assert
        assertNotNull(loggedIn);
        assertEquals(Role.CUSTOMER, loggedIn.role);
    }

    @Test
    public void login_WithWrongPassword_ShouldFail() {
        // Arrange
        TestContext context = newContext();
        context.createCustomer("customer1", "1001", STRONG_PASSWORD);

        // Act
        User loggedIn = context.login("customer1", "WrongPass1!");

        // Assert
        assertNull(loggedIn);
    }

    @Test
    public void login_WithUnknownUsername_ShouldFail() {
        // Arrange
        TestContext context = newContext();

        // Act
        User loggedIn = context.login("unknown", "AnyPass1!");

        // Assert
        assertNull(loggedIn);
    }

    @Test
    public void login_WhenMaximumAttemptsExceeded_ShouldLockAccount() {
        // Arrange
        TestContext context = newContext();
        context.db.saveSecurityPolicy(new SecurityPolicy(10, 1, 1, 1, 1, 2));
        User user = context.createCustomer("customer1", "1001", STRONG_PASSWORD);

        // Act
        context.login("customer1", "WrongPass1!");
        context.login("customer1", "WrongPass1!");

        // Assert
        assertTrue(user.locked);
    }

    @Test
    public void registerCustomer_WithValidData_ShouldCreateCustomer() {
        // Arrange
        TestContext context = newContext();

        // Act
        context.auth.registerCustomer(context.users,
                scanner("customer1", "Customer One", "1001", "0790001001", STRONG_PASSWORD));

        // Assert
        User customer = context.users.get("customer1");
        assertNotNull(customer);
        assertEquals(Role.CUSTOMER, customer.role);
    }

    @Test
    public void registerCustomer_WithDuplicateUsername_ShouldFail() {
        // Arrange
        TestContext context = newContext();
        context.createCustomer("customer1", "1001", STRONG_PASSWORD);

        // Act
        context.auth.registerCustomer(context.users,
                scanner("customer1", "Second Customer", "1002", "0790001002", STRONG_PASSWORD));

        // Assert
        assertEquals(1, context.users.size());
        assertTrue(context.users.containsKey("customer1"));
    }

    @Test
    public void registerCustomer_WithDuplicateIdNumber_ShouldFail() {
        // Arrange
        TestContext context = newContext();
        context.createCustomer("customer1", "1001", STRONG_PASSWORD);

        // Act
        context.auth.registerCustomer(context.users,
                scanner("customer2", "Second Customer", "1001", "0790001002", STRONG_PASSWORD));

        // Assert
        assertEquals(1, context.users.size());
        assertFalse(context.users.containsKey("customer2"));
    }

    @Test
    public void registerCustomer_WithEmptyName_ShouldFail() {
        // Arrange
        TestContext context = newContext();

        // Act
        context.auth.registerCustomer(context.users,
                scanner("customer1", "", "1001", "0790001001", STRONG_PASSWORD));

        // Assert
        assertTrue(context.users.isEmpty());
    }

    @Test
    public void createShipment_ByCustomer_WithValidData_ShouldCreateShipment() {
        // Arrange
        TestContext context = newContext();
        User customer = context.createCustomer("customer1", "1001", STRONG_PASSWORD);

        // Act
        context.shipmentService.createShipmentForCustomer(customer, context.shipments, context.trackingHistory,
                scanner("Receiver One", "Pickup Address One", "Dropoff Address One"));

        // Assert
        assertEquals(1, context.shipments.size());
        Shipment shipment = firstShipment(context.shipments);
        assertNotNull(shipment.shipmentId);
        assertEquals(ShipmentStatus.PENDING, shipment.status);
    }

    @Test
    public void createShipment_WithMissingRequiredField_ShouldFail() {
        // Arrange
        TestContext context = newContext();
        User customer = context.createCustomer("customer1", "1001", STRONG_PASSWORD);

        // Act
        context.shipmentService.createShipmentForCustomer(customer, context.shipments, context.trackingHistory,
                scanner("Receiver One", "", "Dropoff Address One"));

        // Assert
        assertTrue(context.shipments.isEmpty());
    }

    @Test
    public void trackShipment_WithValidTrackingNumber_ShouldReturnShipmentStatus() {
        // Arrange
        TestContext context = newContext();
        User customer = context.createCustomer("customer1", "1001", STRONG_PASSWORD);
        Shipment shipment = context.createShipment(customer);
        clearConsole();

        // Act
        context.shipmentService.trackPackage(scanner(shipment.shipmentId), context.shipments, context.trackingHistory);

        // Assert
        assertTrue(console().contains("Current status: PENDING"));
    }

    @Test
    public void trackShipment_WithInvalidTrackingNumber_ShouldReturnNotFound() {
        // Arrange
        TestContext context = newContext();

        // Act
        context.shipmentService.trackPackage(scanner("SHP_DOES_NOT_EXIST"), context.shipments, context.trackingHistory);

        // Assert
        assertTrue(console().contains("Shipment not found."));
    }

    private TestContext newContext() {
        return new TestContext(tempDir);
    }

    private static Scanner scanner(String... lines) {
        return new Scanner(String.join(System.lineSeparator(), lines) + System.lineSeparator());
    }

    private static Shipment firstShipment(Map<String, Shipment> shipments) {
        return shipments.values().iterator().next();
    }

    private String console() {
        return output.toString();
    }

    private void clearConsole() {
        output.reset();
    }

    private static class TestContext {
        final FileDB db;
        final AuthService auth;
        final ShipmentService shipmentService;
        final Map<String, User> users;
        final Map<String, Shipment> shipments;
        final Map<String, List<TrackingRecord>> trackingHistory;

        TestContext(Path tempDir) {
            db = new FileDB(
                    tempDir.resolve("users.txt").toString(),
                    tempDir.resolve("shipments.txt").toString(),
                    tempDir.resolve("tracking.txt").toString(),
                    tempDir.resolve("security_policy.txt").toString());
            auth = new AuthService(db);
            shipmentService = new ShipmentService(db);
            users = db.loadUsers();
            shipments = db.loadShipments();
            trackingHistory = db.loadTrackingHistory();
        }

        User login(String username, String password) {
            return auth.login(users, scanner(username, password));
        }

        User createCustomer(String username, String idNumber, String password) {
            auth.registerCustomer(users,
                    scanner(username, "Customer One", idNumber, "0790001001", password));
            return users.get(username.toLowerCase());
        }

        Shipment createShipment(User customer) {
            shipmentService.createShipmentForCustomer(customer, shipments, trackingHistory,
                    scanner("Receiver One", "Pickup Address One", "Dropoff Address One"));
            return firstShipment(shipments);
        }
    }
}
