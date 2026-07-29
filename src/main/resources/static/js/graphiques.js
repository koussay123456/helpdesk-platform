/* ==========================================================================
   Dessin des graphiques, en SVG et sans bibliothèque.

   Deux formes seulement : l'anneau pour une répartition en quelques parts,
   l'histogramme pour une comparaison de valeurs. Les deux sont employées
   par les tableaux de bord des trois espaces.
   ========================================================================== */

/*
 * Le vert de l'entreprise vient en premier, les autres teintes suivent en
 * s'éloignant nettement les unes des autres : sur un histogramme à douze
 * barres, un dégradé de verts se lisait mal.
 */
const PALETTE = ['#00A859', '#2563EB', '#F59E0B', '#EF4444', '#8B5CF6',
                 '#0EA5E9', '#EC4899', '#14B8A6', '#84CC16', '#F97316',
                 '#6366F1', '#DC2626'];

/* Couleur attachée à un statut, la même partout où il apparaît. */
const COULEURS_STATUT = {
    NOUVEAU:  '#F59E0B',
    EN_COURS: '#2563EB',
    RESOLU:   '#00A859',
    FERME:    '#6B7A87'
};

const LIBELLES_CATEGORIE = {
    MATERIEL: 'Matériel', LOGICIEL: 'Logiciel', RESEAU: 'Réseau',
    IMPRIMANTE: 'Imprimante', AUTRE: 'Autre'
};

const LIBELLES_PRIORITE = {
    CRITIQUE: 'Critique', ELEVEE: 'Élevée', MOYENNE: 'Moyenne', FAIBLE: 'Faible'
};

/**
 * Anneau proportionnel.
 *
 * Chaque part est un arc obtenu par stroke-dasharray : la longueur du trait
 * vaut la part, le reste est vide, et le décalage place l'arc au bon endroit.
 * Plus sûr que de calculer des chemins à la main.
 */
function anneauSVG(donnees, mention) {
    const total = donnees.reduce((somme, d) => somme + d.valeur, 0);
    if (!total) return '<p class="etat-vide">Aucune donnée sur ce périmètre.</p>';

    const rayon = 90, epaisseur = 34, circonference = 2 * Math.PI * rayon;
    let decalage = 0;

    const arcs = donnees.map((d, i) => {
        const longueur = d.valeur / total * circonference;
        const arc = `
            <circle cx="120" cy="120" r="${rayon}" fill="none"
                    stroke="${d.couleur || PALETTE[i % PALETTE.length]}" stroke-width="${epaisseur}"
                    stroke-dasharray="${longueur.toFixed(2)} ${(circonference - longueur).toFixed(2)}"
                    stroke-dashoffset="${(-decalage).toFixed(2)}"
                    transform="rotate(-90 120 120)">
                <title>${escapeHtml(d.libelle)} : ${d.valeur}</title>
            </circle>`;
        decalage += longueur;
        return arc;
    }).join('');

    const legende = donnees.map((d, i) => `
        <div class="legende-ligne">
            <span class="legende-pastille" style="background:${d.couleur || PALETTE[i % PALETTE.length]}"></span>
            <span class="legende-libelle">${escapeHtml(d.libelle)}</span>
            <span class="legende-valeur">${d.valeur}</span>
            <span class="legende-part">${Math.round(d.valeur / total * 100)} %</span>
        </div>`).join('');

    return `
        <div class="anneau-conteneur">
            <svg viewBox="0 0 240 240" width="240" height="240" role="img"
                 aria-label="${escapeHtml(mention)}">
                <circle cx="120" cy="120" r="${rayon}" fill="none"
                        stroke="#EDF1F4" stroke-width="${epaisseur}"></circle>
                ${arcs}
                <g class="anneau-centre">
                    <text x="120" y="115" class="anneau-total">${total}</text>
                    <text x="120" y="136" class="anneau-mention">${escapeHtml(mention)}</text>
                </g>
            </svg>
            <div class="legende">${legende}</div>
        </div>`;
}

/**
 * Histogramme vertical.
 *
 * La hauteur est proportionnelle à la valeur la plus élevée, jamais à la
 * somme : c'est la comparaison entre barres qui importe. Les deux axes sont
 * nommés, sans quoi le lecteur voit des chiffres sans savoir ce qu'ils
 * comptent ni selon quel découpage.
 */
function barresSVG(donnees, mention, axeX, axeY) {
    if (!donnees.length || donnees.every(d => !d.valeur)) {
        return '<p class="etat-vide">Aucune donnée sur ce périmètre.</p>';
    }

    const largeur = Math.max(660, donnees.length * 78);
    const hauteur = 380;
    const marge = { haut: 34, bas: 104, gauche: 82, droite: 24 };
    const zoneH = hauteur - marge.haut - marge.bas;
    const zoneL = largeur - marge.gauche - marge.droite;

    const maximum = Math.max(...donnees.map(d => d.valeur));
    const pas = Math.max(1, Math.ceil(maximum / 4));
    const plafond = pas * 4;

    const pasBarre = zoneL / donnees.length;
    const largeurBarre = Math.min(48, pasBarre * 0.58);

    let reperes = '';
    for (let v = 0; v <= plafond; v += pas) {
        const y = marge.haut + zoneH - (v / plafond) * zoneH;
        reperes += `
            <line x1="${marge.gauche}" y1="${y}" x2="${largeur - marge.droite}" y2="${y}"
                  class="graphique-axe" ${v ? 'stroke-dasharray="3 5"' : 'stroke-width="1.5"'}></line>
            <text x="${marge.gauche - 12}" y="${y + 4}" class="graphique-texte"
                  text-anchor="end">${v}</text>`;
    }

    const axeVertical = `
        <line x1="${marge.gauche}" y1="${marge.haut - 6}"
              x2="${marge.gauche}" y2="${marge.haut + zoneH}"
              class="graphique-axe" stroke-width="1.5"></line>`;

    const barres = donnees.map((d, i) => {
        const h = d.valeur / plafond * zoneH;
        const x = marge.gauche + i * pasBarre + (pasBarre - largeurBarre) / 2;
        const y = marge.haut + zoneH - h;
        const court = d.libelle.length > 13 ? d.libelle.slice(0, 12) + '…' : d.libelle;

        return `
            <g>
                <rect x="${x.toFixed(1)}" y="${y.toFixed(1)}"
                      width="${largeurBarre.toFixed(1)}" height="${Math.max(h, 2).toFixed(1)}"
                      rx="5" fill="${d.couleur || PALETTE[i % PALETTE.length]}">
                    <title>${escapeHtml(d.libelle)} : ${d.valeur}</title>
                </rect>
                <text x="${(x + largeurBarre / 2).toFixed(1)}" y="${(y - 9).toFixed(1)}"
                      class="graphique-valeur" text-anchor="middle">${d.valeur}</text>
                <text x="${(x + largeurBarre / 2).toFixed(1)}" y="${marge.haut + zoneH + 22}"
                      class="graphique-texte" text-anchor="middle">${escapeHtml(court)}</text>
            </g>`;
    }).join('');

    const titres = `
        <text x="${marge.gauche + zoneL / 2}" y="${hauteur - 26}"
              class="graphique-axe-titre" text-anchor="middle">${escapeHtml(axeX)}</text>
        <text x="22" y="${marge.haut + zoneH / 2}"
              class="graphique-axe-titre" text-anchor="middle"
              transform="rotate(-90 22 ${marge.haut + zoneH / 2})">${escapeHtml(axeY)}</text>`;

    return `
        <svg viewBox="0 0 ${largeur} ${hauteur}" width="${largeur}" height="${hauteur}"
             role="img" aria-label="${escapeHtml(mention)}">
            ${reperes}${axeVertical}${barres}${titres}
        </svg>`;
}

/**
 * Histogramme empilé, une barre par entité et un segment par série.
 *
 * Chaque barre représente le total d'une entité ; les segments qui la
 * composent montrent d'où vient ce total. C'est ce qui permet de comparer
 * deux supportIT sur le volume tout en voyant lequel a le plus de dossiers
 * encore ouverts.
 *
 * @param donnees [{ libelle, valeurs: { cleSerie: nombre } }]
 * @param series  [{ cle, libelle, couleur }] — ordre d'empilement, du bas vers le haut
 */
function barresEmpileesSVG(donnees, series, mention, axeX, axeY) {
    const totaux = donnees.map(d => series.reduce((s, serie) => s + (d.valeurs[serie.cle] || 0), 0));
    if (!donnees.length || totaux.every(t => !t)) {
        return '<p class="etat-vide">Aucune donnée sur ce périmètre.</p>';
    }

    const largeur = Math.max(660, donnees.length * 92);
    const hauteur = 380;
    const marge = { haut: 34, bas: 104, gauche: 82, droite: 24 };
    const zoneH = hauteur - marge.haut - marge.bas;
    const zoneL = largeur - marge.gauche - marge.droite;

    const maximum = Math.max(...totaux);
    const pas = Math.max(1, Math.ceil(maximum / 4));
    const plafond = pas * 4;

    const pasBarre = zoneL / donnees.length;
    const largeurBarre = Math.min(56, pasBarre * 0.56);

    let reperes = '';
    for (let v = 0; v <= plafond; v += pas) {
        const y = marge.haut + zoneH - (v / plafond) * zoneH;
        reperes += `
            <line x1="${marge.gauche}" y1="${y}" x2="${largeur - marge.droite}" y2="${y}"
                  class="graphique-axe" ${v ? 'stroke-dasharray="3 5"' : 'stroke-width="1.5"'}></line>
            <text x="${marge.gauche - 12}" y="${y + 4}" class="graphique-texte"
                  text-anchor="end">${v}</text>`;
    }

    const axeVertical = `
        <line x1="${marge.gauche}" y1="${marge.haut - 6}"
              x2="${marge.gauche}" y2="${marge.haut + zoneH}"
              class="graphique-axe" stroke-width="1.5"></line>`;

    const barres = donnees.map((d, i) => {
        const x = marge.gauche + i * pasBarre + (pasBarre - largeurBarre) / 2;
        const total = totaux[i];
        let bas = marge.haut + zoneH;      // on empile depuis le sol
        let segments = '';

        series.forEach(serie => {
            const valeur = d.valeurs[serie.cle] || 0;
            if (!valeur) return;
            const h = valeur / plafond * zoneH;
            bas -= h;
            segments += `
                <rect x="${x.toFixed(1)}" y="${bas.toFixed(1)}"
                      width="${largeurBarre.toFixed(1)}" height="${h.toFixed(1)}"
                      fill="${serie.couleur}">
                    <title>${escapeHtml(d.libelle)} — ${escapeHtml(serie.libelle)} : ${valeur}</title>
                </rect>`;
        });

        const court = d.libelle.length > 14 ? d.libelle.slice(0, 13) + '…' : d.libelle;
        return `
            <g>
                ${segments}
                <text x="${(x + largeurBarre / 2).toFixed(1)}" y="${(bas - 9).toFixed(1)}"
                      class="graphique-valeur" text-anchor="middle">${total}</text>
                <text x="${(x + largeurBarre / 2).toFixed(1)}" y="${marge.haut + zoneH + 22}"
                      class="graphique-texte" text-anchor="middle">${escapeHtml(court)}</text>
            </g>`;
    }).join('');

    const titres = `
        <text x="${marge.gauche + zoneL / 2}" y="${hauteur - 26}"
              class="graphique-axe-titre" text-anchor="middle">${escapeHtml(axeX)}</text>
        <text x="22" y="${marge.haut + zoneH / 2}"
              class="graphique-axe-titre" text-anchor="middle"
              transform="rotate(-90 22 ${marge.haut + zoneH / 2})">${escapeHtml(axeY)}</text>`;

    return `
        <svg viewBox="0 0 ${largeur} ${hauteur}" width="${largeur}" height="${hauteur}"
             role="img" aria-label="${escapeHtml(mention)}">
            ${reperes}${axeVertical}${barres}${titres}
        </svg>
        ${legendeSVG(series)}`;
}

/** Légende horizontale : un carré de couleur, un libellé. */
function legendeSVG(series) {
    return `
        <div class="legende-carres">
            ${series.map(s => `
                <span class="legende-carre">
                    <span class="carre" style="background:${s.couleur}"></span>
                    ${escapeHtml(s.libelle)}
                </span>`).join('')}
        </div>`;
}

/* ------------------------------------------------------ Regroupements --- */

/** Compte les tickets par statut, dans l'ordre fourni. */
function repartitionStatut(tickets, statuts) {
    const libelles = { NOUVEAU: 'Nouveau', EN_COURS: 'En cours', RESOLU: 'Résolu', FERME: 'Fermé' };
    return statuts.map(s => ({
        libelle: libelles[s],
        valeur: tickets.filter(t => t.statut === s).length,
        couleur: COULEURS_STATUT[s]
    }));
}

/** Compte les tickets par catégorie, les cinq catégories étant toujours présentes. */
function repartitionCategorie(tickets) {
    return Object.entries(LIBELLES_CATEGORIE).map(([cle, libelle]) => ({
        libelle,
        valeur: tickets.filter(t => (t.categorie || 'AUTRE') === cle).length
    }));
}

/** Compte les tickets par priorité, de la plus urgente à la moins urgente. */
function repartitionPriorite(tickets) {
    return Object.entries(LIBELLES_PRIORITE).map(([cle, libelle]) => ({
        libelle,
        valeur: tickets.filter(t => t.priorite === cle).length
    }));
}

/** Compte les tickets par supportIT, « Non assigné » toujours en dernier. */
function repartitionSupportIT(tickets) {
    const compte = {};
    tickets.forEach(t => {
        const cle = t.supportNom || 'Non assigné';
        compte[cle] = (compte[cle] || 0) + 1;
    });
    return Object.entries(compte)
        .map(([libelle, valeur]) => ({ libelle, valeur }))
        .sort((a, b) => (a.libelle === 'Non assigné') - (b.libelle === 'Non assigné')
                        || b.valeur - a.valeur);
}

/**
 * Tickets par supportIT, ventilés par état.
 *
 * Les quatre statuts sont ramenés à trois familles : « ouverts » regroupe
 * NOUVEAU et EN_COURS, puisque du point de vue de la charge un ticket pris
 * en main reste à traiter.
 */
function repartitionSupportITParEtat(tickets) {
    const compte = {};

    tickets.forEach(t => {
        const cle = t.supportNom || 'Non assigné';
        if (!compte[cle]) compte[cle] = { ouverts: 0, resolus: 0, fermes: 0 };

        if (t.statut === 'RESOLU') compte[cle].resolus++;
        else if (t.statut === 'FERME') compte[cle].fermes++;
        else compte[cle].ouverts++;
    });

    return Object.entries(compte)
        .map(([libelle, valeurs]) => ({ libelle, valeurs }))
        .sort((a, b) => {
            const total = v => v.ouverts + v.resolus + v.fermes;
            return (a.libelle === 'Non assigné') - (b.libelle === 'Non assigné')
                || total(b.valeurs) - total(a.valeurs);
        });
}

/** Ordre d'empilement, du bas vers le haut, et couleur de chaque famille. */
const SERIES_ETAT = [
    { cle: 'ouverts', libelle: 'Ouverts', couleur: '#2563EB' },
    { cle: 'resolus', libelle: 'Résolus', couleur: '#00A859' },
    { cle: 'fermes',  libelle: 'Fermés',  couleur: '#6B7A87' }
];

const MOIS_COURTS = ['Janv.', 'Févr.', 'Mars', 'Avr.', 'Mai', 'Juin',
                     'Juil.', 'Août', 'Sept.', 'Oct.', 'Nov.', 'Déc.'];

/**
 * L'année civile entière, de janvier à décembre.
 *
 * L'axe ne dépend donc plus du jour où l'on consulte : deux personnes
 * regardant le même graphique à un mois d'intervalle voient les mêmes douze
 * colonnes, et un mois sans aucun ticket reste visible à zéro.
 */
function repartitionAnnuelle(tickets, annee) {
    const exercice = annee || new Date().getFullYear();

    const cases = MOIS_COURTS.map((libelle, mois) => ({ mois, libelle, valeur: 0 }));

    tickets.forEach(t => {
        if (!t.dateCreation) return;
        const d = new Date(t.dateCreation);
        if (d.getFullYear() === exercice) cases[d.getMonth()].valeur++;
    });

    return cases;
}