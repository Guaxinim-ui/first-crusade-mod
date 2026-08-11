package com.example.examplemod;

/**
 * What a renderer is allowed to ask a soldier about his appearance.
 *
 * <p>Three questions, no more: what kind of troop he is, which regiment raised him, and which
 * individual he is. {@link ImperialTroopAppearance} turns the answers into a file. A renderer that
 * wanted to know his rank, his health or his Command Core would be a renderer deciding art policy,
 * which is exactly the arrangement that let nine units ship the same placeholder.</p>
 *
 * <p>Everything here must be answerable on the client, which means every value behind it is either
 * synced entity data or derived from the entity type. Nothing reads server-only state.</p>
 */
public interface ImperialTroopVisuals {

    /**
     * The troop's art key — by default its entity type registry path, which is also its texture
     * folder name. Overriding this lets two entity types share one wardrobe.
     */
    String appearanceKey();

    /**
     * The regiment that raised this soldier.
     *
     * <p>Returns {@link ImperialTroopAppearance#DEFAULT_REGIMENT} until named regiments have art of
     * their own; the resolver already routes on it, so the day those PNGs land nothing here
     * changes.
     */
    default String appearanceRegiment() {
        return ImperialTroopAppearance.DEFAULT_REGIMENT;
    }

    /** Which individual this is — rolled once at spawn, then persisted for the soldier's life. */
    int getVisualVariant();

    /**
     * Which wardrobe his career has earned him.
     *
     * <p>Derived from rank rather than stored, so a promotion is one field changing on the entity
     * that already exists — the soldier keeps his UUID, his name and his kill count and simply
     * looks like what he now is.
     */
    default ImperialTroopGrade getVisualGrade() {
        return ImperialTroopGrade.LINE;
    }
}
