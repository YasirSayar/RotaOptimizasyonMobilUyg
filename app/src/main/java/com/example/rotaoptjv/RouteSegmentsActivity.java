package com.example.rotaoptjv;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import org.osmdroid.util.GeoPoint;
import java.util.ArrayList;
import java.util.List;
import androidx.appcompat.app.AppCompatActivity;
import org.osmdroid.util.GeoPoint;
import java.util.ArrayList;
import java.util.List;


//Güncel optimize rotayı parçalara ayıran kısım
public class RouteSegmentsActivity extends AppCompatActivity {
    private static final String TAG = "RouteSegmentsActivity";
   // private static final String PREFS_NAME = "RouteSegmentsPrefs";//12'den sonra sıfırlamak için
   // private static final String VISITED_SEGMENTS_KEY = "visitedSegments";//12'den sonra sıfırlamak için

    // Google Maps'in desteklediği maksimum durak sayısı
    private static final int MAX_WAYPOINTS_PER_SEGMENT = 9; // En fazla 10 ara durak + başlangıç ve bitiş

    private List<GeoPoint> allWaypoints = new ArrayList<>();
    private List<Integer> optimizedRouteIndices = new ArrayList<>();

    private SharedPreferences sharedPreferences;
    private List<Button> segmentButtons = new ArrayList<>();

    private LinearLayout segmentsContainer;
    private TextView tvTotalWaypoints;
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_segments);

        // SharedPreferences'ı başlat
        //sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE); //12'den sonra sıfırlamak için
        SharedPreferences prefs = PreferencesManager.getRouteSegmentsPrefs(this);
        // UI bileşenlerini bul
        segmentsContainer = findViewById(R.id.segments_container);
        tvTotalWaypoints = findViewById(R.id.tv_total_waypoints);
        btnBack = findViewById(R.id.btn_back);

        // Intent'ten verileri al
        extractDataFromIntent();

        // Toplam durak sayısını göster
        tvTotalWaypoints.setText("Toplam " + optimizedRouteIndices.size() + " durak");

        // Rota parçalarını oluştur ve butonları ekle
        createRouteSegments();

        // Geri dönüş butonu
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void extractDataFromIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            // Waypoint koordinatlarını al
            ArrayList<Double> latitudes = (ArrayList<Double>) intent.getSerializableExtra("latitudes");
            ArrayList<Double> longitudes = (ArrayList<Double>) intent.getSerializableExtra("longitudes");

            // Optimize edilmiş rota indekslerini al
            optimizedRouteIndices = (ArrayList<Integer>) intent.getSerializableExtra("optimizedIndices");

            // GeoPoint listesini oluştur
            if (latitudes != null && longitudes != null) {
                for (int i = 0; i < latitudes.size(); i++) {
                    allWaypoints.add(new GeoPoint(latitudes.get(i), longitudes.get(i)));
                }
            }
        }
    }

    /**
     * Rota parçalarını oluşturur ve her biri için bir buton ekler
     */
    /**
     * Rota parçalarını oluşturur ve her biri için bir buton ekler
     */
    private void createRouteSegments() {
        if (optimizedRouteIndices.isEmpty() || allWaypoints.isEmpty()) {
            Toast.makeText(this, "Rota verileri bulunamadı!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Toplam noktaları segmentlere böl
        List<List<Integer>> segments = splitRouteIntoSegments();

        // Ziyaret edilmiş segmentleri al
        List<Integer> visitedSegments = getVisitedSegments();

        // Her segment için buton oluştur
        for (int i = 0; i < segments.size(); i++) {
            List<Integer> segment = segments.get(i);

            // Segment başlangıç ve bitiş indeksleri
            int startIdx = segment.get(0);
            int endIdx = segment.get(segment.size() - 1);

            // İndeks değil, gerçek durak numaralarını kullan
            int startStopNumber = optimizedRouteIndices.indexOf(startIdx) + 1;
            int endStopNumber = optimizedRouteIndices.indexOf(endIdx) + 1;

            // Buton oluştur
            Button btnSegment = new Button(this);
            btnSegment.setText((i + 1) + ". Kısım: " + startStopNumber + ". durak - " + endStopNumber + ". durak");
            btnSegment.setAllCaps(false);

            // Segment daha önce ziyaret edilmiş mi kontrol et
            if (visitedSegments.contains(i)) {
                btnSegment.setBackgroundColor(Color.GREEN);
            } else {
                btnSegment.setBackgroundColor(Color.RED);
            }

            // Butona tıklama olayını ekle
            final int segmentIndex = i;
            btnSegment.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //Tıklayınca yeşile dönmesi için
                    v.setBackgroundColor(Color.GREEN);
                    // Tıklanan segmenti ziyaret edilmiş olarak kaydet
                    saveVisitedSegment(segmentIndex);
                    openSegmentInGoogleMaps(segments.get(segmentIndex));
                }
            });

            // Butonun görünümünü ayarla
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 16, 0, 16);
            btnSegment.setLayoutParams(params);

            // Butonu layout'a ekle
            segmentsContainer.addView(btnSegment);
            segmentButtons.add(btnSegment);
        }
    }
    private List<Integer> getVisitedSegments() {
        List<Integer> visited = new ArrayList<>();
        //String visitedSegmentsStr = sharedPreferences.getString(VISITED_SEGMENTS_KEY, "");//12'den sonra sıfırlamak için

        SharedPreferences prefs = PreferencesManager.getRouteSegmentsPrefs(this);
        String visitedSegmentsStr = prefs.getString("visitedSegments", "");
        if (!visitedSegmentsStr.isEmpty()) {
            String[] parts = visitedSegmentsStr.split(",");
            for (String part : parts) {
                try {
                    visited.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Ziyaret edilen segment parse hatası: " + e.getMessage());
                }
            }
        }

        return visited;
    }

    /**
     * Ziyaret edilen segmenti SharedPreferences'a kaydeder
     */
    private void saveVisitedSegment(int segmentIndex) {
        List<Integer> visited = getVisitedSegments();

        if (!visited.contains(segmentIndex)) {
            visited.add(segmentIndex);

            // Listeyi string formatına dönüştür
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < visited.size(); i++) {
                sb.append(visited.get(i));
                if (i < visited.size() - 1) {
                    sb.append(",");
                }
            }

            // Kaydet
            //SharedPreferences.Editor editor = sharedPreferences.edit();//12'den sonra sıfırlamak için
            //editor.putString(VISITED_SEGMENTS_KEY, sb.toString());//12'den sonra sıfırlamak için
            SharedPreferences prefs = PreferencesManager.getRouteSegmentsPrefs(this);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("visitedSegments", sb.toString());
            editor.apply();
        }
    }

    /**
     * Rotayı maksimum durak sayısına göre segmentlere böler
     * @return Segment listesi
     */
    private List<List<Integer>> splitRouteIntoSegments() {
        List<List<Integer>> segments = new ArrayList<>();
        int maxPointsPerSegment = 11;
        int totalPoints = optimizedRouteIndices.size();

        if (totalPoints < 2) return segments; // en az 2 nokta olmalı

        int currentIndex = 0;
        Log.d(TAG, "TAM LİSTE : " + optimizedRouteIndices);
        while (currentIndex < totalPoints - 1) {
            List<Integer> segment = new ArrayList<>();

            // Segmentin başlangıç noktası (önceki segmentin son noktası)
            int start = currentIndex;

            // Segmentin bitiş noktası (en fazla 11 nokta, ancak total'i geçme)
            int end = Math.min(start + maxPointsPerSegment, totalPoints);

            // Segmente noktaları ekle
            for (int i = start; i < end-1; i++) {
                if(i==0){
                    segment.add(optimizedRouteIndices.get(currentIndex));
                }else {
                    segment.add(optimizedRouteIndices.get(i));
                }
            }

            // Sonraki segmentin ilk noktası, bu segmentin son noktası olacak
            // Yani son noktayı tekrar ekleyerek bağlantıyı koruyoruz
            if (end-1 < totalPoints) {
                segment.add(optimizedRouteIndices.get(end-1));
            }

            segments.add(segment);

            // İleri git: her seferinde 10 yeni nokta ilerle ama son noktayı koruyarak
            currentIndex += maxPointsPerSegment - 1;
        }

        for (int i = 0; i < segments.size(); i++) {
            Log.d(TAG, "Segment " + (i + 1) + ": " + segments.get(i));
        }

        return segments;
    }

    /**
     * Belirli bir rota parçasını Google Maps'te açar
     * @param segmentIndices Segment içindeki durak indeksleri
     */
    /**
     * Belirli bir rota parçasını Google Maps'te açar
     * @param segmentIndices Segment içindeki durak indeksleri
     */
    private void openSegmentInGoogleMaps(List<Integer> segmentIndices) {
        if (segmentIndices.size() < 2) {
            Toast.makeText(this, "Geçersiz rota parçası!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Google Maps URL'si oluştur
        StringBuilder googleMapsUrl = new StringBuilder("https://www.google.com/maps/dir/?api=1");

        // Başlangıç noktası
        int startIndex = segmentIndices.get(0);
        GeoPoint startPoint = allWaypoints.get(startIndex);
        googleMapsUrl.append("&origin=")
                .append(startPoint.getLatitude())
                .append(",")
                .append(startPoint.getLongitude());

        // Hedef (bitiş) noktası
        int destIndex = segmentIndices.get(segmentIndices.size() - 1);
        GeoPoint destPoint = allWaypoints.get(destIndex);
        googleMapsUrl.append("&destination=")
                .append(destPoint.getLatitude())
                .append(",")
                .append(destPoint.getLongitude());

        // Ara noktalara ekle
        if (segmentIndices.size() > 2) {
            StringBuilder wayPointsBuilder = new StringBuilder();

            // İlk ve son noktalar hariç, ara noktaları ekle
            for (int i = 1; i < segmentIndices.size() - 1; i++) {
                int waypointIndex = segmentIndices.get(i);
                GeoPoint waypoint = allWaypoints.get(waypointIndex);

                if (i > 1) {
                    wayPointsBuilder.append("|");
                }

                wayPointsBuilder.append(waypoint.getLatitude())
                        .append(",")
                        .append(waypoint.getLongitude());
            }

            googleMapsUrl.append("&waypoints=")
                    .append(wayPointsBuilder.toString());
        }

        // Sürüş modu ekle
        googleMapsUrl.append("&travelmode=driving");

        // Log olarak URL'yi yazdır
        String url = googleMapsUrl.toString();
        Log.d(TAG, "Google Maps URL: " + url);

        // URL'yi aç
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setPackage("com.google.android.apps.maps");

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            // Google Maps yüklü değilse tarayıcıda aç
            intent.setPackage(null);
            startActivity(intent);
        }
    }
}