public class User {
    public String username;
    public Role role;

    public String saltBase64;
    public String hashBase64;

    public boolean locked;
    public int failedAttempts;

    public String fullName;
    public String idNumber;
    public String contactNo;

    public User(String username, Role role,
            String saltBase64, String hashBase64,
            boolean locked, int failedAttempts,
            String fullName, String idNumber, String contactNo) {
        this.username = username;
        this.role = role;
        this.saltBase64 = saltBase64;
        this.hashBase64 = hashBase64;
        this.locked = locked;
        this.failedAttempts = failedAttempts;
        this.fullName = fullName;
        this.idNumber = idNumber;
        this.contactNo = contactNo;
    }
}