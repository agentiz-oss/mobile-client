package com.example.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal, monochrome design tokens inspired by shadcn/ui, Radix and Tailwind:
 * a white surface, near-black text and a single black primary accent — plus a small set of
 * GitHub-mobile-style state colours: colour is reserved for *state* (успех, провал, внимание)
 * and for the icon tiles of navigation rows, never for chrome.
 */
@Immutable
object AppTheme {
    val Background: Color = Color(0xFFFFFFFF)
    val Foreground: Color = Color(0xFF09090B)
    val Primary: Color = Color(0xFF18181B)
    val PrimaryForeground: Color = Color(0xFFFAFAFA)
    val Muted: Color = Color(0xFF71717A)
    val Border: Color = Color(0xFFE4E4E7)
    val Surface: Color = Color(0xFFFAFAFA)

    /**
     * The backdrop the slide-out menu sits on. A touch darker than [Surface] so the white screen
     * pushed on top of it reads as a card lifted off the page, and so the rounded corners of that
     * card have something to show against.
     */
    val MenuBackground: Color = Color(0xFFF4F4F5)

    /**
     * The backdrop of a screen built out of card blocks, GitHub-mobile style: a light grey page
     * with white [Background] cards sitting on it, so the blocks read as lifted panels rather
     * than as rules drawn on paper. Screens that are one continuous surface keep [Background].
     */
    val PageBackground: Color = Color(0xFFF2F2F7)
    val Danger: Color = Color(0xFFDC2626)

    /**
     * A resolved-well state — the green of GitHub's check-circle. State colour only: успех is an
     * icon or a word, never a filled surface.
     */
    val Success: Color = Color(0xFF1A7F37)

    /**
     * The blue reserved for "this needs a person" — the one in-flight state worth colour. Kept
     * apart from [Primary] (which is chrome), the way GitHub keeps its link blue out of buttons.
     */
    val Accent: Color = Color(0xFF0969DA)

    /**
     * The step before [Danger]: a limit that is filling up but has not closed anything. Amber
     * rather than a lighter red, so "скоро кончится" and "кончилось" are not the same signal read
     * at two brightnesses.
     */
    val Warning: Color = Color(0xFFD97706)
    val Disabled: Color = Color(0xFFA1A1AA)

    /**
     * The two tints GitHub puts behind a row-level action — «Review now» on a notification, a
     * destructive verb next to it. Surfaces only for those: a filled [Accent] or [Danger] button
     * inside a list row shouts louder than the row's own title, which is what the reader is meant
     * to read first.
     */
    val AccentSubtle: Color = Color(0xFFDDF4FF)
    val DangerSubtle: Color = Color(0xFFFFEBE9)

    /**
     * Fill for a run's card in the history list — a pastel blue so faint it reads as a tint rather
     * than a colour, just enough to set "запуск" cards apart from the neutral surfaces around them.
     */
    val RunCard: Color = Color(0xFFF2F7FE)

    /**
     * The colour a focused control's border takes, and the halo drawn around it. Mirrors shadcn's
     * `focus-visible:ring-ring` — the ring is the same hue as the border it replaces, drawn at low
     * opacity so it reads as a glow rather than a second frame.
     */
    val Ring: Color = Color(0xFF18181B)
    val RingHalo: Color = Color(0x2918181B)

    /** Width of the halo outside a focused control's own 1.dp border — shadcn's `ring-[3px]`. */
    val RingWidth: Dp = 3.dp

    val Radius: Dp = 12.dp

    /** Corner radius of an [com.example.app.components.IconTile] — squarer than a card. */
    val TileRadius: Dp = 8.dp

    /**
     * Fills for the icon tiles of navigation rows, GitHub's "My Work" palette: the tile is the
     * only place a saturated colour may be a surface, and the glyph on it is always white.
     */
    val TileGreen: Color = Color(0xFF2DA44E)
    val TileBlue: Color = Color(0xFF0969DA)
    val TilePurple: Color = Color(0xFF8250DF)
    val TileOrange: Color = Color(0xFFDB6D28)
    val TileYellow: Color = Color(0xFFEAC54F)
    val TileGray: Color = Color(0xFF59636E)

    val Title: TextStyle = TextStyle(
        fontSize = 34.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /**
     * The heading over a group of card blocks — GitHub's "My Work" / "Favorites" scale: big and
     * bold enough to carry a page section, smaller than the screen's own [Title].
     */
    val Header: TextStyle = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
    )

    val Subtitle: TextStyle = TextStyle(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    )

    val Body: TextStyle = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    )

    val Label: TextStyle = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    )

    val ButtonLabel: TextStyle = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
    )

    /** Metadata scale — the text of a [com.example.app.components.MetaChip] and of fact labels. */
    val Footnote: TextStyle = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    )
}
