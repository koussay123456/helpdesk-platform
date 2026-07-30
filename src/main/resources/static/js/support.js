/* ==========================================================================
   Espace support informatique.

   Deux onglets, calqués sur l'espace administrateur :
     — Gestion des tickets : la totalité des tickets, avec l'action adaptée
       à chacun — se l'assigner, le faire progresser, ou rien du tout s'il
       appartient à un collègue ;
     — Tableau de bord : les chiffres du technicien connecté.
   ========================================================================== */

const PROGRESSION = {
    'NOUVEAU':  { suivant: 'EN_COURS', libelle: 'Prendre en charge', icone: 'fa-play' },
    'EN_COURS': { suivant: 'RESOLU',   libelle: 'Résoudre',          icone: 'fa-check' },
    'RESOLU':   { suivant: 'FERME',    libelle: 'Clôturer',          icone: 'fa-box-archive' }
};

const LIBELLES_STATUT = { 'NOUVEAU': 'Nouveau', 'EN_COURS': 'En cours',
                          'RESOLU': 'Résolu', 'FERME': 'Fermé' };

function classePriorite(priorite) {
    return ({ 'CRITIQUE': 'prio-critique', 'ELEVEE': 'prio-elevee',
              'MOYENNE': 'prio-moyenne', 'FAIBLE': 'prio-faible' })[priorite] || 'prio-faible';
}

function libellePriorite(priorite) {
    return ({ 'CRITIQUE': 'Critique', 'ELEVEE': 'Élevée',
              'MOYENNE': 'Moyenne', 'FAIBLE': 'Faible' })[priorite] || priorite;
}

let ticketsSupport = [];
let bordSupport = null;

/* ---------------------------------------- Onglet 1 : gestion des tickets --- */
async function chargerTicketsSupport() {
    const tbody = document.getElementById('supportTableBody');
    try {
        ticketsSupport = await lireReponse(await fetch(`${API_BASE}/tickets/kanban`));
        rendreTicketsSupport();
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="8" class="etat-vide">Tickets indisponibles : '
            + escapeHtml(error.message) + '</td></tr>';
    }
}

function rendreTicketsSupport() {
    const tbody = document.getElementById('supportTableBody');
    if (!tbody) return;

    const perimetre = document.getElementById('perimetreTickets')?.value || 'tous';
    const recherche = (document.getElementById('rechercheTicketsSupport')?.value || '').trim().toLowerCase();
    const statut = document.getElementById('filtreSupportStatut')?.value || '';
    const priorite = document.getElementById('filtreSupportPriorite')?.value || '';
    const categorie = document.getElementById('filtreSupportCategorie')?.value || '';
    const monEmail = currentUser ? currentUser.email : null;

    const retenus = ticketsSupport.filter(t => {
        if (perimetre === 'libres' && t.supportEmail) return false;
        if (perimetre === 'miens' && t.supportEmail !== monEmail) return false;

        const texte = `${t.numero} ${t.titre} ${t.demandeurNom || ''}`.toLowerCase();
        if (recherche && !texte.includes(recherche)) return false;
        if (statut && t.statut !== statut) return false;
        if (priorite && t.priorite !== priorite) return false;
        if (categorie && t.categorie !== categorie) return false;
        if (!dansPeriode(t.dateCreation, 'dateSupportDebut', 'dateSupportFin')) return false;
        return true;
    });

    document.getElementById('compteTicketsSupport').textContent =
        `${retenus.length} ticket${retenus.length > 1 ? 's' : ''} sur ${ticketsSupport.length}`;

    tbody.innerHTML = '';
    if (!retenus.length) {
        tbody.innerHTML = '<tr><td colspan="8" class="etat-vide">Aucun ticket ne correspond à ces critères.</td></tr>';
        return;
    }

    retenus.forEach(t => {
        const libre = !t.supportEmail;
        const aMoi = t.supportEmail === monEmail;
        const etape = PROGRESSION[t.statut];

        const ligne = document.createElement('tr');
        ligne.style.cursor = 'pointer';
        ligne.title = 'Ouvrir la fiche du ticket';

        // Priorité critique et personne aux commandes : la ligne doit sauter aux yeux.
        if (t.priorite === 'CRITIQUE' && libre) {
            ligne.style.background = 'var(--rouge-voile)';
            ligne.style.boxShadow = 'inset 3px 0 0 var(--rouge)';
        }

        ligne.innerHTML = `
            <td><strong>${escapeHtml(t.numero)}</strong></td>
            <td>${escapeHtml(t.titre)}</td>
            <td>${escapeHtml(LIBELLES_CATEGORIE[t.categorie] || t.categorie || '—')}</td>
            <td><span class="ticket-status ${classeStatut(t.statut)}">${LIBELLES_STATUT[t.statut] || t.statut}</span></td>
            <td><span class="badge-priorite ${classePriorite(t.priorite)}">${libellePriorite(t.priorite)}</span></td>
            <td>${escapeHtml(t.demandeurNom || '—')}</td>
            <td>${escapeHtml(t.supportNom || 'Non assigné')}</td>
            <td style="text-align:center;"><span data-action></span></td>`;

        const cellule = ligne.querySelector('[data-action]');
        if (libre) {
            cellule.innerHTML = `<button type="button" class="btn-valider" style="padding:7px 14px;font-size:.8rem;">
                                     <i class="fas fa-hand"></i> S'assigner</button>`;
            cellule.querySelector('button').addEventListener('click', e => {
                e.stopPropagation();
                assignerTicket(t.id);
            });
        } else if (aMoi && etape) {
            cellule.innerHTML = `<button type="button" class="btn-valider" style="padding:7px 14px;font-size:.8rem;">
                                     <i class="fas ${etape.icone}"></i> ${escapeHtml(etape.libelle)}</button>`;
            cellule.querySelector('button').addEventListener('click', e => {
                e.stopPropagation();
                faireProgresser(t.id, etape.suivant);
            });
        } else {
            cellule.innerHTML = '<span style="color:var(--gris-clair);font-size:.8rem;">—</span>';
        }

        ligne.addEventListener('click', () => ouvrirModalTicket(t.id));
        tbody.appendChild(ligne);
    });
}

async function assignerTicket(ticketId) {
    try {
        await lireReponse(await fetch(`${API_BASE}/tickets/${ticketId}/assigner`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ supportItId: currentUser.id })
        }));
        showNotification('Ticket pris en charge', 'success');
        rafraichirEspaceSupport();
    } catch (error) {
        showNotification(error.message, 'error');
    }
}

async function faireProgresser(ticketId, nouveauStatut) {
    try {
        const params = new URLSearchParams({ statut: nouveauStatut, utilisateurId: currentUser.id });
        await lireReponse(await fetch(`${API_BASE}/tickets/${ticketId}/changer-statut?${params}`,
                { method: 'POST' }));
        showNotification('Statut mis à jour', 'success');
        rafraichirEspaceSupport();
    } catch (error) {
        showNotification(error.message, 'error');
    }
}

function rafraichirEspaceSupport() {
    const racine = document.getElementById('supportDashboard');
    if (!racine || !racine.classList.contains('active')) return;

    if (racine.querySelector('.admin-tab-pane[data-pane="tickets"]').classList.contains('active')) {
        chargerTicketsSupport();
    } else {
        chargerBordSupport();
    }
}

/* ------------------------------------------ Onglet 2 : tableau de bord --- */
function mesTicketsSupport() {
    const monEmail = currentUser ? currentUser.email : null;
    return ticketsSupport.filter(t => t.supportEmail === monEmail);
}

async function chargerBordSupport() {
    try {
        ticketsSupport = await lireReponse(await fetch(`${API_BASE}/tickets/kanban`));
        if (!bordSupport) bordSupport = creerTableauDeBord('bordSupport', CONFIG_BORD_SUPPORT);
        bordSupport.rafraichir();
    } catch (error) {
        document.getElementById('mesInterventionsTableBody').innerHTML =
            '<tr><td colspan="8" class="etat-vide">Données indisponibles : '
            + escapeHtml(error.message) + '</td></tr>';
    }
}

const CONFIG_BORD_SUPPORT = {
    tickets: () => mesTicketsSupport(),
    titreTableau: '<i class="fas fa-folder-open"></i> Suivi de mes tickets',
    rendreTableau: () => rendreMesInterventions(),
    cartes: [
        {
            cle: 'ouverts', icone: 'fa-screwdriver-wrench',
            libelle: 'À traiter',
            valeur: t => t.filter(x => x.statut === 'NOUVEAU' || x.statut === 'EN_COURS').length,
            titre: '<i class="fas fa-folder-open"></i> Mes tickets à traiter, par statut',
            dessin: t => anneauSVG(
                repartitionStatut(t.filter(x => x.statut === 'NOUVEAU' || x.statut === 'EN_COURS'),
                                  ['NOUVEAU', 'EN_COURS']), 'à traiter')
        },
        {
            cle: 'resolus', icone: 'fa-clipboard-check',
            libelle: 'Résolus',
            valeur: t => t.filter(x => x.statut === 'RESOLU' || x.statut === 'FERME').length,
            titre: '<i class="fas fa-circle-check"></i> Mes tickets résolus, par statut',
            dessin: t => anneauSVG(
                repartitionStatut(t.filter(x => x.statut === 'RESOLU' || x.statut === 'FERME'),
                                  ['RESOLU', 'FERME']), 'résolus')
        },
        {
            cle: 'categories', icone: 'fa-layer-group',
            libelle: 'Par catégorie',
            valeur: t => new Set(t.map(x => x.categorie).filter(Boolean)).size,
            titre: '<i class="fas fa-tags"></i> Mes tickets par catégorie',
            dessin: t => barresSVG(repartitionCategorie(t), 'Mes tickets par catégorie',
                                   "Catégorie d'incident", 'Nombre de tickets')
        },
        {
            cle: 'priorites', icone: 'fa-fire',
            libelle: 'Par priorité',
            valeur: t => new Set(t.map(x => x.priorite).filter(Boolean)).size,
            titre: '<i class="fas fa-flag"></i> Mes tickets par priorité',
            dessin: t => barresSVG(repartitionPriorite(t), 'Mes tickets par priorité',
                                   'Priorité', 'Nombre de tickets')
        },
        {
            cle: 'mensuel', icone: 'fa-chart-column',
            libelle: 'Par mois',
            valeur: t => repartitionAnnuelle(t).reduce((s, m) => s + m.valeur, 0),
            titre: '<i class="fas fa-calendar-days"></i> Mes tickets, année ' + new Date().getFullYear(),
            dessin: t => barresSVG(repartitionAnnuelle(t), 'Mes tickets par mois',
                                   'Mois', 'Nombre de tickets')
        }
    ]
};

function rendreMesInterventions() {
    const tbody = document.getElementById('mesInterventionsTableBody');
    if (!tbody) return;

    const miens = mesTicketsSupport();
    const recherche = (document.getElementById('rechercheMesInterventions')?.value || '').trim().toLowerCase();
    const statut = document.getElementById('filtreMesInterventionsStatut')?.value || '';

    const retenus = miens.filter(t => {
        const texte = `${t.numero} ${t.titre}`.toLowerCase();
        if (recherche && !texte.includes(recherche)) return false;
        if (statut && t.statut !== statut) return false;
        if (!dansPeriode(t.dateCreation, 'dateMesInterventionsDebut', 'dateMesInterventionsFin')) return false;
        return true;
    });

    document.getElementById('compteMesInterventions').textContent =
        `${retenus.length} ticket${retenus.length > 1 ? 's' : ''} sur ${miens.length}`;

    tbody.innerHTML = '';
    if (!retenus.length) {
        tbody.innerHTML = miens.length
            ? '<tr><td colspan="8" class="etat-vide">Aucun ticket ne correspond à ces critères.</td></tr>'
            : `<tr><td colspan="8" class="etat-vide">Vous n'avez encore pris aucun ticket en charge.<br>
               Rendez-vous dans « Gestion des tickets ».</td></tr>`;
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
            <td><span class="ticket-status ${classeStatut(t.statut)}">${LIBELLES_STATUT[t.statut] || t.statut}</span></td>
            <td><span class="badge-priorite ${classePriorite(t.priorite)}">${libellePriorite(t.priorite)}</span></td>
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

/* ------------------------------------------- Ajout d'un commentaire --- */

/** Aperçu du dernier échange, ou un tiret si le fil est vide. */
function apercuCommentaire(ticket) {
    if (!ticket.dernierMessage) return '<span style="color:var(--gris-clair);">—</span>';

    return `<span title="${escapeHtml(ticket.dernierMessage)}">
                ${escapeHtml(ticket.dernierMessage)}
                <br><small style="color:var(--gris);">${escapeHtml(ticket.dernierAuteur || '')}
                ${ticket.dateDernierMessage ? '· ' + formatDate(ticket.dateDernierMessage) : ''}</small>
            </span>`;
}

let ticketCommente = null;

function ouvrirCommentaire(ticket) {
    ticketCommente = ticket;
    document.getElementById('titreCommentaire').textContent = 'Commenter ' + ticket.numero;
    document.getElementById('contexteCommentaire').innerHTML =
        `<strong>${escapeHtml(ticket.titre)}</strong><br>
         Demandé par ${escapeHtml(ticket.demandeurNom || '—')}`;
    document.getElementById('texteCommentaire').value = '';
    document.getElementById('modalCommentaire').classList.add('open');
    document.getElementById('texteCommentaire').focus();
}

/**
 * Enregistre le commentaire dans le fil du ticket.
 *
 * C'est la même table que l'historique technique affiché dans la fiche : le
 * demandeur le verra donc apparaître dans son suivi sans traitement
 * supplémentaire, et la fiche du ticket restera la source unique.
 */
async function enregistrerCommentaire(e) {
    e.preventDefault();
    if (!ticketCommente) return;

    const texte = document.getElementById('texteCommentaire').value.trim();
    if (!texte) {
        showNotification('Le commentaire est vide', 'error');
        return;
    }

    try {
        await lireReponse(await fetch(`${API_BASE}/tickets/${ticketCommente.id}/interventions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
            body: new URLSearchParams({ auteurId: currentUser.id, commentaire: texte })
        }));

        document.getElementById('modalCommentaire').classList.remove('open');
        ticketCommente = null;
        showNotification('Commentaire enregistré', 'success');
        chargerBordSupport();
    } catch (error) {
        showNotification(error.message, 'error');
    }
}