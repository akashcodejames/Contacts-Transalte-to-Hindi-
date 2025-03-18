package com.example.ricksmorty;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DuplicateGroupsAdapter extends RecyclerView.Adapter<DuplicateGroupsAdapter.DuplicateGroupViewHolder> {
    
    private final List<DuplicateContactsActivity.DuplicateGroup> duplicateGroups;
    
    public DuplicateGroupsAdapter(List<DuplicateContactsActivity.DuplicateGroup> duplicateGroups) {
        this.duplicateGroups = duplicateGroups;
    }
    
    @NonNull
    @Override
    public DuplicateGroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_duplicate_group, parent, false);
        return new DuplicateGroupViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull DuplicateGroupViewHolder holder, int position) {
        DuplicateContactsActivity.DuplicateGroup group = duplicateGroups.get(position);
        holder.bind(group, position);
    }
    
    @Override
    public int getItemCount() {
        return duplicateGroups.size();
    }
    
    static class DuplicateGroupViewHolder extends RecyclerView.ViewHolder {
        private final TextView groupTitleText;
        private final TextView phoneNumberText;
        private final RecyclerView contactsRecyclerView;
        
        public DuplicateGroupViewHolder(@NonNull View itemView) {
            super(itemView);
            groupTitleText = itemView.findViewById(R.id.groupTitleText);
            phoneNumberText = itemView.findViewById(R.id.phoneNumberText);
            contactsRecyclerView = itemView.findViewById(R.id.contactsRecyclerView);
        }
        
        public void bind(DuplicateContactsActivity.DuplicateGroup group, int position) {
            // Set group title
            String phoneNumber = group.getPhoneNumber();
            List<ContactModel> contacts = group.getContacts();
            groupTitleText.setText("Duplicate Group #" + (position + 1) + " (" + contacts.size() + " contacts)");
            phoneNumberText.setText("Phone: " + phoneNumber);
            
            // Set up the contacts RecyclerView
            contactsRecyclerView.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
            ContactAdapter adapter = new ContactAdapter(contacts, group);
            contactsRecyclerView.setAdapter(adapter);
        }
    }
}
