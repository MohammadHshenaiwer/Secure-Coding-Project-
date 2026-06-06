import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.security.SecureRandom;

public class ShipmentService {
    private final FileDB db;
    private final SecureRandom random = new SecureRandom();

    public ShipmentService(FileDB db) {
        this.db = db;
    }

    public void createShipmentForCustomer(User customer,
                                          Map<String, Shipment> shipments,
                                          Map<String, List<TrackingRecord>> trackingHistory,
                                          Scanner sc) {
        createShipment(customer, customer, shipments, trackingHistory, sc);
    }

    public void assignDriver(User actor,
                             Map<String, User> users,
                             Map<String, Shipment> shipments,
                             Map<String, List<TrackingRecord>> trackingHistory,
                             Scanner sc) {
        if (actor.role != Role.ADMIN && actor.role != Role.DISPATCHER) {
            MyLogger.writeToWarning("UNAUTHORIZED_ACTION: assign_driver username=" + actor.username);
            System.out.println("Only admin or dispatcher can assign drivers.");
            return;
        }

        System.out.println("\n=== Assign Driver ===");
        System.out.print("Shipment ID: ");
        String shipmentId = sc.nextLine().trim().toUpperCase();

        Shipment shipment = shipments.get(shipmentId);
        if (shipment == null) {
            System.out.println("Shipment not found.");
            return;
        }

        System.out.print("Driver username: ");
        String driverUsername = sc.nextLine().trim().toLowerCase();

        User driver = users.get(driverUsername);
        if (driver == null || driver.role != Role.DRIVER) {
            System.out.println("Driver not found.");
            return;
        }
        if (driver.locked) {
            System.out.println("Driver account is locked.");
            return;
        }

        shipment.assignedDriver = driver.username;
        updateShipmentAuditFields(shipment, actor);
        addTrackingRecord(trackingHistory, shipment.shipmentId, shipment.status, actor,
                "Driver assigned: " + driver.username);

        db.saveShipments(shipments);
        db.saveTrackingHistory(trackingHistory);

        MyLogger.writeToLog("SHIPMENT_ACTION: assigned_driver shipment=" + shipment.shipmentId
                + " driver=" + driver.username + " by=" + actor.username + " role=" + actor.role);
        System.out.println("Driver assigned successfully.");
    }

    public void updateShipmentStatus(User actor,
                                     Map<String, Shipment> shipments,
                                     Map<String, List<TrackingRecord>> trackingHistory,
                                     Scanner sc) {
        if (actor.role != Role.ADMIN && actor.role != Role.DISPATCHER && actor.role != Role.DRIVER) {
            MyLogger.writeToWarning("UNAUTHORIZED_ACTION: update_status username=" + actor.username);
            System.out.println("You are not allowed to update shipment status.");
            return;
        }

        System.out.println("\n=== Update Shipment Status ===");
        System.out.print("Shipment ID: ");
        String shipmentId = sc.nextLine().trim().toUpperCase();

        Shipment shipment = shipments.get(shipmentId);
        if (shipment == null) {
            System.out.println("Shipment not found.");
            return;
        }

        if (actor.role == Role.DRIVER && !actor.username.equals(shipment.assignedDriver)) {
            MyLogger.writeToWarning("UNAUTHORIZED_ACTION: driver_update_unassigned_shipment username="
                    + actor.username + " shipment=" + shipmentId);
            System.out.println("Drivers can only update shipments assigned to them.");
            return;
        }

        System.out.println("Current status: " + shipment.status);
        ShipmentStatus newStatus = promptForStatus(sc);
        if (newStatus == null) {
            return;
        }

        System.out.print("Note (optional): ");
        String note = sc.nextLine().trim();
        if (note.isEmpty()) {
            note = "Status updated";
        }

        shipment.status = newStatus;
        updateShipmentAuditFields(shipment, actor);
        addTrackingRecord(trackingHistory, shipment.shipmentId, newStatus, actor, note);

        db.saveShipments(shipments);
        db.saveTrackingHistory(trackingHistory);

        MyLogger.writeToLog("SHIPMENT_ACTION: updated_status shipment=" + shipment.shipmentId
                + " status=" + newStatus + " by=" + actor.username + " role=" + actor.role);
        System.out.println("Shipment status updated.");
    }

    public void listAllShipments(Map<String, Shipment> shipments) {
        System.out.println("\n=== All Shipments ===");
        if (shipments.isEmpty()) {
            System.out.println("No shipments found.");
            return;
        }

        for (Shipment shipment : shipments.values()) {
            printShipmentLine(shipment);
        }
    }

    public void listCustomerShipments(User customer, Map<String, Shipment> shipments) {
        System.out.println("\n=== My Shipments ===");
        boolean found = false;

        for (Shipment shipment : shipments.values()) {
            if (shipment.customerUsername.equals(customer.username)) {
                printShipmentLine(shipment);
                found = true;
            }
        }

        if (!found) {
            System.out.println("No shipments found for your account.");
        }
    }

    public void listDriverShipments(User driver, Map<String, Shipment> shipments) {
        System.out.println("\n=== My Assigned Shipments ===");
        boolean found = false;

        for (Shipment shipment : shipments.values()) {
            if (driver.username.equals(shipment.assignedDriver)) {
                printShipmentLine(shipment);
                found = true;
            }
        }

        if (!found) {
            System.out.println("No shipments assigned to you.");
        }
    }

    public void trackPackage(Scanner sc,
                             Map<String, Shipment> shipments,
                             Map<String, List<TrackingRecord>> trackingHistory) {
        System.out.println("\n=== Track Package ===");
        System.out.print("Shipment ID: ");
        String shipmentId = sc.nextLine().trim().toUpperCase();

        Shipment shipment = shipments.get(shipmentId);
        if (shipment == null) {
            System.out.println("Shipment not found.");
            return;
        }

        List<TrackingRecord> history = trackingHistory.getOrDefault(shipmentId, new ArrayList<>());
        printShipmentDetails(shipment, history);
    }

    private void createShipment(User customer,
                                User actor,
                                Map<String, Shipment> shipments,
                                Map<String, List<TrackingRecord>> trackingHistory,
                                Scanner sc) {
        System.out.println("\n=== Create Shipment ===");

        String senderName = customer.fullName;
        if (senderName == null || senderName.isEmpty()) {
            senderName = customer.username;
        }

        System.out.print("Receiver name: ");
        String receiverName = sc.nextLine().trim();
        if (!validLength(receiverName, 2, 100)) {
            System.out.println("Receiver name length is invalid.");
            return;
        }

        System.out.print("Pickup location: ");
        String pickupLocation = sc.nextLine().trim();
        if (!validLength(pickupLocation, 5, 200)) {
            System.out.println("Pickup location length is invalid.");
            return;
        }

        System.out.print("Dropoff location: ");
        String dropoffLocation = sc.nextLine().trim();
        if (!validLength(dropoffLocation, 5, 200)) {
            System.out.println("Dropoff location length is invalid.");
            return;
        }

        String shipmentId = generateShipmentId(shipments);
        Shipment shipment = new Shipment(shipmentId, customer.username, senderName, receiverName,
                pickupLocation, dropoffLocation, ShipmentStatus.PENDING,
                actor.username, actor.role.toString());

        shipments.put(shipmentId, shipment);
        addTrackingRecord(trackingHistory, shipmentId, ShipmentStatus.PENDING, actor, "Shipment created");

        db.saveShipments(shipments);
        db.saveTrackingHistory(trackingHistory);

        MyLogger.writeToLog("SHIPMENT_ACTION: created shipment=" + shipmentId
                + " customer=" + customer.username + " by=" + actor.username + " role=" + actor.role);
        System.out.println("Shipment created successfully. Shipment ID: " + shipmentId);
    }

    private ShipmentStatus promptForStatus(Scanner sc) {
        System.out.println("1) PENDING");
        System.out.println("2) PICKED_UP");
        System.out.println("3) IN_TRANSIT");
        System.out.println("4) DELIVERED");
        System.out.println("5) CANCELLED");
        System.out.print("Choose: ");

        String choice = sc.nextLine().trim();
        if (choice.equals("1")) {
            return ShipmentStatus.PENDING;
        }
        if (choice.equals("2")) {
            return ShipmentStatus.PICKED_UP;
        }
        if (choice.equals("3")) {
            return ShipmentStatus.IN_TRANSIT;
        }
        if (choice.equals("4")) {
            return ShipmentStatus.DELIVERED;
        }
        if (choice.equals("5")) {
            return ShipmentStatus.CANCELLED;
        }

        System.out.println("Invalid status.");
        return null;
    }

    private void addTrackingRecord(Map<String, List<TrackingRecord>> trackingHistory,
                                   String shipmentId,
                                   ShipmentStatus status,
                                   User actor,
                                   String note) {
        TrackingRecord record = new TrackingRecord(shipmentId, status,
                actor.username, actor.role.toString(), note);
        trackingHistory.computeIfAbsent(shipmentId, key -> new ArrayList<>()).add(record);
    }

    private void updateShipmentAuditFields(Shipment shipment, User actor) {
        shipment.lastUpdaterUsername = actor.username;
        shipment.lastUpdaterRole = actor.role.toString();
        shipment.lastUpdateTimestamp = System.currentTimeMillis();
    }

    private String generateShipmentId(Map<String, Shipment> shipments) {
        String shipmentId;

        do {
            shipmentId = "SHP" + System.currentTimeMillis() + (random.nextInt(9000) + 1000);
        } while (shipments.containsKey(shipmentId));

        return shipmentId;
    }

    private void printShipmentLine(Shipment shipment) {
        System.out.println(shipment.shipmentId
                + " | Customer=" + shipment.customerUsername
                + " | Sender=" + shipment.senderName
                + " | Receiver=" + shipment.receiverName
                + " | Status=" + shipment.status
                + " | Driver=" + emptyToDash(shipment.assignedDriver));
    }


    private void printShipmentDetails(Shipment shipment, List<TrackingRecord> history) {
        System.out.println("Shipment ID: " + shipment.shipmentId);
        System.out.println("Customer username: " + shipment.customerUsername);
        System.out.println("Sender: " + shipment.senderName);
        System.out.println("Receiver: " + shipment.receiverName);
        System.out.println("Pickup: " + shipment.pickupLocation);
        System.out.println("Dropoff: " + shipment.dropoffLocation);
        System.out.println("Current status: " + shipment.status);
        System.out.println("Assigned driver: " + emptyToDash(shipment.assignedDriver));
        System.out.println("Last updated by: " + emptyToDash(shipment.lastUpdaterUsername)
                + " | Role=" + emptyToDash(shipment.lastUpdaterRole));
        System.out.println("History:");

        if (history.isEmpty()) {
            System.out.println("No tracking history found.");
            return;
        }

        for (TrackingRecord record : history) {
            System.out.println(record.timestamp + " | " + record.status
                    + " | By=" + record.updatedByUsername
                    + " | Role=" + record.updatedByRole
                    + " | " + record.note);
        }
    }

    private boolean validLength(String value, int min, int max) {
        return value.length() >= min && value.length() <= max;
    }

    private String emptyToDash(String value) {
        if (value == null || value.isEmpty()) {
            return "-";
        }
        return value;
    }
}
