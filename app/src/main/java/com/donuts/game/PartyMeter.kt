package com.donuts.game

/**
 * The sprinkle party meter as plain data: [meter] donuts poured in so far, and [left] party chains still to
 * play (0 = no party on). No Android types.
 */
internal data class PartyState(val meter: Int = 0, val left: Int = 0) {
    val partyOn: Boolean get() = left > 0
}

/**
 * Sprinkle party rules (pure, unit tested). Every donut cleared, by the player or by a cascade, pours one into
 * the meter, short chains included. At [FILL] the meter empties and a party starts: the next [CHAINS] chains the
 * player makes refill with golden donuts at [GOLDEN], instead of [GameBoard.GOLDEN_CHANCE]. The party counts
 * chains, not seconds, so there is never a clock to beat and nothing is lost by ignoring it. The meter does not
 * fill while a party is on.
 */
internal object PartyMeter {
    const val FILL = 60
    const val CHAINS = 4
    const val GOLDEN = 0.3
    const val LABEL = "SPRINKLE PARTY!"

    /** The party's own celebration: sprinkle rain under a pink banner. */
    val LOOK = CelebrationLook(ConfettiStyle.RAIN, 1)

    /**
     * The state after [cleared] donuts leave the board. [playerClear] is true for the player's own chain,
     * false for a cascade; only the player's chains use up party chains.
     */
    fun after(s: PartyState, cleared: Int, playerClear: Boolean): PartyState {
        if (s.partyOn) return if (playerClear) s.copy(left = s.left - 1) else s
        val meter = s.meter + cleared.coerceAtLeast(0)
        return if (meter >= FILL) PartyState(0, CHAINS) else PartyState(meter, 0)
    }

    /** True when the step from [before] to [after] started a party. */
    fun started(before: PartyState, after: PartyState): Boolean = !before.partyOn && after.partyOn

    /** The golden chance the board refills with in state [s]. */
    fun goldenChance(s: PartyState): Double = if (s.partyOn) GOLDEN else GameBoard.GOLDEN_CHANCE

    /** How full the meter looks, 0..1: the donuts poured in, or during a party the chains still to play. */
    fun fill(s: PartyState): Float =
        if (s.partyOn) s.left.toFloat() / CHAINS else (s.meter.toFloat() / FILL).coerceIn(0f, 1f)

    /** [s] read back from saved numbers, clamped so a bad save can never start an endless party. */
    fun restore(meter: Int, left: Int): PartyState =
        PartyState(meter.coerceIn(0, FILL - 1), left.coerceIn(0, CHAINS))
}
