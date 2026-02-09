package com.example.rotaoptjv;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

//Başla dedikten sonra gelen ana Sınıf
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "MainActivity";
    // Google Maps'in desteklediği maksimum durak sayısı
    private static final int MAX_WAYPOINTS_FOR_GOOGLE_MAPS = 11; // 10 ara nokta + başlangıç ve bitiş = 12 nokta
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    // SharedPreferences için sabitler
    public static final String ROUTE_PREFS = "RoutePrefs";
    public static final String ROUTE_INDICES_KEY = "optimizedRouteIndices";
    public static final String ROUTE_EXISTS_KEY = "routeExists";

    public static class Waypoint {
        private LatLng location;
        private String name;
        private String address;
        private boolean visited;
        private int customerId;

        public Waypoint(LatLng location, String name, String address, int customerId) {
            this.location = location;
            this.name = name;
            this.address = address;
            this.visited = false;
            this.customerId = customerId;
        }

        public LatLng getLocation() {
            return location;
        }

        public String getName() {
            return name;
        }

        public String getAddress() {
            return address;
        }

        public boolean isVisited() {
            return visited;
        }

        public void setVisited(boolean visited) {
            this.visited = visited;
        }

        public int getCustomerId() {
            return customerId;
        }
    }

    private GoogleMap mMap;
    private List<Waypoint> waypoints = new ArrayList<>();
    private Button btnOptimizeRoute;
    private Button btnOpenInGoogleMaps;
    private Button btnPersonList;
    private List<com.google.android.gms.maps.model.Marker> markers = new ArrayList<>();
    private List<Integer> optimizedRouteIndices;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Veritabanı helper'ını başlat
        dbHelper = new DatabaseHelper(this);

        // Harita ve butonları ayarla
        setupMapFragment();
        setupButtons();

        // Konuma erişim izni iste
        checkLocationPermission();
        // onCreate metodunun sonunda
        Log.d(TAG, "MainActivity onCreate tamamlandı");
    }

    /**
     * Google Maps harita parçasını ayarlar
     */
    private void setupMapFragment() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    /**
     * Butonları ve tıklama olaylarını ayarlar
     */
    private void setupButtons() {
        btnOptimizeRoute = findViewById(R.id.btn_optimize_route);
        btnOptimizeRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                optimizeRoute();
                //clearVisitedSegments();
            }
        });

        // Google Maps butonunu tanımla
        btnOpenInGoogleMaps = findViewById(R.id.btn_open_in_Maps);
        btnOpenInGoogleMaps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleGoogleMapsButtonClick();
            }
        });

        // Başlangıçta Google Maps butonu devre dışı, rota optimize edildiğinde aktif olacak
        btnOpenInGoogleMaps.setEnabled(false);

        // Kişi listesi butonunu ekle
        btnPersonList = findViewById(R.id.btn_person_list);
        btnPersonList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPersonListActivity();
            }
        });
    }

    /**
     * Kaydedilmiş rotayı SharedPreferences'tan yükler
     */
    private void loadSavedRoute() {
        SharedPreferences prefs = getSharedPreferences(ROUTE_PREFS, MODE_PRIVATE);
        boolean routeExists = prefs.getBoolean(ROUTE_EXISTS_KEY, false);

        if (routeExists) {
            String routeIndicesString = prefs.getString(ROUTE_INDICES_KEY, "");
            if (!routeIndicesString.isEmpty()) {
                optimizedRouteIndices = new ArrayList<>();
                String[] indices = routeIndicesString.split(",");

                try {
                    for (String index : indices) {
                        optimizedRouteIndices.add(Integer.parseInt(index.trim()));
                    }

                    Log.d(TAG, "Saved route loaded: " + optimizedRouteIndices.toString());

                    // Kaydedilmiş rota varsa Google Maps butonunu aktif et
                    btnOpenInGoogleMaps.setEnabled(true);

                    // Waypoints yüklendikten sonra rotayı göster
                    if (!waypoints.isEmpty()) {
                        displayOptimizedRoute(optimizedRouteIndices);
                    }

                } catch (NumberFormatException e) {
                    Log.e(TAG, "Error parsing saved route indices", e);
                    clearSavedRoute();
                }
            }
        }
    }

    /**
     * Rotayı SharedPreferences'a kaydeder
     */
    private void saveOptimizedRoute() {
        if (optimizedRouteIndices != null && !optimizedRouteIndices.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(ROUTE_PREFS, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            // Liste elemanlarını virgülle ayrılmış string olarak kaydet
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < optimizedRouteIndices.size(); i++) {
                sb.append(optimizedRouteIndices.get(i));
                if (i < optimizedRouteIndices.size() - 1) {
                    sb.append(",");
                }
            }

            editor.putString(ROUTE_INDICES_KEY, sb.toString());
            editor.putBoolean(ROUTE_EXISTS_KEY, true);
            editor.apply();

            Log.d(TAG, "Route saved to SharedPreferences: " + sb.toString());
        }
    }

    /**
     * Kaydedilmiş rotayı temizler
     */
    public void clearSavedRoute() {
        SharedPreferences prefs = getSharedPreferences(ROUTE_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(ROUTE_INDICES_KEY);
        editor.putBoolean(ROUTE_EXISTS_KEY, false);
        editor.apply();

        Log.d(TAG, "Saved route cleared");
    }

    /**
     * Veritabanından müşterileri yükler ve waypoint listesine dönüştürür
     */
    private void loadCustomersFromDatabase() {
        waypoints.clear();

        // Veritabanından rotaya dahil edilen müşterileri al
        List<Customer> customers = dbHelper.getCustomersInRoute();

        // Eğer rotada hiç müşteri yoksa, tüm müşterileri göster
        if (customers.isEmpty()) {
            customers = dbHelper.getAllCustomers();
        }

        for (Customer customer : customers) {
            LatLng position = new LatLng(customer.getLatitude(), customer.getLongitude());
            waypoints.add(new Waypoint(
                    position,
                    customer.getName(),
                    customer.getAddress(),
                    customer.getId()
            ));
        }

        // Waypoint'ler yüklendikten sonra haritada göster
        if (mMap != null) {
            displayWaypointsOnMap();

            // Kaydedilmiş rota varsa onu göster
            loadSavedRoute();
        }
    }

    /**
     * Durakları harita üzerinde gösterir
     */
    private void displayWaypointsOnMap() {
        clearMapOverlays();

        if (waypoints.isEmpty()) {
            Toast.makeText(this, "Gösterilecek müşteri bulunamadı.", Toast.LENGTH_SHORT).show();
            return;
        }

        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        for (int i = 0; i < waypoints.size(); i++) {
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(waypoints.get(i).getLocation())
                    .title(waypoints.get(i).getName())
                    .snippet(waypoints.get(i).getAddress());

            if (i == 0) {
                markerOptions.title("Başlangıç/Bitiş: " + waypoints.get(i).getName())
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
            }

            com.google.android.gms.maps.model.Marker marker = mMap.addMarker(markerOptions);
            markers.add(marker);
            builder.include(waypoints.get(i).getLocation());
        }

        // Haritayı tüm işaretleri gösterecek şekilde ayarla
        if (waypoints.size() > 0) {
            try {
                LatLngBounds bounds = builder.build();
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
            } catch (IllegalStateException e) {
                // Tek bir nokta varsa veya başka bir sorun olursa ilk noktaya git
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(waypoints.get(0).getLocation(), 15f));
            }
        }
    }

    /**
     * Harita üzerindeki tüm işaretleri ve rotaları temizler
     */
    private void clearMapOverlays() {
        for (com.google.android.gms.maps.model.Marker marker : markers) {
            marker.remove();
        }
        markers.clear();

        if (mMap != null) {
            mMap.clear();
        }
    }

    /**
     * Genetik algoritma kullanarak optimal rotayı hesaplar ve haritada gösterir
     */
//    private void optimizeRoute() {
//        if (waypoints.size() < 3) {
//            Toast.makeText(this, "En az 3 durak olmalıdır!", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        int startEndIndex = 0;
//
//        // Waypoint'lerin LatLng'lerini al
//        List<LatLng> latLngs = new ArrayList<>();
//        for (Waypoint waypoint : waypoints) {
//            latLngs.add(waypoint.getLocation());
//        }
//
//        // Genetik algoritma ile rotayı optimize et
//        GeneticTSPGoogleMaps geneticTSP = new GeneticTSPGoogleMaps(latLngs, MainActivity.this);
//        optimizedRouteIndices = geneticTSP.findOptimalRoute(startEndIndex);
//
//        Log.d(TAG, "Optimized route: " + optimizedRouteIndices.toString());
//
//        displayOptimizedRoute(optimizedRouteIndices);
//
//        // Optimize edilmiş rotayı kaydet
//        saveOptimizedRoute();
//        //Ziyaret edilmiş segmentleri(GoogleMaps'te aç kısmında gelen butonları yani) temizlemek için.
//        //clearVisitedSegments();
//        btnOpenInGoogleMaps.setEnabled(true);
//        Toast.makeText(this, "Rota optimize edildi ve kaydedildi!", Toast.LENGTH_SHORT).show();
//    }
    private void optimizeRoute() {
        if (waypoints.size() < 3) {
            Toast.makeText(this, "En az 3 durak olmalıdır!", Toast.LENGTH_SHORT).show();
            return;
        }

        int startEndIndex = 0;

        // Waypoint'lerin LatLng'lerini al
        List<LatLng> latLngs = new ArrayList<>();
        for (Waypoint waypoint : waypoints) {
            latLngs.add(waypoint.getLocation());
        }

        // Progress dialog göster
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setTitle("Hibrit Optimizasyon");
        progressDialog.setMessage("OR-Tools ve SimplifiedLKH karşılaştırılıyor...\nBu 15-40 saniye sürebilir.");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Arka planda çalıştır
        new Thread(() -> {
            try {
                // Hibrit optimizer kullan
                HybridOptimizer optimizer = new HybridOptimizer(latLngs, MainActivity.this);

                // Durak sayısına göre zaman limiti belirle
                int timeLimit;
                if (waypoints.size() <= 30) {
                    timeLimit = 110;  // 10 saniye
                } else if (waypoints.size() <= 70) {
                    timeLimit = 20;  // 20 saniye
                } else if (waypoints.size() <= 100) {
                    timeLimit = 30;  // 30 saniye
                } else {
                    timeLimit = 45;  // 45 saniye
                }

                HybridOptimizer.OptimizationResult result =
                        optimizer.findOptimalRoute(startEndIndex, timeLimit);

                // UI thread'de güncelle
                runOnUiThread(() -> {
                    progressDialog.dismiss();

                    if (result != null && result.success) {
                        optimizedRouteIndices = result.route;

                        Log.d(TAG, "════════════════════════════════════════");
                        Log.d(TAG, "✅ SONUÇ: " + result.algorithmName + " kullanıldı");
                        Log.d(TAG, "📏 Mesafe: " + String.format("%.2f", result.totalDistance) + " km");
                        Log.d(TAG, "⏱️  Süre: " + String.format("%.1f", result.executionTimeMs / 1000.0) + " sn");
                        Log.d(TAG, "════════════════════════════════════════");

                        displayOptimizedRoute(optimizedRouteIndices);
                        saveOptimizedRoute();

                        btnOpenInGoogleMaps.setEnabled(true);

                        // Kullanıcıya bilgi ver
                        String message = result.algorithmName + " ile optimize edildi!\n" +
                                "Mesafe: " + String.format("%.2f", result.totalDistance) + " km\n" +
                                "Süre: " + String.format("%.1f", result.executionTimeMs / 1000.0) + " saniye";

                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                    } else {
                        String errorMsg = (result != null) ? result.errorMessage : "Bilinmeyen hata";
                        Log.e(TAG, "❌ Optimizasyon başarısız: " + errorMsg);
                        Toast.makeText(this, "Optimizasyon başarısız: " + errorMsg, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ Fatal hata: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    /**
     * Verilen sayı ile bir bitmap oluşturur
     * @param number İşaret üzerinde gösterilecek sayı
     * @return Sayıyı içeren bir BitmapDescriptor
     */
    private BitmapDescriptor createNumberedMarkerIcon(int number) {
        String text = String.valueOf(number);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(50); // Sayı boyutu, ihtiyacınıza göre ayarlayın
        paint.setColor(Color.WHITE); // Sayı rengi
        paint.setTextAlign(Paint.Align.CENTER);

        // Arka plan rengi (mavi varsayılan marker rengi gibi)
        Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.parseColor("#4285F4")); // Google Maps mavi rengi

        Rect textBounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), textBounds);

        int width = Math.max(textBounds.width() + 40, 100); // Minimum genişlik
        int height = Math.max(textBounds.height() + 20, 100); // Minimum yükseklik

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Arka planı çiz
        canvas.drawCircle(width / 2, height / 2, Math.min(width, height) / 2, backgroundPaint);

        // Sayıyı ortala
        int xPos = (canvas.getWidth() / 2);
        // Hata düzeltildi: paint.ascent() doğru şekilde yazıldı
        int yPos = (int) ((canvas.getHeight() / 2) - ((paint.descent() + paint.ascent()) / 2));
        canvas.drawText(text, xPos, yPos, paint);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    /**
     * Optimize edilmiş rotayı haritada gösterir
     * @param routeIndices Rota içindeki durak indislerinin sıralı listesi
     */
    private void displayOptimizedRoute(List<Integer> routeIndices) {
        clearMapOverlays();

        StringBuilder logMessage = new StringBuilder("Optimize Edilmiş Rota Koordinatları:\n");
        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        for (int i = 0; i < routeIndices.size(); i++) {
            int waypointIndex = routeIndices.get(i);
            Waypoint waypoint = waypoints.get(waypointIndex);
            LatLng point = waypoint.getLocation();

            String pointInfo;
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(point)
                    .snippet(waypoint.getAddress());

            if (i == 0) {
                pointInfo = "Başlangıç: " + waypoint.getName();
                markerOptions.title("Başlangıç: " + waypoint.getName())
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
            } else if (i == routeIndices.size() - 1) {
                pointInfo = "Bitiş: " + waypoint.getName();
                markerOptions.title("Bitiş: " + waypoint.getName())
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
            } else {
                pointInfo = "Durak " + i + ": " + waypoint.getName();
                markerOptions.title("Durak " + i + ": " + waypoint.getName())
                        .icon(createNumberedMarkerIcon(i));
            }

            logMessage.append(i + 1)
                    .append(". ")
                    .append(pointInfo)
                    .append(": [")
                    .append(point.latitude)
                    .append(", ")
                    .append(point.longitude)
                    .append("] - ")
                    .append(waypoint.getAddress())
                    .append("\n");

            com.google.android.gms.maps.model.Marker marker = mMap.addMarker(markerOptions);
            markers.add(marker);
            builder.include(point);
        }

        Log.d(TAG, logMessage.toString());
        if (routeIndices.size() > 0) {
            try {
                LatLngBounds bounds = builder.build();
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
            } catch (IllegalStateException e) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(waypoints.get(routeIndices.get(0)).getLocation(), 15f));
            }
        }
    }

    /**
     * Google Maps butonuna tıklandığında durak sayısına göre işlem yapar
     */
    private void handleGoogleMapsButtonClick() {
        if (optimizedRouteIndices == null || optimizedRouteIndices.isEmpty()) {
            Toast.makeText(this, "Önce rotayı optimize edin!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Ara durak sayısı (başlangıç ve bitiş hariç)
        int waypointCount = optimizedRouteIndices.size() ;
        Log.d(TAG, "SAYIIIIIIII " + waypointCount);
        // Eğer durak sayısı maksimum limitin altındaysa doğrudan Google Maps'i aç
        if (waypointCount <= MAX_WAYPOINTS_FOR_GOOGLE_MAPS) {
            openInGoogleMaps();
        } else {
            // Çok fazla durak varsa, rotayı parçalara bölmek için yeni aktiviteye git
            openRouteSegmentsActivity();
        }
    }

    /**
     * Rota parçaları aktivitesini açar
     */
    private void openRouteSegmentsActivity() {
        Intent intent = new Intent(this, RouteSegmentsActivity.class);

        ArrayList<Double> latitudes = new ArrayList<>();
        ArrayList<Double> longitudes = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> addresses = new ArrayList<>();
        ArrayList<Boolean> visitedStatus = new ArrayList<>();
        ArrayList<Integer> customerIds = new ArrayList<>();

        for (Waypoint waypoint : waypoints) {
            latitudes.add(waypoint.getLocation().latitude);
            longitudes.add(waypoint.getLocation().longitude);
            names.add(waypoint.getName());
            addresses.add(waypoint.getAddress());
            visitedStatus.add(waypoint.isVisited());
            customerIds.add(waypoint.getCustomerId());
        }

        intent.putExtra("latitudes", latitudes);
        intent.putExtra("longitudes", longitudes);
        intent.putExtra("names", names);
        intent.putExtra("addresses", addresses);
        intent.putExtra("visitedStatus", visitedStatus);
        intent.putExtra("customerIds", customerIds);

        ArrayList<Integer> optimizedIndices = new ArrayList<>(optimizedRouteIndices);
        intent.putExtra("optimizedIndices", optimizedIndices);

        startActivity(intent);
    }

    /**
     * Optimize edilmiş rotayı Google Maps'te açar
     */
    private void openInGoogleMaps() {
        // Google Maps URL'si oluştur
        StringBuilder googleMapsUrl = new StringBuilder("https://www.google.com/maps/dir/?api=1");

        // Başlangıç noktası
        int startIndex = optimizedRouteIndices.get(0);
        LatLng startPoint = waypoints.get(startIndex).getLocation();
        googleMapsUrl.append("&origin=")
                .append(startPoint.latitude)
                .append(",")
                .append(startPoint.longitude);

        // Hedef (bitiş) noktası - döngüsel rotada bu başlangıç ile aynı
        int destIndex = optimizedRouteIndices.get(optimizedRouteIndices.size() - 1);
        LatLng destPoint = waypoints.get(destIndex).getLocation();
        googleMapsUrl.append("&destination=")
                .append(destPoint.latitude)
                .append(",")
                .append(destPoint.longitude);

        // Ara noktalara ekle (maksimum 23 waypoint ekleyebilirsiniz - Google Maps limiti)
        if (optimizedRouteIndices.size() > 2) {
            StringBuilder wayPointsBuilder = new StringBuilder();

            // İlk ve son noktalar hariç, ara noktaları ekle
            int waypointLimit = Math.min(optimizedRouteIndices.size() - 2, MAX_WAYPOINTS_FOR_GOOGLE_MAPS);
            for (int i = 1; i <= waypointLimit; i++) {
                int waypointIndex = optimizedRouteIndices.get(i);
                LatLng waypoint = waypoints.get(waypointIndex).getLocation();

                if (i > 1) {
                    wayPointsBuilder.append("|");
                }

                wayPointsBuilder.append(waypoint.latitude)
                        .append(",")
                        .append(waypoint.longitude);
            }

            if (waypointLimit > 0) {
                googleMapsUrl.append("&waypoints=")
                        .append(wayPointsBuilder.toString());
            }
        }

        // Sürüş modu ekle
        googleMapsUrl.append("&travelmode=driving");

        // Log olarak URL'yi yazdır
        Log.d(TAG, "Google Maps URL: " + googleMapsUrl.toString());

        // URL'yi aç
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(googleMapsUrl.toString()));
        intent.setPackage("com.google.android.apps.maps");

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            // Google Maps yüklü değilse tarayıcıda aç
            intent.setPackage(null);
            startActivity(intent);
        }
    }

    /**
     * Kişi listesi aktivitesini açar
     */
    private void openPersonListActivity() {
        Intent intent = new Intent(this, PersonListActivity.class);

        ArrayList<Double> latitudes = new ArrayList<>();
        ArrayList<Double> longitudes = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> addresses = new ArrayList<>();
        ArrayList<Boolean> visitedStatus = new ArrayList<>();
        ArrayList<Integer> customerIds = new ArrayList<>();

        for (Waypoint waypoint : waypoints) {
            latitudes.add(waypoint.getLocation().latitude);
            longitudes.add(waypoint.getLocation().longitude);
            names.add(waypoint.getName());
            addresses.add(waypoint.getAddress());
            visitedStatus.add(waypoint.isVisited());
            customerIds.add(waypoint.getCustomerId());
        }

        intent.putExtra("latitudes", latitudes);
        intent.putExtra("longitudes", longitudes);
        intent.putExtra("names", names);
        intent.putExtra("addresses", addresses);
        intent.putExtra("visitedStatus", visitedStatus);
        intent.putExtra("customerIds", customerIds);

        // Optimize edilmiş rota varsa gönder
        if (optimizedRouteIndices != null) {
            ArrayList<Integer> optimizedIndices = new ArrayList<>(optimizedRouteIndices);
            intent.putExtra("optimizedIndices", optimizedIndices);
        }

        startActivity(intent);
    }

    /**
     * Ziyaret durumlarını yükle
     */
    private void loadVisitedStatus() {
        SharedPreferences preferences = getSharedPreferences("VisitedStatusPrefs", MODE_PRIVATE);
        for (int i = 0; i < waypoints.size(); i++) {
            boolean visited = preferences.getBoolean("waypoint_" + waypoints.get(i).getCustomerId(), false);
            waypoints.get(i).setVisited(visited);
            Log.d(TAG, "Waypoint " + waypoints.get(i).getCustomerId() + " ziyaret durumu: " + visited);
        }
    }

    /**
     * Ziyaret durumlarını kaydet
     */
    private void saveVisitedStatus() {
        SharedPreferences preferences = getSharedPreferences("VisitedStatusPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        for (int i = 0; i < waypoints.size(); i++) {
            editor.putBoolean("waypoint_" + waypoints.get(i).getCustomerId(), waypoints.get(i).isVisited());
            Log.d(TAG, "Kaydediliyor: waypoint_" + waypoints.get(i).getCustomerId() + " = " + waypoints.get(i).isVisited());
        }

        boolean success = editor.commit(); // apply() yerine commit()
        Log.d(TAG, "MainActivity'de ziyaret durumu kaydetme " + (success ? "başarılı" : "başarısız"));

    }

    /**
     * Ziyaret edilen segmentleri temizler
     */
    private void clearVisitedSegments() {
        SharedPreferences sharedPreferences = getSharedPreferences("RouteSegmentsPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove("visitedSegments");
        editor.apply();
    }

    /**
     * Konum izni kontrol eder ve gerekiyorsa ister
     */
    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mMap != null) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        mMap.setMyLocationEnabled(true);
                    }
                }
            }
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Harita tipini ayarla
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        // Konum butonu için izinleri kontrol et
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        // Marker'a tıklama etkinliğini aktif et
        mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(@NonNull com.google.android.gms.maps.model.Marker marker) {
                // Müşteri detayları aktivitesini başlatabilirsiniz
                for (int i = 0; i < waypoints.size(); i++) {
                    if (marker.getPosition().equals(waypoints.get(i).getLocation())) {
                        // İlgili müşteri detay aktivitesini aç
                        openCustomerDetailActivity(waypoints.get(i).getCustomerId());
                        break;
                    }
                }
            }
        });

        // Veritabanından müşterileri yükle
        loadCustomersFromDatabase();
    }

    /**
     * Müşteri detay aktivitesini açar
     */
    private void openCustomerDetailActivity(int customerId) {
        Intent intent = new Intent(this, PersonListActivity.class);
        intent.putExtra("customerId", customerId);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Veritabanından müşterileri tekrar yükle (güncellemeler olabilir)
        if (mMap != null) {
            loadCustomersFromDatabase();
        }

        // Ziyaret durumlarını güncelle
        loadVisitedStatus();

        // Haritada gösterilen işaretleri de güncelle
        if (mMap != null && optimizedRouteIndices != null && !optimizedRouteIndices.isEmpty()) {
            displayOptimizedRoute(optimizedRouteIndices);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Aktiviteden çıkıldığında ziyaret durumlarını kaydet
        //saveVisitedStatus();
    }
}