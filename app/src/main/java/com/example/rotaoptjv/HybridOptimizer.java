package com.example.rotaoptjv;

import android.content.Context;
import android.util.Log;
import com.google.android.gms.maps.model.LatLng;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Hibrit Optimizer - İki algoritma paralel çalıştırır ve en iyisini seçer
 *
 * Özellikler:
 * - OR-Tools ve SimplifiedLKH'yi aynı anda çalıştırır
 * - Her ikisinin sonucunu karşılaştırır
 * - En iyi sonucu döndürür
 * - Detaylı log çıktısı verir
 * - Hata durumunda fallback olarak 2-opt kullanır
 */
public class HybridOptimizer {
    private static final String TAG = "HybridOptimizer";

    private List<LatLng> locations;
    private Context context;

    // Sonuç sınıfı
    public static class OptimizationResult {
        String algorithmName;
        List<Integer> route;
        double totalDistance;
        long executionTimeMs;
        boolean success;
        String errorMessage;

        public OptimizationResult(String algorithmName) {
            this.algorithmName = algorithmName;
            this.success = false;
        }
    }

    public HybridOptimizer(List<LatLng> locations, Context context) {
        this.locations = new ArrayList<>(locations);
        this.context = context;
    }

    /**
     * Ana optimizasyon fonksiyonu
     * İki algoritmayı paralel çalıştırır ve en iyisini seçer
     */
    public OptimizationResult findOptimalRoute(int startEndIndex, int timeLimitSeconds) {
        int numLocations = locations.size();

        if (numLocations <= 2) {
            OptimizationResult simple = new OptimizationResult("Simple");
            simple.route = new ArrayList<>();
            simple.route.add(startEndIndex);
            simple.route.add(startEndIndex);
            simple.totalDistance = 0;
            simple.success = true;
            return simple;
        }

        Log.d(TAG, "═══════════════════════════════════════════════════════");
        Log.d(TAG, "🚀 HİBRİT OPTİMİZASYON BAŞLADI");
        Log.d(TAG, "═══════════════════════════════════════════════════════");
        Log.d(TAG, "📍 Durak Sayısı: " + numLocations);
        Log.d(TAG, "⏱️  Zaman Limiti: " + timeLimitSeconds + " saniye");
        Log.d(TAG, "🎯 Başlangıç/Bitiş: " + startEndIndex);
        Log.d(TAG, "───────────────────────────────────────────────────────");

        // ExecutorService ile paralel çalıştır
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Her iki algoritmayı da başlat
        Future<OptimizationResult> orToolsFuture = executor.submit(() ->
                runORTools(startEndIndex, timeLimitSeconds)
        );

        Future<OptimizationResult> simplifiedLKHFuture = executor.submit(() ->
                runSimplifiedLKH(startEndIndex, timeLimitSeconds)
        );

        OptimizationResult orToolsResult = null;
        OptimizationResult simplifiedLKHResult = null;

        try {
            // Her ikisinin de bitmesini bekle
            orToolsResult = orToolsFuture.get(timeLimitSeconds + 10, TimeUnit.SECONDS);
            simplifiedLKHResult = simplifiedLKHFuture.get(timeLimitSeconds + 10, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            Log.e(TAG, "⚠️  Timeout! Algoritmalar " + (timeLimitSeconds + 10) + " saniyede tamamlanamadı");
            orToolsFuture.cancel(true);
            simplifiedLKHFuture.cancel(true);

        } catch (Exception e) {
            Log.e(TAG, "❌ Paralel çalıştırma hatası: " + e.getMessage());
        } finally {
            executor.shutdown();
        }

        // Sonuçları karşılaştır ve logla
        Log.d(TAG, "");
        Log.d(TAG, "═══════════════════════════════════════════════════════");
        Log.d(TAG, "📊 SONUÇLAR KARŞILAŞTIRMASI");
        Log.d(TAG, "═══════════════════════════════════════════════════════");

        logResult("OR-Tools", orToolsResult);
        Log.d(TAG, "───────────────────────────────────────────────────────");
        logResult("SimplifiedLKH", simplifiedLKHResult);
        Log.d(TAG, "───────────────────────────────────────────────────────");

        // En iyi sonucu seç
        OptimizationResult bestResult = selectBestResult(orToolsResult, simplifiedLKHResult);

        if (bestResult != null && bestResult.success) {
            Log.d(TAG, "");
            Log.d(TAG, "🏆 KAZANAN: " + bestResult.algorithmName);
            Log.d(TAG, "   Mesafe: " + String.format("%.2f", bestResult.totalDistance) + " km");
            Log.d(TAG, "   Süre: " + String.format("%.2f", bestResult.executionTimeMs / 1000.0) + " saniye");

            // Fark varsa göster
            OptimizationResult otherResult = (bestResult == orToolsResult) ? simplifiedLKHResult : orToolsResult;
            if (otherResult != null && otherResult.success) {
                double distanceDiff = Math.abs(bestResult.totalDistance - otherResult.totalDistance);
                double percentDiff = (distanceDiff / otherResult.totalDistance) * 100;
                long timeDiff = Math.abs(bestResult.executionTimeMs - otherResult.executionTimeMs);

                Log.d(TAG, "");
                Log.d(TAG, "📈 FARKLAR:");
                Log.d(TAG, "   Mesafe farkı: " + String.format("%.2f", distanceDiff) + " km (" +
                        String.format("%.2f", percentDiff) + "%)");
                Log.d(TAG, "   Süre farkı: " + String.format("%.2f", timeDiff / 1000.0) + " saniye");
            }

            Log.d(TAG, "═══════════════════════════════════════════════════════");
            return bestResult;

        } else {
            // Her ikisi de başarısız, fallback kullan
            Log.e(TAG, "");
            Log.e(TAG, "❌ HER İKİ ALGORİTMA DA BAŞARISIZ!");
            Log.e(TAG, "🔄 Fallback: 2-opt algoritmasına geçiliyor...");
            Log.d(TAG, "═══════════════════════════════════════════════════════");

            return runFallback2Opt(startEndIndex);
        }
    }

    /**
     * OR-Tools ile optimize et
     */
    private OptimizationResult runORTools(int startEndIndex, int timeLimitSeconds) {
        OptimizationResult result = new OptimizationResult("OR-Tools");

        Log.d(TAG, "🔵 OR-Tools başlatılıyor...");
        long startTime = System.currentTimeMillis();

        try {
            LKHStyleOptimizer optimizer = new LKHStyleOptimizer(locations, context);
            result.route = optimizer.findOptimalRoute(startEndIndex, timeLimitSeconds);

            if (result.route != null && !result.route.isEmpty()) {
                result.totalDistance = calculateTotalDistance(result.route, optimizer);
                result.executionTimeMs = System.currentTimeMillis() - startTime;
                result.success = true;

                Log.d(TAG, "✓ OR-Tools tamamlandı");
            } else {
                result.errorMessage = "Boş rota döndü";
                Log.e(TAG, "✗ OR-Tools başarısız: " + result.errorMessage);
            }

        } catch (UnsatisfiedLinkError e) {
            result.errorMessage = "OR-Tools kütüphanesi yüklü değil";
            Log.e(TAG, "✗ OR-Tools başarısız: " + result.errorMessage);
        } catch (Exception e) {
            result.errorMessage = e.getMessage();
            Log.e(TAG, "✗ OR-Tools başarısız: " + result.errorMessage);
        }

        return result;
    }

    /**
     * SimplifiedLKH ile optimize et
     */
    private OptimizationResult runSimplifiedLKH(int startEndIndex, int timeLimitSeconds) {
        OptimizationResult result = new OptimizationResult("SimplifiedLKH");

        Log.d(TAG, "🟢 SimplifiedLKH başlatılıyor...");
        long startTime = System.currentTimeMillis();

        try {
            SimplifiedLKH optimizer = new SimplifiedLKH(locations, context);
            result.route = optimizer.findOptimalRoute(startEndIndex);

            if (result.route != null && !result.route.isEmpty()) {
                result.totalDistance = calculateTotalDistance(result.route, optimizer);
                result.executionTimeMs = System.currentTimeMillis() - startTime;
                result.success = true;

                Log.d(TAG, "✓ SimplifiedLKH tamamlandı");
            } else {
                result.errorMessage = "Boş rota döndü";
                Log.e(TAG, "✗ SimplifiedLKH başarısız: " + result.errorMessage);
            }

        } catch (Exception e) {
            result.errorMessage = e.getMessage();
            Log.e(TAG, "✗ SimplifiedLKH başarısız: " + result.errorMessage);
        }

        return result;
    }

    /**
     * Fallback: 2-opt algoritması
     */
    private OptimizationResult runFallback2Opt(int startEndIndex) {
        OptimizationResult result = new OptimizationResult("2-opt (Fallback)");

        Log.d(TAG, "🟡 2-opt başlatılıyor...");
        long startTime = System.currentTimeMillis();

        try {
            TwoOptRouteOptimizer optimizer = new TwoOptRouteOptimizer(locations, context);
            result.route = optimizer.findOptimalRoute(startEndIndex);

            if (result.route != null && !result.route.isEmpty()) {
                result.totalDistance = calculateTotalDistance(result.route, optimizer);
                result.executionTimeMs = System.currentTimeMillis() - startTime;
                result.success = true;

                Log.d(TAG, "✓ 2-opt tamamlandı");
                Log.d(TAG, "   Mesafe: " + String.format("%.2f", result.totalDistance) + " km");
                Log.d(TAG, "   Süre: " + String.format("%.2f", result.executionTimeMs / 1000.0) + " saniye");
            }

        } catch (Exception e) {
            result.errorMessage = e.getMessage();
            Log.e(TAG, "✗ 2-opt bile başarısız: " + result.errorMessage);
        }

        return result;
    }

    /**
     * Sonucu detaylı logla
     */
    private void logResult(String name, OptimizationResult result) {
        if (result == null) {
            Log.d(TAG, name + ": ❌ NULL");
            return;
        }

        if (result.success) {
            Log.d(TAG, name + ": ✓ BAŞARILI");
            Log.d(TAG, "   📏 Toplam Mesafe: " + String.format("%.2f", result.totalDistance) + " km");
            Log.d(TAG, "   ⏱️  Süre: " + String.format("%.2f", result.executionTimeMs / 1000.0) + " saniye");
            Log.d(TAG, "   🗺️  Durak Sayısı: " + (result.route.size() - 1));

            // İlk 10 durak numarasını göster
            if (result.route.size() <= 12) {
                Log.d(TAG, "   📍 Rota: " + result.route);
            } else {
                List<Integer> preview = result.route.subList(0, 10);
                Log.d(TAG, "   📍 Rota (ilk 10): " + preview + "...");
            }
        } else {
            Log.d(TAG, name + ": ✗ BAŞARISIZ");
            Log.d(TAG, "   ❌ Hata: " + result.errorMessage);
            Log.d(TAG, "   ⏱️  Süre: " + String.format("%.2f", result.executionTimeMs / 1000.0) + " saniye");
        }
    }

    /**
     * En iyi sonucu seç
     * Kriterler: 1. Başarılı olmalı, 2. En kısa mesafe
     */
    private OptimizationResult selectBestResult(OptimizationResult r1, OptimizationResult r2) {
        // İkisi de başarısızsa
        if ((r1 == null || !r1.success) && (r2 == null || !r2.success)) {
            return null;
        }

        // Sadece biri başarılıysa
        if (r1 == null || !r1.success) return r2;
        if (r2 == null || !r2.success) return r1;

        // İkisi de başarılı, en kısa olanı seç
        if (r1.totalDistance < r2.totalDistance) {
            return r1;
        } else if (r2.totalDistance < r1.totalDistance) {
            return r2;
        } else {
            // Mesafe eşit, daha hızlı olanı seç
            return (r1.executionTimeMs < r2.executionTimeMs) ? r1 : r2;
        }
    }

    /**
     * Mesafe hesaplama yardımcı fonksiyonu
     */
    private double calculateTotalDistance(List<Integer> route, Object optimizer) {
        // Her optimizer'ın kendi distance matrix'i var, onları kullan
        double total = 0;

        try {
            if (optimizer instanceof LKHStyleOptimizer) {
                // OR-Tools için mesafe hesaplama
                DatabaseHelper dbHelper = new DatabaseHelper(context);
                List<Customer> customers = dbHelper.getAllCustomers();

                for (int i = 0; i < route.size() - 1; i++) {
                    int fromIdx = route.get(i);
                    int toIdx = route.get(i + 1);

                    if (fromIdx < locations.size() && toIdx < locations.size()) {
                        LatLng from = locations.get(fromIdx);
                        LatLng to = locations.get(toIdx);
                        total += calculateHaversineDistance(from, to);
                    }
                }

            } else {
                // SimplifiedLKH veya 2-opt için
                DatabaseHelper dbHelper = new DatabaseHelper(context);

                for (int i = 0; i < route.size() - 1; i++) {
                    int fromIdx = route.get(i);
                    int toIdx = route.get(i + 1);

                    if (fromIdx < locations.size() && toIdx < locations.size()) {
                        LatLng from = locations.get(fromIdx);
                        LatLng to = locations.get(toIdx);
                        total += calculateHaversineDistance(from, to);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Mesafe hesaplama hatası: " + e.getMessage());
        }

        return total;
    }

    /**
     * Haversine mesafe hesaplama
     */
    private double calculateHaversineDistance(LatLng p1, LatLng p2) {
        final int R = 6371; // km

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
     * Hızlı optimizasyon (10 saniye)
     */
    public OptimizationResult findOptimalRouteFast(int startEndIndex) {
        return findOptimalRoute(startEndIndex, 10);
    }

    /**
     * Normal optimizasyon (30 saniye)
     */
    public OptimizationResult findOptimalRouteNormal(int startEndIndex) {
        return findOptimalRoute(startEndIndex, 30);
    }

    /**
     * Güçlü optimizasyon (60 saniye)
     */
    public OptimizationResult findOptimalRouteStrong(int startEndIndex) {
        return findOptimalRoute(startEndIndex, 60);
    }
}