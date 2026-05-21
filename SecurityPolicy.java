public class SecurityPolicy {
    public int minPasswordLength;
    public int minUppercaseLetters;
    public int minLowercaseLetters;
    public int minDigits;
    public int minSpecialCharacters;
    public int maxLoginAttempts;

    public SecurityPolicy(int minPasswordLength,
            int minUppercaseLetters,
            int minLowercaseLetters,
            int minDigits,
            int minSpecialCharacters,
            int maxLoginAttempts) {
        this.minPasswordLength = minPasswordLength;
        this.minUppercaseLetters = minUppercaseLetters;
        this.minLowercaseLetters = minLowercaseLetters;
        this.minDigits = minDigits;
        this.minSpecialCharacters = minSpecialCharacters;
        this.maxLoginAttempts = maxLoginAttempts;
    }

    public static SecurityPolicy defaultPolicy() {
        return new SecurityPolicy(10, 1, 1, 1, 1, 5);
    }

    public boolean isValid() {
        // Ensures the password length can fit all required character types.
        return minPasswordLength >= requiredCharacterCount()
                && minPasswordLength >= 8
                && minUppercaseLetters >= 1
                && minLowercaseLetters >= 1
                && minDigits >= 1
                && minSpecialCharacters >= 1
                && maxLoginAttempts >= 1;
    }

    // to calcuate the length of the password entered
    public int requiredCharacterCount() {
        return minUppercaseLetters + minLowercaseLetters + minDigits + minSpecialCharacters;
    }
}
