package ar.com.arieldebrouvier.cocos.repositories;

import ar.com.arieldebrouvier.cocos.models.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InstrumentRepository extends JpaRepository<Instrument, Integer> {

    /**
     * Case-insensitive search: ticker starting with the query, or name containing it.
     * LIKE wildcards ({@code %}, {@code _}) in the query match literally.
     *
     * @param query text to search for, as typed by the user
     * @return matching instruments ordered by ticker
     */
    default List<Instrument> search(String query) {
        return searchEscaped(EscapeCharacter.DEFAULT.escape(query));
    }

    /**
     * Use {@link #search}: expects a query with LIKE wildcards escaped with '\' (the escape character declared in
     * the query). Both sides are lower-cased by the database, so the comparison follows a single case rule.
     */
    @Query("""
            select i from Instrument i
            where lower(i.ticker) like lower(concat(:q, '%')) escape '\\'
               or lower(i.name) like lower(concat('%', :q, '%')) escape '\\'
            order by i.ticker
            """)
    List<Instrument> searchEscaped(@Param("q") String escapedQuery);
}
