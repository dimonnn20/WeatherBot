package com.example.service;

public enum Month {
    SEPTEMBER("Сентября"),
    OCTOBER("Октябрь"),
    NOVEMBER("Ноябрь"),
    DECEMBER("Декабрь");

    String name;

    Month(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
