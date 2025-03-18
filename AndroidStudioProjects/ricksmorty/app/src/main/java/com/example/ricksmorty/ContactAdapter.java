package com.example.ricksmorty;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {
    
    private final List<ContactModel> contacts;
    private final DuplicateContactsActivity.DuplicateGroup group;
    
    public ContactAdapter(List<ContactModel> contacts, DuplicateContactsActivity.DuplicateGroup group) {
        this.contacts = contacts;
        this.group = group;
    }
    
    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new ContactViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        ContactModel contact = contacts.get(position);
        holder.bind(contact);
    }
    
    @Override
    public int getItemCount() {
        return contacts.size();
    }
    
    class ContactViewHolder extends RecyclerView.ViewHolder {
        private final TextView contactNameText;
        private final TextView contactDetailsText;
        private final TextView contactEmailText;
        private final RadioButton contactRadioButton;
        
        public ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            contactNameText = itemView.findViewById(R.id.contactNameText);
            contactDetailsText = itemView.findViewById(R.id.contactDetailsText);
            contactEmailText = itemView.findViewById(R.id.contactEmailText);
            contactRadioButton = itemView.findViewById(R.id.contactRadioButton);
            
            // Set click listener for the entire item
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    ContactModel contact = contacts.get(position);
                    // Unselect all contacts in the group
                    for (ContactModel c : group.getContacts()) {
                        c.setSelected(false);
                    }
                    // Select this contact
                    contact.setSelected(true);
                    notifyDataSetChanged();
                }
            });
            
            // Set click listener for the radio button
            contactRadioButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    ContactModel contact = contacts.get(position);
                    // Unselect all contacts in the group
                    for (ContactModel c : group.getContacts()) {
                        c.setSelected(false);
                    }
                    // Select this contact
                    contact.setSelected(true);
                    notifyDataSetChanged();
                }
            });
        }
        
        public void bind(ContactModel contact) {
            contactNameText.setText(contact.getDisplayName());
            
            // Build phone details text
            StringBuilder phoneDetails = new StringBuilder("Phone: ");
            if (!contact.getPhoneNumbers().isEmpty()) {
                for (int i = 0; i < contact.getPhoneNumbers().size(); i++) {
                    if (i > 0) phoneDetails.append(", ");
                    phoneDetails.append(contact.getPhoneNumbers().get(i));
                }
            } else {
                phoneDetails.append("None");
            }
            contactDetailsText.setText(phoneDetails.toString());
            
            // Build email details text
            StringBuilder emailDetails = new StringBuilder("Email: ");
            if (!contact.getEmails().isEmpty()) {
                for (int i = 0; i < contact.getEmails().size(); i++) {
                    if (i > 0) emailDetails.append(", ");
                    emailDetails.append(contact.getEmails().get(i));
                }
            } else {
                emailDetails.append("None");
            }
            contactEmailText.setText(emailDetails.toString());
            
            // Set radio button state
            contactRadioButton.setChecked(contact.isSelected());
        }
    }
}
