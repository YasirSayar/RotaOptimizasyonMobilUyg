package com.example.rotaoptjv;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

//Rota başladıktan sonra sırayla kişi listesi gösterilen kısmın class'ı kişi bazında detaylı gösteren
public class PersonDetailActivity extends AppCompatActivity {
    private static final String TAG = "PersonDetailActivity";
    //private static final String VISITED_PREFS = "VisitedStatusPrefs";//SAAT 12DEN DOLAYI SİLDİK BURAYI

    private int index;
    private int customerId;
    private String name;
    private String address;
    private double latitude;
    private double longitude;
    private boolean visited;

    private TextView tvName;
    private TextView tvAddress;
    private TextView tvCoordinates;
    private Button btnOpenLocation;
    private Button btnMarkCompleted;
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_person_detail);

        Log.d(TAG, "onCreate başlatıldı");

        // Intent'ten verileri al
        getIntentData();

        // UI elemanlarını ayarla
        setupUI();

        // Buton tıklama olaylarını ayarla
        setupButtonListeners();

        // SharedPreferences'tan güncel ziyaret durumunu yükle
        loadVisitedStatusFromPrefs();
    }

    private void getIntentData() {
        Intent intent = getIntent();

        index = intent.getIntExtra("index", -1);
        customerId = intent.getIntExtra("customerId", -1);
        name = intent.getStringExtra("name");
        address = intent.getStringExtra("address");
        latitude = intent.getDoubleExtra("latitude", 0);
        longitude = intent.getDoubleExtra("longitude", 0);
        visited = intent.getBooleanExtra("visited", false);

        Log.d(TAG, "Intent verileri alındı - CustomerID: " + customerId + ", Name: " + name);

        // Gerekli verilerin kontrolü
        if (customerId == -1 || name == null || address == null) {
            Toast.makeText(this, "Gerekli veriler bulunamadı.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupUI() {
        tvName = findViewById(R.id.tv_name);
        tvAddress = findViewById(R.id.tv_address);
        tvCoordinates = findViewById(R.id.tv_coordinates);
        btnOpenLocation = findViewById(R.id.btn_open_location);
        btnMarkCompleted = findViewById(R.id.btn_mark_completed);
        btnBack = findViewById(R.id.btn_back);

        // Verileri ekranda göster
        tvName.setText(name);
        tvAddress.setText(address);
        tvCoordinates.setText(String.format("Konum: %.6f, %.6f", latitude, longitude));

        // Ziyaret durumuna göre buton metnini ayarla
        updateCompletedButtonText();
    }

    private void setupButtonListeners() {
        // Konum açma butonu
        btnOpenLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openLocationInGoogleMaps();
            }
        });

        // Tamamlandı butonu
        btnMarkCompleted.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleCompletedStatus();
            }
        });

        // Geri butonu
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    // SharedPreferences'tan güncel ziyaret durumunu yükle
    private void loadVisitedStatusFromPrefs() {
        //SharedPreferences preferences = getSharedPreferences(VISITED_PREFS, MODE_PRIVATE); //12'den sonra sıfırlamak için değiştirdik
        SharedPreferences preferences = PreferencesManager.getVisitedStatusPrefs(this);//12den sonra sıfırlama
        String key = "waypoint_" + customerId;
        visited = preferences.getBoolean(key, false);

        Log.d(TAG, "Ziyaret durumu yüklendi: " + key + " = " + visited);

        // UI'ı güncelle
        updateCompletedButtonText();
    }

    // Konumu Google Maps'te aç
    private void openLocationInGoogleMaps() {
        String uri = String.format("geo:%f,%f?q=%f,%f(%s)",
                latitude, longitude, latitude, longitude, Uri.encode(name));
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage("com.google.android.apps.maps");

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
            Log.d(TAG, "Google Maps açıldı");
        } else {
            // Google Maps yüklü değilse genel map uri kullan
            intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://maps.google.com/maps?q=loc:" + latitude + "," + longitude));
            startActivity(intent);
            Log.d(TAG, "Web tabanlı harita açıldı");
        }
    }

    // Tamamlandı durumunu değiştir
    private void toggleCompletedStatus() {
        visited = !visited;

        // SharedPreferences'a kaydet
        //SharedPreferences preferences = getSharedPreferences(VISITED_PREFS, MODE_PRIVATE);//12'den sonra sıfırlamak için
        SharedPreferences preferences = PreferencesManager.getVisitedStatusPrefs(this);
        SharedPreferences.Editor editor = preferences.edit();
        String key = "waypoint_" + customerId;
        editor.putBoolean(key, visited);
        boolean success = editor.commit(); // apply() yerine commit() kullan

        Log.d(TAG, "Ziyaret durumu değiştirildi: " + key + " = " + visited + " (Başarılı: " + success + ")");

        // Buton metnini güncelle
        updateCompletedButtonText();

        // Bildirim göster
        String message = visited ?
                name + " ziyaret edildi olarak işaretlendi." :
                name + " ziyaret edilmedi olarak işaretlendi.";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Tamamlandı butonunun metnini güncelle
    private void updateCompletedButtonText() {
        if (visited) {
            btnMarkCompleted.setText("Ziyaret Edildi (İptal Et)");
            btnMarkCompleted.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            btnMarkCompleted.setText("Ziyaret Edildi Olarak İşaretle");
            btnMarkCompleted.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume çağrıldı");

        // Activity'ye geri dönüldüğünde güncel durumu yükle
        loadVisitedStatusFromPrefs();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause çağrıldı");
    }
}