import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FileDB {
    private final String usersPath;
    private final String shipmentsPath;
    private final String trackingPath;
    private final String securityPolicyPath;

    public FileDB(String usersPath,
                  String shipmentsPath,
                  String trackingPath,
                  String securityPolicyPath) {
        this.usersPath = usersPath;
        this.shipmentsPath = shipmentsPath;
        this.trackingPath = trackingPath;
        this.securityPolicyPath = securityPolicyPath;
    }

    public SecurityPolicy loadSecurityPolicy() {
        SecurityPolicy policy = SecurityPolicy.defaultPolicy();
        File file = new File(securityPolicyPath);

        if (!file.exists()) {
            saveSecurityPolicy(policy);
            return policy;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("=", 2);
                if (parts.length != 2) {
                    continue;
                }

                try {
                    int value = Integer.parseInt(parts[1].trim());
                    setPolicyValue(policy, parts[0].trim(), value);
                } catch (NumberFormatException e) {
                    MyLogger.writeToWarning("DATA_WARNING: bad_security_policy_value key=" + parts[0].trim());
                }
            }
        } catch (IOException | SecurityException e) {
            System.out.println("Error reading security policy file.");
            return SecurityPolicy.defaultPolicy();
        }

        if (!policy.isValid()) {
            MyLogger.writeToWarning("DATA_WARNING: invalid_security_policy_using_default");
            System.out.println("Security policy file is invalid. Using default policy.");
            return SecurityPolicy.defaultPolicy();
        }

        return policy;
    }

    public void saveSecurityPolicy(SecurityPolicy policy) {
        File file = new File(securityPolicyPath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(file, false))) {
            writer.println("# ShipTrack security policy");
            writer.println("minPasswordLength=" + policy.minPasswordLength);
            writer.println("minUppercaseLetters=" + policy.minUppercaseLetters);
            writer.println("minLowercaseLetters=" + policy.minLowercaseLetters);
            writer.println("minDigits=" + policy.minDigits);
            writer.println("minSpecialCharacters=" + policy.minSpecialCharacters);
            writer.println("maxLoginAttempts=" + policy.maxLoginAttempts);
        } catch (IOException | SecurityException e) {
            System.out.println("Error saving security policy file.");
        }
    }

    public Map<String, User> loadUsers() {
        Map<String, User> users = new LinkedHashMap<>();
        File file = new File(usersPath);

        if (!file.exists()) {
            return users;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\|", -1);
                if (parts.length < 9) {
                    continue;
                }

                try {
                    String username = parts[0];
                    Role role = Role.valueOf(parts[1]);
                    String saltBase64 = parts[2];
                    String hashBase64 = parts[3];
                    boolean locked = Boolean.parseBoolean(parts[4]);
                    int failedAttempts = Integer.parseInt(parts[5]);
                    String fullName = parts[6];
                    String idNumber = parts[7];
                    String contactNo = parts[8];

                    User user = new User(username, role, saltBase64, hashBase64,
                            locked, failedAttempts, fullName, idNumber, contactNo);
                    users.put(username, user);
                } catch (RuntimeException e) {
                    MyLogger.writeToWarning("DATA_WARNING: skipped_bad_user_record");
                }
            }
        } catch (IOException | SecurityException e) {
            System.out.println("Error reading users file.");
        }

        return users;
    }

    public void saveUsers(Map<String, User> users) {
        File file = new File(usersPath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(file, false))) {
            for (User user : users.values()) {
                writer.println(
                        safe(user.username) + "|" +
                        user.role + "|" +
                        safe(user.saltBase64) + "|" +
                        safe(user.hashBase64) + "|" +
                        user.locked + "|" +
                        user.failedAttempts + "|" +
                        safe(user.fullName) + "|" +
                        safe(user.idNumber) + "|" +
                        safe(user.contactNo)
                );
            }
        } catch (IOException | SecurityException e) {
            System.out.println("Error saving users file.");
        }
    }

    public Map<String, Shipment> loadShipments() {
        Map<String, Shipment> shipments = new LinkedHashMap<>();
        File file = new File(shipmentsPath);

        if (!file.exists()) {
            return shipments;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\|", -1);
                if (parts.length < 12) {
                    continue;
                }

                try {
                    String shipmentId = parts[0];
                    String customerUsername = parts[1];
                    String senderName = parts[2];
                    String receiverName = parts[3];
                    String pickupLocation = parts[4];
                    String dropoffLocation = parts[5];
                    ShipmentStatus status = ShipmentStatus.valueOf(parts[6]);
                    String assignedDriver = parts[7];
                    long creationTimestamp = Long.parseLong(parts[8]);
                    long lastUpdateTimestamp = Long.parseLong(parts[9]);
                    String lastUpdaterUsername = parts[10];
                    String lastUpdaterRole = parts[11];

                    Shipment shipment = new Shipment(shipmentId, customerUsername, senderName, receiverName,
                            pickupLocation, dropoffLocation, status, lastUpdaterUsername, lastUpdaterRole);
                    shipment.assignedDriver = assignedDriver;
                    shipment.creationTimestamp = creationTimestamp;
                    shipment.lastUpdateTimestamp = lastUpdateTimestamp;

                    shipments.put(shipmentId, shipment);
                } catch (RuntimeException e) {
                    MyLogger.writeToWarning("DATA_WARNING: skipped_bad_shipment_record");
                }
            }
        } catch (IOException | SecurityException e) {
            System.out.println("Error reading shipments file.");
        }

        return shipments;
    }

    public void saveShipments(Map<String, Shipment> shipments) {
        File file = new File(shipmentsPath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(file, false))) {
            for (Shipment shipment : shipments.values()) {
                writer.println(
                        safe(shipment.shipmentId) + "|" +
                        safe(shipment.customerUsername) + "|" +
                        safe(shipment.senderName) + "|" +
                        safe(shipment.receiverName) + "|" +
                        safe(shipment.pickupLocation) + "|" +
                        safe(shipment.dropoffLocation) + "|" +
                        shipment.status + "|" +
                        safe(shipment.assignedDriver) + "|" +
                        shipment.creationTimestamp + "|" +
                        shipment.lastUpdateTimestamp + "|" +
                        safe(shipment.lastUpdaterUsername) + "|" +
                        safe(shipment.lastUpdaterRole)
                );
            }
        } catch (IOException | SecurityException e) {
            System.out.println("Error saving shipments file.");
        }
    }

    public Map<String, List<TrackingRecord>> loadTrackingHistory() {
        Map<String, List<TrackingRecord>> trackingHistory = new LinkedHashMap<>();
        File file = new File(trackingPath);

        if (!file.exists()) {
            return trackingHistory;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\|", -1);
                if (parts.length < 6) {
                    continue;
                }

                try {
                    String shipmentId = parts[0];
                    ShipmentStatus status = ShipmentStatus.valueOf(parts[1]);
                    String updatedByUsername = parts[2];
                    String updatedByRole = parts[3];
                    String note = parts[4];
                    long timestamp = Long.parseLong(parts[5]);

                    TrackingRecord record = new TrackingRecord(shipmentId, status,
                            updatedByUsername, updatedByRole, note);
                    record.timestamp = timestamp;

                    trackingHistory.computeIfAbsent(shipmentId, key -> new ArrayList<>()).add(record);
                } catch (RuntimeException e) {
                    MyLogger.writeToWarning("DATA_WARNING: skipped_bad_tracking_record");
                }
            }
        } catch (IOException | SecurityException e) {
            System.out.println("Error reading tracking file.");
        }

        return trackingHistory;
    }

    public void saveTrackingHistory(Map<String, List<TrackingRecord>> trackingHistory) {
        File file = new File(trackingPath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(file, false))) {
            for (List<TrackingRecord> records : trackingHistory.values()) {
                for (TrackingRecord record : records) {
                    writer.println(
                            safe(record.shipmentId) + "|" +
                            record.status + "|" +
                            safe(record.updatedByUsername) + "|" +
                            safe(record.updatedByRole) + "|" +
                            safe(record.note) + "|" +
                            record.timestamp
                    );
                }
            }
        } catch (IOException | SecurityException e) {
            System.out.println("Error saving tracking file.");
        }
    }

    private void setPolicyValue(SecurityPolicy policy, String key, int value) {
        if (key.equals("minPasswordLength")) {
            policy.minPasswordLength = value;
        } else if (key.equals("minUppercaseLetters")) {
            policy.minUppercaseLetters = value;
        } else if (key.equals("minLowercaseLetters")) {
            policy.minLowercaseLetters = value;
        } else if (key.equals("minDigits")) {
            policy.minDigits = value;
        } else if (key.equals("minSpecialCharacters")) {
            policy.minSpecialCharacters = value;
        } else if (key.equals("maxLoginAttempts")) {
            policy.maxLoginAttempts = value;
        }
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("|", "/").replace("\r", " ").replace("\n", " ").trim();
    }
}
