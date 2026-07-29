/* ==========================================================================
   Espace administrateur : historique de connexion.

   Toutes les tentatives, abouties comme repoussées, avec leur motif de
   refus. C'est la succession d'échecs sur une même adresse qui signale une
   tentative d'intrusion : le filtre par résultat est donc le plus utile.
   ========================================================================== */

let connexions = [];

async function chargerConnexions() {
    const tbody = document.getElementById('connexionsTableBody');
    if (!tbody) return;

    try {
        const params = new URLSearchParams();
        const debut = document.getElementById('dateConnexionsDebut')?.value;
        const fin = document.getElementById('dateConnexionsFin')?.value;
        const recherche = document.getElementById('rechercheConnexions')?.value.trim();
        const resultat = document.getElementById('filtreConnexionsResultat')?.value;

        if (debut) params.set('debut', debut);
        if (fin) params.set('fin', fin);
        if (recherche) params.set('recherche', recherche);
        if (resultat) params.set('resultat', resultat);

        connexions = await lireReponse(await fetch(`${API_BASE}/audit/connexions?${params}`));
        rendreConnexions();
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="6" class="etat-vide">Historique indisponible : '
            + escapeHtml(error.message) + '</td></tr>';
    }
}

function rendreConnexions() {
    const tbody = document.getElementById('connexionsTableBody');
    if (!tbody) return;

    // Le filtre par rôle s'applique en local : le serveur ignore cette notion
    // pour les tentatives sans compte associé.
    const role = document.getElementById('filtreConnexionsRole')?.value || '';
    const retenues = connexions.filter(c => !role || c.role === role);

    const echecs = retenues.filter(c => !c.reussite).length;
    document.getElementById('compteConnexions').textContent =
        `${retenues.length} tentative${retenues.length > 1 ? 's' : ''}`
        + (echecs ? `, dont ${echecs} refusée${echecs > 1 ? 's' : ''}` : '');

    tbody.innerHTML = '';
    if (!retenues.length) {
        tbody.innerHTML = '<tr><td colspan="6" class="etat-vide">Aucune tentative sur cette période.</td></tr>';
        return;
    }

    const roles = { ADMINISTRATEUR: 'Administrateur', SUPPORT_IT: 'supportIT', UTILISATEUR: 'Utilisateur' };

    retenues.forEach(c => {
        const ligne = document.createElement('tr');
        if (!c.reussite) {
            ligne.style.background = 'var(--rouge-voile)';
            ligne.style.boxShadow = 'inset 3px 0 0 var(--rouge)';
        }
        ligne.innerHTML = `
            <td>${formatDate(c.date)} <small style="color:var(--gris);">${formatHeure(c.date)}</small></td>
            <td>${escapeHtml(c.nom || '—')}</td>
            <td>${escapeHtml(c.email || '—')}</td>
            <td>${c.role ? escapeHtml(roles[c.role] || c.role) : '—'}</td>
            <td>
                <span class="ticket-status ${c.reussite ? 'status-resolu' : 'status-ferme'}">
                    ${c.reussite ? 'Réussite' : 'Échec'}
                </span>
            </td>
            <td>${escapeHtml(c.motif || '—')}</td>`;
        tbody.appendChild(ligne);
    });
}