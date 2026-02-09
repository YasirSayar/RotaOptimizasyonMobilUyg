package com.example.rotaoptjv;

import android.content.Context;
import android.nfc.Tag;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Genetik algoritma kullanarak TSP (Gezgin Satıcı Problemi) çözümü sağlar.
 * Bu sınıf, konumlar arasında optimal rotayı genetik algoritma yaklaşımı ile bulur.
 * Veritabanındaki mesafe matrisini kullanır.
 */
public class GeneticTSPGoogleMaps {
    private static final String TAG = "GeneticTSPGoogleMaps";

    // Genetik algoritma parametreleri
    private static final int POPULATION_SIZE = 50;      // Popülasyon büyüklüğü
    private static final int MAX_GENERATIONS = 100;     // Maksimum iterasyon sayısı
    private static final double MUTATION_RATE = 0.15;   // Mutasyon olasılığı
    private static final double CROSSOVER_RATE = 0.85;  // Çaprazlama olasılığı
    private static final int TOURNAMENT_SIZE = 5;       // Turnuva seçimi için büyüklük

    private List<LatLng> locations;                    // Durak noktaları
    private double[][] distanceMatrix;                 // Duraklar arası uzaklık matrisi
    private Random random;                             // Rastgele sayı üreteci
    private Context context;                           // Context referansı
    private DatabaseHelper dbHelper;                   // Veritabanı yardımcısı
    private List<Integer> customerIds;                 // Müşteri ID'leri listesi

    /**
     * GeneticTSPGoogleMaps sınıfı kurucusu.
     * @param locations Rota oluşturulacak konum listesi
     * @param context Uygulama context'i
     */
    public GeneticTSPGoogleMaps(List<LatLng> locations, Context context) {
        this.locations = new ArrayList<>(locations);
        this.random = new Random();
        this.context = context;
        this.dbHelper = new DatabaseHelper(context);
        this.customerIds = new ArrayList<>();
    }

    /**
     * Veritabanından mesafe matrisini yükler.
     * Eksik mesafeler için Google Routes API kullanır, o da olmazsa Haversine.
     * @return İşlem başarılı ise true, aksi halde false döner
     */
    public boolean loadDistanceMatrixFromDatabase() {
        int size = locations.size();
        distanceMatrix = new double[size][size];

        // Lokasyonlara karşılık gelen müşteri ID'lerini al
        if (!loadCustomerIds()) {
            Log.e(TAG, "Müşteri ID'leri yüklenemedi");
            return false;
        }

        // İlk olarak tüm mesafeleri -1 ile doldur (hesaplanmamış)
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    distanceMatrix[i][j] = 0; // Kendisine mesafe 0
                } else {
                    distanceMatrix[i][j] = -1; // Hesaplanmamış
                }
            }
        }

        // Veritabanından mesafeleri yükle
        boolean allDistancesLoaded = true;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i != j) {
                    int fromId = customerIds.get(i);
                    int toId = customerIds.get(j);

                    double distance = dbHelper.getDistance(fromId, toId);
                    if (distance >= 0) {
                        distanceMatrix[i][j] = distance;
                    } else {
                        allDistancesLoaded = false;

                        // Kuş uçuşu hesaplayarak yedekleme yap
                        distance = calculateHaversineDistance(locations.get(i), locations.get(j));
                        distanceMatrix[i][j] = distance;

                        // Veritabanına kaydet (km cinsinden)
                        dbHelper.saveDistance(fromId, toId, distance);

                        Log.d(TAG, "Veritabanında olmayan mesafe hesaplandı: " +
                                fromId + " -> " + toId + " = " + distance + " km");
                    }
                }
            }
        }

        // Eğer eksik mesafeler varsa, API ile hesaplanmasını öner
        if (!allDistancesLoaded) {
            Log.w(TAG, "Bazı mesafeler veritabanında bulunamadı, Haversine formülü ile hesaplandı");
            Log.i(TAG, "Daha doğru sonuçlar için DatabaseHelper.calculateAllDistancesWithRoutesAPI() metodunu kullanabilirsiniz");
        }

        return true;
    }

    /**
     * Lokasyonlar için müşteri ID'lerini yükler.
     * Bu metod, her lokasyonun veritabanındaki hangi müşteriye karşılık geldiğini belirler.
     * @return İşlem başarılı ise true, değilse false
     */
    private boolean loadCustomerIds() {
        customerIds.clear();

        List<Customer> allCustomers = dbHelper.getAllCustomers();
        if (allCustomers.isEmpty()) {
            Log.e(TAG, "Veritabanında müşteri bulunamadı");
            return false;
        }

        // Her lokasyon için en yakın müşteriyi bul
        for (LatLng location : locations) {
            Customer nearestCustomer = findNearestCustomer(location, allCustomers);
            if (nearestCustomer != null) {
                customerIds.add(nearestCustomer.getId());
            } else {
                Log.e(TAG, "Bir lokasyon için müşteri eşleştirilemedi: " + location);
                return false;
            }
        }

        Log.d(TAG, "Müşteri ID'leri yüklendi: " + customerIds);
        return true;
    }

    /**
     * Verilen lokasyona en yakın müşteriyi bulur.
     * @param location Aranacak lokasyon
     * @param customers Müşteri listesi
     * @return En yakın müşteri veya bulunamazsa null
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

        // Çok küçük bir eşik değeri (1 metre) ile eşleşme kontrolü
        if (minDistance < 0.001) {
            return nearest;
        } else {
            // Eğer makul bir mesafe içinde müşteri bulunamazsa, en yakını kabul et
            return nearest;
        }
    }

    /**
     * İki LatLng arasındaki Haversine mesafe hesaplaması (kilometre cinsinden).
     * @param p1 Birinci konum
     * @param p2 İkinci konum
     * @return İki konum arasındaki mesafe (kilometre)
     */
    private double calculateHaversineDistance(LatLng p1, LatLng p2) {
        final int R = 6371; // Dünya yarıçapı, kilometre cinsinden

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

        return R * c; // Mesafe (kilometre cinsinden)
    }

    /**
     * Genetik algoritmayı çalıştırarak en iyi rotayı bulur.
     * @param startEndIndex Başlangıç ve bitiş durak indeksi (aynı durak)
     * @return Ziyaret sırasına göre durak indekslerinin listesi
     */
    public List<Integer> findOptimalRoute(int startEndIndex) {
        int numLocations = locations.size();
        Log.d(TAG, "StartEnd İndex (findOptimalRoute): " + startEndIndex + ", locations: " + locations.size());

        if (numLocations <= 2) {
            return Arrays.asList(0, 0); // En az 2 durak olmalı
        }

        // Önce mesafe matrisini veritabanından yükle
        boolean matrixSuccess = loadDistanceMatrixFromDatabase();
        if (!matrixSuccess) {
            Log.w(TAG, "Veritabanından mesafe matrisi oluşturulamadı, yedek metod kullanılacak");
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
            //Log.d(TAG,"kontrol"+generation);
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
        List<Integer> routeIndices = arrayToList(bestIndividual.getRoute());

        // İsteğe bağlı olarak bulunan rotadaki müşteri ID'lerini loglayabiliriz
        if (customerIds.size() > 0) {
            List<Integer> customerRoute = new ArrayList<>();
            for (int index : routeIndices) {
                if (index < customerIds.size()) {
                    customerRoute.add(customerIds.get(index));
                }
            }
            Log.d(TAG, "En iyi rotadaki müşteri ID'leri: " + customerRoute);
        }

        return routeIndices;
    }

    /**
     * Haversine formülü kullanarak mesafe matrisini hesaplar.
     * Veritabanı başarısız olduğunda yedek olarak kullanılır.
     */
    private void calculateDistanceMatrixWithHaversine() {
        int size = locations.size();
        distanceMatrix = new double[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    distanceMatrix[i][j] = 0;
                } else {
                    // İki konum arasındaki mesafeyi hesapla (kilometre cinsinden)
                    distanceMatrix[i][j] = calculateHaversineDistance(locations.get(i), locations.get(j));

                    // Eğer customerIds doldurulmuşsa, mesafeyi veritabanına da kaydet
                    if (customerIds.size() == locations.size()) {
                        dbHelper.saveDistance(customerIds.get(i), customerIds.get(j), distanceMatrix[i][j]);
                    }
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
     * Bulunan rota indekslerini müşteri ID'lerine dönüştürür
     * @param routeIndices Rota indeksleri listesi
     * @return Müşteri ID'leri listesi
     */
    public List<Integer> convertRouteToCustomerIds(List<Integer> routeIndices) {
        if (customerIds.isEmpty() || customerIds.size() < locations.size()) {
            Log.e(TAG, "Müşteri ID'leri yüklenemedi");
            return routeIndices; // Dönüştürme yapılamaz, orijinal listeyi döndür
        }

        List<Integer> customerRoute = new ArrayList<>();
        for (int index : routeIndices) {
            if (index < customerIds.size()) {
                customerRoute.add(customerIds.get(index));
            } else {
                customerRoute.add(-1); // Geçersiz indeks için -1 ekle
            }
        }

        return customerRoute;
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
                int from = route[i];
                int to = route[i + 1];

                // Mesafe matrisi sınırları kontrolü
                if (from < distanceMatrix.length && to < distanceMatrix[from].length) {
                    double legDistance = distanceMatrix[from][to];

                    // Eksik mesafe kontrolü
                    if (legDistance < 0) {
                        // Eksik mesafe durumunda büyük bir ceza puanı ekle
                        // Bu, algoritmanın eksik mesafeleri içeren rotalardan kaçınmasını sağlar
                        totalDistance += 1000000; // Çok büyük bir ceza
                    } else {
                        totalDistance += legDistance;
                    }
                } else {
                    // Matris sınırları dışındaysa büyük bir ceza
                    totalDistance += 1000000;
                }
            }

            this.fitness = totalDistance;
        }
    }
}