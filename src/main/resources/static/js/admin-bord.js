/* ==========================================================================
   Espace administrateur : tableau de bord.

   Le composant fait le gros du travail ; ce module se contente de décrire
   les cinq cartes et le tableau affiché par défaut. Tous les chiffres sont
   calculés dans le navigateur à partir de la seule liste des tickets.
   ========================================================================== */

let tousLesTickets = [];
let bordAdmin = null;

async function chargerTicketsAdmin() {
    const tbody = document.getElementById('ticketsTableBody');
    try {
        tousLesTickets = await lireReponse(await fetch(`${API_BASE}/tickets/kanban`));

        if (!bordAdmin) bordAdmin = creerTableauDeBord('bordAdmin', CONFIG_BORD_ADMIN);
        bordAdmin.rafraichir();
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="5" class="etat-vide">Tickets indisponibles : '
            + escapeHtml(error.message) + '</td></tr>';
    }
}

const CONFIG_BORD_ADMIN = {
    tickets: () => tousLesTickets,
    titreTableau: '<i class="fas fa-table-list"></i> Tickets',
    rendreTableau: () => rendreTicketsAdmin(),
    cartes: [
        {
            cle: 'ouverts', icone: 'fa-inbox',
            libelle: 'Tickets ouverts',
            valeur: t => t.filter(x => x.statut === 'NOUVEAU' || x.statut === 'EN_COURS').length,
            titre: '<i class="fas fa-folder-open"></i> Tickets ouverts, par statut',
            dessin: t => anneauSVG(
                repartitionStatut(t.filter(x => x.statut === 'NOUVEAU' || x.statut === 'EN_COURS'),
                                  ['NOUVEAU', 'EN_COURS']), 'ouverts')
        },
        {
            cle: 'resolus', icone: 'fa-circle-check',
            libelle: 'Tickets résolus',
            valeur: t => t.filter(x => x.statut === 'RESOLU' || x.statut === 'FERME').length,
            titre: '<i class="fas fa-circle-check"></i> Tickets résolus, par statut',
            dessin: t => anneauSVG(
                repartitionStatut(t.filter(x => x.statut === 'RESOLU' || x.statut === 'FERME'),
                                  ['RESOLU', 'FERME']), 'résolus')
        },
        {
            cle: 'categories', icone: 'fa-layer-group',
            libelle: 'Par catégorie',
            valeur: t => new Set(t.map(x => x.categorie).filter(Boolean)).size,
            titre: '<i class="fas fa-tags"></i> Répartition par catégorie',
            dessin: t => barresSVG(repartitionCategorie(t), 'Tickets par catégorie',
                                   "Catégorie d'incident", 'Nombre de tickets')
        },
        {
            cle: 'supports', icone: 'fa-user-gear',
            libelle: 'Par supportIT',
            valeur: t => new Set(t.map(x => x.supportNom).filter(Boolean)).size,
            titre: '<i class="fas fa-headset"></i> Répartition par supportIT',
            dessin: t => graphiqueSupportIT(t)
        },
        {
            cle: 'mensuel', icone: 'fa-chart-column',
            libelle: 'Par mois',
            valeur: t => repartitionAnnuelle(t).reduce((s, m) => s + m.valeur, 0),
            titre: '<i class="fas fa-calendar-days"></i> Tickets ouverts, année ' + new Date().getFullYear(),
            dessin: t => barresSVG(repartitionAnnuelle(t), 'Tickets par mois',
                                   'Mois', 'Nombre de tickets ouverts')
        }
    ]
};

/* ------------------------------------- Répartition par supportIT --- */

/** État retenu dans le sélecteur du graphique. Conservé entre deux rendus. */
let etatSupportIT = 'tous';

/**
 * Classement des supportIT selon l'état des tickets qu'ils portent.
 *
 * « Tous » empile les trois familles pour comparer les charges globales sans
 * perdre leur composition ; les trois autres choix isolent une famille, ce
 * qui répond à une question différente — qui a le plus résolu, qui a le plus
 * de dossiers encore ouverts.
 */
function graphiqueSupportIT(tickets) {
    const donnees = repartitionSupportITParEtat(tickets);

    const selecteur = `
        <div class="outils-graphique">
            <label for="etatSupportIT">Afficher</label>
            <select id="etatSupportIT" class="filtre-select">
                <option value="tous">Tous les états</option>
                <option value="ouverts">Ouverts</option>
                <option value="resolus">Résolus</option>
                <option value="fermes">Fermés</option>
            </select>
        </div>`.replace(`value="${etatSupportIT}"`, `value="${etatSupportIT}" selected`);

    if (etatSupportIT === 'tous') {
        return selecteur + barresEmpileesSVG(donnees, SERIES_ETAT,
            'Tickets par supportIT et par état', 'supportIT', 'Nombre de tickets');
    }

    const serie = SERIES_ETAT.find(s => s.cle === etatSupportIT);
    const simple = donnees
        .map(d => ({ libelle: d.libelle, valeur: d.valeurs[etatSupportIT] || 0, couleur: serie.couleur }))
        .sort((a, b) => (a.libelle === 'Non assigné') - (b.libelle === 'Non assigné')
                        || b.valeur - a.valeur);

    return selecteur
        + barresSVG(simple, `Tickets ${serie.libelle.toLowerCase()} par supportIT`,
                    'supportIT', 'Nombre de tickets')
        + legendeSVG([serie]);
}

/* ------------------------------------------------ Table des tickets --- */
function rendreTicketsAdmin() {
    const tbody = document.getElementById('ticketsTableBody');
    if (!tbody) return;

    const recherche = (document.getElementById('rechercheTickets')?.value || '').trim().toLowerCase();
    const statut = document.getElementById('filtreTicketStatut')?.value || '';
    const priorite = document.getElementById('filtreTicketPriorite')?.value || '';
    const categorie = document.getElementById('filtreTicketCategorie')?.value || '';

    const retenus = tousLesTickets.filter(t => {
        const texte = `${t.numero} ${t.titre} ${t.demandeurNom || ''}`.toLowerCase();
        if (recherche && !texte.includes(recherche)) return false;
        if (statut && t.statut !== statut) return false;
        if (priorite && t.priorite !== priorite) return false;
        if (categorie && t.categorie !== categorie) return false;
        if (!dansPeriode(t.dateCreation, 'dateTicketsDebut', 'dateTicketsFin')) return false;
        return true;
    });

    document.getElementById('compteTickets').textContent =
        `${retenus.length} ticket${retenus.length > 1 ? 's' : ''} sur ${tousLesTickets.length}`;

    tbody.innerHTML = '';
    if (!retenus.length) {
        tbody.innerHTML = '<tr><td colspan="5" class="etat-vide">Aucun ticket ne correspond à ces critères.</td></tr>';
        return;
    }

    retenus.forEach(t => {
        const ligne = document.createElement('tr');
        ligne.style.cursor = 'pointer';
        ligne.title = 'Ouvrir la fiche du ticket';
        ligne.innerHTML = `
            <td><strong>${escapeHtml(t.numero)}</strong></td>
            <td>${escapeHtml(LIBELLES_CATEGORIE[t.categorie] || t.categorie || '—')}</td>
            <td><span class="ticket-status ${classeStatut(t.statut)}">${LIBELLES_STATUT[t.statut] || t.statut}</span></td>
            <td><span class="badge-priorite ${classePriorite(t.priorite)}">${libellePriorite(t.priorite)}</span></td>
            <td>${formatDate(t.dateCreation)}</td>`;
        ligne.addEventListener('click', () => ouvrirModalTicket(t.id));
        tbody.appendChild(ligne);
    });
}