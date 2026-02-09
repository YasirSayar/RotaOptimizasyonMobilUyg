package com.example.rotaoptjv;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;

public class PersonListActivity extends AppCompatActivity {
    private static final String TAG = "PersonListActivity";
    //private static final String VISITED_PREFS = "VisitedStatusPrefs";  //saat 12den sonra kaldırmak için

    private RecyclerView recyclerView;
    private PersonAdapter adapter;
    private List<PersonItem> personItems = new ArrayList<>();

    private ArrayList<Double> latitudes;
    private ArrayList<Double> longitudes;
    private ArrayList<String> names;
    private ArrayList<String> addresses;
    private ArrayList<Boolean> visitedStatus;
    private ArrayList<Integer> customerIds;
    private ArrayList<Integer> optimizedIndices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_person_list);

        Log.d(TAG, "onCreate başlatıldı");

        // Intent'ten verileri al
        getIntentData();

        // Kişileri hazırla
        preparePersonItems();

        // RecyclerView'ı ayarla
        setupRecyclerView();

        // Geri butonu
        setupBackButton();
    }

    private void getIntentData() {
        Intent intent = getIntent();

        if (!intent.hasExtra("names") || !intent.hasExtra("addresses")) {
            Toast.makeText(this, "Gerekli veriler bulunamadı.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        latitudes = (ArrayList<Double>) intent.getSerializableExtra("latitudes");
        longitudes = (ArrayList<Double>) intent.getSerializableExtra("longitudes");
        names = intent.getStringArrayListExtra("names");
        addresses = intent.getStringArrayListExtra("addresses");
        visitedStatus = (ArrayList<Boolean>) intent.getSerializableExtra("visitedStatus");
        customerIds = (ArrayList<Integer>) intent.getSerializableExtra("customerIds");

        if (intent.hasExtra("optimizedIndices")) {
            optimizedIndices = intent.getIntegerArrayListExtra("optimizedIndices");
        }

        // Null kontrolü
        if (latitudes == null) latitudes = new ArrayList<>();
        if (longitudes == null) longitudes = new ArrayList<>();
        if (names == null) names = new ArrayList<>();
        if (addresses == null) addresses = new ArrayList<>();
        if (visitedStatus == null) visitedStatus = new ArrayList<>();
        if (customerIds == null) customerIds = new ArrayList<>();

        Log.d(TAG, "Veriler Intent'ten alındı. Customer sayısı: " + customerIds.size());
    }

    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PersonAdapter(personItems);
        recyclerView.setAdapter(adapter);
    }

    private void setupBackButton() {
        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            // Geri gitmeden önce verileri kaydet
            saveAllVisitedStatus();
            finish();
        });
    }

    private void preparePersonItems() {
        personItems.clear();

        if (names.isEmpty() || addresses.isEmpty() || latitudes.isEmpty() ||
                longitudes.isEmpty() || customerIds.isEmpty()) {
            Toast.makeText(this, "Gösterilecek kişi verisi bulunamadı.", Toast.LENGTH_SHORT).show();
            return;
        }

        int size = Math.min(names.size(),
                Math.min(addresses.size(),
                        Math.min(latitudes.size(),
                                Math.min(longitudes.size(), customerIds.size()))));

        // visitedStatus listesi daha küçükse genişlet
        while (visitedStatus.size() < size) {
            visitedStatus.add(false);
        }

        Log.d(TAG, "PersonItem'lar hazırlanıyor. Toplam: " + size);

        if (optimizedIndices != null && !optimizedIndices.isEmpty()) {
            // Optimize edilmiş sırayla
            for (int i = 0; i < optimizedIndices.size(); i++) {
                int index = optimizedIndices.get(i);

                if (index < 0 || index >= size) {
                    continue;
                }

                // Başlangıç/bitiş noktasının tekrarını önle
                if (index == optimizedIndices.get(0) && i > 0) {
                    continue;
                }

                int customerId = customerIds.get(index);

                PersonItem item = new PersonItem(
                        index,
                        customerId,
                        names.get(index),
                        addresses.get(index),
                        new GeoPoint(latitudes.get(index), longitudes.get(index)),
                        false // SharedPrefs'tan yüklenecek
                );

                personItems.add(item);
                Log.d(TAG, "PersonItem eklendi: " + item.getName() + " (CustomerID: " + customerId + ")");
            }
        } else {
            // Normal sırayla
            for (int i = 0; i < size; i++) {
                int customerId = customerIds.get(i);

                PersonItem item = new PersonItem(
                        i,
                        customerId,
                        names.get(i),
                        addresses.get(i),
                        new GeoPoint(latitudes.get(i), longitudes.get(i)),
                        false // SharedPrefs'tan yüklenecek
                );

                personItems.add(item);
                Log.d(TAG, "PersonItem eklendi: " + item.getName() + " (CustomerID: " + customerId + ")");
            }
        }

        // PersonItem'lar hazırlandıktan sonra SharedPrefs'tan ziyaret durumlarını yükle
        loadVisitedStatusFromPrefs();
    }

    private void loadVisitedStatusFromPrefs() {
        //SharedPreferences prefs = getSharedPreferences(VISITED_PREFS, MODE_PRIVATE);//12'den sonra sıfırlamak için siliyoruz
        SharedPreferences prefs = PreferencesManager.getVisitedStatusPrefs(this);
        Log.d(TAG, "SharedPreferences'tan ziyaret durumları yükleniyor...");

        for (PersonItem item : personItems) {
            String key = "waypoint_" + item.getCustomerId();
            boolean visited = prefs.getBoolean(key, false);
            item.setVisited(visited);

            Log.d(TAG, "Customer " + item.getCustomerId() + " (" + item.getName() + ") ziyaret durumu: " + visited);
        }

        Log.d(TAG, "Toplam " + personItems.size() + " kişinin ziyaret durumu yüklendi");
    }

    private void saveAllVisitedStatus() {
        //SharedPreferences prefs = getSharedPreferences(VISITED_PREFS, MODE_PRIVATE);//12'den sonra sıfırlamak için
        //SharedPreferences.Editor editor = prefs.edit();
        SharedPreferences prefs = PreferencesManager.getVisitedStatusPrefs(this);
        SharedPreferences.Editor editor = prefs.edit();
        Log.d(TAG, "Tüm ziyaret durumları kaydediliyor...");

        for (PersonItem item : personItems) {
            String key = "waypoint_" + item.getCustomerId();
            editor.putBoolean(key, item.isVisited());

            Log.d(TAG, "Kaydediliyor: " + key + " = " + item.isVisited() + " (" + item.getName() + ")");
        }

        boolean success = editor.commit(); // apply() yerine commit() kullan
        Log.d(TAG, "SharedPreferences kaydetme " + (success ? "başarılı" : "başarısız"));
    }

    public void updateVisitedStatus(int customerId, boolean visited) {
        //SharedPreferences prefs = getSharedPreferences(VISITED_PREFS, MODE_PRIVATE);//12'den sonra sıfırlamak için
        SharedPreferences prefs = PreferencesManager.getVisitedStatusPrefs(this);
        SharedPreferences.Editor editor = prefs.edit();

        String key = "waypoint_" + customerId;
        editor.putBoolean(key, visited);
        boolean success = editor.commit(); // apply() yerine commit() kullan

        Log.d(TAG, "Ziyaret durumu güncellendi: " + key + " = " + visited + " (Başarılı: " + success + ")");

        // Listedeki item'ı da güncelle
        for (PersonItem item : personItems) {
            if (item.getCustomerId() == customerId) {
                item.setVisited(visited);
                break;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume çağrıldı");

        // SharedPrefs'tan tekrar yükle ve adapter'ı güncelle
        loadVisitedStatusFromPrefs();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause çağrıldı");

        // Activity pause olduğunda kaydet
        saveAllVisitedStatus();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop çağrıldı");

        // Activity stop olduğunda da kaydet
        saveAllVisitedStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy çağrıldı");

        // Activity destroy olduğunda da kaydet
        saveAllVisitedStatus();
    }

    // PersonItem sınıfı
    public static class PersonItem {
        private int index;
        private int customerId;
        private String name;
        private String address;
        private GeoPoint location;
        private boolean visited;

        public PersonItem(int index, int customerId, String name, String address, GeoPoint location, boolean visited) {
            this.index = index;
            this.customerId = customerId;
            this.name = name;
            this.address = address;
            this.location = location;
            this.visited = visited;
        }

        public int getIndex() { return index; }
        public int getCustomerId() { return customerId; }
        public String getName() { return name; }
        public String getAddress() { return address; }
        public GeoPoint getLocation() { return location; }
        public boolean isVisited() { return visited; }
        public void setVisited(boolean visited) { this.visited = visited; }
    }

    // PersonAdapter sınıfı
    public class PersonAdapter extends RecyclerView.Adapter<PersonAdapter.PersonViewHolder> {
        private List<PersonItem> personList;

        public PersonAdapter(List<PersonItem> personList) {
            this.personList = personList;
        }

        @NonNull
        @Override
        public PersonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_person, parent, false);
            return new PersonViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PersonViewHolder holder, int position) {
            PersonItem person = personList.get(position);

            // CheckBox listener'ını geçici olarak kaldır
            holder.checkBoxVisited.setOnCheckedChangeListener(null);

            // CheckBox durumunu ayarla
            holder.checkBoxVisited.setChecked(person.isVisited());
            holder.btnPersonName.setText(person.getName());

            // Yeni listener ekle
            holder.checkBoxVisited.setOnCheckedChangeListener((buttonView, isChecked) -> {
                person.setVisited(isChecked);
                updateVisitedStatus(person.getCustomerId(), isChecked);

                String message = isChecked ?
                        person.getName() + " ziyaret edildi olarak işaretlendi." :
                        person.getName() + " ziyaret edilmedi olarak işaretlendi.";
                Toast.makeText(PersonListActivity.this, message, Toast.LENGTH_SHORT).show();
            });

            // Kişi adı butonuna tıklama olayı
            holder.btnPersonName.setOnClickListener(v -> {
                Intent intent = new Intent(PersonListActivity.this, PersonDetailActivity.class);
                intent.putExtra("index", person.getIndex());
                intent.putExtra("customerId", person.getCustomerId());
                intent.putExtra("name", person.getName());
                intent.putExtra("address", person.getAddress());
                intent.putExtra("latitude", person.getLocation().getLatitude());
                intent.putExtra("longitude", person.getLocation().getLongitude());
                intent.putExtra("visited", person.isVisited());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return personList.size();
        }

        public class PersonViewHolder extends RecyclerView.ViewHolder {
            CheckBox checkBoxVisited;
            Button btnPersonName;

            public PersonViewHolder(@NonNull View itemView) {
                super(itemView);
                checkBoxVisited = itemView.findViewById(R.id.checkbox_visited);
                btnPersonName = itemView.findViewById(R.id.btn_person_name);
            }
        }
    }
}