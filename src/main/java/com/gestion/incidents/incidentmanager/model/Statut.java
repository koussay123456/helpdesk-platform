package com.gestion.incidents.incidentmanager.model;

public enum Statut {
    NOUVEAU("Nouveau"),
    EN_COURS("En cours"),
    RESOLU("Résolu"),
    FERME("Fermé");

    private final String label;

    Statut(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}