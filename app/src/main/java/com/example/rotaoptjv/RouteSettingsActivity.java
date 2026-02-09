package com.example.rotaoptjv;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.content.SharedPreferences; // Bu satırı ekleyin
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
//ANA MENÜDEKİ ROTA AYARLAMA KISMI
public class RouteSettingsActivity extends AppCompatActivity implements RouteCustomerAdapter.OnCustomerStatusChangeListener {

    private RecyclerView recyclerViewCustomers;
    private RouteCustomerAdapter adapter;
    private DatabaseHelper dbHelper;
    private List<Customer> customerList;
    private Button btnSaveRoute;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_settings);

        // UI bileşenlerini başlat
        recyclerViewCustomers = findViewById(R.id.recycler_view_customers);
        btnSaveRoute = findViewById(R.id.btn_save_route);

        // Veritabanı yardımcısını başlat
        dbHelper = new DatabaseHelper(this);

        // Müşteri listesini al
        customerList = dbHelper.getAllCustomers();

        // RecyclerView'i ayarla
        recyclerViewCustomers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RouteCustomerAdapter(this, customerList, this);
        recyclerViewCustomers.setAdapter(adapter);

        // Kaydet butonuna tıklama olayı ekle
        btnSaveRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // SharedPreferences'ı temizle
                clearOptimizedRoutePrefs(); // Yeni eklenecek metod çağrısı
                Toast.makeText(RouteSettingsActivity.this, "Rota ayarları kaydedildi", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
    // Yeni metod: Optimize edilmiş rota SharedPreferences'ı temizle
//    private void clearOptimizedRoutePrefs() {
//        SharedPreferences prefs = getSharedPreferences(MainActivity.ROUTE_PREFS, MODE_PRIVATE);
//        SharedPreferences.Editor editor = prefs.edit();
//        editor.remove(MainActivity.ROUTE_INDICES_KEY);
//        editor.putBoolean(MainActivity.ROUTE_EXISTS_KEY, false);
//        editor.apply();
//    }
    // Yeni metod: Optimize edilmiş rota SharedPreferences'ı ve visited status'ları temizle
    private void clearOptimizedRoutePrefs() {
        // Mevcut rota preferences'larını temizle
        SharedPreferences prefs = getSharedPreferences(MainActivity.ROUTE_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(MainActivity.ROUTE_INDICES_KEY);
        editor.putBoolean(MainActivity.ROUTE_EXISTS_KEY, false);
        editor.apply();

        // Visited status preferences'larını da temizle
        SharedPreferences visitedPrefs = getSharedPreferences("VisitedStatusPrefs", MODE_PRIVATE);
        visitedPrefs.edit().clear().apply();

        // Route segments preferences'larını da temizle
        SharedPreferences routeSegmentsPrefs = getSharedPreferences("RouteSegmentsPrefs", MODE_PRIVATE);
        routeSegmentsPrefs.edit().clear().apply();

        // Son sıfırlama tarihini de sıfırla (opsiyonel)
        SharedPreferences mainPrefs = getSharedPreferences("AppMainPrefs", MODE_PRIVATE);
        mainPrefs.edit().remove("lastResetDate").apply();
    }
    @Override
    public void onStatusChanged(Customer customer, boolean isInRoute) {
        // Müşteri durumunu veritabanında güncelle
        dbHelper.updateCustomerStatus(customer.getId(), isInRoute ? 1 : 0);
    }
}