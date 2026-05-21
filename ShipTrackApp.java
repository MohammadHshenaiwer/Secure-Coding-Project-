import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class ShipTrackApp {
    public static void main(String[] args) {
        String usersPath = "data/users.txt";
        String shipmentsPath = "data/shipments.txt";
        String trackingPath = "data/tracking.txt";
        String securityPolicyPath = "data/security_policy.txt";

        FileDB db = new FileDB(usersPath, shipmentsPath, trackingPath, securityPolicyPath);
        AuthService auth = new AuthService(db);
        ShipmentService shipmentService = new ShipmentService(db);

        Map<String, User> users = db.loadUsers();
        Map<String, Shipment> shipments = db.loadShipments();
        Map<String, List<TrackingRecord>> trackingHistory = db.loadTrackingHistory();

        Scanner sc = new Scanner(System.in);

        MyLogger.writeToLog("SYSTEM: application_started");
        auth.ensureAdminExists(users, sc);

        while (true) {
            System.out.println("\n==============================");
            System.out.println("ShipTrack - Console System");
            System.out.println("1) Register (Customer)");
            System.out.println("2) Login");
            System.out.println("3) Track Package");
            System.out.println("4) Security Principles");
            System.out.println("5) Exit");
            System.out.print("Choose: ");

            String choice = sc.nextLine().trim();

            if (choice.equals("1")) {
                auth.registerCustomer(users, sc);
            } else if (choice.equals("2")) {
                User loggedIn = auth.login(users, sc);
                if (loggedIn != null) {
                    runRoleMenu(loggedIn, auth, shipmentService, users, shipments, trackingHistory, sc);
                }
            } else if (choice.equals("3")) {
                shipmentService.trackPackage(sc, shipments, trackingHistory);
            } else if (choice.equals("4")) {
                printSecurityPrinciples();
            } else if (choice.equals("5")) {
                MyLogger.writeToLog("SYSTEM: application_closed");
                System.out.println("Goodbye!");
                break;
            } else {
                System.out.println("Invalid option.");
            }
        }
    }

    private static void runRoleMenu(User user,
            AuthService auth,
            ShipmentService shipmentService,
            Map<String, User> users,
            Map<String, Shipment> shipments,
            Map<String, List<TrackingRecord>> trackingHistory,
            Scanner sc) {
        while (true) {
            System.out.println("\n--- " + user.role + " Menu ---");

            if (user.role == Role.ADMIN) {
                System.out.println("1) Create Staff User");
                System.out.println("2) Remove User");
                System.out.println("3) Lock User");
                System.out.println("4) Unlock User");
                System.out.println("5) List Users");
                System.out.println("6) Update Security Policy");
                System.out.println("7) View Logs");
                System.out.println("8) Create Shipment For Customer");
                System.out.println("9) Assign Driver");
                System.out.println("10) List All Shipments");
                System.out.println("11) Update Shipment Status");
                System.out.println("12) Track Package");
                System.out.println("13) Security Principles");
                System.out.println("14) Logout");
                System.out.print("Choose: ");

                String choice = sc.nextLine().trim();

                if (choice.equals("1"))
                    auth.adminCreateStaffUser(users, sc);
                else if (choice.equals("2"))
                    auth.adminRemoveUser(users, sc);
                else if (choice.equals("3"))
                    auth.adminLockUser(users, sc);
                else if (choice.equals("4"))
                    auth.adminUnlockUser(users, sc);
                else if (choice.equals("5"))
                    auth.adminListUsers(users);
                else if (choice.equals("6"))
                    auth.adminUpdateSecurityPolicy(sc);
                else if (choice.equals("7"))
                    MyLogger.printLogs(user);
                else if (choice.equals("8"))
                    shipmentService.createShipmentForExistingCustomer(user, users, shipments, trackingHistory, sc);
                else if (choice.equals("9"))
                    shipmentService.assignDriver(user, users, shipments, trackingHistory, sc);
                else if (choice.equals("10"))
                    shipmentService.listAllShipments(shipments);
                else if (choice.equals("11"))
                    shipmentService.updateShipmentStatus(user, shipments, trackingHistory, sc);
                else if (choice.equals("12"))
                    shipmentService.trackPackage(sc, shipments, trackingHistory);
                else if (choice.equals("13"))
                    printSecurityPrinciples();
                else if (choice.equals("14"))
                    return;
                else
                    System.out.println("Invalid option.");

            } else if (user.role == Role.DISPATCHER) {
                System.out.println("1) Create Shipment For Customer");
                System.out.println("2) Assign Driver");
                System.out.println("3) Update Shipment Status");
                System.out.println("4) List All Shipments");
                System.out.println("5) Track Package");
                System.out.println("6) Logout");
                System.out.print("Choose: ");

                String choice = sc.nextLine().trim();

                if (choice.equals("1"))
                    shipmentService.createShipmentForExistingCustomer(user, users, shipments, trackingHistory, sc);
                else if (choice.equals("2"))
                    shipmentService.assignDriver(user, users, shipments, trackingHistory, sc);
                else if (choice.equals("3"))
                    shipmentService.updateShipmentStatus(user, shipments, trackingHistory, sc);
                else if (choice.equals("4"))
                    shipmentService.listAllShipments(shipments);
                else if (choice.equals("5"))
                    shipmentService.trackPackage(sc, shipments, trackingHistory);
                else if (choice.equals("6"))
                    return;
                else
                    System.out.println("Invalid option.");

            } else if (user.role == Role.DRIVER) {
                System.out.println("1) View My Assigned Shipments");
                System.out.println("2) Update Shipment Status");
                System.out.println("3) Track Package");
                System.out.println("4) Logout");
                System.out.print("Choose: ");

                String choice = sc.nextLine().trim();

                if (choice.equals("1"))
                    shipmentService.listDriverShipments(user, shipments);
                else if (choice.equals("2"))
                    shipmentService.updateShipmentStatus(user, shipments, trackingHistory, sc);
                else if (choice.equals("3"))
                    shipmentService.trackPackage(sc, shipments, trackingHistory);
                else if (choice.equals("4"))
                    return;
                else
                    System.out.println("Invalid option.");

            } else if (user.role == Role.CUSTOMER) {
                System.out.println("1) Create Shipment");
                System.out.println("2) View My Shipments");
                System.out.println("3) Track Package");
                System.out.println("4) View My Profile");
                System.out.println("5) Update My Profile");
                System.out.println("6) Logout");
                System.out.print("Choose: ");

                String choice = sc.nextLine().trim();

                if (choice.equals("1"))
                    shipmentService.createShipmentForCustomer(user, shipments, trackingHistory, sc);
                else if (choice.equals("2"))
                    shipmentService.listCustomerShipments(user, shipments);
                else if (choice.equals("3"))
                    shipmentService.trackPackage(sc, shipments, trackingHistory);
                else if (choice.equals("4"))
                    auth.customerViewProfile(user);
                else if (choice.equals("5"))
                    auth.customerUpdateProfile(user, users, sc);
                else if (choice.equals("6"))
                    return;
                else
                    System.out.println("Invalid option.");

            } else {
                System.out.println("1) Logout");
                System.out.print("Choose: ");
                if (sc.nextLine().trim().equals("1")) {
                    return;
                }
                System.out.println("Invalid option.");
            }
        }
    }

    private static void printSecurityPrinciples() {
        System.out.println("\n=== Security Principles Covered ===");
        System.out.println("- Authentication with salted PBKDF2 password hashing");
        System.out.println("- Authorization with role-based menus");
        System.out.println("- Least privilege for admin, dispatcher, driver, and customer");
        System.out.println("- Fail securely with account lockout after failed logins");
        System.out.println("- Admin-controlled password and login policy loaded from a file");
        System.out.println("- Activity logging for authentication and shipment actions");
        System.out.println("- Input validation before saving data");
    }
}
