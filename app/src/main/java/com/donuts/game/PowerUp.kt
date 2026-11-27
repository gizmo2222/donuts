package com.donuts.game

/**
 * Power-up tiers unlocked by clearing a long chain, or by closing one into a ring.
 *
 *   NONE        — standard clear (chain 3–4)
 *   BOMB        — chain 5–6: clears a 3×3 area around the chain mid-point
 *   ROW_BLAST   — chain 7–8: clears the entire row of the chain mid-point
 *   COLOR_BURST — chain 9+:  clears every cell of the chain's donut type
 *   RING        — a chain of [LoopRules.MIN_LOOP]+ that closes on its start: clears every cell
 *                 of the chain's type, whatever its length (wins over every length tier)
 *   BUBBLE      — a bubble donut ([BubbleRules]) burst by a tap or inside a plain chain: clears the
 *                 3×3 around it. Kept last so saved [PlayTally] slots keep their order.
 */
enum class PowerUp { NONE, BOMB, ROW_BLAST, COLOR_BURST, RING, BUBBLE }
