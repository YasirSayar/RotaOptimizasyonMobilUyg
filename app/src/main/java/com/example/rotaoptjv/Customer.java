package com.example.rotaoptjv;

import java.io.Serializable;

public class Customer implements Serializable {
    private int id;
    private String name;
    private String phoneNumber;
    private String address;
    private double latitude;
    private double longitude;
    private int status = 0; // Rotaya dahil edilip edilmediğini belirten durum değişkeni (0: dahil değil, 1: dahil)

    public Customer() {
    }

    public Customer(int id, String name, String phoneNumber, String address, double latitude, double longitude) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isInRoute() {
        return status == 1;
    }

    public void setInRoute(boolean inRoute) {
        this.status = inRoute ? 1 : 0;
    }

    @Override
    public String toString() {
        return name;
    }
}