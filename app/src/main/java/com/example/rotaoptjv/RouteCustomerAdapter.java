package com.example.rotaoptjv;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

//Rota ayarlarına müşterilerle ilgili adapter recyclerView yapısı yani
public class RouteCustomerAdapter extends RecyclerView.Adapter<RouteCustomerAdapter.ViewHolder> {

    private final List<Customer> customerList;
    private final Context context;
    private final OnCustomerStatusChangeListener statusChangeListener;

    public interface OnCustomerStatusChangeListener {
        void onStatusChanged(Customer customer, boolean isInRoute);
    }

    public RouteCustomerAdapter(Context context, List<Customer> customerList, OnCustomerStatusChangeListener listener) {
        this.context = context;
        this.customerList = customerList;
        this.statusChangeListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_route_customer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Customer customer = customerList.get(position);

        holder.txtName.setText(customer.getName());
        holder.txtPhone.setText(customer.getPhoneNumber());
        holder.txtAddress.setText(customer.getAddress());

        // CheckBox durumunu ayarla
        holder.checkRoute.setChecked(customer.isInRoute());

        // CheckBox tıklama olayını dinle
        holder.checkRoute.setOnClickListener(v -> {
            boolean isChecked = holder.checkRoute.isChecked();
            customer.setInRoute(isChecked);
            statusChangeListener.onStatusChanged(customer, isChecked);
        });
    }

    @Override
    public int getItemCount() {
        return customerList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView txtName;
        public TextView txtPhone;
        public TextView txtAddress;
        public CheckBox checkRoute;

        public ViewHolder(View view) {
            super(view);
            txtName = view.findViewById(R.id.txt_customer_name);
            txtPhone = view.findViewById(R.id.txt_customer_phone);
            txtAddress = view.findViewById(R.id.txt_customer_address);
            checkRoute = view.findViewById(R.id.check_route);
        }
    }
}