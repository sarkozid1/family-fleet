package hu.familyfleet.security;

/**
 * Van-e függő háztartás-meghívó egy e-mail-címre. A security csomag ezt az interfészt definiálja,
 * a household csomag valósítja meg (függőség-megfordítás): így a security nem függ a householdtól,
 * és nem jön létre körkörös csomagfüggés.
 */
public interface InvitationLookup {

    boolean hasPendingInvitation(String normalizedEmail);
}
