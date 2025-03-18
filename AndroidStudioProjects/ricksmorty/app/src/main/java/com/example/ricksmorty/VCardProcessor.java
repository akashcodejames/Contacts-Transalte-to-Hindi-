package com.example.ricksmorty;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;
import java.io.*;
import java.util.*;
import java.util.regex.*;

public class VCardProcessor {
    private static final String TAG = "VCardProcessor";
    
    // Interface for progress updates
    public interface ProgressListener {
        void onProgressUpdate(int current, int total, String currentName);
        void onNameProcessed(String originalName, String processedName);
    }

    public static List<String> processVCF(ContentResolver contentResolver, Uri fileUri, ProgressListener listener) {
        List<String> processedContacts = new ArrayList<>();
        try {
            // First pass: count total contacts for progress reporting
            int totalContacts = countContacts(contentResolver, fileUri);
            if (totalContacts == 0) {
                throw new IOException("No valid contacts found in the file. Please check if it's a valid VCF file.");
            }
            
            // Second pass: process contacts
            InputStream inputStream = contentResolver.openInputStream(fileUri);
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream");
                throw new IOException("Failed to open the selected file");
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder contact = new StringBuilder();
            String line;
            boolean inContact = false;
            int contactCount = 0;
            String currentName = "";

            while ((line = reader.readLine()) != null) {
                // Trim the line to handle any extra whitespace
                line = line.trim();
                
                if (line.startsWith("BEGIN:VCARD")) {
                    contact = new StringBuilder();
                    inContact = true;
                    contactCount++;
                    currentName = "Contact #" + contactCount;
                    if (listener != null) {
                        listener.onProgressUpdate(contactCount, totalContacts, currentName);
                    }
                    Log.d(TAG, "Processing contact #" + contactCount + " of " + totalContacts);
                }

                if (inContact) {
                    contact.append(line).append("\n");
                    
                    // Try to extract name as soon as we see it for better progress reporting
                    if (line.startsWith("FN:")) {
                        currentName = line.substring(3).trim();
                        if (listener != null) {
                            listener.onProgressUpdate(contactCount, totalContacts, currentName);
                        }
                    } else if (line.startsWith("N:") && currentName.equals("Contact #" + contactCount)) {
                        // Only use N if we haven't found FN yet
                        String[] nameParts = line.substring(2).trim().split(";");
                        if (nameParts.length > 1 && !nameParts[1].isEmpty()) {
                            // Use first name if available
                            currentName = nameParts[1].trim();
                            if (!nameParts[0].isEmpty()) {
                                // Add last name if available
                                currentName += " " + nameParts[0].trim();
                            }
                            if (listener != null) {
                                listener.onProgressUpdate(contactCount, totalContacts, currentName);
                            }
                        }
                    }
                }

                if (line.startsWith("END:VCARD")) {
                    String contactStr = contact.toString();
                    // Process with translation enabled
                    String processed = processContact(contactStr, listener, true);
                    processedContacts.add(processed);
                    inContact = false;
                }
            }
            reader.close();
            
            Log.d(TAG, "Finished processing file. Total contacts: " + processedContacts.size());
        } catch (IOException e) {
            Log.e(TAG, "Error processing VCF file: " + e.getMessage(), e);
            throw new RuntimeException("Error processing file: " + e.getMessage(), e);
        }
        return processedContacts;
    }
    
    /**
     * Process VCF file and return a list of ContactModel objects for duplicate detection
     */
    public static List<ContactModel> processVCFForDuplicates(ContentResolver contentResolver, Uri fileUri, ProgressListener listener) {
        List<ContactModel> contacts = new ArrayList<>();
        try {
            // First pass: count total contacts for progress reporting
            int totalContacts = countContacts(contentResolver, fileUri);
            if (totalContacts == 0) {
                throw new IOException("No valid contacts found in the file. Please check if it's a valid VCF file.");
            }
            
            // Second pass: process contacts
            InputStream inputStream = contentResolver.openInputStream(fileUri);
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream");
                throw new IOException("Failed to open the selected file");
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder contact = new StringBuilder();
            String line;
            boolean inContact = false;
            int contactCount = 0;
            String currentName = "";

            while ((line = reader.readLine()) != null) {
                // Trim the line to handle any extra whitespace
                line = line.trim();
                
                if (line.startsWith("BEGIN:VCARD")) {
                    contact = new StringBuilder();
                    inContact = true;
                    contactCount++;
                    currentName = "Contact #" + contactCount;
                    if (listener != null) {
                        listener.onProgressUpdate(contactCount, totalContacts, currentName);
                    }
                }

                if (inContact) {
                    contact.append(line).append("\n");
                    
                    // Try to extract name as soon as we see it for better progress reporting
                    if (line.startsWith("FN:")) {
                        currentName = line.substring(3).trim();
                        if (listener != null) {
                            listener.onProgressUpdate(contactCount, totalContacts, currentName);
                        }
                    } else if (line.startsWith("N:") && currentName.equals("Contact #" + contactCount)) {
                        // Only use N if we haven't found FN yet
                        String[] nameParts = line.substring(2).trim().split(";");
                        if (nameParts.length > 1 && !nameParts[1].isEmpty()) {
                            // Use first name if available
                            currentName = nameParts[1].trim();
                            if (!nameParts[0].isEmpty()) {
                                // Add last name if available
                                currentName += " " + nameParts[0].trim();
                            }
                            if (listener != null) {
                                listener.onProgressUpdate(contactCount, totalContacts, currentName);
                            }
                        }
                    }
                }

                if (line.startsWith("END:VCARD")) {
                    String contactStr = contact.toString();
                    // Process without translation for duplicate detection
                    String processed = processContact(contactStr, listener, false);
                    ContactModel contactModel = new ContactModel(processed);
                    contacts.add(contactModel);
                    inContact = false;
                }
            }
            reader.close();
            
            Log.d(TAG, "Finished processing file for duplicates. Total contacts: " + contacts.size());
            
            // Find duplicate contacts
            findDuplicates(contacts);
            
        } catch (IOException e) {
            Log.e(TAG, "Error processing VCF file: " + e.getMessage(), e);
            throw new RuntimeException("Error processing file: " + e.getMessage(), e);
        }
        return contacts;
    }
    
    /**
     * Find duplicate contacts in the list
     */
    private static void findDuplicates(List<ContactModel> contacts) {
        for (int i = 0; i < contacts.size(); i++) {
            ContactModel contact1 = contacts.get(i);
            
            for (int j = i + 1; j < contacts.size(); j++) {
                ContactModel contact2 = contacts.get(j);
                
                if (contact1.isPotentialDuplicate(contact2)) {
                    contact1.addDuplicate(contact2);
                }
            }
        }
    }
    
    // Count total contacts in the file for progress reporting
    private static int countContacts(ContentResolver contentResolver, Uri fileUri) throws IOException {
        int count = 0;
        InputStream inputStream = contentResolver.openInputStream(fileUri);
        if (inputStream == null) {
            return 0;
        }
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("BEGIN:VCARD")) {
                    count++;
                }
            }
        }
        return count;
    }

    private static String processContact(String contact, ProgressListener listener, boolean translate) {
        Log.d(TAG, "Processing contact: " + contact);
        
        // Extract the FN (Full Name) field
        Pattern fnPattern = Pattern.compile("FN:(.*?)(?:\\r?\\n)");
        Matcher matcher = fnPattern.matcher(contact);
        
        if (!matcher.find()) {
            Log.d(TAG, "No FN field found in contact, trying to find N field");
            
            // If FN is not found, try to extract from N field
            Pattern nPattern = Pattern.compile("N:(.*?)(?:\\r?\\n)");
            Matcher nMatcher = nPattern.matcher(contact);
            
            if (nMatcher.find()) {
                // Found N field, create FN field
                String nValue = nMatcher.group(1);
                String[] nameParts = nValue.split(";");
                
                // Typically N format is: last;first;middle;prefix;suffix
                StringBuilder nameBuilder = new StringBuilder();
                
                // Add prefix if exists
                if (nameParts.length > 3 && !nameParts[3].isEmpty()) {
                    nameBuilder.append(nameParts[3]).append(" ");
                }
                
                // Add first name if exists
                if (nameParts.length > 1 && !nameParts[1].isEmpty()) {
                    nameBuilder.append(nameParts[1]).append(" ");
                }
                
                // Add middle name if exists
                if (nameParts.length > 2 && !nameParts[2].isEmpty()) {
                    nameBuilder.append(nameParts[2]).append(" ");
                }
                
                // Add last name if exists
                if (!nameParts[0].isEmpty()) {
                    nameBuilder.append(nameParts[0]);
                }
                
                String constructedName = nameBuilder.toString().trim();
                
                if (!constructedName.isEmpty()) {
                    // Add FN field to the contact
                    contact = "FN:" + constructedName + "\n" + contact;
                    Log.d(TAG, "Created FN field from N field: " + constructedName);
                    
                    // Update matcher to use the newly created FN field
                    matcher = fnPattern.matcher(contact);
                    matcher.find();
                } else {
                    Log.d(TAG, "Could not construct name from N field");
                    return contact; // Return unchanged if we can't create a name
                }
            } else {
                Log.d(TAG, "No name fields found in contact");
                return contact; // Return unchanged if no name fields found
            }
        }

        String originalName = matcher.group(1);
        Log.d(TAG, "Original name: " + originalName);
        
        // Check if the name already contains both English and Hindi translations
        if (containsBothLanguages(originalName)) {
            Log.d(TAG, "Name already contains both languages: " + originalName);
            
            // Notify listener about the already processed name
            if (listener != null) {
                listener.onNameProcessed(originalName, originalName);
            }
            
            // Return unchanged
            return contact;
        }
        
        if (!translate) {
            return contact;
        }
        
        String englishName, hindiName;

        try {
            if (isHindi(originalName)) {
                hindiName = originalName;
                Log.d(TAG, "Detected Hindi name, translating to English");
                englishName = GoogleTranslateAPI.translate(hindiName, "hi", "en");
                
                // Fallback if translation fails
                if (englishName == null || englishName.equals(hindiName)) {
                    Log.d(TAG, "Translation failed or returned same text, using original name");
                    englishName = hindiName;
                }
            } else {
                englishName = originalName;
                Log.d(TAG, "Detected English/other name, translating to Hindi");
                hindiName = GoogleTranslateAPI.translate(englishName, "en", "hi");
                
                // Fallback if translation fails
                if (hindiName == null || hindiName.equals(englishName)) {
                    Log.d(TAG, "Translation failed or returned same text, using original name");
                    hindiName = englishName;
                }
            }

            Log.d(TAG, "English name: " + englishName + ", Hindi name: " + hindiName);
            String newName = englishName + " (" + hindiName + ")";
            Log.d(TAG, "New name: " + newName);
            
            // Notify listener about the processed name
            if (listener != null) {
                listener.onNameProcessed(originalName, newName);
            }
            
            // Replace the FN field with the new name
            String updatedContact = contact.replaceFirst("FN:.*?(\\r?\\n)", "FN:" + newName + "$1");
            Log.d(TAG, "Updated contact with translated name: " + newName);
            return updatedContact;
        } catch (Exception e) {
            Log.e(TAG, "Error during translation: " + e.getMessage(), e);
            return contact; // Return unchanged if translation fails
        }
    }

    // Overloaded method for backward compatibility
    private static String processContact(String contact, ProgressListener listener) {
        // Call the new method with translation enabled
        return processContact(contact, listener, true);
    }

    /**
     * Checks if the text contains both English and Hindi languages
     * Common patterns: "English (Hindi)" or "Hindi (English)"
     */
    private static boolean containsBothLanguages(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        // Check for parentheses pattern which often indicates dual-language names
        if (text.contains("(") && text.contains(")")) {
            // Check if there are Hindi characters inside or outside the parentheses
            String outsideParentheses = text.replaceAll("\\(.*?\\)", "").trim();
            
            // Extract content inside parentheses
            Pattern pattern = Pattern.compile("\\((.*?)\\)");
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String insideParentheses = matcher.group(1).trim();
                
                boolean outsideHasHindi = outsideParentheses.matches(".*[\\u0900-\\u097F]+.*");
                boolean insideHasHindi = insideParentheses.matches(".*[\\u0900-\\u097F]+.*");
                
                // If one part has Hindi and the other doesn't, it likely contains both languages
                return (outsideHasHindi && !insideHasHindi) || (!outsideHasHindi && insideHasHindi);
            }
        }
        
        return false;
    }

    private static boolean isHindi(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        // Check if the text contains Hindi Unicode characters (0900-097F)
        boolean isHindi = text.matches(".*[\\u0900-\\u097F]+.*");
        Log.d(TAG, "Is Hindi check for '" + text + "': " + isHindi);
        return isHindi;
    }
}