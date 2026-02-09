package com.example.rotaoptjv;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class CustomerListActivity extends AppCompatActivity implements CustomerAdapter.OnCustomerClickListener {

    private RecyclerView recyclerView;
    private CustomerAdapter adapter;
    private List<Customer> customerList;
    private DatabaseHelper dbHelper;
    private FloatingActionButton fabAddCustomer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_list);

        // Initialize database helper
        dbHelper = new DatabaseHelper(this);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Müşteriler");

        // Initialize views
        recyclerView = findViewById(R.id.customerRecyclerView);
        fabAddCustomer = findViewById(R.id.fabAddCustomer);

        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        customerList = new ArrayList<>();
        adapter = new CustomerAdapter(this, customerList, this);
        recyclerView.setAdapter(adapter);

        // Set up FAB
        fabAddCustomer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CustomerListActivity.this, CustomerEditActivity.class);
                startActivity(intent);
            }
        });

        // Load customers
        loadCustomers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the customer list when returning to this activity
        loadCustomers();
    }

    private void loadCustomers() {
        customerList.clear();
        customerList.addAll(dbHelper.getAllCustomers());
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onEditClick(Customer customer, int position) {
        Intent intent = new Intent(CustomerListActivity.this, CustomerEditActivity.class);
        intent.putExtra("customer", customer);
        startActivity(intent);
    }

    @Override
    public void onDeleteClick(Customer customer, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Müşteri Sil")
                .setMessage(customer.getName() + " isimli müşteriyi silmek istediğinize emin misiniz?")
                .setPositiveButton("Evet", (dialog, which) -> {
                    dbHelper.deleteCustomer(customer);
                    customerList.remove(position);
                    adapter.notifyItemRemoved(position);
                })
                .setNegativeButton("Hayır", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}