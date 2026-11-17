package ar.com.arieldebrouvier.cocos.services;

import ar.com.arieldebrouvier.cocos.exceptions.NotFoundException;
import ar.com.arieldebrouvier.cocos.models.Instrument;
import ar.com.arieldebrouvier.cocos.repositories.InstrumentRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Access to the instruments that can be traded or used as currency. */
@Service
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;

    public InstrumentService(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    /**
     * Searches instruments by ticker and/or name, case-insensitively: an instrument matches if its ticker
     * starts with the query or its name contains it. LIKE wildcards ({@code %}, {@code _}) in the query
     * match literally.
     *
     * @param query text to search for; must not be null. Surrounding whitespace is ignored
     * @return matching instruments ordered by ticker; empty if none match
     */
    public List<Instrument> search(String query) {
        return instrumentRepository.search(query.trim());
    }

    /**
     * Finds an instrument by id.
     *
     * @param id id of the instrument
     * @return the instrument
     * @throws NotFoundException if the instrument doesn't exist
     */
    public Instrument getById(Integer id) {
        return instrumentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Instrument " + id + " not found"));
    }

    /**
     * Finds several instruments in a single query.
     *
     * @param ids ids of the instruments; may be empty
     * @return instruments keyed by id. Ids that don't exist are absent from the map
     */
    public Map<Integer, Instrument> getByIds(Collection<Integer> ids) {
        return instrumentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Instrument::getId, Function.identity()));
    }
}
