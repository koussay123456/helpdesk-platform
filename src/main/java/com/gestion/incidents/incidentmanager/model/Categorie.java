package com.gestion.incidents.incidentmanager.model;

public enum Categorie {
    MATERIEL("Matériel"),
    LOGICIEL("Logiciel"),
    RESEAU("Réseau"),
    IMPRIMANTE("Imprimante"),
    AUTRE("Autre");

    private final String label;

    Categorie(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}