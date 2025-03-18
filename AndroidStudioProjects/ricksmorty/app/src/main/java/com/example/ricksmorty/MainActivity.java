package com.example.ricksmorty;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements VCardProcessor.ProgressListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_DUPLICATE_CONTACTS = 1001;
    private static final int REQUEST_STANDALONE_DUPLICATES = 1002;

    private TextView statusText;
    private TextView outputPathText;
    private ProgressBar progressBar;
    private MaterialButton processFileButton;
    private MaterialButton shareFileButton;
    private RecyclerView processedNamesRecyclerView;
    private ProcessedNamesAdapter processedNamesAdapter;
    private SwitchMaterial detectDuplicatesSwitch;
    private TextView processedNamesLabel;
    private Uri selectedFileUri;
    private Uri processedFileUri;
    private String processedFilePath;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MaterialButton manageDuplicatesButton;
    private TextInputEditText countryCodeInput;

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedFileUri = result.getData().getData();
                    String fileName = getFileNameFromUri(selectedFileUri);
                    statusText.setText("Selected: " + fileName);
                    processFileButton.setEnabled(true);
                    shareFileButton.setEnabled(false);
                    outputPathText.setVisibility(View.GONE);
                    processedNamesAdapter.clearItems();
                    Log.d(TAG, "File selected: " + fileName + " with URI: " + selectedFileUri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initialize UI components
        statusText = findViewById(R.id.statusText);
        outputPathText = findViewById(R.id.outputPathText);
        progressBar = findViewById(R.id.progressBar);
        MaterialButton selectFileButton = findViewById(R.id.selectFileButton);
        processFileButton = findViewById(R.id.processFileButton);
        shareFileButton = findViewById(R.id.shareFileButton);
        processedNamesRecyclerView = findViewById(R.id.processedNamesRecyclerView);
        detectDuplicatesSwitch = findViewById(R.id.detectDuplicatesSwitch);
        processedNamesLabel = findViewById(R.id.processedNamesLabel);
        countryCodeInput = findViewById(R.id.countryCodeInput);
        manageDuplicatesButton = findViewById(R.id.manageDuplicatesButton);

        // Add button for standalone duplicate management
        Button standaloneButton = findViewById(R.id.standaloneButton);
        if (standaloneButton != null) {
            standaloneButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, StandaloneDuplicateActivity.class);
                startActivityForResult(intent, REQUEST_STANDALONE_DUPLICATES);
            });
        }

        // Ensure text colors are set to white for better visibility
        statusText.setTextColor(android.graphics.Color.WHITE);
        outputPathText.setTextColor(android.graphics.Color.WHITE);
        processedNamesLabel.setTextColor(android.graphics.Color.WHITE);

        // Set up RecyclerView
        processedNamesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        processedNamesAdapter = new ProcessedNamesAdapter();
        processedNamesRecyclerView.setAdapter(processedNamesAdapter);

        // Set initial button states
        processFileButton.setEnabled(false);
        shareFileButton.setEnabled(false);
        manageDuplicatesButton.setEnabled(false);

        selectFileButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*"); // Accept all file types to ensure compatibility
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            filePickerLauncher.launch(Intent.createChooser(intent, "Select VCF File"));
        });

        processFileButton.setOnClickListener(v -> {
            if (selectedFileUri != null) {
                processFile();
            } else {
                statusText.setText("No file selected.");
                Log.d(TAG, "No file selected");
            }
        });

        shareFileButton.setOnClickListener(v -> {
            if (processedFileUri != null) {
                shareProcessedFile();
            } else {
                Toast.makeText(this, "No processed file to share", Toast.LENGTH_SHORT).show();
            }
        });

        manageDuplicatesButton.setOnClickListener(v -> {
            if (selectedFileUri != null) {
                manageDuplicatesOnly();
            } else {
                statusText.setText("No file selected.");
                Log.d(TAG, "No file selected");
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ((requestCode == REQUEST_DUPLICATE_CONTACTS || requestCode == REQUEST_STANDALONE_DUPLICATES) && resultCode == RESULT_OK) {
            // Get results from duplicate contacts activity
            if (data != null) {
                processedFileUri = data.getParcelableExtra("processed_file_uri");
                processedFilePath = data.getStringExtra("processed_file_path");
                int contactCount = data.getIntExtra("contact_count", 0);

                // Update UI
                progressBar.setVisibility(View.GONE);
                statusText.setText("Processed and deduplicated " + contactCount + " contacts!");
                if (processedFileUri != null) {
                    outputPathText.setText("Saved to Downloads: " + processedFilePath);
                    outputPathText.setVisibility(View.VISIBLE);
                    shareFileButton.setEnabled(true);
                }
                processFileButton.setEnabled(true);
                manageDuplicatesButton.setEnabled(true);
            }
        }
    }

    private void processFile() {
        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        statusText.setText("Processing file...");
        processFileButton.setEnabled(false);
        manageDuplicatesButton.setEnabled(false);
        processedNamesAdapter.clearItems();

        // Get country code
        String countryCode = countryCodeInput.getText().toString().trim();
        if (countryCode.isEmpty()) {
            countryCode = "91"; // Default to India
        }

        final String finalCountryCode = countryCode;

        // Process in background
        executorService.execute(() -> {
            try {
                if (detectDuplicatesSwitch.isChecked()) {
                    // Process with duplicate detection
                    List<ContactModel> contacts = VCardProcessor.processVCFForDuplicates(getContentResolver(), selectedFileUri, this);

                    // Set country code for better duplicate detection
                    for (ContactModel contact : contacts) {
                        contact.setCountryCode(finalCountryCode);
                    }

                    // Check if there are duplicates
                    boolean hasDuplicates = false;
                    for (ContactModel contact : contacts) {
                        if (contact.isDuplicate() || !contact.getDuplicates().isEmpty()) {
                            hasDuplicates = true;
                            break;
                        }
                    }

                    if (hasDuplicates) {
                        // Launch duplicate contacts activity
                        mainHandler.post(() -> {
                            Intent intent = new Intent(MainActivity.this, DuplicateContactsActivity.class);
                            intent.putExtra(DuplicateContactsActivity.EXTRA_CONTACTS, (Serializable) contacts);
                            intent.putExtra("original_filename", getFileNameFromUri(selectedFileUri));
                            intent.putExtra("country_code", finalCountryCode);
                            startActivityForResult(intent, REQUEST_DUPLICATE_CONTACTS);
                        });
                    } else {
                        // No duplicates found, process normally
                        List<String> processedContacts = new ArrayList<>();
                        for (ContactModel contact : contacts) {
                            processedContacts.add(contact.getRawVCardData());
                        }

                        // Get the original filename
                        String originalFileName = getFileNameFromUri(selectedFileUri);
                        String newFileName = "processed_" + originalFileName;

                        // Save to Downloads directory
                        saveToDownloads(processedContacts, newFileName);

                        // Update UI on main thread
                        mainHandler.post(() -> {
                            progressBar.setVisibility(View.GONE);
                            statusText.setText("Processed " + processedContacts.size() + " contacts! No duplicates found.");
                            if (processedFileUri != null) {
                                outputPathText.setText("Saved to Downloads: " + newFileName);
                                outputPathText.setVisibility(View.VISIBLE);
                                shareFileButton.setEnabled(true);
                            } else {
                                outputPathText.setText("File saved but URI not available. Check your Downloads folder.");
                                outputPathText.setVisibility(View.VISIBLE);
                            }
                            processFileButton.setEnabled(true);
                            manageDuplicatesButton.setEnabled(true);
                            Toast.makeText(MainActivity.this, "File processed successfully", Toast.LENGTH_LONG).show();
                        });
                    }
                } else {
                    // Process without duplicate detection
                    List<String> processedContacts = VCardProcessor.processVCF(getContentResolver(), selectedFileUri, this);

                    // Get the original filename
                    String originalFileName = getFileNameFromUri(selectedFileUri);
                    String newFileName = "processed_" + originalFileName;

                    // Save to Downloads directory
                    saveToDownloads(processedContacts, newFileName);

                    // Update UI on main thread
                    mainHandler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        statusText.setText("Processed " + processedContacts.size() + " contacts with translations!");
                        if (processedFileUri != null) {
                            outputPathText.setText("Saved to Downloads: " + newFileName);
                            outputPathText.setVisibility(View.VISIBLE);
                            shareFileButton.setEnabled(true);
                        } else {
                            outputPathText.setText("File saved but URI not available. Check your Downloads folder.");
                            outputPathText.setVisibility(View.VISIBLE);
                        }
                        processFileButton.setEnabled(true);
                        manageDuplicatesButton.setEnabled(true);
                        Toast.makeText(MainActivity.this, "File processed successfully with translations", Toast.LENGTH_LONG).show();
                    });
                }

                Log.d(TAG, "Saved processed contacts to Downloads");
            } catch (Exception e) {
                Log.e(TAG, "Error processing file: " + e.getMessage(), e);

                // Update UI on main thread
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("Error: " + e.getMessage());
                    processFileButton.setEnabled(true);
                    manageDuplicatesButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Error processing file: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
            statusText.setText("Processing " + current + " of " + total + ": " + currentName);
        });
    }

    @Override
    public void onNameProcessed(String originalName, String processedName) {
        mainHandler.post(() -> {
            processedNamesAdapter.addItem(new ProcessedNameItem(originalName, processedName));
            processedNamesRecyclerView.scrollToPosition(processedNamesAdapter.getItemCount() - 1);
        });
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

    // Share the processed file
    private void shareProcessedFile() {
        if (processedFileUri != null) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/x-vcard");
            shareIntent.putExtra(Intent.EXTRA_STREAM, processedFileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share Processed Contacts"));
        }
    }

    // Helper method to get file name from URI
    public static String getFileNameFromUri(android.content.Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting filename: " + e.getMessage(), e);
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    private String getFileNameFromUri(Uri uri) {
        return getFileNameFromUri(this, uri);
    }

    private void manageDuplicatesOnly() {
        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        statusText.setText("Analyzing contacts for duplicates...");
        processFileButton.setEnabled(false);
        manageDuplicatesButton.setEnabled(false);
        processedNamesAdapter.clearItems();

        // Get country code
        String countryCode = countryCodeInput.getText().toString().trim();
        if (countryCode.isEmpty()) {
            countryCode = "91"; // Default to India
        }

        final String finalCountryCode = countryCode;

        // Process in background
        executorService.execute(() -> {
            try {
                // Process with duplicate detection
                List<ContactModel> contacts = VCardProcessor.processVCFForDuplicates(getContentResolver(), selectedFileUri, this);

                // Set country code for better duplicate detection
                for (ContactModel contact : contacts) {
                    contact.setCountryCode(finalCountryCode);
                }

                // Check if there are duplicates
                boolean hasDuplicates = false;
                for (ContactModel contact : contacts) {
                    if (contact.isDuplicate() || !contact.getDuplicates().isEmpty()) {
                        hasDuplicates = true;
                        break;
                    }
                }

                if (hasDuplicates) {
                    // Launch duplicate contacts activity
                    mainHandler.post(() -> {
                        Intent intent = new Intent(MainActivity.this, DuplicateContactsActivity.class);
                        intent.putExtra(DuplicateContactsActivity.EXTRA_CONTACTS, (Serializable) contacts);
                        intent.putExtra("original_filename", getFileNameFromUri(selectedFileUri));
                        intent.putExtra("country_code", finalCountryCode);
                        startActivityForResult(intent, REQUEST_DUPLICATE_CONTACTS);
                    });
                } else {
                    // No duplicates found
                    mainHandler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        statusText.setText("No duplicate contacts found in the file.");
                        processFileButton.setEnabled(true);
                        manageDuplicatesButton.setEnabled(true);
                        Toast.makeText(MainActivity.this, "No duplicate contacts found", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing file: " + e.getMessage(), e);

                // Update UI on main thread
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("Error: " + e.getMessage());
                    processFileButton.setEnabled(true);
                    manageDuplicatesButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Error processing file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // Inner class for the RecyclerView adapter
    private static class ProcessedNamesAdapter extends RecyclerView.Adapter<ProcessedNamesAdapter.ViewHolder> {
        private final List<ProcessedNameItem> items = new ArrayList<>();

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ProcessedNameItem item = items.get(position);
            holder.text1.setText(item.getOriginalName());
            holder.text1.setTextColor(android.graphics.Color.WHITE);
            holder.text2.setText("→ " + item.getProcessedName());
            holder.text2.setTextColor(android.graphics.Color.WHITE);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        public void addItem(ProcessedNameItem item) {
            items.add(item);
            notifyItemInserted(items.size() - 1);
        }

        public void clearItems() {
            int size = items.size();
            items.clear();
            notifyItemRangeRemoved(0, size);
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1;
            TextView text2;

            ViewHolder(android.view.View view) {
                super(view);
                text1 = view.findViewById(android.R.id.text1);
                text2 = view.findViewById(android.R.id.text2);
            }
        }
    }

    // Data class for processed names
    private static class ProcessedNameItem {
        private final String originalName;
        private final String processedName;

        ProcessedNameItem(String originalName, String processedName) {
            this.originalName = originalName;
            this.processedName = processedName;
        }

        public String getOriginalName() {
            return originalName;
        }

        public String getProcessedName() {
            return processedName;
        }
    }
}