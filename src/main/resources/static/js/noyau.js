/* ==========================================================================
   Configuration, appels au serveur et fonctions d'affichage communes.
   ========================================================================== */

/* ============================================================
   CONFIGURATION
   ============================================================ */
// Servi par Spring Boot : même origine que la page.
// Pour un front ouvert en fichier local, remplacer par 'http://localhost:8080/api'.
const API_BASE = window.location.protocol.startsWith('http')
    ? window.location.origin + '/api'
    : 'http://localhost:8080/api';

let currentUser = null;

/* ============================================================
   UTILITAIRES
   ============================================================ */
function showNotification(message, type = 'info') {
    const el = document.getElementById('notification');
    el.textContent = message;
    el.className = 'show ' + type;
    clearTimeout(el._timer);
    el._timer = setTimeout(() => el.classList.remove('show'), 3500);
}

// Tout contenu venant de la base est échappé avant insertion dans le DOM.
function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, c => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
}

function formatDate(iso) {
    const d = new Date(iso);
    return isNaN(d) ? '' : d.toLocaleDateString('fr-FR');
}

function formatHeure(iso) {
    const d = new Date(iso);
    return isNaN(d) ? '' : d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
}

function initiales(nomComplet) {
    return String(nomComplet || '?')
        .trim().split(/\s+/).slice(0, 2)
        .map(m => m.charAt(0).toUpperCase()).join('');
}

async function lireReponse(response) {
    const data = await response.json().catch(() => ({}));
    if (!response.ok || data.success === false) {
        throw new Error(data.message || ('Erreur HTTP ' + response.status));
    }
    return data;
}

/**
 * Vide toutes les barres de recherche et remet les filtres sur « tous ».
 *
 * L'application ne recharge jamais la page : les champs de saisie survivent
 * donc à une déconnexion. Sans cette remise à zéro, la session suivante
 * héritait du filtre de la précédente et découvrait une table vide sans
 * comprendre pourquoi.
 */
function reinitialiserFiltres() {
    document.querySelectorAll('.champ-recherche, .audit-search')
            .forEach(champ => { champ.value = ''; });
    document.querySelectorAll('.filtre-select')
            .forEach(liste => { liste.selectedIndex = 0; });
    document.querySelectorAll('.filtre-dates input[type="date"]')
            .forEach(champ => { champ.value = ''; });
}

/**
 * Vrai si la date tombe dans la période, bornes comprises.
 *
 * Les deux bornes sont facultatives et indépendantes : ne renseigner que
 * « Du » revient à demander « depuis cette date », et l'inverse pour « au ».
 * La borne de fin couvre la journée entière, sans quoi un ticket ouvert
 * l'après-midi serait exclu d'une recherche s'arrêtant à ce jour-là.
 */
function dansPeriode(dateIso, debutId, finId) {
    const debut = document.getElementById(debutId)?.value;
    const fin = document.getElementById(finId)?.value;
    if (!debut && !fin) return true;
    if (!dateIso) return false;

    const quand = new Date(dateIso);
    if (debut && quand < new Date(debut + 'T00:00:00')) return false;
    if (fin && quand > new Date(fin + 'T23:59:59')) return false;
    return true;
}

function togglePassword(btn) {
    const input = document.getElementById('loginPassword');
    const visible = input.type === 'text';
    input.type = visible ? 'password' : 'text';
    btn.innerHTML = visible ? '<i class="fas fa-eye"></i>' : '<i class="fas fa-eye-slash"></i>';
}