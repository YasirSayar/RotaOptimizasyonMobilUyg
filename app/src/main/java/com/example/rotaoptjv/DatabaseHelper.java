package com.example.rotaoptjv;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "route_management.db";
    private static final int DATABASE_VERSION = 3;

    // API related constants
    private static final String ROUTES_API_URL = "https://routes.googleapis.com/directions/v2:computeRoutes";
    private static final String API_KEY = BuildConfig.MAPS_API_KEY; // API anahtarınızı buraya ekleyin
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();

    // Customer table
    private static final String TABLE_CUSTOMERS = "customers";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_PHONE = "phone";
    private static final String COLUMN_ADDRESS = "address";
    private static final String COLUMN_LATITUDE = "latitude";
    private static final String COLUMN_LONGITUDE = "longitude";
    private static final String COLUMN_STATUS = "status";

    // Distance matrix table
    private static final String TABLE_DISTANCES = "distances";
    private static final String COLUMN_FROM_ID = "from_id";
    private static final String COLUMN_TO_ID = "to_id";
    private static final String COLUMN_DISTANCE = "distance";

    private Context context;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Müşteri tablosunu oluştur
        String CREATE_CUSTOMERS_TABLE = "CREATE TABLE " + TABLE_CUSTOMERS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_NAME + " TEXT,"
                + COLUMN_PHONE + " TEXT,"
                + COLUMN_ADDRESS + " TEXT,"
                + COLUMN_LATITUDE + " REAL,"
                + COLUMN_LONGITUDE + " REAL,"
                + COLUMN_STATUS + " INTEGER DEFAULT 0" // Varsayılan olarak rotaya dahil değil (0)
                + ")";
        db.execSQL(CREATE_CUSTOMERS_TABLE);

        // Mesafe matrisi tablosunu oluştur
        String CREATE_DISTANCES_TABLE = "CREATE TABLE " + TABLE_DISTANCES + "("
                + COLUMN_FROM_ID + " INTEGER,"
                + COLUMN_TO_ID + " INTEGER,"
                + COLUMN_DISTANCE + " REAL,"
                + "PRIMARY KEY (" + COLUMN_FROM_ID + ", " + COLUMN_TO_ID + "),"
                + "FOREIGN KEY (" + COLUMN_FROM_ID + ") REFERENCES " + TABLE_CUSTOMERS + "(" + COLUMN_ID + ") ON DELETE CASCADE,"
                + "FOREIGN KEY (" + COLUMN_TO_ID + ") REFERENCES " + TABLE_CUSTOMERS + "(" + COLUMN_ID + ") ON DELETE CASCADE"
                + ")";
        db.execSQL(CREATE_DISTANCES_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Status sütununu mevcut tabloya ekle
            db.execSQL("ALTER TABLE " + TABLE_CUSTOMERS + " ADD COLUMN " + COLUMN_STATUS + " INTEGER DEFAULT 0");
        }

        if (oldVersion < 3) {
            // Mesafe matrisi tablosunu oluştur
            String CREATE_DISTANCES_TABLE = "CREATE TABLE " + TABLE_DISTANCES + "("
                    + COLUMN_FROM_ID + " INTEGER,"
                    + COLUMN_TO_ID + " INTEGER,"
                    + COLUMN_DISTANCE + " REAL,"
                    + "PRIMARY KEY (" + COLUMN_FROM_ID + ", " + COLUMN_TO_ID + "),"
                    + "FOREIGN KEY (" + COLUMN_FROM_ID + ") REFERENCES " + TABLE_CUSTOMERS + "(" + COLUMN_ID + ") ON DELETE CASCADE,"
                    + "FOREIGN KEY (" + COLUMN_TO_ID + ") REFERENCES " + TABLE_CUSTOMERS + "(" + COLUMN_ID + ") ON DELETE CASCADE"
                    + ")";
            db.execSQL(CREATE_DISTANCES_TABLE);
        }
    }

    // Customer CRUD Operations

    // Add a new customer
    public long addCustomer(Customer customer) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_NAME, customer.getName());
        values.put(COLUMN_PHONE, customer.getPhoneNumber());
        values.put(COLUMN_ADDRESS, customer.getAddress());
        values.put(COLUMN_LATITUDE, customer.getLatitude());
        values.put(COLUMN_LONGITUDE, customer.getLongitude());
        values.put(COLUMN_STATUS, customer.getStatus());

        long id = db.insert(TABLE_CUSTOMERS, null, values);
        customer.setId((int) id);
        db.close();
        return id;
    }

    // Get a single customer
    public Customer getCustomer(int id) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_CUSTOMERS,
                new String[]{COLUMN_ID, COLUMN_NAME, COLUMN_PHONE, COLUMN_ADDRESS, COLUMN_LATITUDE, COLUMN_LONGITUDE, COLUMN_STATUS},
                COLUMN_ID + "=?", new String[]{String.valueOf(id)},
                null, null, null, null);

        Customer customer = null;

        if (cursor != null && cursor.moveToFirst()) {
            int idIndex = cursor.getColumnIndex(COLUMN_ID);
            int nameIndex = cursor.getColumnIndex(COLUMN_NAME);
            int phoneIndex = cursor.getColumnIndex(COLUMN_PHONE);
            int addressIndex = cursor.getColumnIndex(COLUMN_ADDRESS);
            int latIndex = cursor.getColumnIndex(COLUMN_LATITUDE);
            int longIndex = cursor.getColumnIndex(COLUMN_LONGITUDE);
            int statusIndex = cursor.getColumnIndex(COLUMN_STATUS);

            customer = new Customer();

            if (idIndex != -1) {
                customer.setId(cursor.getInt(idIndex));
            }

            if (nameIndex != -1) {
                customer.setName(cursor.getString(nameIndex));
            }

            if (phoneIndex != -1) {
                customer.setPhoneNumber(cursor.getString(phoneIndex));
            }

            if (addressIndex != -1) {
                customer.setAddress(cursor.getString(addressIndex));
            }

            if (latIndex != -1) {
                customer.setLatitude(cursor.getDouble(latIndex));
            }

            if (longIndex != -1) {
                customer.setLongitude(cursor.getDouble(longIndex));
            }

            if (statusIndex != -1) {
                customer.setStatus(cursor.getInt(statusIndex));
            } else {
                customer.setStatus(0);
            }

            cursor.close();
        }

        db.close();
        return customer;
    }

    // Get all customers
    public List<Customer> getAllCustomers() {
        List<Customer> customerList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_CUSTOMERS;

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            int idIndex = cursor.getColumnIndex(COLUMN_ID);
            int nameIndex = cursor.getColumnIndex(COLUMN_NAME);
            int phoneIndex = cursor.getColumnIndex(COLUMN_PHONE);
            int addressIndex = cursor.getColumnIndex(COLUMN_ADDRESS);
            int latIndex = cursor.getColumnIndex(COLUMN_LATITUDE);
            int longIndex = cursor.getColumnIndex(COLUMN_LONGITUDE);
            int statusIndex = cursor.getColumnIndex(COLUMN_STATUS);

            do {
                Customer customer = new Customer();

                if (idIndex != -1) {
                    customer.setId(cursor.getInt(idIndex));
                }

                if (nameIndex != -1) {
                    customer.setName(cursor.getString(nameIndex));
                }

                if (phoneIndex != -1) {
                    customer.setPhoneNumber(cursor.getString(phoneIndex));
                }

                if (addressIndex != -1) {
                    customer.setAddress(cursor.getString(addressIndex));
                }

                if (latIndex != -1) {
                    customer.setLatitude(cursor.getDouble(latIndex));
                }

                if (longIndex != -1) {
                    customer.setLongitude(cursor.getDouble(longIndex));
                }

                // Status alanı için varsayılan değer 0
                if (statusIndex != -1) {
                    customer.setStatus(cursor.getInt(statusIndex));
                } else {
                    customer.setStatus(0);
                }

                customerList.add(customer);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return customerList;
    }

    // Get customers in route
    public List<Customer> getCustomersInRoute() {
        List<Customer> customerList = new ArrayList<>();

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_CUSTOMERS,
                null,
                COLUMN_STATUS + "=?", new String[]{"1"},
                null, null, null);

        if (cursor.moveToFirst()) {
            int idIndex = cursor.getColumnIndex(COLUMN_ID);
            int nameIndex = cursor.getColumnIndex(COLUMN_NAME);
            int phoneIndex = cursor.getColumnIndex(COLUMN_PHONE);
            int addressIndex = cursor.getColumnIndex(COLUMN_ADDRESS);
            int latIndex = cursor.getColumnIndex(COLUMN_LATITUDE);
            int longIndex = cursor.getColumnIndex(COLUMN_LONGITUDE);

            do {
                Customer customer = new Customer();

                if (idIndex != -1) {
                    customer.setId(cursor.getInt(idIndex));
                }

                if (nameIndex != -1) {
                    customer.setName(cursor.getString(nameIndex));
                }

                if (phoneIndex != -1) {
                    customer.setPhoneNumber(cursor.getString(phoneIndex));
                }

                if (addressIndex != -1) {
                    customer.setAddress(cursor.getString(addressIndex));
                }

                if (latIndex != -1) {
                    customer.setLatitude(cursor.getDouble(latIndex));
                }

                if (longIndex != -1) {
                    customer.setLongitude(cursor.getDouble(longIndex));
                }

                customer.setStatus(1); // Bu listedeki müşteriler rotada olduğu için status=1 olacak

                customerList.add(customer);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return customerList;
    }

    // Update a customer
    public int updateCustomer(Customer customer) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_NAME, customer.getName());
        values.put(COLUMN_PHONE, customer.getPhoneNumber());
        values.put(COLUMN_ADDRESS, customer.getAddress());
        values.put(COLUMN_LATITUDE, customer.getLatitude());
        values.put(COLUMN_LONGITUDE, customer.getLongitude());
        values.put(COLUMN_STATUS, customer.getStatus());

        int result = db.update(TABLE_CUSTOMERS, values, COLUMN_ID + "=?",
                new String[]{String.valueOf(customer.getId())});
        db.close();
        return result;
    }

    // Update customer route status
    public int updateCustomerStatus(int customerId, int status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_STATUS, status);

        int result = db.update(TABLE_CUSTOMERS, values, COLUMN_ID + "=?",
                new String[]{String.valueOf(customerId)});
        db.close();
        return result;
    }

    // Delete a customer
    public void deleteCustomer(Customer customer) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Müşteriyi sil
        db.delete(TABLE_CUSTOMERS, COLUMN_ID + "=?",
                new String[]{String.valueOf(customer.getId())});

        // Mesafe matrisinden ilgili kayıtları sil
        db.delete(TABLE_DISTANCES, COLUMN_FROM_ID + "=? OR " + COLUMN_TO_ID + "=?",
                new String[]{String.valueOf(customer.getId()), String.valueOf(customer.getId())});

        db.close();
    }

    // Mesafe matrisi işlemleri

    // İki müşteri arasındaki mesafeyi kaydet
    public void saveDistance(int fromId, int toId, double distance) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_FROM_ID, fromId);
        values.put(COLUMN_TO_ID, toId);
        values.put(COLUMN_DISTANCE, distance);

        // REPLACE kullanarak, varsa güncelle yoksa ekle
        db.insertWithOnConflict(TABLE_DISTANCES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    // İki müşteri arasındaki mesafeyi getir
    public double getDistance(int fromId, int toId) {
        SQLiteDatabase db = this.getReadableDatabase();
        double distance = -1;

        Cursor cursor = db.query(TABLE_DISTANCES,
                new String[]{COLUMN_DISTANCE},
                COLUMN_FROM_ID + "=? AND " + COLUMN_TO_ID + "=?",
                new String[]{String.valueOf(fromId), String.valueOf(toId)},
                null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            int distanceIndex = cursor.getColumnIndex(COLUMN_DISTANCE);
            if (distanceIndex != -1) {
                distance = cursor.getDouble(distanceIndex);
            }
            cursor.close();
        }

        db.close();
        return distance;
    }

    // Rotada olan müşteriler için mesafe matrisini getir
    public double[][] getRouteDistanceMatrix() {
        List<Customer> routeCustomers = getCustomersInRoute();
        int size = routeCustomers.size();
        double[][] matrix = new double[size][size];

        // Müşteri ID'lerini indeks konumlarıyla eşleştirmek için map
        int[] customerIds = new int[size];
        for (int i = 0; i < size; i++) {
            customerIds[i] = routeCustomers.get(i).getId();
        }

        // Mesafeleri doldur
        SQLiteDatabase db = this.getReadableDatabase();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    matrix[i][j] = 0;  // Kendine mesafe 0
                } else {
                    int fromId = customerIds[i];
                    int toId = customerIds[j];
                    double distance = getDistance(fromId, toId);
                    matrix[i][j] = distance;
                }
            }
        }

        return matrix;
    }

    // Bir müşterinin tüm mesafe verilerini sil
    public void deleteCustomerDistances(int customerId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_DISTANCES,
                COLUMN_FROM_ID + "=? OR " + COLUMN_TO_ID + "=?",
                new String[]{String.valueOf(customerId), String.valueOf(customerId)});
        db.close();
    }

    // Bir müşterinin konumu değiştiğinde tüm mesafelerini Google Routes API ile hesapla ve güncelle
    public void updateCustomerDistances(int customerId, double latitude, double longitude) {
        // Önce bu müşteriye ait tüm mesafeleri sil
        deleteCustomerDistances(customerId);

        // Diğer tüm müşterilerle arasındaki mesafeleri hesapla ve kaydet
        List<Customer> allCustomers = getAllCustomers();
        Customer fromCustomer = getCustomer(customerId);

        if (fromCustomer == null) return;

        // Paralel istekler için ExecutorService
        ExecutorService executor = Executors.newFixedThreadPool(4);
        final AtomicBoolean success = new AtomicBoolean(true);

        // Tüm isteklerin tamamlanmasını beklemek için
        int otherCustomersCount = allCustomers.size() - 1; // Kendisi hariç
        CountDownLatch latch = new CountDownLatch(otherCustomersCount);

        for (Customer toCustomer : allCustomers) {
            if (toCustomer.getId() == customerId) continue; // Kendisi ile mesafe hesaplanmaz

            executor.execute(() -> {
                try {
                    // API'den mesafeyi al
                    double distance = getRouteDistanceFromAPI(fromCustomer, toCustomer);
                    if (distance >= 0) {
                        // Mesafeyi her iki yönde de kaydet (kilometre cinsinden)
                        saveDistance(fromCustomer.getId(), toCustomer.getId(), distance / 1000);
                        saveDistance(toCustomer.getId(), fromCustomer.getId(), distance / 1000);
                    } else {
                        // API hatası durumunda kuş uçuşu hesapla (yedek)
                        Log.w(TAG, "API hatası, kuş uçuşu mesafe kullanılıyor: " +
                                fromCustomer.getId() + " -> " + toCustomer.getId());
                        double fallbackDistance = calculateHaversineDistance(
                                fromCustomer.getLatitude(), fromCustomer.getLongitude(),
                                toCustomer.getLatitude(), toCustomer.getLongitude());
                        saveDistance(fromCustomer.getId(), toCustomer.getId(), fallbackDistance);
                        saveDistance(toCustomer.getId(), fromCustomer.getId(), fallbackDistance);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Mesafe hesaplama hatası: " + e.getMessage());
                    success.set(false);

                    // Hata durumunda Haversine ile hesapla
                    double fallbackDistance = calculateHaversineDistance(
                            fromCustomer.getLatitude(), fromCustomer.getLongitude(),
                            toCustomer.getLatitude(), toCustomer.getLongitude());
                    saveDistance(fromCustomer.getId(), toCustomer.getId(), fallbackDistance);
                    saveDistance(toCustomer.getId(), fromCustomer.getId(), fallbackDistance);
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            // Tüm istekler tamamlanana kadar bekle (maksimum 60 saniye)
            boolean completed = latch.await(60, TimeUnit.SECONDS);
            if (!completed) {
                Log.e(TAG, "Zaman aşımı: Tüm mesafe hesaplamaları tamamlanamadı.");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Mesafe hesaplama beklemesi kesintiye uğradı: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Google Routes API kullanarak iki müşteri arasındaki gerçek yol mesafesini hesaplar
     * @param origin Başlangıç müşterisi
     * @param destination Hedef müşterisi
     * @return Metre cinsinden mesafe, hata durumunda -1
     */
    private double getRouteDistanceFromAPI(Customer origin, Customer destination) {
        // JSON isteği oluştur
        JSONObject requestJson = new JSONObject();
        try {
            // Origin
            JSONObject originLocation = new JSONObject()
                    .put("location", new JSONObject()
                            .put("latLng", new JSONObject()
                                    .put("latitude", origin.getLatitude())
                                    .put("longitude", origin.getLongitude())));
            // Destination
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
                            // Mesafe başarılı şekilde alındıysa logla
                        Log.d(TAG, "Mesafe (" + origin.getId() + " -> " + destination.getId() + "): " + distanceMeters + " metre");
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
                Log.e(TAG, "API yanıtı zaman aşımına uğradı: " +
                        origin.getId() + " -> " + destination.getId());
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
     * İki konum arasındaki kuş uçuşu mesafeyi hesaplar (Haversine formülü)
     * API başarısız olduğunda yedek olarak kullanılır
     */
    private static double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Dünya yarıçapı (km)

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c; // Kilometre cinsinden mesafe
    }

    /**
     * Tüm müşterilerin mesafe matrisini Google Routes API kullanarak hesaplar
     * @return İşlem başarılı ise true, değilse false
     */
    public boolean calculateAllDistancesWithRoutesAPI() {
        List<Customer> customers = getAllCustomers();
        int size = customers.size();
        AtomicBoolean success = new AtomicBoolean(true);

        // Paralel istekler için ExecutorService
        ExecutorService executor = Executors.newFixedThreadPool(4);

        // İstek sayısını hesapla
        int totalRequests = size * (size - 1) / 2; // Üst üçgensel matris için
        CountDownLatch latch = new CountDownLatch(totalRequests);

        // Her bir konum çifti için mesafe hesapla
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) { // Sadece üst üçgensel matris
                final Customer origin = customers.get(i);
                final Customer destination = customers.get(j);

                executor.execute(() -> {
                    try {
                        // API'den mesafeyi al
                        double distance = getRouteDistanceFromAPI(origin, destination);
                        if (distance >= 0) {
                            // Mesafeyi her iki yönde de kaydet (km cinsinden)
                            saveDistance(origin.getId(), destination.getId(), distance / 1000);
                            saveDistance(destination.getId(), origin.getId(), distance / 1000);
                        } else {
                            // API hatası durumunda kuş uçuşu hesapla
                            Log.w(TAG, "API hatası, kuş uçuşu mesafe kullanılıyor: " +
                                    origin.getId() + " -> " + destination.getId());
                            double fallbackDistance = calculateHaversineDistance(
                                    origin.getLatitude(), origin.getLongitude(),
                                    destination.getLatitude(), destination.getLongitude());
                            saveDistance(origin.getId(), destination.getId(), fallbackDistance);
                            saveDistance(destination.getId(), origin.getId(), fallbackDistance);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Mesafe hesaplama hatası: " + e.getMessage());
                        success.set(false);

                        // Hata durumunda Haversine ile hesapla
                        double fallbackDistance = calculateHaversineDistance(
                                origin.getLatitude(), origin.getLongitude(),
                                destination.getLatitude(), destination.getLongitude());
                        saveDistance(origin.getId(), destination.getId(), fallbackDistance);
                        saveDistance(destination.getId(), origin.getId(), fallbackDistance);
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        try {
            // Tüm istekler tamamlanana kadar bekle (maksimum 120 saniye)
            boolean completed = latch.await(120, TimeUnit.SECONDS);
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

        return success.get();
    }
}