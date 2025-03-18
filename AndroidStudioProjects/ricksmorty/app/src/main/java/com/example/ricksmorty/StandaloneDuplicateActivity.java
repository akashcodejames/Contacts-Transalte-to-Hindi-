package com.example.ricksmorty;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StandaloneDuplicateActivity extends AppCompatActivity implements VCardProcessor.ProgressListener {
    private static final String TAG = "StandaloneDuplicateActivity";
    private static final int REQUEST_DUPLICATE_CONTACTS = 1001;
    
    private TextView statusText;
    private ProgressBar progressBar;
    private MaterialButton selectFileButton;
    private MaterialButton processButton;
    private TextInputEditText countryCodeInput;
    private Uri selectedFileUri;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedFileUri = result.getData().getData();
                    String fileName = MainActivity.getFileNameFromUri(this, selectedFileUri);
                    statusText.setText("Selected: " + fileName);
                    processButton.setEnabled(true);
                    Log.d(TAG, "File selected: " + fileName + " with URI: " + selectedFileUri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_standalone_duplicate);

        // Set up toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // Initialize UI components
        statusText = findViewById(R.id.statusText);
        progressBar = findViewById(R.id.progressBar);
        selectFileButton = findViewById(R.id.selectFileButton);
        processButton = findViewById(R.id.processButton);
        countryCodeInput = findViewById(R.id.countryCodeInput);
        Button backButton = findViewById(R.id.backButton);

        // Ensure text colors are set to white for better visibility
        statusText.setTextColor(android.graphics.Color.WHITE);

        // Set initial button states
        processButton.setEnabled(false);

        selectFileButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*"); // Accept all file types to ensure compatibility
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            filePickerLauncher.launch(Intent.createChooser(intent, "Select VCF File"));
        });

        processButton.setOnClickListener(v -> {
            if (selectedFileUri != null) {
                processFile();
            } else {
                statusText.setText("No file selected.");
                Log.d(TAG, "No file selected");
            }
        });

        backButton.setOnClickListener(v -> finish());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_DUPLICATE_CONTACTS && resultCode == RESULT_OK) {
            // Get results from duplicate contacts activity
            if (data != null) {
                Uri processedFileUri = data.getParcelableExtra("processed_file_uri");
                String processedFilePath = data.getStringExtra("processed_file_path");
                int contactCount = data.getIntExtra("contact_count", 0);
                
                // Update UI
                progressBar.setVisibility(View.GONE);
                statusText.setText("Deduplicated " + contactCount + " contacts! Saved to Downloads: " + processedFilePath);
                processButton.setEnabled(true);
                
                // Return to main activity with the result
                Intent resultIntent = new Intent();
                resultIntent.putExtra("processed_file_uri", processedFileUri);
                resultIntent.putExtra("processed_file_path", processedFilePath);
                resultIntent.putExtra("contact_count", contactCount);
                setResult(RESULT_OK, resultIntent);
                
                Toast.makeText(this, "Contacts deduplicated successfully!", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void processFile() {
        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        statusText.setText("Checking for duplicate contacts...");
        processButton.setEnabled(false);

        // Get country code
        String countryCode = countryCodeInput.getText().toString().trim();
        if (countryCode.isEmpty()) {
            countryCode = "91"; // Default to India
        }

        final String finalCountryCode = countryCode;

        // Process in background
        executorService.execute(() -> {
            try {
                // Process with duplicate detection only
                List<ContactModel> contacts = VCardProcessor.processVCFForDuplicates(getContentResolver(), selectedFileUri, this);

                // Set country code for better duplicate detection
                for (ContactModel contact : contacts) {
                    contact.setCountryCode(finalCountryCode);
                }

                // Check if there are duplicates
                boolean hasDuplicates = contacts.stream().anyMatch(contact -> contact.isDuplicate() || !contact.getDuplicates().isEmpty());

                if (hasDuplicates) {
                    // Launch duplicate contacts activity
                    mainHandler.post(() -> {
                        Intent intent = new Intent(StandaloneDuplicateActivity.this, DuplicateContactsActivity.class);
                        intent.putExtra(DuplicateContactsActivity.EXTRA_CONTACTS, (Serializable) contacts);
                        intent.putExtra("original_filename", MainActivity.getFileNameFromUri(this, selectedFileUri));
                        intent.putExtra("country_code", finalCountryCode);
                        startActivityForResult(intent, REQUEST_DUPLICATE_CONTACTS);
                    });
                } else {
                    // No duplicates found
                    mainHandler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        statusText.setText("No duplicate contacts found in the file.");
                        processButton.setEnabled(true);
                        Toast.makeText(StandaloneDuplicateActivity.this, "No duplicate contacts found", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing file: " + e.getMessage(), e);

                // Update UI on main thread
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("Error: " + e.getMessage());
                    processButton.setEnabled(true);
                    Toast.makeText(StandaloneDuplicateActivity.this, "Error processing file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // Implementation of ProgressListener interface
    @Override
    public void onProgressUpdate(int current, int total, String currentName) {
        mainHandler.post(() -> {
            if (progressBar.isIndeterminate()) {
                progressBar.setIndeterminate(false);
                progressBar.setMax(total);
            }
            progressBar.setProgress(current);
            statusText.setText("Checking " + current + " of " + total + ": " + currentName);
        });
    }

    @Override
    public void onNameProcessed(String originalName, String processedName) {
        // Not used in this activity
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
