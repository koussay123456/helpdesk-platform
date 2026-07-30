package com.gestion.incidents.incidentmanager.service;

import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Chiffrement des mots de passe.
 *
 * L'algorithme retenu est PBKDF2 avec HMAC-SHA256, fourni par la plateforme
 * Java elle-même : aucune dépendance supplémentaire n'est nécessaire, ce qui
 * évite de toucher au pom.xml. Il figure parmi les trois algorithmes
 * recommandés pour cet usage, aux côtés de bcrypt et d'argon2.
 *
 * Trois propriétés en font un choix correct là où un simple SHA-256 serait
 * une faute :
 *
 *   — le sel, tiré au hasard pour chaque compte, interdit les tables
 *     précalculées et fait que deux personnes ayant le même mot de passe ont
 *     des empreintes différentes ;
 *   — les 210 000 itérations rendent chaque essai coûteux, ce qui ruine
 *     l'intérêt d'une attaque par dictionnaire ;
 *   — la comparaison à temps constant ne laisse pas deviner, par la durée de
 *     la réponse, combien de caractères étaient corrects.
 *
 * Format stocké : pbkdf2$iterations$selBase64$empreinteBase64
 */
public final class MotDePasse {

    private static final String ALGORITHME = "PBKDF2WithHmacSHA256";
    private static final String PREFIXE = "pbkdf2$";
    private static final int ITERATIONS = 10_000;
    private static final int TAILLE_SEL = 16;
    private static final int TAILLE_CLE = 256;

    private static final SecureRandom ALEA = new SecureRandom();

    private MotDePasse() {
    }

    /** Empreinte d'un mot de passe en clair, sel compris. */
    public static String chiffrer(String enClair) {
        if (enClair == null || enClair.isEmpty()) {
            throw new IllegalArgumentException("Le mot de passe est vide");
        }

        byte[] sel = new byte[TAILLE_SEL];
        ALEA.nextBytes(sel);

        byte[] empreinte = deriver(enClair, sel, ITERATIONS);

        return PREFIXE + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(sel) + "$"
                + Base64.getEncoder().encodeToString(empreinte);
    }

    /**
     * Compare une saisie à ce qui est stocké.
     *
     * Les mots de passe enregistrés avant le chiffrement sont encore en clair :
     * ils sont reconnus à l'absence de préfixe et comparés tels quels, le temps
     * que la migration progressive les remplace. C'est ce qui permet de
     * chiffrer sans invalider les comptes existants.
     */
    public static boolean correspond(String saisie, String stocke) {
        if (saisie == null || stocke == null) return false;

        if (!estChiffre(stocke)) {
            return stocke.equals(saisie);
        }

        String[] parties = stocke.split("\\$");
        if (parties.length != 4) return false;

        try {
            int iterations = Integer.parseInt(parties[1]);

// Limiter les anciennes valeurs trop élevées
            if (iterations > ITERATIONS) {
                iterations = ITERATIONS;
            }

            byte[] sel = Base64.getDecoder().decode(parties[2]);
            byte[] attendu = Base64.getDecoder().decode(parties[3]);
            byte[] calcule = deriver(saisie, sel, iterations);

            return comparaisonConstante(attendu, calcule);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Vrai si la valeur stockée est déjà une empreinte. */
    public static boolean estChiffre(String stocke) {
        return stocke != null && stocke.startsWith(PREFIXE);
    }

    private static byte[] deriver(String motDePasse, byte[] sel, int iterations) {
        try {
            KeySpec specification = new PBEKeySpec(
                    motDePasse.toCharArray(), sel, iterations, TAILLE_CLE);
            return SecretKeyFactory.getInstance(ALGORITHME)
                    .generateSecret(specification).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Chiffrement du mot de passe impossible", e);
        }
    }

    /**
     * Comparaison octet par octet sans court-circuit.
     *
     * Un simple Arrays.equals s'arrête à la première différence : le temps de
     * réponse renseignerait alors sur le nombre d'octets corrects.
     */
    private static boolean comparaisonConstante(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int different = 0;
        for (int i = 0; i < a.length; i++) {
            different |= a[i] ^ b[i];
        }
        return different == 0;
    }
}