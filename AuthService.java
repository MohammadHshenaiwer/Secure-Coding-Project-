import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Scanner;
import java.util.function.IntPredicate;

public class AuthService {
    private final FileDB db;
    private SecurityPolicy securityPolicy;

    private final int iterations = 120000;
    private final int keyLengthBits = 256;

    public AuthService(FileDB db) {
        this.db = db;
        this.securityPolicy = db.loadSecurityPolicy();
    }

    public void ensureAdminExists(Map<String, User> users, Scanner sc) {
        boolean adminExists = users.values().stream().anyMatch(user -> user.role == Role.ADMIN);
        if (adminExists) {
            return;
        }

        System.out.println("=== First run setup ===");
        System.out.println("No ADMIN account found. Please create an admin password now.");

        String password = promptForStrongPassword(sc);
        createUser(users, "admin", Role.ADMIN, password, "System Admin", "admin", "N/A");
        db.saveUsers(users);

        MyLogger.writeToLog("SYSTEM: first_run_admin_created username=admin");
        System.out.println("Admin created successfully. Username: admin");
    }

    public void registerCustomer(Map<String, User> users, Scanner sc) {
        System.out.println("\n=== Customer Registration ===");

        System.out.print("Choose username: ");
        String username = normalizeUsername(sc.nextLine());

        if (!validUsername(username)) {
            System.out.println("Username must be 3-50 characters and use letters, numbers, or underscore only.");
            return;
        }
        if (users.containsKey(username)) {
            System.out.println("Username already exists.");
            return;
        }

        System.out.print("Full name: ");
        String fullName = sc.nextLine().trim();
        if (fullName.length() < 2 || fullName.length() > 100) {
            System.out.println("Full name length is invalid.");
            return;
        }

        System.out.print("ID number: ");
        String idNumber = sc.nextLine().trim();
        if (idNumber.isEmpty() || !idNumber.matches("\\d+")) {
            System.out.println("ID number must contain digits only.");
            return;
        }
        if (idNumberExists(users, idNumber)) {
            System.out.println("ID number already exists.");
            return;
        }

        System.out.print("Contact number: ");
        String contactNo = sc.nextLine().trim();
        if (contactNo.isEmpty() || !contactNo.matches("\\d+")) {
            System.out.println("Contact number must contain digits only.");
            return;
        }

        String password = promptForStrongPassword(sc);
        createUser(users, username, Role.CUSTOMER, password, fullName, idNumber, contactNo);
        db.saveUsers(users);

        MyLogger.writeToLog("REGISTER: new_customer username=" + username);
        System.out.println("Registration successful.");
    }

    public User login(Map<String, User> users, Scanner sc) {
        SecurityPolicy policy = currentSecurityPolicy();

        System.out.println("\n=== Login ===");
        System.out.print("Username: ");
        String username = normalizeUsername(sc.nextLine());

        System.out.print("Password: ");
        String password = sc.nextLine();

        User user = users.get(username);
        if (user == null) {
            MyLogger.writeToWarning("LOGIN_FAIL: user_not_found username=" + username);
            System.out.println("Invalid credentials.");
            return null;
        }

        if (user.locked) {
            MyLogger.writeToWarning("LOGIN_BLOCKED: locked_account username=" + username);
            System.out.println("Account is locked. Contact admin.");
            return null;
        }

        try {
            String computedHash = hashPasswordToBase64(password.toCharArray(), fromBase64(user.saltBase64));
            if (constantTimeEquals(computedHash, user.hashBase64)) {
                user.failedAttempts = 0;
                db.saveUsers(users);

                MyLogger.writeToLog("LOGIN_OK: username=" + username + " role=" + user.role);
                System.out.println("Login successful. Welcome " + user.role + "!");
                return user;
            }
        } catch (RuntimeException e) {
            MyLogger.writeToWarning("LOGIN_FAIL: bad_saved_password_data username=" + username);
        }

        user.failedAttempts++;
        MyLogger.writeToWarning("LOGIN_FAIL: wrong_password username=" + username
                + " attempts=" + user.failedAttempts);

        if (user.failedAttempts >= policy.maxLoginAttempts) {
            user.locked = true;
            MyLogger.writeToWarning("ACCOUNT_LOCKED: username=" + username);
            System.out.println("Too many attempts. Account locked.");
        } else {
            System.out.println("Invalid credentials. Attempts left: "
                    + (policy.maxLoginAttempts - user.failedAttempts));
        }

        db.saveUsers(users);
        return null;
    }

    public void adminCreateStaffUser(Map<String, User> users, Scanner sc) {
        System.out.println("\n=== Admin: Create Staff User ===");

        System.out.print("Enter new username: ");
        String username = normalizeUsername(sc.nextLine());

        if (!validUsername(username)) {
            System.out.println("Username must be 3-50 characters and use letters, numbers, or underscore only.");
            return;
        }
        if (users.containsKey(username)) {
            System.out.println("Username already exists.");
            return;
        }

        Role role = promptForStaffRole(sc);
        if (role == null) {
            return;
        }

        System.out.print("Full name: ");
        String fullName = sc.nextLine().trim();
        if (fullName.length() < 2 || fullName.length() > 100) {
            System.out.println("Full name length is invalid.");
            return;
        }

        System.out.print("ID number: ");
        String idNumber = sc.nextLine().trim();
        if (idNumber.isEmpty() || !idNumber.matches("\\d+")) {
            System.out.println("ID number must contain digits only.");
            return;
        }
        if (idNumberExists(users, idNumber)) {
            System.out.println("ID number already exists.");
            return;
        }

        System.out.print("Contact number: ");
        String contactNo = sc.nextLine().trim();
        if (contactNo.isEmpty() || !contactNo.matches("\\d+")) {
            System.out.println("Contact number must contain digits only.");
            return;
        }

        String password = promptForStrongPassword(sc);
        createUser(users, username, role, password, fullName, idNumber, contactNo);
        db.saveUsers(users);

        MyLogger.writeToLog("ADMIN_ACTION: created_user username=" + username + " role=" + role);
        System.out.println("User created successfully.");
    }

    public void adminRemoveUser(Map<String, User> users, Scanner sc) {
        System.out.println("\n=== Admin: Remove User ===");
        System.out.print("Enter username to remove: ");
        String username = normalizeUsername(sc.nextLine());

        User user = users.get(username);
        if (user == null) {
            System.out.println("User not found.");
            return;
        }
        if (user.role == Role.ADMIN) {
            System.out.println("Admin accounts cannot be removed here.");
            return;
        }

        users.remove(username);
        db.saveUsers(users);

        MyLogger.writeToLog("ADMIN_ACTION: removed_user username=" + username);
        System.out.println("User removed successfully.");
    }

    public void adminLockUser(Map<String, User> users, Scanner sc) {
        System.out.println("\n=== Admin: Lock User ===");
        System.out.print("Enter username to lock: ");
        String username = normalizeUsername(sc.nextLine());

        User user = users.get(username);
        if (user == null) {
            System.out.println("User not found.");
            return;
        }
        if (user.role == Role.ADMIN) {
            System.out.println("Admin accounts cannot be locked here.");
            return;
        }
        if (user.locked) {
            System.out.println("User is already locked.");
            return;
        }

        user.locked = true;
        user.failedAttempts = 0;
        db.saveUsers(users);

        MyLogger.writeToLog("ADMIN_ACTION: locked_user username=" + username);
        System.out.println("User locked successfully.");
    }

    public void adminUnlockUser(Map<String, User> users, Scanner sc) {
        System.out.println("\n=== Admin: Unlock User ===");
        System.out.print("Enter username to unlock: ");
        String username = normalizeUsername(sc.nextLine());

        User user = users.get(username);
        if (user == null) {
            System.out.println("User not found.");
            return;
        }

        user.locked = false;
        user.failedAttempts = 0;
        db.saveUsers(users);

        MyLogger.writeToLog("ADMIN_ACTION: unlocked_user username=" + username);
        System.out.println("User unlocked successfully.");
    }

    public void adminListUsers(Map<String, User> users) {
        System.out.println("\n=== Users List ===");
        if (users.isEmpty()) {
            System.out.println("No users found.");
            return;
        }

        for (User user : users.values()) {
            System.out.println("- " + user.username + " | " + user.role + " | locked="
                    + user.locked + " | failedAttempts=" + user.failedAttempts);
        }
    }

    public void adminUpdateSecurityPolicy(Scanner sc) {
        SecurityPolicy current = currentSecurityPolicy();

        System.out.println("\n=== Admin: Update Security Policy ===");
        printSecurityPolicy(current);
        System.out.println("Leave input empty to keep the current value.");

        int minLength = promptForPolicyNumber(sc, "Minimum password characters", current.minPasswordLength, 8);
        int minUppercase = promptForPolicyNumber(sc, "Minimum uppercase letters", current.minUppercaseLetters, 1);
        int minLowercase = promptForPolicyNumber(sc, "Minimum lowercase letters", current.minLowercaseLetters, 1);
        int minDigits = promptForPolicyNumber(sc, "Minimum digits", current.minDigits, 1);
        int minSpecial = promptForPolicyNumber(sc, "Minimum special characters", current.minSpecialCharacters, 1);
        int maxAttempts = promptForPolicyNumber(sc, "Maximum login attempts", current.maxLoginAttempts, 1);

        SecurityPolicy updated = new SecurityPolicy(minLength, minUppercase, minLowercase,
                minDigits, minSpecial, maxAttempts);

        if (!updated.isValid()) {
            System.out.println("Invalid policy. Minimum password characters must be at least "
                    + updated.requiredCharacterCount() + " to fit all required character counts.");
            return;
        }

        securityPolicy = updated;
        db.saveSecurityPolicy(securityPolicy);

        MyLogger.writeToLog("ADMIN_ACTION: updated_security_policy");
        System.out.println("Security policy updated and saved to file.");
    }

    public void customerViewProfile(User user) {
        System.out.println("\n=== My Profile ===");
        System.out.println("Username: " + user.username);
        System.out.println("Name: " + user.fullName);
        System.out.println("ID number: " + user.idNumber);
        System.out.println("Contact: " + user.contactNo);
    }

    public void customerUpdateProfile(User user, Map<String, User> users, Scanner sc) {
        System.out.println("\n=== Update My Profile ===");
        System.out.println("Leave field empty to keep current value.");

        System.out.print("Full name (current: " + user.fullName + "): ");
        String fullName = sc.nextLine().trim();
        if (!fullName.isEmpty()) {
            if (fullName.length() < 2 || fullName.length() > 100) {
                System.out.println("Full name length is invalid.");
                return;
            }
            user.fullName = fullName;
        }

        System.out.print("ID number (current: " + user.idNumber + "): ");
        String idNumber = sc.nextLine().trim();
        if (!idNumber.isEmpty()) {
            if (!idNumber.matches("\\d+")) {
                System.out.println("ID number must contain digits only.");
                return;
            }
            if (!idNumber.equals(user.idNumber) && idNumberExistsForOtherUser(users, user.username, idNumber)) {
                System.out.println("ID number already exists.");
                return;
            }
            user.idNumber = idNumber;
        }

        System.out.print("Contact number (current: " + user.contactNo + "): ");
        String contactNo = sc.nextLine().trim();
        if (!contactNo.isEmpty()) {
            if (!contactNo.matches("\\d+")) {
                System.out.println("Contact number must contain digits only.");
                return;
            }
            user.contactNo = contactNo;
        }

        users.put(user.username, user);
        db.saveUsers(users);

        MyLogger.writeToLog("CUSTOMER_ACTION: updated_profile username=" + user.username);
        System.out.println("Profile updated successfully.");
    }

    private SecurityPolicy currentSecurityPolicy() {
        securityPolicy = db.loadSecurityPolicy();
        return securityPolicy;
    }

    private String promptForStrongPassword(Scanner sc) {
        SecurityPolicy policy = currentSecurityPolicy();

        while (true) {
            System.out.print("Choose password (min " + policy.minPasswordLength
                    + " chars, uppercase=" + policy.minUppercaseLetters
                    + ", lowercase=" + policy.minLowercaseLetters
                    + ", digits=" + policy.minDigits
                    + ", special=" + policy.minSpecialCharacters + "): ");
            String password = sc.nextLine();

            if (password.length() < policy.minPasswordLength) {
                System.out.println("Password too short.");
                continue;
            }

            int uppercase = countMatches(password, Character::isUpperCase);
            int lowercase = countMatches(password, Character::isLowerCase);
            int digits = countMatches(password, Character::isDigit);
            int special = countMatches(password, ch -> !Character.isLetterOrDigit(ch));

            if (uppercase < policy.minUppercaseLetters
                    || lowercase < policy.minLowercaseLetters
                    || digits < policy.minDigits
                    || special < policy.minSpecialCharacters) {
                System.out.println("Password does not meet the current security policy.");
                continue;
            }

            return password;
        }
    }

    private void createUser(Map<String, User> users,
                            String username,
                            Role role,
                            String password,
                            String fullName,
                            String idNumber,
                            String contactNo) {
        byte[] salt = generateSalt(16);
        String saltBase64 = toBase64(salt);
        String hashBase64 = hashPasswordToBase64(password.toCharArray(), salt);

        User user = new User(username, role, saltBase64, hashBase64,
                false, 0, fullName, idNumber, contactNo);
        users.put(username, user);
    }

    private Role promptForStaffRole(Scanner sc) {
        System.out.println("Select role:");
        System.out.println("1) DISPATCHER");
        System.out.println("2) DRIVER");
        System.out.println("3) ADMIN");
        System.out.print("Choose: ");

        String choice = sc.nextLine().trim();
        if (choice.equals("1")) {
            return Role.DISPATCHER;
        }
        if (choice.equals("2")) {
            return Role.DRIVER;
        }
        if (choice.equals("3")) {
            return Role.ADMIN;
        }

        System.out.println("Invalid role.");
        return null;
    }

    private int promptForPolicyNumber(Scanner sc, String label, int currentValue, int minimumValue) {
        while (true) {
            System.out.print(label + " (current " + currentValue + "): ");
            String input = sc.nextLine().trim();
            if (input.isEmpty()) {
                return currentValue;
            }

            try {
                int value = Integer.parseInt(input);
                if (value >= minimumValue) {
                    return value;
                }
            } catch (NumberFormatException e) {
                // Show the same safe validation message below.
            }

            System.out.println("Enter a whole number greater than or equal to " + minimumValue + ".");
        }
    }

    private void printSecurityPolicy(SecurityPolicy policy) {
        System.out.println("Current policy:");
        System.out.println("- Minimum password characters: " + policy.minPasswordLength);
        System.out.println("- Minimum uppercase letters: " + policy.minUppercaseLetters);
        System.out.println("- Minimum lowercase letters: " + policy.minLowercaseLetters);
        System.out.println("- Minimum digits: " + policy.minDigits);
        System.out.println("- Minimum special characters: " + policy.minSpecialCharacters);
        System.out.println("- Maximum login attempts: " + policy.maxLoginAttempts);
    }

    private int countMatches(String value, IntPredicate predicate) {
        return (int) value.chars().filter(predicate).count();
    }

    private byte[] generateSalt(int lengthBytes) {
        byte[] salt = new byte[lengthBytes];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private String hashPasswordToBase64(char[] password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return toBase64(keyFactory.generateSecret(spec).getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    private String toBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    private byte[] fromBase64(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);

        int diff = x.length ^ y.length;
        for (int i = 0; i < Math.min(x.length, y.length); i++) {
            diff |= x[i] ^ y[i];
        }
        return diff == 0;
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase();
    }

    private boolean validUsername(String username) {
        return username.matches("[a-z0-9_]{3,50}");
    }

    private boolean idNumberExists(Map<String, User> users, String idNumber) {
        return users.values().stream().anyMatch(user -> idNumber.equals(user.idNumber));
    }

    private boolean idNumberExistsForOtherUser(Map<String, User> users, String username, String idNumber) {
        return users.values().stream()
                .anyMatch(user -> !user.username.equals(username) && idNumber.equals(user.idNumber));
    }
}
