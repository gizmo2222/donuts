package com.donuts.game

/**
 * Power-up tiers unlocked by clearing a long chain.
 *
 *   NONE        — standard clear (chain 3–4)
 *   BOMB        — chain 5–6: clears a 3×3 area around the chain mid-point
 *   ROW_BLAST   — chain 7–8: clears the entire row of the chain mid-point
 *   COLOR_BURST — chain 9+:  clears every cell of the chain's donut type
 */
enum class PowerUp { NONE, BOMB, ROW_BLAST, COLOR_BURST }
