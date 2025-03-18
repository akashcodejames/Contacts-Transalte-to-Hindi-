package com.example.ricksmorty;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuplicateContactsActivity extends AppCompatActivity {
    private static final String TAG = "DuplicateContactsActivity";
    public static final String EXTRA_CONTACTS = "extra_contacts";
    
    private RecyclerView duplicatesRecyclerView;
    private DuplicateGroupsAdapter adapter;
    private Button mergeButton;
    private Button cancelButton;
    private List<ContactModel> allContacts;
    private List<DuplicateGroup> duplicateGroups;
    private Uri processedFileUri;
    private String processedFilePath;
    private String originalFileName;
    private String countryCode = "91"; // Default country code

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_duplicate_contacts);
        
        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        
        // Initialize UI components
        duplicatesRecyclerView = findViewById(R.id.duplicateGroupsRecyclerView);
        TextView duplicateCountText = findViewById(R.id.duplicateCountText);
        TextView instructionText = findViewById(R.id.instructionText);
        mergeButton = findViewById(R.id.mergeButton);
        cancelButton = findViewById(R.id.cancelButton);
        
        // Set text colors to black for better visibility
        duplicateCountText.setTextColor(android.graphics.Color.BLACK);
        instructionText.setTextColor(android.graphics.Color.BLACK);
        
        // Get contacts from intent
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_CONTACTS)) {
            try {
                allContacts = (List<ContactModel>) intent.getSerializableExtra(EXTRA_CONTACTS);
                originalFileName = intent.getStringExtra("original_filename");
                countryCode = intent.getStringExtra("country_code");
                if (countryCode == null || countryCode.isEmpty()) {
                    countryCode = "91"; // Default to India if not specified
                }
                
                // Set country code for all contacts
                for (ContactModel contact : allContacts) {
                    contact.setCountryCode(countryCode);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deserializing contacts: " + e.getMessage(), e);
                Toast.makeText(this, "Error loading contacts: " + e.getMessage(), Toast.LENGTH_LONG).show();
                allContacts = new ArrayList<>();
                finish();
                return;
            }
        } else {
            allContacts = new ArrayList<>();
        }
        
        // Find duplicate contacts
        findDuplicates();
        
        // Set up RecyclerView
        duplicatesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DuplicateGroupsAdapter(duplicateGroups);
        duplicatesRecyclerView.setAdapter(adapter);
        
        // Set up buttons
        mergeButton.setOnClickListener(v -> saveSelectedContacts());
        cancelButton.setOnClickListener(v -> finish());
        
        // Update status text
        updateStatusText(duplicateCountText, instructionText, mergeButton);
    }
    
    private void findDuplicates() {
        Map<String, List<ContactModel>> phoneMap = new HashMap<>();
        duplicateGroups = new ArrayList<>();
        
        // First pass: group contacts by normalized phone number
        for (ContactModel contact : allContacts) {
            for (String phone : contact.getPhoneNumbers()) {
                // Normalize phone number for better matching
                String normalizedPhone = normalizePhoneNumber(phone);
                if (!phoneMap.containsKey(normalizedPhone)) {
                    phoneMap.put(normalizedPhone, new ArrayList<>());
                }
                phoneMap.get(normalizedPhone).add(contact);
            }
        }
        
        // Second pass: create duplicate groups
        for (Map.Entry<String, List<ContactModel>> entry : phoneMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                // This phone number appears in multiple contacts
                DuplicateGroup group = new DuplicateGroup(entry.getKey(), entry.getValue());
                duplicateGroups.add(group);
            }
        }
        
        // Remove duplicates from duplicate groups (same group might be created multiple times)
        List<DuplicateGroup> uniqueGroups = new ArrayList<>();
        for (DuplicateGroup group : duplicateGroups) {
            boolean isDuplicate = false;
            for (DuplicateGroup existingGroup : uniqueGroups) {
                if (group.hasSameContacts(existingGroup)) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                uniqueGroups.add(group);
            }
        }
        
        duplicateGroups = uniqueGroups;
        Log.d(TAG, "Found " + duplicateGroups.size() + " duplicate groups");
    }
    
    private void updateStatusText(TextView duplicateCountText, TextView instructionText, Button mergeButton) {
        int totalDuplicates = 0;
        for (DuplicateGroup group : duplicateGroups) {
            totalDuplicates += group.getContacts().size() - 1; // -1 because one contact is not a duplicate
        }
        
        if (duplicateGroups.isEmpty()) {
            duplicateCountText.setText("No duplicate contacts found");
            instructionText.setText("No action needed");
            mergeButton.setText("Continue");
        } else {
            duplicateCountText.setText("Found " + totalDuplicates + " duplicate contacts in " + 
                    duplicateGroups.size() + " groups");
            instructionText.setText("Select which contacts to keep for each group");
            mergeButton.setText("Merge Selected Contacts");
        }
    }
    
    private void saveSelectedContacts() {
        // Create a list of all selected contacts
        List<ContactModel> selectedContacts = new ArrayList<>();
        
        // Add all contacts that are not in any duplicate group
        for (ContactModel contact : allContacts) {
            boolean isInDuplicateGroup = false;
            for (DuplicateGroup group : duplicateGroups) {
                if (group.getContacts().contains(contact)) {
                    isInDuplicateGroup = true;
                    break;
                }
            }
            
            if (!isInDuplicateGroup) {
                selectedContacts.add(contact);
            }
        }
        
        // Add selected contacts from duplicate groups
        for (DuplicateGroup group : duplicateGroups) {
            for (ContactModel contact : group.getContacts()) {
                if (contact.isSelected()) {
                    selectedContacts.add(contact);
                }
            }
        }
        
        // Save to file
        try {
            List<String> vCardEntries = new ArrayList<>();
            for (ContactModel contact : selectedContacts) {
                vCardEntries.add(contact.getRawVCardData());
            }
            
            String newFileName = "deduplicated_" + (originalFileName != null ? originalFileName : "contacts.vcf");
            saveToDownloads(vCardEntries, newFileName);
            
            Toast.makeText(this, "Saved " + selectedContacts.size() + " contacts to Downloads", 
                    Toast.LENGTH_LONG).show();
            
            // Return result to MainActivity
            Intent resultIntent = new Intent();
            resultIntent.putExtra("processed_file_uri", processedFileUri);
            resultIntent.putExtra("processed_file_path", processedFilePath);
            resultIntent.putExtra("contact_count", selectedContacts.size());
            setResult(RESULT_OK, resultIntent);
            finish();
            
        } catch (IOException e) {
            Log.e(TAG, "Error saving contacts: " + e.getMessage(), e);
            Toast.makeText(this, "Error saving contacts: " + e.getMessage(), 
                    Toast.LENGTH_LONG).show();
        }
    }
    
    // Save the processed contacts to the Downloads directory
    private void saveToDownloads(List<String> processedContacts, String fileName) throws IOException {
        String content = String.join("", processedContacts);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 (API 29) and above, use MediaStore
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/x-vcard");
            values.put(MediaStore.Downloads.IS_PENDING, 1);

            ContentResolver resolver = getContentResolver();
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                try (OutputStream os = resolver.openOutputStream(uri)) {
                    if (os != null) {
                        os.write(content.getBytes());
                    }
                }

                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
                
                processedFileUri = uri;
                processedFilePath = "Downloads/" + fileName;
            } else {
                throw new IOException("Failed to create file in Downloads");
            }
        } else {
            // For older Android versions
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                if (!downloadsDir.mkdirs()) {
                    throw new IOException("Failed to create Downloads directory");
                }
            }
            
            File outputFile = new File(downloadsDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(content.getBytes());
            }
            
            processedFilePath = outputFile.getAbsolutePath();
            processedFileUri = Uri.fromFile(outputFile);
        }
    }
    
    /**
     * Normalize phone number for better matching of duplicates
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
        if (normalizedNumber.length() > 10) {
            normalizedNumber = normalizedNumber.substring(normalizedNumber.length() - 10);
        }
        
        // Remove leading zeros
        normalizedNumber = normalizedNumber.replaceFirst("^0+", "");
        
        return normalizedNumber;
    }
    
    /**
     * Class to represent a group of duplicate contacts
     */
    public static class DuplicateGroup implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        
        private final String phoneNumber;
        private final List<ContactModel> contacts;
        
        public DuplicateGroup(String phoneNumber, List<ContactModel> contacts) {
            this.phoneNumber = phoneNumber;
            this.contacts = new ArrayList<>(contacts);
        }
        
        public String getPhoneNumber() {
            return phoneNumber;
        }
        
        public List<ContactModel> getContacts() {
            return contacts;
        }
        
        public boolean hasSameContacts(DuplicateGroup other) {
            if (contacts.size() != other.contacts.size()) {
                return false;
            }
            
            for (ContactModel contact : contacts) {
                if (!other.contacts.contains(contact)) {
                    return false;
                }
            }
            
            return true;
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
