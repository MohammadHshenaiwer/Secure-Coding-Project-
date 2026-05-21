public class TrackingRecord {
    public String shipmentId;
    public ShipmentStatus status;
    public String updatedByUsername;
    public String updatedByRole;
    public String note;
    public long timestamp;

    public TrackingRecord(String shipmentId, ShipmentStatus status,
            String updatedByUsername, String updatedByRole, String note) {
        this.shipmentId = shipmentId;
        this.status = status;
        this.updatedByUsername = updatedByUsername;
        this.updatedByRole = updatedByRole;
        this.note = note;
        this.timestamp = System.currentTimeMillis();
    }
}
