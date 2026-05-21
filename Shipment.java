public class Shipment {
    public String shipmentId;
    public String customerUsername;
    public String senderName;
    public String receiverName;
    public String pickupLocation;
    public String dropoffLocation;
    public ShipmentStatus status;
    public String assignedDriver;
    public long creationTimestamp;
    public long lastUpdateTimestamp;
    public String lastUpdaterUsername;
    public String lastUpdaterRole;

    public Shipment(String shipmentId, String customerUsername, String senderName,
            String receiverName,
            String pickupLocation, String dropoffLocation,
            ShipmentStatus status, String lastUpdaterUsername, String lastUpdaterRole) {
        this.shipmentId = shipmentId;
        this.customerUsername = customerUsername;
        this.senderName = senderName;
        this.receiverName = receiverName;
        this.pickupLocation = pickupLocation;
        this.dropoffLocation = dropoffLocation;
        this.status = status;
        this.creationTimestamp = System.currentTimeMillis();
        this.lastUpdateTimestamp = this.creationTimestamp;
        this.lastUpdaterUsername = lastUpdaterUsername;
        this.lastUpdaterRole = lastUpdaterRole;
    }
}
