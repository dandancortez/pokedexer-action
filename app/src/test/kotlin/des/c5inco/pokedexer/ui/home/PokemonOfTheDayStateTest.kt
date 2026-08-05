package des.c5inco.pokedexer.ui.home

import des.c5inco.pokedexer.data.pokemon.SamplePokemonData
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PokemonOfTheDayStateTest {
    private val pokemon = SamplePokemonData.take(4)

    @Test
    fun `selection is deterministic regardless of repository order`() {
        val date = LocalDate.of(2026, 8, 4)

        val selected = PokemonOfTheDayState().update(date, pokemon).pokemon
        val selectedFromReordered = PokemonOfTheDayState().update(date, pokemon.reversed()).pokemon

        assertEquals(selected?.id, selectedFromReordered?.id)
    }

    @Test
    fun `date advance chooses pokemon for new day`() {
        val date = LocalDate.ofEpochDay(1)
        val initial = PokemonOfTheDayState().update(date, pokemon)

        val nextDay = initial.update(date.plusDays(1), pokemon)

        assertNotEquals(initial.pokemon?.id, nextDay.pokemon?.id)
    }

    @Test
    fun `same day reorder and addition retain selected id`() {
        val date = LocalDate.of(2026, 8, 4)
        val initial = PokemonOfTheDayState().update(date, pokemon.take(3))
        val reorderedWithAddition = listOf(pokemon[3], pokemon[2], pokemon[0], pokemon[1])

        val updated = initial.update(date, reorderedWithAddition)

        assertEquals(initial.pokemon?.id, updated.pokemon?.id)
    }

    @Test
    fun `empty repository selects pokemon when data becomes available`() {
        val date = LocalDate.of(2026, 8, 4)
        val empty = PokemonOfTheDayState().update(date, emptyList())

        val populated = empty.update(date, pokemon)

        assertNull(empty.pokemon)
        assertEquals(
            PokemonOfTheDayState().update(date, pokemon).pokemon?.id,
            populated.pokemon?.id,
        )
    }

    @Test
    fun `missing selected pokemon is replaced`() {
        val date = LocalDate.of(2026, 8, 4)
        val initial = PokemonOfTheDayState().update(date, pokemon)

        val replacement = initial.update(date, pokemon.filterNot { it.id == initial.pokemon?.id })

        assertNotEquals(initial.pokemon?.id, replacement.pokemon?.id)
    }
}
