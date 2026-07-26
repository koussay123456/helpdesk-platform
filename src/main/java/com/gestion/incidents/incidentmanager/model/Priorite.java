package com.gestion.incidents.incidentmanager.model;

public enum Priorite {
    FAIBLE("Faible", 1),
    MOYENNE("Moyenne", 2),
    ELEVEE("Élevée", 3),
    CRITIQUE("Critique", 4);

    private final String label;
    private final int niveau;

    Priorite(String label, int niveau) {
        this.label = label;
        this.niveau = niveau;
    }

    public String getLabel() {
        return label;
    }

    public int getNiveau() {
        return niveau;
    }
}