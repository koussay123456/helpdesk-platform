/* ==========================================================================
   Tableau de bord réutilisable.

   Une bande de cartes commande une zone unique en dessous : un tableau par
   défaut, un graphique au clic. Les trois espaces s'en servent avec le même
   composant ; seuls changent les tickets observés, les cartes proposées et
   le tableau affiché par défaut.
   ========================================================================== */

const tableauxDeBord = new Map();

/**
 * @param {string} racineId  identifiant du conteneur dans la vue
 * @param {Object} config
 *   tickets ......... fonction rendant la liste de tickets observée
 *   titreTableau .... intitulé de la vue par défaut
 *   rendreTableau ... fonction qui peuple le tableau
 *   cartes .......... [{ cle, icone, libelle, valeur(tickets), titre, dessin(tickets) }]
 */
function creerTableauDeBord(racineId, config) {
    const racine = document.getElementById(racineId);
    if (!racine) return null;

    const bord = {
        racine, config,
        vue: 'tableau',

        elements: {
            cartes:    racine.querySelector('[data-role="cartes"]'),
            titre:     racine.querySelector('[data-role="titre"]'),
            retour:    racine.querySelector('[data-role="retour"]'),
            tableau:   racine.querySelector('[data-role="tableau"]'),
            graphique: racine.querySelector('[data-role="graphique"]')
        },

        /** Construit les cartes une fois pour toutes, puis les remplit. */
        construireCartes() {
            this.elements.cartes.innerHTML = config.cartes.map(c => `
                <button type="button" class="carte-choix" data-vue="${c.cle}" style="position: relative; padding-bottom: 24px;">
                    <span class="carte-tete"><i class="fas ${c.icone}"></i> ${escapeHtml(c.libelle)}</span>
                    <span class="carte-valeur" data-valeur="${c.cle}">—</span>
                    <span style="position: absolute; bottom: 6px; right: 12px; font-size: 0.75rem; color: var(--gris); font-style: italic;">voir détails</span>
                </button>`).join('');
        },

        majCartes() {
            const tickets = config.tickets();
            config.cartes.forEach(c => {
                const cible = this.elements.cartes.querySelector(`[data-valeur="${c.cle}"]`);
                if (cible) cible.textContent = c.valeur(tickets);
            });
        },

        afficherVue(vue) {
            this.vue = vue;

            this.elements.cartes.querySelectorAll('.carte-choix')
                .forEach(carte => carte.classList.toggle('active', carte.dataset.vue === vue));

            if (vue === 'tableau') {
                this.elements.tableau.hidden = false;
                this.elements.graphique.hidden = true;
                this.elements.retour.hidden = true;
                this.elements.titre.innerHTML = config.titreTableau;
                config.rendreTableau();
                return;
            }

            const carte = config.cartes.find(c => c.cle === vue);
            if (!carte) return;

            this.elements.tableau.hidden = true;
            this.elements.graphique.hidden = false;
            this.elements.retour.hidden = false;
            this.elements.titre.innerHTML = carte.titre;
            this.elements.graphique.innerHTML = carte.dessin(config.tickets());
        },

        /** Recalcule les chiffres et redessine la vue courante. */
        rafraichir() {
            this.majCartes();
            this.afficherVue(this.vue);
        }
    };

    bord.construireCartes();
    tableauxDeBord.set(racineId, bord);
    return bord;
}

/** Retrouve le tableau de bord auquel appartient un élément cliqué. */
function tableauDeBordDe(element) {
    const racine = element.closest('[data-bord]');
    return racine ? tableauxDeBord.get(racine.id) : null;
}