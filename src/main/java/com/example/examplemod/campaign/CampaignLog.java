package com.example.examplemod.campaign;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

/**
 * The campaign layer's console voice.
 *
 * <h2>Events, never ticks</h2>
 *
 * Every method here is for something that <b>happened once</b>: a sector changed hands, a planet was
 * conquered, a raid launched. Nothing in this class may be called from inside a loop over sectors or
 * a per-tick pass — a war log that prints the same line every ten seconds is a log nobody reads, and
 * it hides the three lines a session actually needed.
 *
 * <p>The prefixes match the shape the brief asked for, so the source of a line is legible without
 * reading the message:
 *
 * <pre>
 * [FirstCrusade/War]  armageddon sector MANUFACTORUM changed ORKS -&gt; IMPERIUM
 * [FirstCrusade/Raid] raid launched from ork_camp_17
 * </pre>
 */
public final class CampaignLog {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CampaignLog() {
    }

    /** A change in the state of the war: control, sectors, planets, campaign states. */
    public static void war(String format, Object... args) {
        LOGGER.info("[FirstCrusade/War] " + format, args);
    }

    /** An enemy offensive being prepared, launched or resolved. */
    public static void raid(String format, Object... args) {
        LOGGER.info("[FirstCrusade/Raid] " + format, args);
    }

    /**
     * Something the campaign could not do and had to work around — a front whose dimension this
     * installation does not have, a save whose sector type no longer exists. Warnings only: the
     * campaign always degrades rather than throwing, so nothing here is fatal.
     */
    public static void warn(String format, Object... args) {
        LOGGER.warn("[FirstCrusade/War] " + format, args);
    }
}
