import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;

public class MyLogger {
    private static final String logPath = loadLogPath(); // to serch the .env for the log file path

    public static void writeToLog(String message) { // to write normal logs in the other files
        write("INFO", message);
    }

    public static void writeToWarning(String message) { // to write warning logs in the other files
        write("WARNING", message);
    }

    public static void printLogs(User user) {
        if (user == null || user.role != Role.ADMIN) {
            String username;

            if (user == null) {
                username = "unknown";
            } else {
                username = user.username;
            }

            writeToWarning("UNAUTHORIZED_ACTION: view_logs username=" + username);
            System.out.println("Only admin can view logs.");
            return;
        }

        if (logPath == null || logPath.isEmpty()) {
            System.out.println("Logging configuration missing.");
            return;
        }

        File file = new File(logPath);
        if (!file.exists()) {
            System.out.println("No logs found.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException | SecurityException e) {
            System.out.println("Could not read logs.");
        }
    }

    // will be called by the above function to actually write the log in the file
    private static void write(String level, String message) {
        if (logPath == null || logPath.isEmpty()) {
            System.out.println("Logging configuration missing.");
            return;
        }

        File file = new File(logPath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        // open the log file for writing without removing the old logs
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
            // write the log in the file instant.now() will get the timepstamp
            writer.println(Instant.now() + " | " + level + " | " + message);

        } catch (IOException | SecurityException e) {// try with resources to close the writer after the writing and
                                                     // catch the exception if something went wrong

            System.out.println("Logging failed.");
        }
    }

    private static String loadLogPath() {
        File envFile = new File(".env");
        if (!envFile.exists()) {
            System.out.println("Logging configuration file is missing.");
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
            String line;

            while ((line = reader.readLine()) != null) { // reads the file line by line until there is no more lines
                line = line.trim(); // removes any white spaces from the line
                if (line.isEmpty() || line.startsWith("#")) { // if the line is empty or starts with # ignore it
                    continue;
                }

                String[] parts = line.split("=", 2);
                if (parts.length == 2 && parts[0].trim().equals("LOG_FILE_PATH")) {
                    String value = parts[1].trim();
                    if (!value.isEmpty()) {
                        return value;
                    }
                }
            }
        } catch (IOException | SecurityException e) {
            System.out.println("Could not read log configuration.");
        }

        System.out.println("LOG_FILE_PATH is missing in .env.");
        return null;
    }
}
