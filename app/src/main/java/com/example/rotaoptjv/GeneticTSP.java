package com.example.rotaoptjv;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Genetik algoritma kullanarak TSP (Gezgin Satıcı Problemi) çözümü sağlar.
 * Bu sınıf, konumlar arasında optimal rotayı genetik algoritma yaklaşımı ile bulur.
 * Google Routes API kullanarak gerçek yol mesafelerini alır.
 */
public class GeneticTSP {
    private static final String TAG = "GeneticTSP";

    // Genetik algoritma parametreleri
    private static final int POPULATION_SIZE = 50;      // Popülasyon büyüklüğü
    private static final int MAX_GENERATIONS = 100;     // Maksimum iterasyon sayısı
    private static final double MUTATION_RATE = 0.15;   // Mutasyon olasılığı
    private static final double CROSSOVER_RATE = 0.85;  // Çaprazlama olasılığı
    private static final int TOURNAMENT_SIZE = 5;       // Turnuva seçimi için büyüklük

    // Google Routes API için parametreler
    private static final String ROUTES_API_URL = "https://routes.googleapis.com/directions/v2:computeRoutes";
    private static final String API_KEY = BuildConfig.MAPS_API_KEY; // API anahtarınızı buraya ekleyin
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private List<GeoPoint> locations;                   // Durak noktaları
    private double[][] distanceMatrix;                  // Duraklar arası uzaklık matrisi
    private Random random;                              // Rastgele sayı üreteci
    private OkHttpClient client;                        // HTTP istekleri için
    private Context context;                            // Context referansı

    /**
     * GeneticTSP sınıfı kurucusu.
     * @param locations Rota oluşturulacak konum listesi
     * @param context Uygulama context'i
     */
    public GeneticTSP(List<GeoPoint> locations, Context context) {
        this.locations = new ArrayList<>(locations);
        this.random = new Random();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.context = context;
    }

    /**
     * Google Routes API kullanarak tüm konumlar arasındaki gerçek yol mesafelerini hesaplar.
     * Bu işlem API çağrılarını kolaylaştırmak için bir mesafe matrisi oluşturur.
     * @return İşlem başarılı ise true, aksi halde false döner
     */
    public boolean calculateDistanceMatrixWithRoutesAPI() {
        int size = locations.size();
        distanceMatrix = new double[size][size];
        AtomicBoolean success = new AtomicBoolean(true);

        // İstek sayısını azaltmak için API istek havuzu oluşturma
        ExecutorService executor = Executors.newFixedThreadPool(4); // Paralel istekler için

        // Başlangıçta tüm mesafeleri -1 olarak işaretle (hesaplanmamış)
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    distanceMatrix[i][j] = 0; // Kendisine olan mesafe 0
                } else {
                    distanceMatrix[i][j] = -1; // Hesaplanmadı
                }
            }
        }

        // İsteklerin tamamlanmasını beklemek için
        CountDownLatch latch = new CountDownLatch(size * (size-1) / 2);

        // Her bir konum çifti için mesafe hesapla
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) { // Sadece üst üçgensel matris hesaplanır
                final int origin = i;
                final int destination = j;

                executor.execute(() -> {
                    try {
                        // API'den mesafeyi al
                        double distance = getRouteDistanceFromAPI(origin, destination);
                        if (distance >= 0) {
                            distanceMatrix[origin][destination] = distance;
                            distanceMatrix[destination][origin] = distance; // Simetrik matris
                        } else {
                            // API hatası durumunda kuş uçuşu hesapla (yedek)
                            Log.w(TAG, "API hatası, kuş uçuşu mesafe kullanılıyor: " + origin + " -> " + destination);
                            double fallbackDistance = calculateHaversineDistance(locations.get(origin), locations.get(destination));
                            distanceMatrix[origin][destination] = fallbackDistance;
                            distanceMatrix[destination][origin] = fallbackDistance;
                            Log.d("API", "İstek: from(" + locations.get(origin).toString() + "," + locations.get(origin).toString() +
                                    ") to(" + locations.get(destination).toString() + "," + locations.get(destination).toString() + ")");

                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Mesafe hesaplama hatası: " + e.getMessage());
                        success.set(false);
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        try {
            // Tüm istekler tamamlanana kadar bekle (maksimum 60 saniye)
            boolean completed = latch.await(60, TimeUnit.SECONDS);
            if (!completed) {
                Log.e(TAG, "Zaman aşımı: Tüm mesafe hesaplamaları tamamlanamadı.");
                success.set(false);
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Mesafe hesaplama beklemesi kesintiye uğradı: " + e.getMessage());
            Thread.currentThread().interrupt();
            success.set(false);
        } finally {
            executor.shutdown();
        }

        // Matris kontrolü - eksik değer var mı?
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (distanceMatrix[i][j] < 0 && i != j) {
                    // Eksik değerleri Haversine mesafesi ile doldur
                    distanceMatrix[i][j] = calculateHaversineDistance(locations.get(i), locations.get(j));
                    Log.w(TAG, "Eksik mesafe dolduruldu: " + i + " -> " + j);
                }
            }
        }

        return success.get();
    }

    /**
     * Google Routes API kullanarak iki konum arasındaki gerçek yol mesafesini alır.
     * @param originIndex Başlangıç konumu indeksi
     * @param destIndex Hedef konumu indeksi
     * @return Metre cinsinden mesafe, hata durumunda -1
     */
    private double getRouteDistanceFromAPI(int originIndex, int destIndex) {
        // Başlangıç ve bitiş konumları
        GeoPoint origin = locations.get(originIndex);
        GeoPoint destination = locations.get(destIndex);

        // JSON isteği oluştur
        JSONObject requestJson = new JSONObject();
        try {
            // Origin
            JSONObject originLocation = new JSONObject()
                    .put("location", new JSONObject()
                            .put("latLng", new JSONObject()
                                    .put("latitude", origin.getLatitude())
                                    .put("longitude", origin.getLongitude())));
            //Destination
            JSONObject destLocation = new JSONObject()
                    .put("location", new JSONObject()
                            .put("latLng", new JSONObject()
                                    .put("latitude", destination.getLatitude())
                                    .put("longitude", destination.getLongitude())));


            // Route modifiers
            JSONObject routeModifiers = new JSONObject()
                    .put("avoidTolls", false)
                    .put("avoidHighways", false)
                    .put("avoidFerries", false);

            // Tam istek
            requestJson.put("origin", originLocation)
                    .put("destination", destLocation)
                    .put("travelMode", "DRIVE")
                    .put("routingPreference", "TRAFFIC_AWARE")
                    .put("computeAlternativeRoutes", false)
                    .put("routeModifiers", routeModifiers)
                    .put("languageCode", "tr-TR")
                    .put("units", "METRIC");

        } catch (JSONException e) {
            Log.e(TAG, "JSON oluşturma hatası: " + e.getMessage());
            return -1;
        }

        // Senkron çağrı için latch
        final CountDownLatch responseLatch = new CountDownLatch(1);
        final double[] resultDistance = {-1};

        // API isteği oluştur
        Request request = new Request.Builder()
                .url(ROUTES_API_URL)
                .addHeader("X-Goog-Api-Key", API_KEY)
                .addHeader("X-Goog-FieldMask", "routes.legs.distanceMeters")
                .post(RequestBody.create(JSON, requestJson.toString()))
                .build();

        // Asenkron istek gönder
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "API çağrısı başarısız: " + e.getMessage());
                responseLatch.countDown();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "API hatası: " + response.code() + " - " + response.message() + "\n" +
                                "Gövde: " + response.body().string());

                        responseLatch.countDown();
                        return;
                    }

                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    JSONArray routes = jsonResponse.getJSONArray("routes");

                    if (routes.length() > 0) {
                        JSONObject route = routes.getJSONObject(0);
                        double distanceMeters = route
                                .getJSONArray("legs")
                                .getJSONObject(0)
                                .getDouble("distanceMeters");

                        resultDistance[0] = distanceMeters;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "API yanıtı işleme hatası: " + e.getMessage());
                } finally {
                    responseLatch.countDown();
                }
            }
        });

        try {
            // Yanıt için maksimum 10 saniye bekle
            boolean received = responseLatch.await(10, TimeUnit.SECONDS);
            if (!received) {
                Log.e(TAG, "API yanıtı zaman aşımına uğradı: " + originIndex + " -> " + destIndex);
                return -1;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Yanıt bekleme kesintiye uğradı: " + e.getMessage());
            Thread.currentThread().interrupt();
            return -1;
        }

        return resultDistance[0];
    }

    /**
     * İki GeoPoint arasındaki Haversine mesafe hesaplaması (metre cinsinden).
     * API hatası durumunda yedek olarak kullanılır.
     * @param p1 Birinci konum
     * @param p2 İkinci konum
     * @return İki konum arasındaki mesafe (metre)
     */
    private double calculateHaversineDistance(GeoPoint p1, GeoPoint p2) {
        final int R = 6371000; // Dünya yarıçapı, metre cinsinden

        double lat1 = Math.toRadians(p1.getLatitude());
        double lon1 = Math.toRadians(p1.getLongitude());
        double lat2 = Math.toRadians(p2.getLatitude());
        double lon2 = Math.toRadians(p2.getLongitude());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c; // Mesafe (metre cinsinden)
    }

    /**
     * Genetik algoritmayı çalıştırarak en iyi rotayı bulur.
     * @param startEndIndex Başlangıç ve bitiş durak indeksi (aynı durak)
     * @return Ziyaret sırasına göre durak indekslerinin listesi
     */
    public List<Integer> findOptimalRoute(int startEndIndex) {
        int numLocations = locations.size();
        Log.d(TAG, "Startend İndex (findoptimalroute)" + startEndIndex + "locations : "+ locations + locations.size());
        if (numLocations <= 2) {
            return Arrays.asList(0, 0); // En az 2 durak olmalı
        }

        // Önce mesafe matrisini hesapla
        boolean matrixSuccess = calculateDistanceMatrixWithRoutesAPI();
        if (!matrixSuccess) {
            Log.w(TAG, "Routes API ile mesafe matrisi oluşturulamadı, yedek metod kullanılacak");
            calculateDistanceMatrixWithHaversine();
        }

        // Başlangıç popülasyonunu oluştur
        List<Individual> population = initializePopulation(startEndIndex, numLocations);
        // Her birey için uygunluk değerini hesapla
        evaluatePopulation(population);

        // En iyi bireyi sakla
        Individual bestIndividual = findBestIndividual(population);

        Log.d(TAG, "Initial best distance: " + bestIndividual.getFitness());

        // Kuşaklar boyunca gelişim
        for (int generation = 0; generation < MAX_GENERATIONS; generation++) {
            // Yeni popülasyonu oluştur
            List<Individual> newPopulation = new ArrayList<>();

            // Elitizm: En iyi bireyi doğrudan bir sonraki nesle aktar
            newPopulation.add(bestIndividual);

            // Yeni popülasyonu doldur
            while (newPopulation.size() < POPULATION_SIZE) {
                // Seçim: Turnuva yöntemi ile ebeveyn seç
                Individual parent1 = tournamentSelection(population);
                Individual parent2 = tournamentSelection(population);

                // Çaprazlama
                Individual child;
                if (random.nextDouble() < CROSSOVER_RATE) {
                    child = crossover(parent1, parent2, startEndIndex);
                } else {
                    child = new Individual(parent1); // Ebeveynin kopyası
                }

                // Mutasyon
                if (random.nextDouble() < MUTATION_RATE) {
                    mutate(child, startEndIndex);
                }

                // Uygunluk değerini hesapla
                child.calculateFitness(distanceMatrix);

                // Yeni popülasyona ekle
                newPopulation.add(child);
            }

            // Popülasyonu güncelle
            population = newPopulation;

            // En iyi bireyi güncelle
            Individual currentBest = findBestIndividual(population);
            if (currentBest.getFitness() < bestIndividual.getFitness()) {
                bestIndividual = currentBest;
                Log.d(TAG, "Generation " + generation + ", New best distance: " + bestIndividual.getFitness());
            }
        }

        Log.d(TAG, "Final best distance: " + bestIndividual.getFitness() +
                ", Route: " + Arrays.toString(bestIndividual.getRoute()));

        // En iyi rotayı liste olarak döndür
        return arrayToList(bestIndividual.getRoute());
    }

    /**
     * Haversine formülü kullanarak mesafe matrisini hesaplar.
     * Routes API başarısız olduğunda yedek olarak kullanılır.
     */
    private void calculateDistanceMatrixWithHaversine() {
        int size = locations.size();
        distanceMatrix = new double[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    distanceMatrix[i][j] = 0;
                } else {
                    // İki konum arasındaki mesafeyi hesapla (metre cinsinden)
                    distanceMatrix[i][j] = calculateHaversineDistance(locations.get(i), locations.get(j));
                }
            }
        }
    }

    /**
     * Başlangıç popülasyonunu oluşturur.
     * @param startEndIndex Başlangıç ve bitiş durak indeksi
     * @param numLocations Toplam konum sayısı
     * @return Rastgele oluşturulmuş bireylerden oluşan popülasyon
     */
    private List<Individual> initializePopulation(int startEndIndex, int numLocations) {
        List<Individual> population = new ArrayList<>();

        for (int i = 0; i < POPULATION_SIZE; i++) {
            Individual individual = new Individual(numLocations + 1);
            int[] route = new int[numLocations + 1];
            route[0] = startEndIndex;
            route[numLocations] = startEndIndex;

            // Ara durakları rastgele doldur
            List<Integer> middlePoints = new ArrayList<>();
            for (int j = 0; j < numLocations; j++) {
                if (j != startEndIndex) {
                    middlePoints.add(j);
                }
            }
            Collections.shuffle(middlePoints);

            // Ara durakları yerleştir
            for (int j = 0; j < middlePoints.size(); j++) {
                route[j + 1] = middlePoints.get(j);
            }

            individual.setRoute(route);
            individual.calculateFitness(distanceMatrix);
            population.add(individual);
        }

        return population;
    }

    /**
     * Tüm popülasyondaki bireylerin uygunluk değerlerini hesaplar.
     * @param population Değerlendirilecek popülasyon
     */
    private void evaluatePopulation(List<Individual> population) {
        for (Individual individual : population) {
            individual.calculateFitness(distanceMatrix);
        }
    }

    /**
     * Popülasyondan turnuva yöntemiyle birey seçer.
     * @param population Seçimin yapılacağı popülasyon
     * @return Seçilen birey
     */
    private Individual tournamentSelection(List<Individual> population) {
        List<Individual> tournament = new ArrayList<>();

        // Turnuva için rastgele bireyler seç
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            int randomIndex = random.nextInt(population.size());
            tournament.add(population.get(randomIndex));
        }

        // En iyi uygunluk değerine sahip bireyi döndür
        return findBestIndividual(tournament);
    }

    /**
     * İki ebeveyn arasında Order Crossover (OX) çaprazlaması yapar.
     * @param parent1 Birinci ebeveyn
     * @param parent2 İkinci ebeveyn
     * @param fixedIndex Sabit kalması gereken başlangıç/bitiş indeksi
     * @return Çaprazlama sonucu oluşan çocuk birey
     */
    private Individual crossover(Individual parent1, Individual parent2, int fixedIndex) {
        int routeLength = parent1.getRoute().length;
        int[] childRoute = new int[routeLength];

        // Başlangıç ve bitiş noktalarını sabit tut
        childRoute[0] = fixedIndex;
        childRoute[routeLength - 1] = fixedIndex;

        // Çaprazlama için kesim noktaları belirle (başlangıç ve bitiş hariç)
        int start = 1 + random.nextInt(routeLength - 3);
        int end = start + 1 + random.nextInt(routeLength - start - 2);

        // Parent1'den seçilen segmenti çocuğa aktar
        boolean[] added = new boolean[routeLength];
        added[fixedIndex] = true; // Başlangıç/bitiş noktasını işaretle

        for (int i = start; i <= end; i++) {
            childRoute[i] = parent1.getRoute()[i];
            added[childRoute[i]] = true;
        }

        // Parent2'den kalan şehirleri ekle
        int currentPos = 1; // Başlangıç noktasından sonra

        for (int i = 1; i < routeLength - 1; i++) {
            int cityFromParent2 = parent2.getRoute()[i];

            if (!added[cityFromParent2]) {
                // Eğer currentPos zaten doluysa, boş bir yer bul
                while (currentPos >= start && currentPos <= end) {
                    currentPos++;
                }

                if (currentPos < routeLength - 1) {
                    childRoute[currentPos] = cityFromParent2;
                    added[cityFromParent2] = true;
                    currentPos++;
                }
            }
        }

        Individual child = new Individual(routeLength);
        child.setRoute(childRoute);
        return child;
    }

    /**
     * Bireye mutasyon uygular - iki durak arasında yer değiştirme yapar.
     * @param individual Mutasyon uygulanacak birey
     * @param fixedIndex Sabit kalması gereken başlangıç/bitiş indeksi
     */
    private void mutate(Individual individual, int fixedIndex) {
        int routeLength = individual.getRoute().length;

        if (routeLength <= 3) return; // Mutasyon için yeterli durak yoksa çık

        // Rastgele iki indeks seç (başlangıç ve bitiş hariç)
        int pos1 = 1 + random.nextInt(routeLength - 2);
        int pos2 = 1 + random.nextInt(routeLength - 2);

        // Farklı pozisyonlar olana kadar tekrar seç
        while (pos1 == pos2) {
            pos2 = 1 + random.nextInt(routeLength - 2);
        }

        // İki pozisyonu değiştir
        int[] route = individual.getRoute();
        int temp = route[pos1];
        route[pos1] = route[pos2];
        route[pos2] = temp;

        individual.setRoute(route);
    }

    /**
     * Verilen popülasyondaki en iyi bireyi bulur.
     * @param population Arama yapılacak popülasyon
     * @return En düşük uygunluk değerine sahip birey (en kısa rota)
     */
    private Individual findBestIndividual(List<Individual> population) {
        return Collections.min(population, Comparator.comparing(Individual::getFitness));
    }

    /**
     * Diziyi listeye dönüştürür.
     * @param array Dönüştürülecek dizi
     * @return Dönüştürülmüş liste
     */
    private List<Integer> arrayToList(int[] array) {
        List<Integer> list = new ArrayList<>();
        for (int value : array) {
            list.add(value);
        }
        return list;
    }

    /**
     * TSP bireyini temsil eden iç sınıf.
     * Her birey bir rotayı ve onun uygunluk değerini (toplam mesafe) tutar.
     */
    private static class Individual {
        private int[] route;
        private double fitness;

        /**
         * Belirli rota uzunluğuna sahip boş birey oluşturur.
         * @param routeLength Rota uzunluğu
         */
        public Individual(int routeLength) {
            this.route = new int[routeLength];
            this.fitness = Double.MAX_VALUE;
        }

        /**
         * Var olan bireyden kopya oluşturur.
         * @param other Kopyalanacak birey
         */
        public Individual(Individual other) {
            this.route = Arrays.copyOf(other.route, other.route.length);
            this.fitness = other.fitness;
        }

        /**
         * Rotayı ayarlar.
         * @param route Yeni rota
         */
        public void setRoute(int[] route) {
            this.route = Arrays.copyOf(route, route.length);
        }

        /**
         * Rotayı döndürür.
         * @return Rota
         */
        public int[] getRoute() {
            return route;
        }

        /**
         * Uygunluk değerini döndürür (toplam mesafe).
         * @return Uygunluk değeri
         */
        public double getFitness() {
            return fitness;
        }

        /**
         * Rota için toplam mesafeyi hesaplayarak uygunluk değerini belirler.
         * @param distanceMatrix Lokasyonlar arası mesafe matrisi
         */
        public void calculateFitness(double[][] distanceMatrix) {
            double totalDistance = 0;

            for (int i = 0; i < route.length - 1; i++) {
                totalDistance += distanceMatrix[route[i]][route[i + 1]];
            }

            this.fitness = totalDistance;
        }
    }
}