package com.example.rotaoptjv;

import android.content.Context;
import android.util.Log;
import com.google.android.gms.maps.model.LatLng;
import java.util.ArrayList;
import java.util.List;

/**
 * 2-opt algoritması ile rota optimizasyonu
 * Genetik algoritmadan daha hızlı ve daha güvenilir sonuçlar verir
 */
public class TwoOptRouteOptimizer {
    private static final String TAG = "TwoOptOptimizer";

    private List<LatLng> locations;
    private double[][] distanceMatrix;
    private Context context;
    private DatabaseHelper dbHelper;
    private List<Integer> customerIds;

    // Optimizasyon parametreleri
    private static final int MAX_ITERATIONS = 1000; // Maksimum iterasyon sayısı
    private static final double MIN_IMPROVEMENT = 0.001; // Minimum iyileştirme oranı (%0.1)

    public TwoOptRouteOptimizer(List<LatLng> locations, Context context) {
        this.locations = new ArrayList<>(locations);
        this.context = context;
        this.dbHelper = new DatabaseHelper(context);
        this.customerIds = new ArrayList<>();
    }

    /**
     * Veritabanından mesafe matrisini yükler
     */
    public boolean loadDistanceMatrixFromDatabase() {
        int size = locations.size();
        distanceMatrix = new double[size][size];

        if (!loadCustomerIds()) {
            Log.e(TAG, "Müşteri ID'leri yüklenemedi");
            return false;
        }

        // Mesafeleri veritabanından yükle
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    distanceMatrix[i][j] = 0;
                } else {
                    int fromId = customerIds.get(i);
                    int toId = customerIds.get(j);
                    double distance = dbHelper.getDistance(fromId, toId);

                    if (distance >= 0) {
                        distanceMatrix[i][j] = distance;
                    } else {
                        // Eksik mesafe varsa Haversine ile hesapla
                        distance = calculateHaversineDistance(locations.get(i), locations.get(j));
                        distanceMatrix[i][j] = distance;
                        dbHelper.saveDistance(fromId, toId, distance);
                    }
                }
            }
        }

        return true;
    }

    /**
     * Müşteri ID'lerini yükler
     */
    private boolean loadCustomerIds() {
        customerIds.clear();
        List<Customer> allCustomers = dbHelper.getAllCustomers();

        if (allCustomers.isEmpty()) {
            Log.e(TAG, "Veritabanında müşteri bulunamadı");
            return false;
        }

        for (LatLng location : locations) {
            Customer nearestCustomer = findNearestCustomer(location, allCustomers);
            if (nearestCustomer != null) {
                customerIds.add(nearestCustomer.getId());
            } else {
                return false;
            }
        }

        return true;
    }

    /**
     * En yakın müşteriyi bulur
     */
    private Customer findNearestCustomer(LatLng location, List<Customer> customers) {
        Customer nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Customer customer : customers) {
            LatLng customerLocation = new LatLng(customer.getLatitude(), customer.getLongitude());
            double distance = calculateHaversineDistance(location, customerLocation);

            if (distance < minDistance) {
                minDistance = distance;
                nearest = customer;
            }
        }

        return nearest;
    }

    /**
     * Haversine mesafe hesaplama
     */
    private double calculateHaversineDistance(LatLng p1, LatLng p2) {
        final int R = 6371; // Dünya yarıçapı (km)

        double lat1 = Math.toRadians(p1.latitude);
        double lon1 = Math.toRadians(p1.longitude);
        double lat2 = Math.toRadians(p2.latitude);
        double lon2 = Math.toRadians(p2.longitude);

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * 2-opt algoritması ile optimal rotayı bulur
     * @param startEndIndex Başlangıç ve bitiş durak indeksi
     * @return Optimize edilmiş rota indeksleri
     */
    public List<Integer> findOptimalRoute(int startEndIndex) {
        int numLocations = locations.size();

        if (numLocations <= 2) {
            List<Integer> simpleRoute = new ArrayList<>();
            simpleRoute.add(0);
            simpleRoute.add(0);
            return simpleRoute;
        }

        // Mesafe matrisini yükle
        if (!loadDistanceMatrixFromDatabase()) {
            Log.w(TAG, "Mesafe matrisi yüklenemedi, Haversine kullanılacak");
            calculateDistanceMatrixWithHaversine();
        }

        // Başlangıç rotasını oluştur (Nearest Neighbor ile)
        List<Integer> route = createInitialRouteNearestNeighbor(startEndIndex);

        Log.d(TAG, "Başlangıç rota mesafesi: " + calculateTotalDistance(route) + " km");

        // 2-opt optimizasyonu uygula
        route = twoOptOptimization(route);

        Log.d(TAG, "Optimize edilmiş rota mesafesi: " + calculateTotalDistance(route) + " km");
        Log.d(TAG, "Optimize edilmiş rota: " + route);

        return route;
    }

    /**
     * Nearest Neighbor algoritması ile başlangıç rotası oluşturur
     * Bu, genetik algoritmadan daha iyi bir başlangıç noktası sağlar
     */
    private List<Integer> createInitialRouteNearestNeighbor(int startIndex) {
        List<Integer> route = new ArrayList<>();
        boolean[] visited = new boolean[locations.size()];

        int current = startIndex;
        route.add(current);
        visited[current] = true;

        // Her adımda en yakın ziyaret edilmemiş noktayı seç
        for (int i = 1; i < locations.size(); i++) {
            double minDistance = Double.MAX_VALUE;
            int nearest = -1;

            for (int j = 0; j < locations.size(); j++) {
                if (!visited[j] && distanceMatrix[current][j] < minDistance) {
                    minDistance = distanceMatrix[current][j];
                    nearest = j;
                }
            }

            if (nearest != -1) {
                route.add(nearest);
                visited[nearest] = true;
                current = nearest;
            }
        }

        // Başlangıç noktasına dön
        route.add(startIndex);

        return route;
    }

    /**
     * 2-opt optimizasyonu
     * Rotadaki iki kenarı seçip aralarını ters çevirerek iyileştirme arar
     */
    private List<Integer> twoOptOptimization(List<Integer> route) {
        List<Integer> bestRoute = new ArrayList<>(route);
        double bestDistance = calculateTotalDistance(bestRoute);
        boolean improved = true;
        int iteration = 0;

        while (improved && iteration < MAX_ITERATIONS) {
            improved = false;
            iteration++;

            // Rota üzerindeki tüm edge çiftlerini dene
            for (int i = 1; i < route.size() - 2; i++) {
                for (int j = i + 1; j < route.size() - 1; j++) {
                    // i ile j arasını ters çevir
                    List<Integer> newRoute = twoOptSwap(bestRoute, i, j);
                    double newDistance = calculateTotalDistance(newRoute);

                    // İyileştirme var mı kontrol et
                    double improvement = (bestDistance - newDistance) / bestDistance;

                    if (improvement > MIN_IMPROVEMENT) {
                        bestRoute = newRoute;
                        bestDistance = newDistance;
                        improved = true;

                        Log.d(TAG, "İterasyon " + iteration + ": Yeni mesafe = " +
                                String.format("%.2f", bestDistance) + " km (İyileştirme: %" +
                                String.format("%.2f", improvement * 100) + ")");
                    }
                }
            }
        }

        Log.d(TAG, "Optimizasyon tamamlandı. Toplam iterasyon: " + iteration);

        return bestRoute;
    }

    /**
     * 2-opt swap işlemi: i ile j arasını ters çevir
     */
    private List<Integer> twoOptSwap(List<Integer> route, int i, int j) {
        List<Integer> newRoute = new ArrayList<>();

        // 0'dan i'ye kadar aynı
        for (int k = 0; k < i; k++) {
            newRoute.add(route.get(k));
        }

        // i'den j'ye kadar ters çevir
        for (int k = j; k >= i; k--) {
            newRoute.add(route.get(k));
        }

        // j'den sonrası aynı
        for (int k = j + 1; k < route.size(); k++) {
            newRoute.add(route.get(k));
        }

        return newRoute;
    }

    /**
     * Rotanın toplam mesafesini hesaplar
     */
    private double calculateTotalDistance(List<Integer> route) {
        double totalDistance = 0;

        for (int i = 0; i < route.size() - 1; i++) {
            int from = route.get(i);
            int to = route.get(i + 1);
            totalDistance += distanceMatrix[from][to];
        }

        return totalDistance;
    }

    /**
     * Haversine ile mesafe matrisi oluştur (yedek)
     */
    private void calculateDistanceMatrixWithHaversine() {
        int size = locations.size();
        distanceMatrix = new double[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    distanceMatrix[i][j] = 0;
                } else {
                    distanceMatrix[i][j] = calculateHaversineDistance(
                            locations.get(i),
                            locations.get(j)
                    );
                }
            }
        }
    }

    /**
     * 3-opt algoritması (daha güçlü ama daha yavaş)
     * Gerekirse kullanabilirsiniz
     */
    public List<Integer> findOptimalRouteWith3Opt(int startEndIndex) {
        // Önce 2-opt ile optimize et
        List<Integer> route = findOptimalRoute(startEndIndex);

        Log.d(TAG, "3-opt optimizasyonu başlatılıyor...");

        double bestDistance = calculateTotalDistance(route);
        boolean improved = true;
        int iteration = 0;

        while (improved && iteration < MAX_ITERATIONS / 2) { // 3-opt daha yavaş, daha az iterasyon
            improved = false;
            iteration++;

            for (int i = 1; i < route.size() - 3; i++) {
                for (int j = i + 1; j < route.size() - 2; j++) {
                    for (int k = j + 1; k < route.size() - 1; k++) {
                        // 3-opt için 7 farklı yeniden bağlama şekli var
                        // En iyi 4 tanesini deneyelim
                        List<List<Integer>> candidates = generate3OptMoves(route, i, j, k);

                        for (List<Integer> candidate : candidates) {
                            double candidateDistance = calculateTotalDistance(candidate);

                            if (candidateDistance < bestDistance - MIN_IMPROVEMENT) {
                                route = candidate;
                                bestDistance = candidateDistance;
                                improved = true;

                                Log.d(TAG, "3-opt İterasyon " + iteration +
                                        ": Yeni mesafe = " + String.format("%.2f", bestDistance) + " km");
                                break;
                            }
                        }

                        if (improved) break;
                    }
                    if (improved) break;
                }
                if (improved) break;
            }
        }

        Log.d(TAG, "3-opt optimizasyonu tamamlandı. Final mesafe: " +
                String.format("%.2f", bestDistance) + " km");

        return route;
    }

    /**
     * 3-opt için farklı yeniden bağlama şekillerini oluştur
     */
    private List<List<Integer>> generate3OptMoves(List<Integer> route, int i, int j, int k) {
        List<List<Integer>> moves = new ArrayList<>();

        // Segment'leri tanımla
        List<Integer> segment1 = route.subList(0, i);
        List<Integer> segment2 = route.subList(i, j);
        List<Integer> segment3 = route.subList(j, k);
        List<Integer> segment4 = route.subList(k, route.size());

        // 4 farklı kombinasyon dene
        // 1. segment2'yi ters çevir
        moves.add(combineSegments(segment1, reverse(segment2), segment3, segment4));

        // 2. segment3'ü ters çevir
        moves.add(combineSegments(segment1, segment2, reverse(segment3), segment4));

        // 3. segment2 ve segment3'ü değiştir
        moves.add(combineSegments(segment1, segment3, segment2, segment4));

        // 4. segment2 ve segment3'ü değiştir ve ikisini de ters çevir
        moves.add(combineSegments(segment1, reverse(segment3), reverse(segment2), segment4));

        return moves;
    }

    /**
     * Liste segment'lerini birleştir
     */
    private List<Integer> combineSegments(List<Integer>... segments) {
        List<Integer> combined = new ArrayList<>();
        for (List<Integer> segment : segments) {
            combined.addAll(segment);
        }
        return combined;
    }

    /**
     * Listeyi ters çevir
     */
    private List<Integer> reverse(List<Integer> list) {
        List<Integer> reversed = new ArrayList<>(list);
        java.util.Collections.reverse(reversed);
        return reversed;
    }
}