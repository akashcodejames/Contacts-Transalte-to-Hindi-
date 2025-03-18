package com.example.ricksmorty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Model class to represent a contact with its VCard data and extracted information
 */
public class ContactModel implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String rawVCardData;
    private String displayName;
    private List<String> phoneNumbers;
    private List<String> emails;
    private boolean isDuplicate = false;
    private List<ContactModel> duplicates = new ArrayList<>();
    private boolean selected = true; // Default to selected
    private String countryCode = "91"; // Default country code for India

    public ContactModel(String rawVCardData) {
        this.rawVCardData = rawVCardData;
        this.phoneNumbers = new ArrayList<>();
        this.emails = new ArrayList<>();
        extractContactInfo();
    }

    private void extractContactInfo() {
        // Extract display name
        Pattern fnPattern = Pattern.compile("FN:(.*?)(?:\\r?\\n)");
        Matcher fnMatcher = fnPattern.matcher(rawVCardData);
        if (fnMatcher.find()) {
            displayName = fnMatcher.group(1).trim();
        } else {
            displayName = "Unknown";
        }

        // Extract phone numbers
        Pattern telPattern = Pattern.compile("TEL[^:]*:(.*?)(?:\\r?\\n)");
        Matcher telMatcher = telPattern.matcher(rawVCardData);
        while (telMatcher.find()) {
            String phone = telMatcher.group(1).trim();
            // Normalize phone number by removing non-digits
            phone = phone.replaceAll("[^\\d+]", "");
            if (!phone.isEmpty() && !phoneNumbers.contains(phone)) {
                phoneNumbers.add(phone);
            }
        }

        // Extract emails
        Pattern emailPattern = Pattern.compile("EMAIL[^:]*:(.*?)(?:\\r?\\n)");
        Matcher emailMatcher = emailPattern.matcher(rawVCardData);
        while (emailMatcher.find()) {
            String email = emailMatcher.group(1).trim();
            if (!email.isEmpty() && !emails.contains(email)) {
                emails.add(email);
            }
        }
    }

    /**
     * Normalize phone number by removing country code prefixes and leading zeros
     * to enable better matching of duplicate numbers
     */
    private String normalizePhoneNumber(String phone) {
        // Remove all non-digit characters except the + sign
        String normalizedNumber = phone.replaceAll("[^\\d+]", "");
        
        // If the number starts with a +, remove it
        if (normalizedNumber.startsWith("+")) {
            normalizedNumber = normalizedNumber.substring(1);
        }
        
        // Remove specific country code if present
        if (countryCode != null && !countryCode.isEmpty() && normalizedNumber.startsWith(countryCode)) {
            normalizedNumber = normalizedNumber.substring(countryCode.length());
        }
        
        // Remove country code if present (assuming a max of 3 digits for country code)
        // This is a simplified approach - a more comprehensive solution would use a library
        if (normalizedNumber.length() > 10) {
            normalizedNumber = normalizedNumber.substring(normalizedNumber.length() - 10);
        }
        
        // Remove leading zeros
        normalizedNumber = normalizedNumber.replaceFirst("^0+", "");
        
        return normalizedNumber;
    }

    /**
     * Check if this contact is potentially a duplicate of another contact
     */
    public boolean isPotentialDuplicate(ContactModel other) {
        // If they have the same phone number, they're duplicates
        for (String phone : phoneNumbers) {
            for (String otherPhone : other.getPhoneNumbers()) {
                if (normalizePhoneNumber(phone).equals(normalizePhoneNumber(otherPhone))) {
                    return true;
                }
            }
        }
        
        // If they have the same email, they're duplicates
        for (String email : emails) {
            if (other.getEmails().contains(email)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Add a duplicate contact to this contact's list of duplicates
     */
    public void addDuplicate(ContactModel duplicate) {
        if (!duplicates.contains(duplicate)) {
            duplicates.add(duplicate);
            isDuplicate = true;
            duplicate.setDuplicate(true);
        }
    }

    // Getters and setters
    public String getRawVCardData() {
        return rawVCardData;
    }

    public void setRawVCardData(String rawVCardData) {
        this.rawVCardData = rawVCardData;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getPhoneNumbers() {
        return phoneNumbers;
    }

    public List<String> getEmails() {
        return emails;
    }

    public boolean isDuplicate() {
        return isDuplicate;
    }

    public void setDuplicate(boolean duplicate) {
        isDuplicate = duplicate;
    }

    public List<ContactModel> getDuplicates() {
        return duplicates;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
    
    public String getCountryCode() {
        return countryCode;
    }
    
    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContactModel that = (ContactModel) o;
        return Objects.equals(rawVCardData, that.rawVCardData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawVCardData);
    }

    @Override
    public String toString() {
        return "ContactModel{" +
                "displayName='" + displayName + '\'' +
                ", phoneNumbers=" + phoneNumbers +
                ", emails=" + emails +
                ", isDuplicate=" + isDuplicate +
                ", duplicates=" + duplicates.size() +
                '}';
    }
}
