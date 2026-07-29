/* ==========================================================================
   Espace utilisateur.

   Deux onglets, calqués sur les autres espaces :
     — Mes tickets : la liste de ses demandes, avec le bouton de création ;
     — Tableau de bord : ses chiffres et leurs graphiques.
   ========================================================================== */

let mesTickets = [];
let bordUtilisateur = null;

async function chargerMesTickets() {
    const tbody = document.getElementById('mesTicketsTableBody');
    try {
        mesTickets = await lireReponse(await fetch(
            `${API_BASE}/tickets/historique?utilisateurId=${currentUser.id}`));
        rendreMesTickets();
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="7" class="etat-vide">Tickets indisponibles : '
            + escapeHtml(error.message) + '</td></tr>';
    }
}

function rendreMesTickets() {
    const tbody = document.getElementById('mesTicketsTableBody');
    if (!tbody) return;

    const recherche = (document.getElementById('rechercheMesTickets')?.value || '').trim().toLowerCase();
    const statut = document.getElementById('filtreMesTicketsStatut')?.value || '';
    const priorite = document.getElementById('filtreMesTicketsPriorite')?.value || '';
    const categorie = document.getElementById('filtreMesTicketsCategorie')?.value || '';

    const retenus = mesTickets.filter(t => {
        const texte = `${t.numero} ${t.titre}`.toLowerCase();
        if (recherche && !texte.includes(recherche)) return false;
        if (statut && t.statut !== statut) return false;
        if (priorite && t.priorite !== priorite) return false;
        if (categorie && t.categorie !== categorie) return false;
        if (!dansPeriode(t.dateCreation, 'dateMesTicketsDebut', 'dateMesTicketsFin')) return false;
        return true;
    });

    document.getElementById('compteMesTickets').textContent =
        `${retenus.length} ticket${retenus.length > 1 ? 's' : ''} sur ${mesTickets.length}`;

    tbody.innerHTML = '';
    if (!retenus.length) {
        tbody.innerHTML = mesTickets.length
            ? '<tr><td colspan="7" class="etat-vide">Aucun ticket ne correspond à ces critères.</td></tr>'
            : `<tr><td colspan="7" class="etat-vide">Vous n'avez encore déclaré aucun incident.<br>
               Utilisez le bouton « Créer » au-dessus.</td></tr>`;
        return;
    }

    retenus.forEach(t => {
        const ligne = document.createElement('tr');
        ligne.style.cursor = 'pointer';
        ligne.title = 'Ouvrir la fiche du ticket';
        ligne.innerHTML = `
            <td><strong>${escapeHtml(t.numero)}</strong></td>
            <td>${escapeHtml(t.titre)}</td>
            <td>${escapeHtml(LIBELLES_CATEGORIE[t.categorie] || t.categorie || '—')}</td>
            <td><span class="ticket-status ${classeStatut(t.statut)}">${LIBELLES_STATUT[t.statut] || t.statut}</span>
                ${t.statut === 'RESOLU'
                    ? '<br><small style="color:var(--vert);font-weight:600;">à valider</small>' : ''}</td>
            <td><span class="badge-priorite ${classePriorite(t.priorite)}">${libellePriorite(t.priorite)}</span></td>
            <td>${formatDate(t.dateCreation)}</td>
            <td>${escapeHtml(t.supportNom || 'Non assigné')}</td>`;
        ligne.addEventListener('click', () => ouvrirModalTicket(t.id));
        tbody.appendChild(ligne);
    });
}

/* ------------------------------------------ Onglet 2 : tableau de bord --- */
async function chargerBordUtilisateur() {
    try {
        mesTickets = await lireReponse(await fetch(
            `${API_BASE}/tickets/historique?utilisateurId=${currentUser.id}`));
        if (!bordUtilisateur) bordUtilisateur = creerTableauDeBord('bordUtilisateur', CONFIG_BORD_UTILISATEUR);
        bordUtilisateur.rafraichir();
    } catch (error) {
        document.getElementById('suiviTableBody').innerHTML =
            '<tr><td colspan="6" class="etat-vide">Données indisponibles : '
            + escapeHtml(error.message) + '</td></tr>';
    }
}

const CONFIG_BORD_UTILISATEUR = {
    tickets: () => mesTickets,
    titreTableau: '<i class="fas fa-list-check"></i> Suivi de mes demandes',
    rendreTableau: () => rendreSuivi(),
    cartes: [
        {
            cle: 'ouverts', icone: 'fa-hourglass-half',
            libelle: 'En cours',
            valeur: t => t.filter(x => x.statut === 'NOUVEAU' || x.statut === 'EN_COURS').length,
            titre: '<i class="fas fa-folder-open"></i> Mes demandes en cours, par statut',
            dessin: t => anneauSVG(
                repartitionStatut(t.filter(x => x.statut === 'NOUVEAU' || x.statut === 'EN_COURS'),
                                  ['NOUVEAU', 'EN_COURS']), 'en cours')
        },
        {
            cle: 'resolus', icone: 'fa-circle-check',
            libelle: 'Traités',
            valeur: t => t.filter(x => x.statut === 'RESOLU' || x.statut === 'FERME').length,
            titre: '<i class="fas fa-circle-check"></i> Mes demandes traitées, par statut',
            dessin: t => anneauSVG(
                repartitionStatut(t.filter(x => x.statut === 'RESOLU' || x.statut === 'FERME'),
                                  ['RESOLU', 'FERME']), 'traités')
        },
        {
            cle: 'categories', icone: 'fa-layer-group',
            libelle: 'Par catégorie',
            valeur: t => new Set(t.map(x => x.categorie).filter(Boolean)).size,
            titre: '<i class="fas fa-tags"></i> Mes demandes par catégorie',
            dessin: t => barresSVG(repartitionCategorie(t), 'Mes demandes par catégorie',
                                   "Catégorie d'incident", 'Nombre de demandes')
        },
        {
            cle: 'priorites', icone: 'fa-fire',
            libelle: 'Par priorité',
            valeur: t => new Set(t.map(x => x.priorite).filter(Boolean)).size,
            titre: '<i class="fas fa-flag"></i> Mes demandes par priorité',
            dessin: t => barresSVG(repartitionPriorite(t), 'Mes demandes par priorité',
                                   'Priorité', 'Nombre de demandes')
        },
        {
            cle: 'mensuel', icone: 'fa-chart-column',
            libelle: 'Par mois',
            valeur: t => repartitionAnnuelle(t).reduce((s, m) => s + m.valeur, 0),
            titre: '<i class="fas fa-calendar-days"></i> Mes demandes, année ' + new Date().getFullYear(),
            dessin: t => barresSVG(repartitionAnnuelle(t), 'Mes demandes par mois',
                                   'Mois', 'Nombre de demandes')
        }
    ]
};

function rendreSuivi() {
    const tbody = document.getElementById('suiviTableBody');
    if (!tbody) return;

    tbody.innerHTML = '';
    if (!mesTickets.length) {
        tbody.innerHTML = `<tr><td colspan="7" class="etat-vide">Aucune demande enregistrée.</td></tr>`;
        return;
    }

    mesTickets.forEach(t => {
        const ligne = document.createElement('tr');
        ligne.style.cursor = 'pointer';
        ligne.title = 'Ouvrir la fiche du ticket';
        ligne.innerHTML = `
            <td><strong>${escapeHtml(t.numero)}</strong></td>
            <td>${escapeHtml(t.titre)}</td>
            <td>${escapeHtml(LIBELLES_CATEGORIE[t.categorie] || t.categorie || '—')}</td>
            <td><span class="ticket-status ${classeStatut(t.statut)}">${LIBELLES_STATUT[t.statut] || t.statut}</span></td>
            <td>${formatDate(t.dateCreation)}</td>
            <td class="cellule-commentaire">${apercuCommentaire(t)}</td>
            <td style="text-align:center;"><span data-commenter></span></td>`;
            
        const action = ligne.querySelector('[data-commenter]');
        action.innerHTML = `<button type="button" class="btn-valider"
                                    style="padding:7px 14px;font-size:.8rem;">
                                <i class="fas fa-comment-dots"></i> Commenter</button>`;
        action.querySelector('button').addEventListener('click', e => {
            e.stopPropagation();
            ouvrirCommentaire(t);
        });

        ligne.addEventListener('click', () => ouvrirModalTicket(t.id));
        tbody.appendChild(ligne);
    });
}