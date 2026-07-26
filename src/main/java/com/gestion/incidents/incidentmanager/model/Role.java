package com.gestion.incidents.incidentmanager.model;

public enum Role {
    ADMINISTRATEUR("Administrateur"),
    SUPPORT_IT("Support IT"),
    UTILISATEUR("Utilisateur");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}