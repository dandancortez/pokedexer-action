package des.c5inco.pokedexer.ui.home

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import des.c5inco.pokedexer.shared.data.items.ItemsRepository
import des.c5inco.pokedexer.shared.data.moves.MovesRepository
import des.c5inco.pokedexer.shared.data.pokemon.PokemonRepository
import des.c5inco.pokedexer.shared.model.Item
import des.c5inco.pokedexer.shared.model.Move
import des.c5inco.pokedexer.shared.model.Pokemon
import dev.zacsweers.metro.Inject
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchResponse(
    val currentText: String = "",
    val foundPokemon: List<Pokemon> = emptyList(),
    val foundMoves: List<Move> = emptyList(),
    val foundItems: List<Item> = emptyList(),
)

internal data class PokemonOfTheDayState(
    val date: LocalDate? = null,
    val pokemon: Pokemon? = null,
) {
    fun update(date: LocalDate, availablePokemon: List<Pokemon>): PokemonOfTheDayState {
        if (availablePokemon.isEmpty()) return PokemonOfTheDayState(date = date)

        val retainedPokemon =
            pokemon
                ?.takeIf { this.date == date }
                ?.let { selected -> availablePokemon.firstOrNull { it.id == selected.id } }
        val selectedPokemon =
            retainedPokemon
                ?: availablePokemon.sortedBy(Pokemon::id).let { sorted ->
                    sorted[Math.floorMod(date.toEpochDay(), sorted.size.toLong()).toInt()]
                }

        return PokemonOfTheDayState(date = date, pokemon = selectedPokemon)
    }
}

@Inject
class HomeViewModel(
    private val pokemonRepository: PokemonRepository,
    private val movesRepository: MovesRepository,
    private val itemsRepository: ItemsRepository,
) : ViewModel() {
    val searchText = TextFieldState()

    val loading by mutableStateOf(false)

    private val currentDate = MutableStateFlow(LocalDate.now())
    private var midnightJob: Job? = null

    val pokemonOfTheDay: StateFlow<Pokemon?> =
        combine(pokemonRepository.pokemon(), currentDate) { pokemon, date -> date to pokemon }
            .runningFold(PokemonOfTheDayState()) { state, (date, pokemon) ->
                state.update(date, pokemon)
            }
            .map { it.pokemon }
            .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = null)

    val searchResponses: StateFlow<SearchResponse> =
        snapshotFlow { searchText.text }
            .debounce(200)
            .mapLatest {
                val textContent = it.toString()
                if (textContent.isEmpty()) {
                    SearchResponse(currentText = textContent)
                } else {
                    combine(
                            pokemonRepository.getPokemonByName(textContent),
                            movesRepository.getMovesByName(textContent),
                            itemsRepository.getItemsByName(textContent),
                        ) { pokemonResults, movesResults, itemsResults ->
                            SearchResponse(
                                currentText = textContent,
                                foundPokemon = pokemonResults,
                                foundMoves = movesResults,
                                foundItems = itemsResults,
                            )
                        }
                        .first()
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SearchResponse(currentText = ""),
            )

    init {
        refreshPokemonOfTheDay()
    }

    fun refreshPokemonOfTheDay() {
        currentDate.value = LocalDate.now()
        midnightJob?.cancel()
        midnightJob =
            viewModelScope.launch {
                while (true) {
                    val now = ZonedDateTime.now()
                    val nextMidnight =
                        now.toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault())
                    delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1))
                    currentDate.value = LocalDate.now()
                }
            }
    }
}
