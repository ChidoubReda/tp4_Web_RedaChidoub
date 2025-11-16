package ma.emsi.chidoub.tp2_web_redachidoub.jsf;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import ma.emsi.chidoub.tp2_web_redachidoub.llm.LlmClient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Backing bean pour la page JSF index.xhtml.
 * Portée view pour conserver l'état de la conversation qui dure pendant plusieurs requêtes HTTP.
 * La portée view nécessite l'implémentation de Serializable (le backing bean peut être mis en mémoire secondaire).
 */
@Named
@ViewScoped
public class Bb implements Serializable {

    @Inject
    private LlmClient llm;

    /** Rôle "système" choisi/modifiable au tout début par l'utilisateur. */
    private String roleSysteme;

    /** Une fois le rôle choisi (premier envoi), on le verrouille. */
    private boolean roleSystemeChangeable = true;

    /** Liste des rôles prédéfinis pour la liste déroulante. */
    private List<SelectItem> listeRolesSysteme;

    /** Dernière question posée par l'utilisateur. */
    private String question;

    /** Dernière réponse du LLM. */
    private String reponse;

    /** Historique texte de la conversation. */
    private StringBuilder conversation = new StringBuilder();

    /** Contexte JSF pour afficher les messages d'erreur. */
    @Inject
    private FacesContext facesContext;

    public Bb() {}

    public String getRoleSysteme() { return roleSysteme; }
    public void setRoleSysteme(String roleSysteme) { this.roleSysteme = roleSysteme; }
    public boolean isRoleSystemeChangeable() { return roleSystemeChangeable; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getReponse() { return reponse; }
    public void setReponse(String reponse) { this.reponse = reponse; }

    public String getConversation() { return conversation.toString(); }
    public void setConversation(String conversation) { this.conversation = new StringBuilder(conversation); }

    /**
     * Envoie la question au LLM via LlmClient.
     * - Au premier message : fixe le rôle système et verrouille le sélecteur.
     * - Ensuite : délègue au LLM et ajoute la réponse à la conversation.
     */
    public String envoyer() {
        if (question == null || question.isBlank()) {
            facesContext.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "Texte question vide",
                    "Il manque le texte de la question"));
            return null;
        }

        try {
            // Au tout début de la conversation, on positionne le rôle système et on le verrouille
            if (this.conversation.isEmpty()) {
                llm.setSystemRole(roleSysteme);
                this.roleSystemeChangeable = false;
            }

            // Appel direct au LLM
            this.reponse = llm.ask(question);

            // Historiser dans le textarea
            afficherConversation();
            return null; // rester sur la même page
        } catch (Exception e) {
            facesContext.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "Erreur LLM",
                    "Impossible d'obtenir une réponse (" + e.getMessage() + ")"));
            return null;
        }
    }

    /**
     * Pour un nouveau chat :
     * - Réinitialise la mémoire côté LLM (pas de rôle système injecté).
     * - Change de vue pour recréer un nouveau backing bean.
     */
    public String nouveauChat() {
        try {
            llm.setSystemRole(null); // clear mémoire + pas de SystemMessage ajouté
        } catch (Exception ignored) {
        }
        return "index";
    }

    /** Ajoute la question/réponse à l'historique affiché. */
    private void afficherConversation() {
        this.conversation
                .append("== User:\n").append(question)
                .append("\n== Serveur:\n").append(reponse)
                .append("\n");
    }

    /** Rôles prédéfinis pour la liste déroulante. */
    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            this.listeRolesSysteme = new ArrayList<>();

            String role = """
                    You are a helpful assistant. You help the user to find the information they need.
                    If the user type a question, you answer it.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Assistant"));

            role = """
                    You are an interpreter. You translate from English to French and from French to English.
                    If the user type a French text, you translate it into English.
                    If the user type an English text, you translate it into French.
                    If the text contains only one to three words, give some examples of usage of these words in English.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Traducteur Anglais-Français"));

            role = """
                    Your are a travel guide. If the user type the name of a country or of a town,
                    you tell them what are the main places to visit in the country or the town
                    are you tell them the average price of a meal.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Guide touristique"));
        }
        return this.listeRolesSysteme;
    }
}

