package com.example.rotaoptjv;

import android.Manifest;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Toast;

import android.location.Address;
import android.text.TextUtils;
import android.app.ProgressDialog;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomerEditActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "CustomerEditActivity";
    private static final int PICK_CONTACT_REQUEST = 1;
    private static final int PERMISSION_REQUEST_READ_CONTACTS = 100;
    private static final int PERMISSION_REQUEST_LOCATION = 101;

    private EditText etName, etPhone, etAddress, etLatitude, etLongitude;
    private Button btnPickContact, btnPickLocation, btnSave;
    private DatabaseHelper dbHelper;
    private Customer editCustomer;
    private GoogleMap mMap;
    private LatLng selectedLocation;
    private LatLng originalLocation; // Müşteri konumunun orijinal değeri
    private LatLng pendingLocationUpdate; // Harita hazır olmadan önce gelen konum güncellemeleri için

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_edit);

        // Log intent details for debugging
        logIntentDetails(getIntent());

        // Initialize database helper
        dbHelper = new DatabaseHelper(this);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Initialize views
        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etAddress = findViewById(R.id.etAddress);
        etLatitude = findViewById(R.id.etLatitude);
        etLongitude = findViewById(R.id.etLongitude);
        btnPickContact = findViewById(R.id.btnPickContact);
        btnPickLocation = findViewById(R.id.btnPickLocation);
        btnSave = findViewById(R.id.btnSave);

        // Initialize map
        CustomMapFragment mapFragment = (CustomMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        mapFragment.getMapAsync(this);

        // ScrollView'ı bulun
        final ScrollView scrollView = findViewById(R.id.scrollView);

        // Map touch listener'ı ayarlayın
        mapFragment.setListener(new CustomMapFragment.OnTouchListener() {
            @Override
            public void onTouch() {
                // Haritaya dokunulduğunda ScrollView'ı devre dışı bırak
                scrollView.requestDisallowInterceptTouchEvent(true);
            }

            @Override
            public void onRelease() {
                // Dokunma bırakıldığında ScrollView'ı tekrar etkinleştir
                scrollView.requestDisallowInterceptTouchEvent(false);
            }
        });

        // Paylaşılan konumları işle
        handleSharedLocation();

        // Check if editing existing customer
        if (getIntent().hasExtra("customer")) {
            editCustomer = (Customer) getIntent().getSerializableExtra("customer");
            fillCustomerData();
            getSupportActionBar().setTitle("Müşteri Düzenle");

            // Orijinal konumu kaydet (konum değişikliğini tespit etmek için)
            if (editCustomer != null) {
                originalLocation = new LatLng(editCustomer.getLatitude(), editCustomer.getLongitude());
            }
        } else {
            getSupportActionBar().setTitle("Yeni Müşteri");
            originalLocation = null;
        }

        // Set click listeners
        btnPickContact.setOnClickListener(v -> pickContact());
        btnPickLocation.setOnClickListener(v -> {
            // Update map with current coordinates if available
            try {
                double lat = Double.parseDouble(etLatitude.getText().toString());
                double lng = Double.parseDouble(etLongitude.getText().toString());
                updateMapLocation(new LatLng(lat, lng));
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Lütfen geçerli koordinat giriniz", Toast.LENGTH_SHORT).show();
            }
        });

        btnSave.setOnClickListener(v -> saveCustomer());
    }

    /**
     * Intent detaylarını loglamak için yardımcı metot
     */
    private void logIntentDetails(Intent intent) {
        if (intent == null) {
            Log.d(TAG, "Intent is null");
            return;
        }

        Log.d(TAG, "Intent Action: " + intent.getAction());

        String dataString = intent.getDataString();
        Log.d(TAG, "Intent Data URI: " + dataString);

        Uri data = intent.getData();
        if (data != null) {
            Log.d(TAG, "Scheme: " + data.getScheme());
            Log.d(TAG, "Host: " + data.getHost());
            Log.d(TAG, "Path: " + data.getPath());
            Log.d(TAG, "Query: " + data.getQuery());
        }

        String type = intent.getType();
        Log.d(TAG, "Intent Type: " + type);

        if (Intent.ACTION_SEND.equals(intent.getAction()) && type != null) {
            if ("text/plain".equals(type)) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                Log.d(TAG, "Shared text: " + sharedText);
                // Text içinden konum bilgisini çıkarabiliriz
                extractLocationFromText(sharedText);
            }
        }
    }

    /**
     * WhatsApp veya Google Maps'ten paylaşılan konum bilgisini işler
     */
    private void handleSharedLocation() {
        Intent intent = getIntent();
        Uri data = intent.getData();

        // 1. Intent URI'den konum bilgisi alma
        if (data != null) {
            processLocationFromUri(data);
        }

        // 2. ACTION_SEND intent'inden text/plain paylaşımları işleme
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null) {
                extractLocationFromText(sharedText);
            }
        }

        // 3. Eğer panoda konum linki varsa onu da işlemeyi deneyelim
        tryExtractLocationFromClipboard();
    }

    /**
     * URI'den konum bilgisini işler
     */
    private void processLocationFromUri(Uri data) {
        String scheme = data.getScheme();
        String url = data.toString();
        double latitude = 0;
        double longitude = 0;
        boolean coordinatesFound = false;

        try {
            // geo: URI formatındaki konumları işle (geo:38.6823,29.4082)
            if ("geo".equals(scheme)) {
                String ssp = data.getSchemeSpecificPart();
                if (ssp != null && ssp.contains(",")) {
                    String[] parts = ssp.split(",");
                    if (parts.length >= 2) {
                        // Parametreler (zoom vs) varsa onları kaldır
                        String latStr = parts[0];
                        String lngStr = parts[1];

                        if (lngStr.contains("?")) {
                            lngStr = lngStr.substring(0, lngStr.indexOf("?"));
                        }

                        latitude = Double.parseDouble(latStr);
                        longitude = Double.parseDouble(lngStr);
                        coordinatesFound = true;
                    }
                }
            }
            // Google Maps URL formatındaki konumları işle
            else if ("https".equals(scheme) || "http".equals(scheme)) {
                // Format 1: https://www.google.com/maps?q=38.6823,29.4082
                if (url.contains("maps?q=")) {
                    String coordsPart = url.substring(url.indexOf("q=") + 2);
                    // Sonraki parametreyi kaldır (varsa)
                    if (coordsPart.contains("&")) {
                        coordsPart = coordsPart.substring(0, coordsPart.indexOf("&"));
                    }

                    String[] latLng = coordsPart.split(",");
                    if (latLng.length == 2) {
                        latitude = Double.parseDouble(latLng[0]);
                        longitude = Double.parseDouble(latLng[1]);
                        coordinatesFound = true;
                    }
                }
                // Format 2: https://www.google.com/maps/place/.../@38.6823,29.4082,15z/...
                else if (url.contains("/@")) {
                    String afterAt = url.substring(url.indexOf("/@") + 2);
                    String coordsPart = afterAt.substring(0, afterAt.indexOf("/"));
                    String[] parts = coordsPart.split(",");
                    if (parts.length >= 2) {
                        latitude = Double.parseDouble(parts[0]);
                        longitude = Double.parseDouble(parts[1]);
                        coordinatesFound = true;
                    }
                }
                // Format 3: https://goo.gl/maps/xyz
                else if (url.contains("goo.gl/maps/") || url.contains("maps.app.goo.gl")) {
                    // Bu tür kısa linkleri çözümlemek için ağ istekleri gerekebilir
                    // Şimdilik bu linkler için kullanıcıya uyarı gösterelim
                    Toast.makeText(this, "Kısa Google Maps linki tespit edildi, lütfen doğrudan konumu paylaşın",
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }

            // Koordinatlar bulunduysa haritayı güncelle ve alanları doldur
            if (coordinatesFound) {
                updateLocationFields(latitude, longitude);
            }
        } catch (Exception e) {
            Log.e(TAG, "Konum işlenirken hata oluştu", e);
            Toast.makeText(this, "Konum bilgisi işlenemedi", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Metin içinden konum bilgisini çıkarır
     */
    private void extractLocationFromText(String text) {
        if (text == null) return;

        Log.d(TAG, "Extracting location from: " + text);

        try {
            // Google Maps linki için kontrol
            if (text.contains("maps.google.com") || text.contains("goo.gl/maps") ||
                    text.contains("maps.app.goo.gl") || text.contains("google.com/maps")) {

                // URL'yi metin içinden çıkar
                Pattern pattern = Pattern.compile("https?://[\\w.-]+/[\\w./-]+");
                Matcher matcher = pattern.matcher(text);

                if (matcher.find()) {
                    String url = matcher.group();
                    Log.d(TAG, "Found URL: " + url);
                    Uri uri = Uri.parse(url);
                    processLocationFromUri(uri);
                    return;
                }
            }

            // Düz konum koordinatları arıyoruz (sayı, virgül, sayı formatı)
            Pattern coordPattern = Pattern.compile("(\\d+\\.\\d+),\\s*(\\d+\\.\\d+)");
            Matcher coordMatcher = coordPattern.matcher(text);

            if (coordMatcher.find()) {
                try {
                    double latitude = Double.parseDouble(coordMatcher.group(1));
                    double longitude = Double.parseDouble(coordMatcher.group(2));
                    Log.d(TAG, "Found coordinates: " + latitude + ", " + longitude);
                    updateLocationFields(latitude, longitude);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Coordinate parsing error", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting location from text", e);
        }
    }

    /**
     * Panodan konum bilgisini çıkarmayı dener
     */
    private void tryExtractLocationFromClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    extractLocationFromText(text.toString());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error accessing clipboard", e);
        }
    }

    /**
     * Bulunan konum bilgisini UI alanlarına doldurur
     */
    private void updateLocationFields(double latitude, double longitude) {
        etLatitude.setText(String.format(Locale.US, "%.6f", latitude));
        etLongitude.setText(String.format(Locale.US, "%.6f", longitude));

        // Konum bilgisini kaydet
        LatLng newLocation = new LatLng(latitude, longitude);
        selectedLocation = newLocation;

        // Adresi Geocoder ile bul ve otomatik doldur
        findAddressFromLocation(latitude, longitude);

        // Harita hazırsa hemen güncelle, değilse bekletilecek konum olarak kaydet
        if (mMap != null) {
            updateMapLocation(newLocation);
        } else {
            pendingLocationUpdate = newLocation;
            Log.d(TAG, "Harita henüz hazır değil, konum güncelleme bekletiliyor: " + latitude + ", " + longitude);
        }

        Toast.makeText(this, "Konum başarıyla alındı", Toast.LENGTH_SHORT).show();
    }

    /**
     * Koordinatlardan adres bilgisini çeker ve adres alanını doldurur
     */
    private void findAddressFromLocation(double latitude, double longitude) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder sb = new StringBuilder();

                // Sokak adı
                if (address.getThoroughfare() != null) {
                    sb.append(address.getThoroughfare()).append(", ");
                }

                // Mahalle/Semt
                if (address.getSubLocality() != null) {
                    sb.append(address.getSubLocality()).append(", ");
                }

                // İlçe
                if (address.getSubAdminArea() != null) {
                    sb.append(address.getSubAdminArea()).append(", ");
                }

                // Şehir
                if (address.getAdminArea() != null) {
                    sb.append(address.getAdminArea());
                }

                etAddress.setText(sb.toString());
            }
        } catch (IOException e) {
            Log.e(TAG, "Geocoder hatası", e);
        } catch (Exception e) {
            Log.e(TAG, "Adres çözümlenirken hata oluştu", e);
        }
    }

    private void fillCustomerData() {
        if (editCustomer != null) {
            etName.setText(editCustomer.getName());
            etPhone.setText(editCustomer.getPhoneNumber());
            etAddress.setText(editCustomer.getAddress());
            etLatitude.setText(String.valueOf(editCustomer.getLatitude()));
            etLongitude.setText(String.valueOf(editCustomer.getLongitude()));
            selectedLocation = new LatLng(editCustomer.getLatitude(), editCustomer.getLongitude());

            // Müşteri verileri doldurulurken de harita güncellemesi bekletilebilir
            if (mMap != null) {
                updateMapLocation(selectedLocation);
            } else {
                pendingLocationUpdate = selectedLocation;
            }
        }
    }

    private void pickContact() {
        // Check for permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    PERMISSION_REQUEST_READ_CONTACTS);
        } else {
            // Launch the contact picker
            Intent pickContactIntent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
            startActivityForResult(pickContactIntent, PICK_CONTACT_REQUEST);
        }
    }

    private void saveCustomer() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String address = etAddress.getText().toString().trim();

        // Validate input
        if (name.isEmpty() || phone.isEmpty() || address.isEmpty()) {
            Toast.makeText(this, "Lütfen tüm alanları doldurun", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate location
        double latitude, longitude;
        try {
            latitude = Double.parseDouble(etLatitude.getText().toString().trim());
            longitude = Double.parseDouble(etLongitude.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Lütfen geçerli koordinat bilgisi girin", Toast.LENGTH_SHORT).show();
            return;
        }

        // Konumun değişip değişmediğini kontrol edelim
        boolean locationChanged = false;

        if (editCustomer != null) {
            // Düzenleme modunda, konum değişikliğini kontrol et
            locationChanged = (originalLocation == null ||
                    Math.abs(originalLocation.latitude - latitude) > 0.000001 ||
                    Math.abs(originalLocation.longitude - longitude) > 0.000001);
        }

        // Müşteri ekleme/güncelleme işlemleri için ProgressDialog göster
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Müşteri kaydediliyor...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // AsyncTask kullanarak mesafe hesaplamalarını arka planda yap
        new CustomerSaveTask(name, phone, address, latitude, longitude, locationChanged, progressDialog).execute();
    }

    /**
     * Müşteri ekleme/güncelleme ve mesafe hesaplamalarını arka planda yapan AsyncTask
     */
    private class CustomerSaveTask extends AsyncTask<Void, Void, Boolean> {
        private String name;
        private String phone;
        private String address;
        private double latitude;
        private double longitude;
        private boolean locationChanged;
        private ProgressDialog progressDialog;
        private long customerId;
        private String resultMessage;

        public CustomerSaveTask(String name, String phone, String address,
                                double latitude, double longitude, boolean locationChanged,
                                ProgressDialog progressDialog) {
            this.name = name;
            this.phone = phone;
            this.address = address;
            this.latitude = latitude;
            this.longitude = longitude;
            this.locationChanged = locationChanged;
            this.progressDialog = progressDialog;
            this.customerId = -1;
            this.resultMessage = "";
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                if (editCustomer == null) {
                    // Yeni müşteri ekleme
                    Customer newCustomer = new Customer();
                    newCustomer.setName(name);
                    newCustomer.setPhoneNumber(phone);
                    newCustomer.setAddress(address);
                    newCustomer.setLatitude(latitude);
                    newCustomer.setLongitude(longitude);
                    newCustomer.setStatus(0); // Varsayılan olarak rotaya dahil değil

                    // Müşteri ekle ve ID'sini al
                    customerId = dbHelper.addCustomer(newCustomer);

                    if (customerId > 0) {
                        // Yeni müşteri için mesafeleri hesapla
                        dbHelper.updateCustomerDistances((int) customerId, latitude, longitude);
                        resultMessage = "Müşteri başarıyla eklendi";
                        return true;
                    } else {
                        resultMessage = "Müşteri eklenirken hata oluştu";
                        return false;
                    }
                } else {
                    // Mevcut müşteriyi güncelleme
                    editCustomer.setName(name);
                    editCustomer.setPhoneNumber(phone);
                    editCustomer.setAddress(address);
                    editCustomer.setLatitude(latitude);
                    editCustomer.setLongitude(longitude);
                    // Status değerini koruyalım (rotaya eklenmiş olabilir)

                    int result = dbHelper.updateCustomer(editCustomer);

                    if (result > 0) {
                        // Konum değiştiyse mesafeleri güncelle
                        if (locationChanged) {
                            dbHelper.updateCustomerDistances(editCustomer.getId(), latitude, longitude);
                            resultMessage = "Müşteri ve mesafe bilgileri güncellendi";
                        } else {
                            resultMessage = "Müşteri bilgileri güncellendi";
                        }
                        customerId = editCustomer.getId();
                        return true;
                    } else {
                        resultMessage = "Müşteri güncellenirken hata oluştu";
                        return false;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Müşteri kaydetme hatası: " + e.getMessage());
                resultMessage = "İşlem sırasında bir hata oluştu: " + e.getMessage();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            try {
                progressDialog.dismiss();
            } catch (Exception e) {
                // Dialog kapanma hatalarını görmezden gel
            }

            Toast.makeText(CustomerEditActivity.this, resultMessage, Toast.LENGTH_SHORT).show();

            if (success) {
                // İşlem başarılıysa aktiviteyi sonlandır
                finish();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_READ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickContact();
            } else {
                Toast.makeText(this, "Kişilere erişim izni gerekli", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK && data != null) {
            // Handle the contact selection
            Uri contactUri = data.getData();

            // Query for contact data
            Cursor cursor = getContentResolver().query(contactUri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                // Sütun indeksi kontrolü eklendi
                String contactName = nameIndex != -1 ? cursor.getString(nameIndex) : "";

                // Get contact ID
                int idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID);
                if (idIndex != -1) {
                    String contactId = cursor.getString(idIndex);

                    // Query for phone numbers
                    Cursor phoneCursor = getContentResolver().query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{contactId},
                            null);

                    if (phoneCursor != null && phoneCursor.moveToFirst()) {
                        int phoneIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                        // Sütun indeksi kontrolü eklendi
                        String phoneNumber = phoneIndex != -1 ? phoneCursor.getString(phoneIndex) : "";

                        // Set to UI if we have valid data
                        if (!contactName.isEmpty()) {
                            etName.setText(contactName);
                        }
                        if (!phoneNumber.isEmpty()) {
                            etPhone.setText(phoneNumber);
                        }

                        phoneCursor.close();
                    }
                }
                cursor.close();
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        Log.d(TAG, "Harita hazır!");

        // Bekleyen konum güncellemesi varsa uygula
        if (pendingLocationUpdate != null) {
            Log.d(TAG, "Bekleyen konum güncelleniyor: " + pendingLocationUpdate.latitude + ", " + pendingLocationUpdate.longitude);
            updateMapLocation(pendingLocationUpdate);
            pendingLocationUpdate = null; // Güncellendikten sonra temizle
        }
        // Seçilen konum varsa onu göster
        else if (selectedLocation != null) {
            updateMapLocation(selectedLocation);
        }
        // Hiç konum yoksa varsayılan konumu göster
        else {
            // Default to Amasya coordinates if none provided
            LatLng defaultLocation = new LatLng(40.6539, 35.8333);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 13));
        }

        // Set up map click listener
        mMap.setOnMapClickListener(latLng -> {
            updateMapLocation(latLng);
            etLatitude.setText(String.format(Locale.US, "%.6f", latLng.latitude));
            etLongitude.setText(String.format(Locale.US, "%.6f", latLng.longitude));

            // Haritadan seçilen konuma göre adresi güncelle
            findAddressFromLocation(latLng.latitude, latLng.longitude);
        });
    }

    private void updateMapLocation(LatLng location) {
        if (mMap != null && location != null) {
            Log.d(TAG, "Harita konumu güncelleniyor: " + location.latitude + ", " + location.longitude);
            mMap.clear();
            mMap.addMarker(new MarkerOptions()
                    .position(location)
                    .title("Seçilen Konum")
                    .snippet("Lat: " + String.format(Locale.US, "%.6f", location.latitude) +
                            ", Lng: " + String.format(Locale.US, "%.6f", location.longitude)));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
            selectedLocation = location;
        } else {
            Log.w(TAG, "Harita güncelleme başarısız - mMap: " + (mMap != null) + ", location: " + (location != null));
        }
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