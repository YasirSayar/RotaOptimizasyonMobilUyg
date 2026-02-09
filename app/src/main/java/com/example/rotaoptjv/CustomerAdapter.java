package com.example.rotaoptjv;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CustomerAdapter extends RecyclerView.Adapter<CustomerAdapter.CustomerViewHolder> {

    private List<Customer> customerList;
    private Context context;
    private OnCustomerClickListener listener;

    public interface OnCustomerClickListener {
        void onEditClick(Customer customer, int position);
        void onDeleteClick(Customer customer, int position);
    }

    public CustomerAdapter(Context context, List<Customer> customerList, OnCustomerClickListener listener) {
        this.context = context;
        this.customerList = customerList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CustomerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_customer, parent, false);
        return new CustomerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CustomerViewHolder holder, int position) {
        Customer customer = customerList.get(position);

        holder.tvName.setText(customer.getName());
        holder.tvPhone.setText("Telefon: " + customer.getPhoneNumber());
        holder.tvAddress.setText("Adres: " + customer.getAddress());

        holder.btnEdit.setOnClickListener(v -> {
            listener.onEditClick(customer, holder.getAdapterPosition());
        });

        holder.btnDelete.setOnClickListener(v -> {
            listener.onDeleteClick(customer, holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return customerList.size();
    }

    public void updateList(List<Customer> newList) {
        this.customerList = newList;
        notifyDataSetChanged();
    }

    public static class CustomerViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvPhone, tvAddress;
        Button btnEdit, btnDelete;

        public CustomerViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvCustomerName);
            tvPhone = itemView.findViewById(R.id.tvCustomerPhone);
            tvAddress = itemView.findViewById(R.id.tvCustomerAddress);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}