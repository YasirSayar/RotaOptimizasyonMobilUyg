package com.example.rotaoptjv;

import static android.content.ContentValues.TAG;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

//Müşteriler , rota ayarları , başla yazan kısım
public class GirisEkraniActivity extends AppCompatActivity {
    private Button btnCustomers, btnRouteSettings, btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_giris_ekrani);

        // Initialize UI components
        btnCustomers = findViewById(R.id.btnCustomers);
        btnRouteSettings = findViewById(R.id.btnRouteSettings);
        btnStart = findViewById(R.id.btnStart);

        // Set click listeners
        btnCustomers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(GirisEkraniActivity.this, CustomerListActivity.class);
                startActivity(intent);
            }
        });

        btnRouteSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Rota ayarları aktivitesini başlat
                Intent intent = new Intent(GirisEkraniActivity.this, RouteSettingsActivity.class);
                startActivity(intent);
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Intent for starting route navigation (to be implemented)
                // Intent intent = new Intent(MainActivity.this, NavigationActivity.class);
                // startActivity(intent);
                Intent intent = new Intent(GirisEkraniActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });
    }
}